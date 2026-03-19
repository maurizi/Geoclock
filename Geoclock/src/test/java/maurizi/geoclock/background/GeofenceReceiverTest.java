package maurizi.geoclock.background;

import static org.junit.Assert.assertNull;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.maps.model.LatLng;
import com.google.common.collect.ImmutableSet;
import java.time.DayOfWeek;
import java.util.UUID;
import maurizi.geoclock.GeoAlarm;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlarmManager;

/**
 * Unit tests for GeofenceReceiver.
 *
 * <p>NOTE: Tests that require a valid GeofencingEvent (ENTER/EXIT transitions with real geofencing
 * data) are not achievable in Robolectric because GeofencingEvent is a Play Services internal class
 * whose intent format is not publicly documented. Those paths are covered by instrumentation tests
 * in integration/GeofenceIntegrationTest.java.
 *
 * <p>These unit tests cover the defensive/error paths and verify no crashes on malformed input.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
@SuppressWarnings("deprecation") // ShadowAlarmManager.getNextScheduledAlarm — no replacement
public class GeofenceReceiverTest {

  private Context context;
  private GeofenceReceiver receiver;
  private ShadowAlarmManager shadowAlarmManager;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    receiver = new GeofenceReceiver();
    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    shadowAlarmManager = Shadows.shadowOf(alarmManager);
    ShadowAlarmManager.setCanScheduleExactAlarms(true);
  }

  @Test
  public void onReceive_nullGeofencingEvent_doesNotCrash() {
    // A plain intent produces a null GeofencingEvent — receiver should return silently
    Intent plainIntent = new Intent(context, GeofenceReceiver.class);
    receiver.onReceive(context, plainIntent);
    // No alarm should be scheduled
    assertNull(shadowAlarmManager.getNextScheduledAlarm());
  }

  @Test
  public void onReceive_nullGeofencingEvent_doesNotScheduleAlarm() {
    saveAlarm(enabledAlarm());
    Intent plainIntent = new Intent(context, GeofenceReceiver.class);
    receiver.onReceive(context, plainIntent);
    assertNull(shadowAlarmManager.getNextScheduledAlarm());
  }

  @Test
  public void onReceive_withEnabledAlarmInPrefs_nullEvent_doesNotSchedule() {
    // Even with a valid alarm saved, a null event means no transition is processed
    GeoAlarm alarm = saveAlarm(enabledAlarm());
    Intent intent = new Intent(context, GeofenceReceiver.class);
    intent.putExtra("alarm_id", alarm.id.toString());
    receiver.onReceive(context, intent);
    assertNull(shadowAlarmManager.getNextScheduledAlarm());
  }

  @Test
  public void onReceive_withDisabledAlarm_nullEvent_doesNotSchedule() {
    GeoAlarm alarm = saveAlarm(enabledAlarm().withEnabled(false));
    Intent intent = new Intent(context, GeofenceReceiver.class);
    intent.putExtra("alarm_id", alarm.id.toString());
    receiver.onReceive(context, intent);
    assertNull(shadowAlarmManager.getNextScheduledAlarm());
  }

  @Test
  public void onReceive_withUnknownAlarmId_nullEvent_doesNotCrash() {
    Intent intent = new Intent(context, GeofenceReceiver.class);
    intent.putExtra("alarm_id", UUID.randomUUID().toString());
    receiver.onReceive(context, intent);
    assertNull(shadowAlarmManager.getNextScheduledAlarm());
  }

  // ---- helpers ----

  private GeoAlarm enabledAlarm() {
    return GeoAlarm.builder()
        .id(UUID.randomUUID())
        .location(new LatLng(37.4, -122.0))
        .radius(100)
        .enabled(true)
        .hour(8)
        .minute(0)
        .days(ImmutableSet.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
        .build();
  }

  private GeoAlarm saveAlarm(GeoAlarm alarm) {
    GeoAlarm.save(context, alarm);
    return alarm;
  }
}
