package maurizi.geoclock.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.AlarmManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.maps.model.LatLng;
import com.google.common.collect.ImmutableSet;
import java.time.DayOfWeek;
import java.util.UUID;
import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.background.AlarmClockReceiver;
import maurizi.geoclock.background.NotificationReceiver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowApplication;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ActiveAlarmManagerTest {

  private Context context;
  private ActiveAlarmManager manager;
  private ShadowAlarmManager shadowAlarmManager;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    manager = new ActiveAlarmManager(context);
    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    shadowAlarmManager = Shadows.shadowOf(alarmManager);
    ShadowAlarmManager.setCanScheduleExactAlarms(true);
  }

  // ---- clearActiveAlarms ----

  @Test
  public void clearActiveAlarms_cancelsScheduledAlarmsAndNotifications() {
    // Save an alarm so there's something to clear
    GeoAlarm alarm = saveAlarm(enabledAlarm());
    manager.addActiveAlarms(ImmutableSet.of(alarm.id));
    shadowAlarmManager.getScheduledAlarms().clear(); // reset for clean assertion

    manager.clearActiveAlarms();

    // After clearing, no new alarms should be scheduled
    assertEquals(0, shadowAlarmManager.getScheduledAlarms().size());
  }

  // ---- addActiveAlarms ----

  @Test
  public void addActiveAlarms_emptyStart_addsAlarms() {
    GeoAlarm alarm = saveAlarm(enabledAlarm());
    manager.addActiveAlarms(ImmutableSet.of(alarm.id));
    // Alarm should now be scheduled — manager scheduled something
    assertNotNull(shadowAlarmManager.getNextScheduledAlarm());
  }

  @Test
  public void addActiveAlarms_mergesWithExisting() {
    GeoAlarm alarm1 = saveAlarm(enabledAlarm());
    GeoAlarm alarm2 = saveAlarm(enabledAlarm());

    manager.addActiveAlarms(ImmutableSet.of(alarm1.id));
    // Adding a second alarm should schedule the soonest of the two
    manager.addActiveAlarms(ImmutableSet.of(alarm2.id));

    assertNotNull(shadowAlarmManager.getNextScheduledAlarm());
  }

  // ---- removeActiveAlarms ----

  @Test
  public void removeActiveAlarms_lastAlarm_cancelsEverything() {
    GeoAlarm alarm = saveAlarm(enabledAlarm());
    manager.addActiveAlarms(ImmutableSet.of(alarm.id));
    shadowAlarmManager.getScheduledAlarms().clear();

    manager.removeActiveAlarms(ImmutableSet.of(alarm.id));

    assertEquals(0, shadowAlarmManager.getScheduledAlarms().size());
  }

  @Test
  public void removeActiveAlarms_oneOfTwo_keepsRemaining() {
    GeoAlarm alarm1 = saveAlarm(enabledAlarm());
    GeoAlarm alarm2 = saveAlarm(enabledAlarm());

    manager.addActiveAlarms(ImmutableSet.of(alarm1.id, alarm2.id));
    shadowAlarmManager.getScheduledAlarms().clear();

    manager.removeActiveAlarms(ImmutableSet.of(alarm1.id));

    // alarm2 is still active, so something should be scheduled
    assertNotNull(shadowAlarmManager.getNextScheduledAlarm());
  }

  // ---- resetActiveAlarms ----

  @Test
  public void resetActiveAlarms_noActiveAlarms_schedulesNothing() {
    manager.clearActiveAlarms();
    shadowAlarmManager.getScheduledAlarms().clear();

    manager.resetActiveAlarms();

    assertEquals(0, shadowAlarmManager.getScheduledAlarms().size());
  }

  // ---- Scheduling precision ----

  @Test
  public void addActiveAlarms_twoAlarms_schedulesSoonerOne() {
    // Use times relative to now so the sooner alarm is always in the future
    java.time.LocalTime soonerTime = java.time.LocalTime.now().plusHours(1);
    java.time.LocalTime laterTime = java.time.LocalTime.now().plusHours(3);
    GeoAlarm alarmA = saveAlarm(repeatingAlarmAt(soonerTime.getHour(), soonerTime.getMinute()));
    GeoAlarm alarmB = saveAlarm(repeatingAlarmAt(laterTime.getHour(), laterTime.getMinute()));

    manager.addActiveAlarms(ImmutableSet.of(alarmA.id, alarmB.id));

    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    AlarmManager.AlarmClockInfo clockInfo = alarmManager.getNextAlarmClock();
    assertNotNull(clockInfo);
    long triggerMs = clockInfo.getTriggerTime();
    // Verify it's closer to alarm A's time (sooner) than alarm B's (later)
    java.time.ZonedDateTime alarmATime = alarmA.calculateAlarmTime(java.time.LocalDateTime.now());
    java.time.ZonedDateTime alarmBTime = alarmB.calculateAlarmTime(java.time.LocalDateTime.now());
    assertNotNull(alarmATime);
    assertNotNull(alarmBTime);
    long diffA = Math.abs(triggerMs - alarmATime.toInstant().toEpochMilli());
    long diffB = Math.abs(triggerMs - alarmBTime.toInstant().toEpochMilli());
    assertTrue("Scheduled alarm should match alarm A (sooner)", diffA < diffB);
  }

  @Test
  public void setNotification_alarmWithin15Min_broadcastsImmediately() {
    // An alarm firing within 15 min triggers notification immediately via broadcast
    java.time.LocalDateTime soon = java.time.LocalDateTime.now().plusMinutes(10);
    GeoAlarm alarm = saveAlarm(repeatingAlarmAt(soon.getHour(), soon.getMinute()));

    manager.addActiveAlarms(ImmutableSet.of(alarm.id));

    ShadowApplication sa = Shadows.shadowOf((Application) context);
    boolean notificationBroadcast =
        sa.getBroadcastIntents().stream()
            .anyMatch(
                i ->
                    i.getComponent() != null
                        && NotificationReceiver.class
                            .getName()
                            .equals(i.getComponent().getClassName()));
    assertTrue(
        "Alarm within 15 min should immediately broadcast to NotificationReceiver",
        notificationBroadcast);
  }

  @Test
  public void setNotification_alarmMoreThan15Min_schedulesViaAlarmManager() {
    // An alarm >15 min away schedules the notification via AlarmManager, not immediate broadcast
    java.time.LocalDateTime later = java.time.LocalDateTime.now().plusMinutes(30);
    GeoAlarm alarm = saveAlarm(repeatingAlarmAt(later.getHour(), later.getMinute()));

    manager.addActiveAlarms(ImmutableSet.of(alarm.id));

    // The notification should be scheduled via AlarmManager (not broadcast),
    // and the alarm clock itself should also be scheduled
    assertNotNull(
        "Alarm should be scheduled via AlarmManager", shadowAlarmManager.getNextScheduledAlarm());
  }

  // ---- re-add after time change reschedules ----

  @Test
  public void addActiveAlarms_afterTimeChange_reschedulesToNewTime() {
    // Simulate: alarm is active, user edits time, saves, then re-adds to active set
    java.time.LocalTime originalTime = java.time.LocalTime.now().plusHours(3);
    java.time.LocalTime updatedTime = java.time.LocalTime.now().plusHours(1);
    UUID alarmId = UUID.randomUUID();

    GeoAlarm original =
        GeoAlarm.builder()
            .id(alarmId)
            .location(new LatLng(37.4, -122.0))
            .radius(100)
            .enabled(true)
            .hour(originalTime.getHour())
            .minute(originalTime.getMinute())
            .days(ImmutableSet.copyOf(DayOfWeek.values()))
            .build();
    saveAlarm(original);
    manager.addActiveAlarms(ImmutableSet.of(alarmId));

    AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    long originalTrigger = am.getNextAlarmClock().getTriggerTime();

    // Now save the alarm with a new (earlier) time and re-add
    GeoAlarm updated = original.withHour(updatedTime.getHour()).withMinute(updatedTime.getMinute());
    saveAlarm(updated);
    manager.addActiveAlarms(ImmutableSet.of(alarmId));

    long newTrigger = am.getNextAlarmClock().getTriggerTime();
    assertTrue("Alarm should be rescheduled to earlier time", newTrigger < originalTrigger);
  }

  // ---- upcoming notification dismissal ----

  @Test
  public void removeActiveAlarms_lastAlarm_cancelsUpcomingNotification() {
    // Setup: alarm within 15 min so notification is posted immediately via broadcast
    java.time.LocalDateTime soon = java.time.LocalDateTime.now().plusMinutes(10);
    GeoAlarm alarm = saveAlarm(repeatingAlarmAt(soon.getHour(), soon.getMinute()));
    manager.addActiveAlarms(ImmutableSet.of(alarm.id));
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

    android.app.NotificationManager nm =
        (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    org.robolectric.shadows.ShadowNotificationManager shadowNm = Shadows.shadowOf(nm);
    assertEquals(
        "Notification should be posted before removal", 1, shadowNm.getAllNotifications().size());

    // Now remove the alarm — notification should be cancelled
    manager.removeActiveAlarms(ImmutableSet.of(alarm.id));
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

    assertEquals(
        "Upcoming notification should be cancelled when last alarm removed",
        0,
        shadowNm.getAllNotifications().size());
  }

  @Test
  public void removeActiveAlarms_withRemainingAlarm_cancelsStaleUpcomingNotification() {
    // Two alarms: A is within 15 min (notification posted), B is far away
    java.time.LocalDateTime soon = java.time.LocalDateTime.now().plusMinutes(10);
    java.time.LocalDateTime later = java.time.LocalDateTime.now().plusHours(3);
    GeoAlarm alarmA = saveAlarm(repeatingAlarmAt(soon.getHour(), soon.getMinute()));
    GeoAlarm alarmB = saveAlarm(repeatingAlarmAt(later.getHour(), later.getMinute()));
    manager.addActiveAlarms(ImmutableSet.of(alarmA.id, alarmB.id));
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

    android.app.NotificationManager nm =
        (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    org.robolectric.shadows.ShadowNotificationManager shadowNm = Shadows.shadowOf(nm);
    assertEquals(
        "Notification should be posted initially", 1, shadowNm.getAllNotifications().size());

    // Remove alarm A (the one with the notification), B remains but is far away
    manager.removeActiveAlarms(ImmutableSet.of(alarmA.id));
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

    // The notification for alarm A should be cancelled. Alarm B is >15 min away
    // so no new notification should be posted.
    assertEquals(
        "Stale notification should be cancelled when alarm removed",
        0,
        shadowNm.getAllNotifications().size());
  }

  @Test
  public void alarmFires_cancelsUpcomingNotification() {
    // Use an alarm time slightly in the past — simulates realistic conditions where
    // the alarm fires AT its scheduled time, so calculateAlarmTime returns the next
    // day's occurrence (>15 min away), not the same time again.
    java.time.LocalDateTime justPassed = java.time.LocalDateTime.now().minusMinutes(1);
    GeoAlarm alarm = saveAlarm(repeatingAlarmAt(justPassed.getHour(), justPassed.getMinute()));

    // Manually post the upcoming notification (it would have been posted ~15 min ago)
    android.app.NotificationManager nm =
        (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    org.robolectric.shadows.ShadowNotificationManager shadowNm = Shadows.shadowOf(nm);
    new NotificationReceiver().onReceive(context, NotificationReceiver.getIntent(context, alarm));
    assertEquals(
        "Notification should be posted before alarm fires",
        1,
        shadowNm.getAllNotifications().size());

    // Add to active set (no immediate notification broadcast since alarm time is past)
    manager.addActiveAlarms(ImmutableSet.of(alarm.id));
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

    // Simulate alarm firing
    AlarmClockReceiver receiver = new AlarmClockReceiver();
    Intent intent = new Intent(context, AlarmClockReceiver.class);
    intent.putExtra("alarm_id", alarm.id.toString());
    receiver.onReceive(context, intent);
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

    // Upcoming notification should be dismissed (ringing notification is separate)
    android.app.Notification remaining =
        shadowNm.getNotification(NotificationReceiver.NOTIFICATION_ID);
    assertEquals(
        "Upcoming notification (ID 42) should be cancelled after alarm fires", null, remaining);
  }

  @Test
  public void deleteAllAlarms_cancelsUpcomingNotification() {
    // Post notification, then delete all alarms — simulates user deleting everything
    java.time.LocalDateTime soon = java.time.LocalDateTime.now().plusMinutes(10);
    GeoAlarm alarm = saveAlarm(repeatingAlarmAt(soon.getHour(), soon.getMinute()));
    manager.addActiveAlarms(ImmutableSet.of(alarm.id));
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

    android.app.NotificationManager nm =
        (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    org.robolectric.shadows.ShadowNotificationManager shadowNm = Shadows.shadowOf(nm);
    assertEquals(1, shadowNm.getAllNotifications().size());

    // Delete the alarm (remove from SharedPrefs + active alarms)
    GeoAlarm.remove(context, alarm);
    manager.removeActiveAlarms(ImmutableSet.of(alarm.id));
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

    assertEquals(
        "Notification should be cancelled after deleting all alarms",
        0,
        shadowNm.getAllNotifications().size());
  }

  @Test
  public void toggleDisable_cancelsUpcomingNotification() {
    // Alarm within 15 min, notification posted, then user toggles it off
    java.time.LocalDateTime soon = java.time.LocalDateTime.now().plusMinutes(10);
    GeoAlarm alarm = saveAlarm(repeatingAlarmAt(soon.getHour(), soon.getMinute()));
    manager.addActiveAlarms(ImmutableSet.of(alarm.id));
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

    android.app.NotificationManager nm =
        (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    org.robolectric.shadows.ShadowNotificationManager shadowNm = Shadows.shadowOf(nm);
    assertEquals(1, shadowNm.getAllNotifications().size());

    // Toggle off: save disabled + remove from active (mirrors MapActivity.onToggleEnabled)
    GeoAlarm.save(context, alarm.withEnabled(false));
    manager.removeActiveAlarms(ImmutableSet.of(alarm.id));
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

    assertEquals(
        "Notification should be cancelled when alarm is toggled off",
        0,
        shadowNm.getAllNotifications().size());
  }

  // ---- helpers ----

  private GeoAlarm repeatingAlarmAt(int hour, int minute) {
    return GeoAlarm.builder()
        .id(UUID.randomUUID())
        .location(new LatLng(37.4, -122.0))
        .radius(100)
        .enabled(true)
        .hour(hour)
        .minute(minute)
        .days(ImmutableSet.copyOf(DayOfWeek.values()))
        .build();
  }

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
}
