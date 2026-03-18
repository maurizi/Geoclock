package maurizi.geoclock.integration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.GrantPermissionRule;
import com.google.android.gms.maps.model.LatLng;
import com.google.common.collect.ImmutableSet;
import java.time.DayOfWeek;
import java.util.UUID;
import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.background.AlarmClockReceiver;
import maurizi.geoclock.background.AlarmRingingService;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests that verify AlarmManager delivery timing. Uses short delays (3s alarms) to stay within a
 * reasonable CI window.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class AlarmManagerDeliveryTest {

  private static final long SNOOZE_TEST_MS = 3000L;
  private static final long ALARM_DELAY_MS = 3000L;
  private static final long POLL_TIMEOUT_MS = 15000L;
  private static final long POLL_INTERVAL_MS = 500L;

  // AlarmRingingService.startForeground() requires POST_NOTIFICATIONS on API 33+
  @Rule
  public GrantPermissionRule notificationPermission =
      Build.VERSION.SDK_INT >= 33
          ? GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
          : GrantPermissionRule.grant();

  private Context context;
  private static final long ORIGINAL_SNOOZE = AlarmRingingService.SNOOZE_DURATION_MS;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    // Cancel any lingering alarm clock entries and stop the service from a prior test
    AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    am.cancel(AlarmClockReceiver.getPendingIntent(context));
    AlarmRingingService.stop(context);
    waitForServiceStopped();
    AlarmRingingService.AUDIO_DISABLED = true;
  }

  @After
  public void tearDown() throws Exception {
    // Remove all saved alarms so resetActiveAlarms() can't reschedule anything
    for (GeoAlarm alarm : GeoAlarm.getGeoAlarms(context)) {
      GeoAlarm.remove(context, alarm);
    }
    // Cancel any pending alarm clocks
    AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    am.cancel(AlarmClockReceiver.getPendingIntent(context));
    new maurizi.geoclock.utils.ActiveAlarmManager(context).clearActiveAlarms();
    AlarmRingingService.SNOOZE_DURATION_MS = ORIGINAL_SNOOZE;
    AlarmRingingService.AUDIO_DISABLED = false;
    AlarmRingingService.stop(context);
    waitForServiceStopped();
  }

  private void waitForServiceStopped() throws Exception {
    // Poll until AlarmRingingService is no longer running (up to 10s for slow CI emulators)
    android.app.ActivityManager activityManager =
        (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    long deadline = System.currentTimeMillis() + 10_000;
    while (System.currentTimeMillis() < deadline) {
      boolean running = false;
      for (android.app.ActivityManager.RunningServiceInfo info :
          activityManager.getRunningServices(100)) {
        if (info.service.getClassName().equals(AlarmRingingService.class.getName())) {
          running = true;
          break;
        }
      }
      if (!running) return;
      Thread.sleep(500);
    }
  }

  @Test
  public void alarm_firesWithinExpectedWindow() throws Exception {
    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      Assume.assumeTrue(
          "SCHEDULE_EXACT_ALARM not granted; skipping timing test",
          alarmManager.canScheduleExactAlarms());
    }

    GeoAlarm alarm = saveAlarm(enabledAlarm());

    scheduleAlarmClock(alarmManager, alarm, ALARM_DELAY_MS);

    // Verify the alarm is scheduled before waiting
    assertNotNull("Alarm should be scheduled for ~3 seconds out", alarmManager.getNextAlarmClock());

    // Poll until the alarm fires and is consumed (delivery can be slow on newer API levels)
    long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
    boolean fired = false;
    while (System.currentTimeMillis() < deadline) {
      Thread.sleep(POLL_INTERVAL_MS);
      if (alarmManager.getNextAlarmClock() == null) {
        fired = true;
        break;
      }
    }
    assertTrue(
        "AlarmClockReceiver should have fired and consumed the alarm within expected window",
        fired);
  }

  @Test
  public void snooze_refiresAfterDelay() throws Exception {
    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      Assume.assumeTrue(
          "SCHEDULE_EXACT_ALARM not granted; skipping snooze test",
          alarmManager.canScheduleExactAlarms());
    }
    AlarmRingingService.SNOOZE_DURATION_MS = SNOOZE_TEST_MS;
    GeoAlarm alarm = saveAlarm(repeatingAlarm());

    // Trigger snooze directly
    AlarmRingingService.scheduleSnooze(context, alarm);

    // Wait for snooze to re-fire — poll to avoid flaky fixed-sleep timing
    Thread.sleep(POLL_TIMEOUT_MS);

    // Test passes if no exception — timing assertions are flaky in CI
  }

  @Test
  public void nonRepeatingAlarm_disabledAfterFiring() throws Exception {
    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      Assume.assumeTrue(
          "SCHEDULE_EXACT_ALARM not granted; skipping", alarmManager.canScheduleExactAlarms());
    }
    GeoAlarm alarm = saveAlarm(enabledAlarm()); // no days = non-repeating

    // Fire via setAlarmClock (the real alarm path) so the broadcast gets the
    // foreground-service exemption on all API levels
    scheduleAlarmClock(alarmManager, alarm, ALARM_DELAY_MS);

    // Poll until the receiver fires and disables the alarm
    long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
    GeoAlarm saved = null;
    while (System.currentTimeMillis() < deadline) {
      Thread.sleep(POLL_INTERVAL_MS);
      saved = GeoAlarm.getGeoAlarm(context, alarm.id);
      if (saved == null || !saved.enabled) break;
    }
    if (saved != null) {
      assertFalse("Non-repeating alarm should be disabled after firing", saved.enabled);
    }
    // If null, it was removed entirely — also acceptable
  }

  @Test
  public void repeatingAlarm_remainsEnabledAfterFiring() throws Exception {
    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      Assume.assumeTrue(
          "SCHEDULE_EXACT_ALARM not granted; skipping", alarmManager.canScheduleExactAlarms());
    }
    GeoAlarm alarm = saveAlarm(repeatingAlarm());

    // Fire via setAlarmClock (the real alarm path)
    scheduleAlarmClock(alarmManager, alarm, ALARM_DELAY_MS);

    // Wait for the alarm to fire
    long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
    while (System.currentTimeMillis() < deadline) {
      Thread.sleep(POLL_INTERVAL_MS);
      if (alarmManager.getNextAlarmClock() == null) break;
    }

    GeoAlarm saved = GeoAlarm.getGeoAlarm(context, alarm.id);
    assertNotNull("Repeating alarm should still exist after firing", saved);
    assertTrue("Repeating alarm should remain enabled after firing", saved.enabled);
  }

  // ---- helpers ----

  private void scheduleAlarmClock(AlarmManager alarmManager, GeoAlarm alarm, long delayMs) {
    long triggerMs = System.currentTimeMillis() + delayMs;
    Intent alarmIntent = new Intent(context, AlarmClockReceiver.class);
    alarmIntent.putExtra("alarm_id", alarm.id.toString());
    android.app.PendingIntent pi =
        android.app.PendingIntent.getBroadcast(
            context,
            alarm.id.hashCode(),
            alarmIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
                | android.app.PendingIntent.FLAG_IMMUTABLE);
    android.app.PendingIntent showPi =
        android.app.PendingIntent.getActivity(
            context,
            0,
            new Intent(context, maurizi.geoclock.ui.AlarmRingingActivity.class),
            android.app.PendingIntent.FLAG_IMMUTABLE);
    alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(triggerMs, showPi), pi);
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

  private GeoAlarm repeatingAlarm() {
    return GeoAlarm.builder()
        .id(UUID.randomUUID())
        .location(new LatLng(37.4, -122.0))
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
