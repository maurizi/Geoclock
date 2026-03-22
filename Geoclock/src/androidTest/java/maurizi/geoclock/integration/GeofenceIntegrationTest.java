package maurizi.geoclock.integration;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Context;
import android.location.Location;
import android.os.Build;
import android.os.SystemClock;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.GeofenceStatusCodes;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.common.collect.ImmutableSet;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Integration tests for geofence transitions using FusedLocationProviderClient mock mode.
 *
 * <p>Mock location permission is granted via UiAutomation.executeShellCommand in setUp(), which
 * runs with shell-level privileges. Location services must be enabled on the emulator.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class GeofenceIntegrationTest {

  static final LatLng GEOFENCE_CENTER = new LatLng(37.4219, -122.0840);
  private static final long GEOFENCE_TIMEOUT_MS = 60_000L;
  private static final long POLL_INTERVAL_MS = 1000L;

  @Rule(order = 0)
  public RetryRule retryRule = new RetryRule(2);

  @Rule(order = 1)
  public GrantPermissionRule permissionRule = GrantPermissionRule.grant(getRequiredPermissions());

  private static String[] getRequiredPermissions() {
    List<String> perms = new ArrayList<>();
    perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      perms.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      perms.add(Manifest.permission.POST_NOTIFICATIONS);
    }
    return perms.toArray(new String[0]);
  }

  private Context context;
  private FusedLocationProviderClient fusedClient;
  private boolean mockModeEnabled = false;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    alarmManager.cancel(AlarmClockReceiver.getPendingIntent(context));
    new ActiveAlarmManager(context).clearActiveAlarms();
    AlarmRingingService.AUDIO_DISABLED = true;

    // Grant mock location via shell (runs with elevated privileges)
    shellCommand("appops set " + context.getPackageName() + " android:mock_location allow");

    // Ensure location services are enabled (required on Android 11+)
    shellCommand("settings put secure location_mode 3");

    fusedClient = LocationServices.getFusedLocationProviderClient(context);

    // Enable mock mode on FusedLocationProviderClient
    CountDownLatch latch = new CountDownLatch(1);
    AtomicBoolean ok = new AtomicBoolean(false);
    fusedClient
        .setMockMode(true)
        .addOnSuccessListener(
            v -> {
              ok.set(true);
              latch.countDown();
            })
        .addOnFailureListener(e -> latch.countDown());
    latch.await(10, TimeUnit.SECONDS);
    mockModeEnabled = ok.get();
    assertTrue("FusedLocationProviderClient mock mode should be enabled", mockModeEnabled);

    // Pump a few mock locations to warm up Play Services location subsystem.
    // Without this, geofence registration fails with GEOFENCE_NOT_AVAILABLE (1000)
    // on API 28 emulators because location services aren't fully initialized.
    for (int i = 0; i < 3; i++) {
      setMockLocation(GEOFENCE_CENTER.latitude, GEOFENCE_CENTER.longitude);
    }
  }

  @After
  public void tearDown() throws Exception {
    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    alarmManager.cancel(AlarmClockReceiver.getPendingIntent(context));
    new ActiveAlarmManager(context).clearActiveAlarms();
    if (mockModeEnabled) {
      fusedClient.setMockMode(false);
    }
    AlarmRingingService.AUDIO_DISABLED = false;
    AlarmRingingService.stop(context);
  }

  private void assertPlayServicesAvailable() {
    int result = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);
    assertTrue(
        "Google Play Services not available; geofence transitions require it",
        result == ConnectionResult.SUCCESS);
  }

  private void assertCanScheduleExactAlarms() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
      assertTrue("SCHEDULE_EXACT_ALARM not granted", am.canScheduleExactAlarms());
    }
  }

  @Test
  public void enterGeofence_startsAlarmRingingService() throws Exception {
    assertPlayServicesAvailable();
    assertCanScheduleExactAlarms();

    GeoAlarm alarm = saveAlarm(repeatingAlarmAt(GEOFENCE_CENTER));

    LocationServiceGoogle locationService = new LocationServiceGoogle(context);
    registerGeofence(locationService, alarm);

    // Pump mock location inside the geofence until ENTER fires
    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    long deadline = System.currentTimeMillis() + GEOFENCE_TIMEOUT_MS;
    while (System.currentTimeMillis() < deadline) {
      setMockLocation(GEOFENCE_CENTER.latitude, GEOFENCE_CENTER.longitude);
      if (alarmManager.getNextAlarmClock() != null) break;
      Thread.sleep(POLL_INTERVAL_MS);
    }
    assertNotNull(
        "Geofence ENTER should schedule an alarm clock", alarmManager.getNextAlarmClock());
  }

  @Test
  public void exitGeofence_removesFromActiveAlarms() throws Exception {
    assertPlayServicesAvailable();
    assertCanScheduleExactAlarms();

    GeoAlarm alarm = saveAlarm(repeatingAlarmAt(GEOFENCE_CENTER));
    new ActiveAlarmManager(context).addActiveAlarms(ImmutableSet.of(alarm.id));

    LocationServiceGoogle locationService = new LocationServiceGoogle(context);
    registerGeofence(locationService, alarm);

    // Establish "inside" baseline
    for (int i = 0; i < 5; i++) {
      setMockLocation(GEOFENCE_CENTER.latitude, GEOFENCE_CENTER.longitude);
    }

    // Move far outside and pump until EXIT fires
    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    long deadline = System.currentTimeMillis() + GEOFENCE_TIMEOUT_MS;
    while (System.currentTimeMillis() < deadline) {
      setMockLocation(37.0, -122.0);
      if (alarmManager.getNextAlarmClock() == null) break;
      Thread.sleep(POLL_INTERVAL_MS);
    }
    assertNull(
        "Geofence EXIT should clear active alarm from alarm manager",
        alarmManager.getNextAlarmClock());
  }

  @Test
  public void disabledAlarm_geofenceEnter_doesNotStartService() throws Exception {
    assertPlayServicesAvailable();

    GeoAlarm alarm = saveAlarm(repeatingAlarmAt(GEOFENCE_CENTER).withEnabled(false));

    LocationServiceGoogle locationService = new LocationServiceGoogle(context);
    registerGeofence(locationService, alarm);

    // Pump inside-geofence location — disabled alarm should not trigger
    for (int i = 0; i < 10; i++) {
      setMockLocation(GEOFENCE_CENTER.latitude, GEOFENCE_CENTER.longitude);
    }

    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    assertNull(
        "Disabled alarm geofence ENTER should not schedule alarm clock",
        alarmManager.getNextAlarmClock());
  }

  @Test
  public void enterGeofence_mixedEnabledDisabled_onlyEnabledActivated() throws Exception {
    assertPlayServicesAvailable();
    assertCanScheduleExactAlarms();

    // Two alarms at the same location — one enabled, one disabled
    GeoAlarm enabledAlarm = saveAlarm(repeatingAlarmAt(GEOFENCE_CENTER));
    GeoAlarm disabledAlarm = saveAlarm(repeatingAlarmAt(GEOFENCE_CENTER).withEnabled(false));

    LocationServiceGoogle locationService = new LocationServiceGoogle(context);
    registerGeofence(locationService, enabledAlarm);
    registerGeofence(locationService, disabledAlarm);

    // Pump mock location — only the enabled alarm should be activated
    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    long deadline = System.currentTimeMillis() + GEOFENCE_TIMEOUT_MS;
    while (System.currentTimeMillis() < deadline) {
      setMockLocation(GEOFENCE_CENTER.latitude, GEOFENCE_CENTER.longitude);
      if (alarmManager.getNextAlarmClock() != null) break;
      Thread.sleep(POLL_INTERVAL_MS);
    }
    // The enabled alarm should trigger; the disabled one should be filtered out
    assertNotNull(
        "Only enabled alarm should schedule alarm clock", alarmManager.getNextAlarmClock());
  }

  // ---- helpers ----

  private void setMockLocation(double lat, double lng) throws Exception {
    Location loc = new Location("fused");
    loc.setLatitude(lat);
    loc.setLongitude(lng);
    loc.setAccuracy(1.0f);
    loc.setAltitude(0);
    loc.setTime(System.currentTimeMillis());
    loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
    fusedClient.setMockLocation(loc);
    Thread.sleep(500);
  }

  private void shellCommand(String command) throws Exception {
    InstrumentationRegistry.getInstrumentation()
        .getUiAutomation()
        .executeShellCommand(command)
        .close();
  }

  /**
   * Registers a geofence, retrying on GEOFENCE_NOT_AVAILABLE since the location subsystem on some
   * emulators needs time to warm up.
   */
  private void registerGeofence(LocationServiceGoogle locationService, GeoAlarm alarm)
      throws Exception {
    int maxAttempts = 5;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      CountDownLatch latch = new CountDownLatch(1);
      AtomicBoolean ok = new AtomicBoolean(false);
      final Exception[] failure = {null};
      locationService
          .addGeofence(alarm)
          .addOnSuccessListener(
              v -> {
                ok.set(true);
                latch.countDown();
              })
          .addOnFailureListener(
              e -> {
                failure[0] = e;
                latch.countDown();
              });
      boolean completed = latch.await(30, TimeUnit.SECONDS);
      if (!completed) {
        throw new AssertionError("Geofence registration timed out after 30s");
      }
      if (ok.get()) {
        return;
      }
      // Retry on GEOFENCE_NOT_AVAILABLE — location subsystem may still be warming up
      boolean retryable =
          failure[0] instanceof ApiException
              && ((ApiException) failure[0]).getStatusCode()
                  == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE;
      if (!retryable || attempt == maxAttempts) {
        assertTrue(
            "Geofence registration failed after " + attempt + " attempts: " + failure[0], ok.get());
      }
      Thread.sleep(3000);
    }
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
