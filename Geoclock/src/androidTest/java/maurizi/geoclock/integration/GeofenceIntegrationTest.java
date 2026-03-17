package maurizi.geoclock.integration;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Context;
import android.location.Location;
import android.os.Build;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.GrantPermissionRule;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.common.collect.ImmutableSet;
import java.time.DayOfWeek;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.background.AlarmClockReceiver;
import maurizi.geoclock.background.AlarmRingingService;
import maurizi.geoclock.utils.ActiveAlarmManager;
import maurizi.geoclock.utils.LocationServiceGoogle;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Integration tests for geofence transitions using mock locations.
 *
 * <p>These tests require a google_apis emulator image (Play Services). They are
 * annotated @LargeTest because geofence evaluation by Google Play Services can take up to 30
 * seconds.
 *
 * <p>If flaky in CI, annotate with @RequiresDevice and exclude from automated runs using: gradlew
 * connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.notAnnotation=...
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class GeofenceIntegrationTest {

  private static final long GEOFENCE_TIMEOUT_MS = 60_000L;
  private static final long POLL_INTERVAL_MS = 1000L;

  @Rule(order = 0)
  public RetryRule retryRule = new RetryRule(2);

  @Rule(order = 1)
  public GrantPermissionRule notificationPermission =
      Build.VERSION.SDK_INT >= 33
          ? GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
          : GrantPermissionRule.grant();

  private Context context;
  private FusedLocationProviderClient fusedClient;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    // Clear any AlarmManager state left by previous tests
    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    alarmManager.cancel(AlarmClockReceiver.getPendingIntent(context));
    new ActiveAlarmManager(context).clearActiveAlarms();
    fusedClient = LocationServices.getFusedLocationProviderClient(context);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicBoolean mockModeOk = new AtomicBoolean(false);
    fusedClient
        .setMockMode(true)
        .addOnSuccessListener(
            v -> {
              mockModeOk.set(true);
              latch.countDown();
            })
        .addOnFailureListener(e -> latch.countDown());
    latch.await(5, TimeUnit.SECONDS);
    Assume.assumeTrue("Mock location not available", mockModeOk.get());
  }

  @After
  public void tearDown() throws Exception {
    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    alarmManager.cancel(AlarmClockReceiver.getPendingIntent(context));
    new ActiveAlarmManager(context).clearActiveAlarms();
    fusedClient.setMockMode(false);
    AlarmRingingService.stop(context);
  }

  private void assumePlayServices() {
    int result = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);
    Assume.assumeTrue(
        "Google Play Services not available; geofence transitions require it",
        result == ConnectionResult.SUCCESS);
  }

  private void assumeCanScheduleExactAlarms() {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
      AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
      Assume.assumeTrue("SCHEDULE_EXACT_ALARM not granted; skipping", am.canScheduleExactAlarms());
    }
  }

  @Test
  public void enterGeofence_startsAlarmRingingService() throws Exception {
    assumePlayServices();
    assumeCanScheduleExactAlarms();
    LatLng alarmLocation = new LatLng(37.4219, -122.0840);
    GeoAlarm alarm = saveAlarm(repeatingAlarmAt(alarmLocation));

    // Register geofences
    LocationServiceGoogle locationService = new LocationServiceGoogle(context);
    CountDownLatch geofenceLatch = new CountDownLatch(1);
    AtomicBoolean geofenceOk = new AtomicBoolean(false);
    locationService
        .addGeofence(alarm)
        .addOnSuccessListener(
            v -> {
              geofenceOk.set(true);
              geofenceLatch.countDown();
            })
        .addOnFailureListener(e -> geofenceLatch.countDown());
    geofenceLatch.await(10, TimeUnit.SECONDS);
    Assume.assumeTrue(
        "Geofence registration failed (GEOFENCE_NOT_AVAILABLE on emulator)", geofenceOk.get());

    // Poll for AlarmManager to receive the alarm (geofence ENTER → addActiveAlarms).
    // Send repeated mock locations; newer API levels need sustained updates.
    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    long deadline = System.currentTimeMillis() + GEOFENCE_TIMEOUT_MS;
    while (System.currentTimeMillis() < deadline) {
      setMockLocation(alarmLocation.latitude, alarmLocation.longitude);
      if (alarmManager.getNextAlarmClock() != null) break;
      Thread.sleep(POLL_INTERVAL_MS);
    }
    assertNotNull(
        "Geofence ENTER should schedule an alarm clock", alarmManager.getNextAlarmClock());
  }

  @Test
  public void exitGeofence_removesFromActiveAlarms() throws Exception {
    assumePlayServices();
    assumeCanScheduleExactAlarms();
    LatLng alarmLocation = new LatLng(37.4219, -122.0840);
    GeoAlarm alarm = saveAlarm(repeatingAlarmAt(alarmLocation));

    // Pre-add as active
    new ActiveAlarmManager(context).addActiveAlarms(ImmutableSet.of(alarm.id));

    // Register geofence and simulate being inside
    LocationServiceGoogle locationService = new LocationServiceGoogle(context);
    CountDownLatch geofenceLatch = new CountDownLatch(1);
    AtomicBoolean geofenceOk = new AtomicBoolean(false);
    locationService
        .addGeofence(alarm)
        .addOnSuccessListener(
            v -> {
              geofenceOk.set(true);
              geofenceLatch.countDown();
            })
        .addOnFailureListener(e -> geofenceLatch.countDown());
    geofenceLatch.await(10, TimeUnit.SECONDS);
    Assume.assumeTrue(
        "Geofence registration failed (GEOFENCE_NOT_AVAILABLE on emulator)", geofenceOk.get());
    setMockLocation(alarmLocation.latitude, alarmLocation.longitude);
    Thread.sleep(3000);

    // Now move away — EXIT transition
    // Send repeated mock locations outside the geofence; newer API levels need
    // sustained location updates before Play Services fires the EXIT transition.
    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    long deadline = System.currentTimeMillis() + GEOFENCE_TIMEOUT_MS;
    while (System.currentTimeMillis() < deadline) {
      setMockLocation(0.0, 0.0);
      if (alarmManager.getNextAlarmClock() == null) break;
      Thread.sleep(POLL_INTERVAL_MS);
    }
    assertNull(
        "Geofence EXIT should clear active alarm from alarm manager",
        alarmManager.getNextAlarmClock());
  }

  @Test
  public void disabledAlarm_geofenceEnter_doesNotStartService() throws Exception {
    assumePlayServices();
    LatLng alarmLocation = new LatLng(37.4219, -122.0840);
    GeoAlarm alarm = saveAlarm(repeatingAlarmAt(alarmLocation).withEnabled(false));

    LocationServiceGoogle locationService = new LocationServiceGoogle(context);
    CountDownLatch geofenceLatch = new CountDownLatch(1);
    AtomicBoolean geofenceOk = new AtomicBoolean(false);
    locationService
        .addGeofence(alarm)
        .addOnSuccessListener(
            v -> {
              geofenceOk.set(true);
              geofenceLatch.countDown();
            })
        .addOnFailureListener(e -> geofenceLatch.countDown());
    geofenceLatch.await(10, TimeUnit.SECONDS);
    Assume.assumeTrue(
        "Geofence registration failed (GEOFENCE_NOT_AVAILABLE on emulator)", geofenceOk.get());

    setMockLocation(alarmLocation.latitude, alarmLocation.longitude);
    Thread.sleep(5000); // short wait — disabled alarm should not trigger

    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    assertNull(
        "Disabled alarm geofence ENTER should not schedule alarm clock",
        alarmManager.getNextAlarmClock());
  }

  // ---- helpers ----

  private void setMockLocation(double lat, double lng) throws Exception {
    Location mockLocation = new Location("mock");
    mockLocation.setLatitude(lat);
    mockLocation.setLongitude(lng);
    mockLocation.setAccuracy(1.0f);
    mockLocation.setTime(System.currentTimeMillis());
    mockLocation.setElapsedRealtimeNanos(android.os.SystemClock.elapsedRealtimeNanos());
    fusedClient.setMockLocation(mockLocation);
    Thread.sleep(500);
  }

  private GeoAlarm repeatingAlarmAt(LatLng location) {
    return GeoAlarm.builder()
        .id(UUID.randomUUID())
        .location(location)
        .radius(100)
        .enabled(true)
        .hour(8)
        .minute(0)
        .days(ImmutableSet.copyOf(DayOfWeek.values()))
        .build();
  }

  private GeoAlarm saveAlarm(GeoAlarm alarm) {
    GeoAlarm.save(context, alarm);
    return alarm;
  }
}
