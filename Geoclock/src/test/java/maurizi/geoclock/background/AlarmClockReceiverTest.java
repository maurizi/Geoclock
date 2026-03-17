package maurizi.geoclock.background;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.maps.model.LatLng;
import java.util.UUID;
import maurizi.geoclock.GeoAlarm;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowNotificationManager;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class AlarmClockReceiverTest {

  private Context context;
  private AlarmClockReceiver receiver;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    receiver = new AlarmClockReceiver();
  }

  @Test
  public void onReceive_noAlarmIdExtra_doesNotStartActivity() {
    receiver.onReceive(context, new Intent(context, AlarmClockReceiver.class));
    assertNull(Shadows.shadowOf((android.app.Application) context).getNextStartedActivity());
  }

  @Test
  public void onReceive_unknownAlarmId_doesNotStartActivity() {
    Intent intent = new Intent(context, AlarmClockReceiver.class);
    intent.putExtra("alarm_id", UUID.randomUUID().toString());

    receiver.onReceive(context, intent);

    assertNull(Shadows.shadowOf((android.app.Application) context).getNextStartedActivity());
  }

  @Test
  public void onReceive_enabledAlarm_startsAlarmRingingService() {
    GeoAlarm alarm = saveAlarm(enabledAlarm());

    receiver.onReceive(context, alarmIntent(alarm));

    // Audio and full-screen intent are now handled by AlarmRingingService, not startActivity.
    assertNull(Shadows.shadowOf((android.app.Application) context).getNextStartedActivity());
    Intent startedService =
        Shadows.shadowOf((android.app.Application) context).getNextStartedService();
    assertNotNull("Expected AlarmRingingService to be started", startedService);
    assertEquals(AlarmRingingService.class.getName(), startedService.getComponent().getClassName());
  }

  @Test
  public void onReceive_disabledAlarm_doesNotStartActivity() {
    GeoAlarm alarm = saveAlarm(enabledAlarm().withEnabled(false));
    receiver.onReceive(context, alarmIntent(alarm));
    assertNull(Shadows.shadowOf((android.app.Application) context).getNextStartedActivity());
  }

  @Test
  public void onReceive_nonRepeatingAlarm_disablesAlarmAfterFiring() {
    GeoAlarm alarm = saveAlarm(enabledAlarm()); // no days = non-repeating
    receiver.onReceive(context, alarmIntent(alarm));

    // Alarm should be saved as disabled
    GeoAlarm saved = GeoAlarm.getGeoAlarm(context, alarm.id);
    assertNotNull(saved);
    assertEquals(false, saved.enabled);
  }

  @Test
  public void onReceive_repeatingAlarm_remainsEnabled() {
    GeoAlarm alarm = saveAlarm(enabledRepeatingAlarm());
    receiver.onReceive(context, alarmIntent(alarm));

    GeoAlarm saved = GeoAlarm.getGeoAlarm(context, alarm.id);
    assertNotNull(saved);
    assertEquals(true, saved.enabled);
  }

  // ---- Cancel upcoming ----

  @Test
  public void onReceive_cancelUpcoming_disablesAlarm() {
    GeoAlarm alarm = saveAlarm(enabledAlarm());
    Intent intent = new Intent(context, AlarmClockReceiver.class);
    intent.setAction(AlarmClockReceiver.ACTION_CANCEL_UPCOMING);
    intent.putExtra("alarm_id", alarm.id.toString());
    receiver.onReceive(context, intent);
    GeoAlarm saved = GeoAlarm.getGeoAlarm(context, alarm.id);
    assertNotNull(saved);
    assertEquals(false, saved.enabled);
  }

  @Test
  public void onReceive_cancelUpcoming_cancelsNotification() {
    NotificationManager nm =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    ShadowNotificationManager shadowNm = Shadows.shadowOf(nm);

    GeoAlarm alarm = saveAlarm(enabledAlarm());
    Intent intent = new Intent(context, AlarmClockReceiver.class);
    intent.setAction(AlarmClockReceiver.ACTION_CANCEL_UPCOMING);
    intent.putExtra("alarm_id", alarm.id.toString());
    receiver.onReceive(context, intent);

    // The NOTIFICATION_ID from NotificationReceiver should be cancelled
    assertEquals(0, shadowNm.getAllNotifications().size());
  }

  @Test
  public void onReceive_cancelUpcoming_unknownAlarmId_doesNotCrash() {
    Intent intent = new Intent(context, AlarmClockReceiver.class);
    intent.setAction(AlarmClockReceiver.ACTION_CANCEL_UPCOMING);
    intent.putExtra("alarm_id", UUID.randomUUID().toString());
    receiver.onReceive(context, intent); // should not throw
  }

  // ---- Snooze bypass ----

  @Test
  public void onReceive_snoozedDisabledAlarm_startsAlarmRingingService() {
    GeoAlarm alarm = saveAlarm(enabledAlarm().withEnabled(false));
    Intent intent = new Intent(context, AlarmClockReceiver.class);
    intent.putExtra("alarm_id", alarm.id.toString());
    intent.putExtra(AlarmClockReceiver.EXTRA_IS_SNOOZE, true);
    receiver.onReceive(context, intent);
    Intent startedService =
        Shadows.shadowOf((android.app.Application) context).getNextStartedService();
    assertNotNull(
        "Snoozed alarm should start AlarmRingingService even if disabled", startedService);
    assertEquals(AlarmRingingService.class.getName(), startedService.getComponent().getClassName());
  }

  @Test
  public void onReceive_disabledAlarm_noSnoozeFlag_doesNotStartService() {
    GeoAlarm alarm = saveAlarm(enabledAlarm().withEnabled(false));
    receiver.onReceive(context, alarmIntent(alarm));
    assertNull(
        "Disabled alarm without snooze flag should not start service",
        Shadows.shadowOf((android.app.Application) context).getNextStartedService());
  }

  // ---- helpers ----

  private GeoAlarm enabledAlarm() {
    return GeoAlarm.builder()
        .id(UUID.randomUUID())
        .location(new LatLng(0, 0))
        .radius(100)
        .enabled(true)
        .hour(8)
        .minute(0)
        .build();
  }

  private GeoAlarm enabledRepeatingAlarm() {
    return enabledAlarm()
        .withDays(
            com.google.common.collect.ImmutableSet.of(
                java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.FRIDAY));
  }

  private GeoAlarm saveAlarm(GeoAlarm alarm) {
    GeoAlarm.save(context, alarm);
    return alarm;
  }

  private Intent alarmIntent(GeoAlarm alarm) {
    Intent intent = new Intent(context, AlarmClockReceiver.class);
    intent.putExtra("alarm_id", alarm.id.toString());
    return intent;
  }
}
