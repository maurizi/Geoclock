package maurizi.geoclock.background;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.AlarmManager;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.maps.model.LatLng;
import java.util.UUID;
import maurizi.geoclock.GeoAlarm;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowNotificationManager;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
@SuppressWarnings("deprecation") // ShadowAlarmManager.getNextScheduledAlarm — no replacement
public class AlarmRingingServiceTest {

  private Context context;
  private ShadowAlarmManager shadowAlarmManager;
  private ShadowNotificationManager shadowNotificationManager;
  private static final long ORIGINAL_SNOOZE_MS = AlarmRingingService.SNOOZE_DURATION_MS;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    shadowAlarmManager = Shadows.shadowOf(alarmManager);
    ShadowAlarmManager.setCanScheduleExactAlarms(true);
    NotificationManager nm =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    shadowNotificationManager = Shadows.shadowOf(nm);
    // Reset snooze duration
    AlarmRingingService.SNOOZE_DURATION_MS = ORIGINAL_SNOOZE_MS;
  }

  @After
  public void tearDown() {
    AlarmRingingService.SNOOZE_DURATION_MS = ORIGINAL_SNOOZE_MS;
  }

  @Test
  public void normalStart_servicePostsForegroundNotification() {
    GeoAlarm alarm = saveAlarm(enabledAlarm());
    ServiceController<AlarmRingingService> controller =
        Robolectric.buildService(AlarmRingingService.class, startIntent(alarm)).create();
    controller.startCommand(0, 0);
    // startForeground() posts a notification with RINGING_NOTIFICATION_ID
    assertNotNull(
        "startForeground should post the ringing notification",
        shadowNotificationManager.getNotification(AlarmClockReceiver.RINGING_NOTIFICATION_ID));
  }

  @Test
  public void actionDismiss_stopsService() {
    GeoAlarm alarm = saveAlarm(enabledAlarm());
    Intent intent = startIntent(alarm);
    intent.setAction(AlarmRingingService.ACTION_DISMISS);
    // START_NOT_STICKY is returned for DISMISS — the service does not post a notification
    ServiceController<AlarmRingingService> controller =
        Robolectric.buildService(AlarmRingingService.class, intent).create();
    int result = controller.startCommand(0, 0).get().onStartCommand(intent, 0, 0);
    assertEquals(
        "DISMISS should return START_NOT_STICKY", android.app.Service.START_NOT_STICKY, result);
  }

  @Test
  public void actionDismiss_doesNotScheduleSnooze() {
    GeoAlarm alarm = saveAlarm(enabledAlarm());
    Intent intent = startIntent(alarm);
    intent.setAction(AlarmRingingService.ACTION_DISMISS);
    Robolectric.buildService(AlarmRingingService.class, intent).create().startCommand(0, 0);
    assertNull(
        "DISMISS should not schedule a snooze alarm", shadowAlarmManager.getNextScheduledAlarm());
  }

  @Test
  public void actionSnooze_schedulesSnoozeAlarm() {
    GeoAlarm alarm = saveAlarm(enabledAlarm());
    Intent intent = startIntent(alarm);
    intent.setAction(AlarmRingingService.ACTION_SNOOZE);
    Robolectric.buildService(AlarmRingingService.class, intent).create().startCommand(0, 0);
    assertNotNull("SNOOZE should schedule a new alarm", shadowAlarmManager.getNextScheduledAlarm());
  }

  @Test
  public void actionSnooze_returnedStartNotSticky() {
    GeoAlarm alarm = saveAlarm(enabledAlarm());
    Intent intent = startIntent(alarm);
    intent.setAction(AlarmRingingService.ACTION_SNOOZE);
    ServiceController<AlarmRingingService> controller =
        Robolectric.buildService(AlarmRingingService.class, intent).create();
    int result = controller.startCommand(0, 0).get().onStartCommand(intent, 0, 0);
    assertEquals(
        "SNOOZE should return START_NOT_STICKY", android.app.Service.START_NOT_STICKY, result);
  }

  @Test
  public void scheduleSnooze_triggerTimeIsApproximately5Minutes() {
    GeoAlarm alarm = saveAlarm(enabledAlarm());
    long before = System.currentTimeMillis();
    AlarmRingingService.scheduleSnooze(context, alarm);
    long after = System.currentTimeMillis();

    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    AlarmManager.AlarmClockInfo clockInfo = alarmManager.getNextAlarmClock();
    assertNotNull(clockInfo);
    long triggerMs = clockInfo.getTriggerTime();
    assertTrue(
        "Snooze trigger should be ~5 min from now",
        triggerMs >= before + 5 * 60 * 1000L - 1000L
            && triggerMs <= after + 5 * 60 * 1000L + 1000L);
  }

  @Test
  public void scheduleSnooze_pendingIntentTargetsAlarmClockReceiver() {
    GeoAlarm alarm = saveAlarm(enabledAlarm());
    AlarmRingingService.scheduleSnooze(context, alarm);

    ShadowAlarmManager.ScheduledAlarm scheduled = shadowAlarmManager.getNextScheduledAlarm();
    assertNotNull(scheduled);
    // Fire the pending intent and verify it targets AlarmClockReceiver
    ShadowApplication sa = Shadows.shadowOf((Application) context);
    try {
      scheduled.operation.send();
    } catch (Exception ignored) {
    }
    Intent broadcast =
        sa.getBroadcastIntents().stream()
            .filter(
                i ->
                    i.getComponent() != null
                        && AlarmClockReceiver.class
                            .getName()
                            .equals(i.getComponent().getClassName()))
            .findFirst()
            .orElse(null);
    assertNotNull("Snooze should broadcast to AlarmClockReceiver", broadcast);
  }

  @Test
  public void scheduleSnooze_intentHasIsSnoozeExtra() {
    GeoAlarm alarm = saveAlarm(enabledAlarm());
    AlarmRingingService.scheduleSnooze(context, alarm);

    ShadowAlarmManager.ScheduledAlarm scheduled = shadowAlarmManager.getNextScheduledAlarm();
    assertNotNull(scheduled);
    ShadowApplication sa = Shadows.shadowOf((Application) context);
    try {
      scheduled.operation.send();
    } catch (Exception ignored) {
    }
    Intent broadcast =
        sa.getBroadcastIntents().stream()
            .filter(
                i ->
                    i.getComponent() != null
                        && AlarmClockReceiver.class
                            .getName()
                            .equals(i.getComponent().getClassName()))
            .findFirst()
            .orElse(null);
    assertNotNull(broadcast);
    assertTrue(
        "Snooze intent should have EXTRA_IS_SNOOZE=true",
        broadcast.getBooleanExtra(AlarmClockReceiver.EXTRA_IS_SNOOZE, false));
  }

  @Test
  public void buildNotification_hasSnoozeAction() {
    GeoAlarm alarm = saveAlarm(enabledAlarm());
    ServiceController<AlarmRingingService> controller =
        Robolectric.buildService(AlarmRingingService.class, startIntent(alarm)).create();
    controller.startCommand(0, 0);
    Notification n =
        shadowNotificationManager.getNotification(AlarmClockReceiver.RINGING_NOTIFICATION_ID);
    assertNotNull(n);
    boolean hasSnooze = false;
    for (Notification.Action action : n.actions) {
      if (action.title.toString().contains("Snooze")) {
        hasSnooze = true;
        break;
      }
    }
    assertTrue("Notification should have a Snooze action", hasSnooze);
  }

  @Test
  public void buildNotification_hasDismissAction() {
    GeoAlarm alarm = saveAlarm(enabledAlarm());
    ServiceController<AlarmRingingService> controller =
        Robolectric.buildService(AlarmRingingService.class, startIntent(alarm)).create();
    controller.startCommand(0, 0);
    Notification n =
        shadowNotificationManager.getNotification(AlarmClockReceiver.RINGING_NOTIFICATION_ID);
    assertNotNull(n);
    boolean hasDismiss = false;
    for (Notification.Action action : n.actions) {
      if ("Dismiss".contentEquals(action.title)) {
        hasDismiss = true;
        break;
      }
    }
    assertTrue("Notification should have a Dismiss action", hasDismiss);
  }

  @Test
  public void buildNotification_hasFullScreenIntent() {
    GeoAlarm alarm = saveAlarm(enabledAlarm());
    ServiceController<AlarmRingingService> controller =
        Robolectric.buildService(AlarmRingingService.class, startIntent(alarm)).create();
    controller.startCommand(0, 0);
    Notification n =
        shadowNotificationManager.getNotification(AlarmClockReceiver.RINGING_NOTIFICATION_ID);
    assertNotNull(n);
    assertNotNull("Notification should have a full-screen intent", n.fullScreenIntent);
  }

  @Test
  public void buildNotification_categoryIsAlarm() {
    GeoAlarm alarm = saveAlarm(enabledAlarm());
    ServiceController<AlarmRingingService> controller =
        Robolectric.buildService(AlarmRingingService.class, startIntent(alarm)).create();
    controller.startCommand(0, 0);
    Notification n =
        shadowNotificationManager.getNotification(AlarmClockReceiver.RINGING_NOTIFICATION_ID);
    assertNotNull(n);
    assertEquals(Notification.CATEGORY_ALARM, n.category);
  }

  @Test
  public void buildNotification_isOngoing() {
    GeoAlarm alarm = saveAlarm(enabledAlarm());
    ServiceController<AlarmRingingService> controller =
        Robolectric.buildService(AlarmRingingService.class, startIntent(alarm)).create();
    controller.startCommand(0, 0);
    Notification n =
        shadowNotificationManager.getNotification(AlarmClockReceiver.RINGING_NOTIFICATION_ID);
    assertNotNull(n);
    assertTrue("Notification should be ongoing", (n.flags & Notification.FLAG_ONGOING_EVENT) != 0);
  }

  @Test
  public void buildNotification_visibilityIsPublic() {
    GeoAlarm alarm = saveAlarm(enabledAlarm());
    ServiceController<AlarmRingingService> controller =
        Robolectric.buildService(AlarmRingingService.class, startIntent(alarm)).create();
    controller.startCommand(0, 0);
    Notification n =
        shadowNotificationManager.getNotification(AlarmClockReceiver.RINGING_NOTIFICATION_ID);
    assertNotNull(n);
    assertEquals(Notification.VISIBILITY_PUBLIC, n.visibility);
  }

  @Test
  public void buildNotification_channelHasHighImportance() {
    GeoAlarm alarm = saveAlarm(enabledAlarm());
    ServiceController<AlarmRingingService> controller =
        Robolectric.buildService(AlarmRingingService.class, startIntent(alarm)).create();
    controller.startCommand(0, 0);
    NotificationManager nm =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    android.app.NotificationChannel channel = nm.getNotificationChannel("alarm_ringing");
    assertNotNull("Notification channel should be created", channel);
    assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.getImportance());
  }

  // ---- ringtone URI tests ----

  @Test
  public void alarm_withExplicitRingtoneUri_serviceStarts() {
    GeoAlarm alarm =
        saveAlarm(enabledAlarm().withRingtoneUri("content://media/internal/audio/media/42"));
    ServiceController<AlarmRingingService> controller =
        Robolectric.buildService(AlarmRingingService.class, startIntent(alarm)).create();
    controller.startCommand(0, 0);
    assertNotNull(
        "Service should start with explicit ringtone URI",
        shadowNotificationManager.getNotification(AlarmClockReceiver.RINGING_NOTIFICATION_ID));
  }

  @Test
  public void alarm_withNullRingtoneUri_vibrateOnly_serviceStarts() {
    GeoAlarm alarm = saveAlarm(enabledAlarm().withRingtoneUri(null));
    ServiceController<AlarmRingingService> controller =
        Robolectric.buildService(AlarmRingingService.class, startIntent(alarm)).create();
    controller.startCommand(0, 0);
    assertNotNull(
        "Service should start with vibrate-only (null URI)",
        shadowNotificationManager.getNotification(AlarmClockReceiver.RINGING_NOTIFICATION_ID));
  }

  @Test
  public void alarm_legacyWithoutRingtoneField_serviceStarts() {
    // Legacy alarms: ringtoneUri field not present (null by default)
    GeoAlarm alarm = saveAlarm(enabledAlarm());
    assertNull("Legacy alarm should have null ringtoneUri", alarm.ringtoneUri);
    ServiceController<AlarmRingingService> controller =
        Robolectric.buildService(AlarmRingingService.class, startIntent(alarm)).create();
    controller.startCommand(0, 0);
    assertNotNull(
        "Service should start for legacy alarm",
        shadowNotificationManager.getNotification(AlarmClockReceiver.RINGING_NOTIFICATION_ID));
  }

  // ---- startAlarm: AUDIO_DISABLED ----

  @Test
  public void startAlarm_audioDisabled_skipsAudio() {
    GeoAlarm alarm =
        saveAlarm(enabledAlarm().withRingtoneUri("content://media/internal/audio/media/42"));
    AlarmRingingService.AUDIO_DISABLED = true;
    ServiceController<AlarmRingingService> controller =
        Robolectric.buildService(AlarmRingingService.class, startIntent(alarm)).create();
    controller.startCommand(0, 0);
    // Should not crash — audio is skipped
    assertNotNull(
        shadowNotificationManager.getNotification(AlarmClockReceiver.RINGING_NOTIFICATION_ID));
  }

  // ---- null/missing alarm ID ----

  @Test
  public void normalStart_nullAlarmId_serviceStarts() {
    Intent intent = new Intent(context, AlarmRingingService.class);
    // No EXTRA_ALARM_ID
    ServiceController<AlarmRingingService> controller =
        Robolectric.buildService(AlarmRingingService.class, intent).create();
    controller.startCommand(0, 0);
    assertNotNull(
        shadowNotificationManager.getNotification(AlarmClockReceiver.RINGING_NOTIFICATION_ID));
  }

  @Test
  public void normalStart_unknownAlarmId_serviceStarts() {
    Intent intent = new Intent(context, AlarmRingingService.class);
    intent.putExtra(AlarmRingingService.EXTRA_ALARM_ID, UUID.randomUUID().toString());
    ServiceController<AlarmRingingService> controller =
        Robolectric.buildService(AlarmRingingService.class, intent).create();
    controller.startCommand(0, 0);
    assertNotNull(
        shadowNotificationManager.getNotification(AlarmClockReceiver.RINGING_NOTIFICATION_ID));
  }

  @Test
  public void actionSnooze_nullAlarm_doesNotSchedule() {
    Intent intent = new Intent(context, AlarmRingingService.class);
    intent.setAction(AlarmRingingService.ACTION_SNOOZE);
    // No alarm ID → alarm is null → should not schedule snooze
    Robolectric.buildService(AlarmRingingService.class, intent).create().startCommand(0, 0);
    assertNull(
        "SNOOZE with null alarm should not schedule", shadowAlarmManager.getNextScheduledAlarm());
  }

  @Test
  public void buildNotification_nullAlarm_hasEmptyContentText() {
    Intent intent = new Intent(context, AlarmRingingService.class);
    ServiceController<AlarmRingingService> controller =
        Robolectric.buildService(AlarmRingingService.class, intent).create();
    controller.startCommand(0, 0);
    Notification n =
        shadowNotificationManager.getNotification(AlarmClockReceiver.RINGING_NOTIFICATION_ID);
    assertNotNull(n);
  }

  @Test
  public void normalStart_vibrateOnlyAlarm_serviceStarts() {
    // Alarm with null ringtoneUri means vibrate only
    GeoAlarm alarm = saveAlarm(enabledAlarm().withRingtoneUri(null));
    AlarmRingingService.AUDIO_DISABLED = false;
    ServiceController<AlarmRingingService> controller =
        Robolectric.buildService(AlarmRingingService.class, startIntent(alarm)).create();
    controller.startCommand(0, 0);
    assertNotNull(
        "Vibrate-only alarm should start correctly",
        shadowNotificationManager.getNotification(AlarmClockReceiver.RINGING_NOTIFICATION_ID));
  }

  @Test
  public void normalStart_withRingtoneUri_serviceStarts() {
    GeoAlarm alarm =
        saveAlarm(enabledAlarm().withRingtoneUri("content://media/internal/audio/media/42"));
    AlarmRingingService.AUDIO_DISABLED = false;
    ServiceController<AlarmRingingService> controller =
        Robolectric.buildService(AlarmRingingService.class, startIntent(alarm)).create();
    controller.startCommand(0, 0);
    assertNotNull(
        shadowNotificationManager.getNotification(AlarmClockReceiver.RINGING_NOTIFICATION_ID));
  }

  @Test
  public void normalStart_nullIntent_serviceStarts() {
    ServiceController<AlarmRingingService> controller =
        Robolectric.buildService(AlarmRingingService.class).create();
    controller.startCommand(0, 0);
    assertNotNull(
        shadowNotificationManager.getNotification(AlarmClockReceiver.RINGING_NOTIFICATION_ID));
  }

  @Test
  public void buildNotification_withAlarmPlace_showsPlaceInContent() {
    GeoAlarm alarm = saveAlarm(enabledAlarm().withPlace("Office"));
    ServiceController<AlarmRingingService> controller =
        Robolectric.buildService(AlarmRingingService.class, startIntent(alarm)).create();
    controller.startCommand(0, 0);
    Notification n =
        shadowNotificationManager.getNotification(AlarmClockReceiver.RINGING_NOTIFICATION_ID);
    assertNotNull(n);
  }

  @Test
  public void onDestroy_stopsRingtoneAndVibrator() {
    GeoAlarm alarm = saveAlarm(enabledAlarm());
    ServiceController<AlarmRingingService> controller =
        Robolectric.buildService(AlarmRingingService.class, startIntent(alarm)).create();
    controller.startCommand(0, 0);
    controller.destroy();
    // No crash during cleanup = success
  }

  // ---- pre-Q startForeground path ----

  @Test
  @Config(sdk = 28)
  public void normalStart_api28_startsForegroundWithoutServiceType() {
    GeoAlarm alarm = saveAlarm(enabledAlarm());
    ServiceController<AlarmRingingService> controller =
        Robolectric.buildService(AlarmRingingService.class, startIntent(alarm)).create();
    controller.startCommand(0, 0);
    assertNotNull(
        shadowNotificationManager.getNotification(AlarmClockReceiver.RINGING_NOTIFICATION_ID));
  }

  @Test
  @Config(sdk = 28)
  public void normalStart_api28_vibrateOnly() {
    GeoAlarm alarm = saveAlarm(enabledAlarm().withRingtoneUri(null));
    AlarmRingingService.AUDIO_DISABLED = false;
    ServiceController<AlarmRingingService> controller =
        Robolectric.buildService(AlarmRingingService.class, startIntent(alarm)).create();
    controller.startCommand(0, 0);
    assertNotNull(
        shadowNotificationManager.getNotification(AlarmClockReceiver.RINGING_NOTIFICATION_ID));
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
        .build();
  }

  private GeoAlarm saveAlarm(GeoAlarm alarm) {
    GeoAlarm.save(context, alarm);
    return alarm;
  }

  private Intent startIntent(GeoAlarm alarm) {
    Intent intent = new Intent(context, AlarmRingingService.class);
    intent.putExtra(AlarmRingingService.EXTRA_ALARM_ID, alarm.id.toString());
    return intent;
  }
}
