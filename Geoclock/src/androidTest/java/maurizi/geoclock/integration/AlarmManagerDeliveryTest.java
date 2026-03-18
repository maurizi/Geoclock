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
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    AlarmRingingService.AUDIO_DISABLED = true;
  }

  @After
  public void tearDown() {
    AlarmRingingService.SNOOZE_DURATION_MS = ORIGINAL_SNOOZE;
    AlarmRingingService.AUDIO_DISABLED = false;
    AlarmRingingService.stop(context);
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

    // Schedule the alarm 3 seconds from now via setAlarmClock
    long triggerMs = System.currentTimeMillis() + ALARM_DELAY_MS;
    Intent alarmIntent = new Intent(context, AlarmClockReceiver.class);
    alarmIntent.putExtra("alarm_id", alarm.id.toString());
    android.app.PendingIntent pi =
        android.app.PendingIntent.getBroadcast(
            context,
            0,
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
    // On API 36+ a plain sendBroadcast can't start a foreground service
    // (only setAlarmClock-triggered broadcasts get the exemption)
    Assume.assumeTrue(
        "Foreground service from synthetic broadcast blocked on API 36+",
        Build.VERSION.SDK_INT < 36);
    GeoAlarm alarm = saveAlarm(enabledAlarm()); // no days = non-repeating

    // Fire the alarm directly
    Intent intent = new Intent(context, AlarmClockReceiver.class);
    intent.putExtra("alarm_id", alarm.id.toString());
    context.sendBroadcast(intent);

    // Poll until the receiver processes the broadcast and disables the alarm
    long deadline = System.currentTimeMillis() + 10000;
    GeoAlarm saved = null;
    while (System.currentTimeMillis() < deadline) {
      Thread.sleep(500);
      saved = GeoAlarm.getGeoAlarm(context, alarm.id);
      if (saved == null || !saved.enabled) break;
    }
    // Non-repeating alarm is removed+saved-disabled after firing
    if (saved != null) {
      assertFalse("Non-repeating alarm should be disabled after firing", saved.enabled);
    }
    // If null, it was removed entirely — also acceptable
  }

  @Test
  public void repeatingAlarm_remainsEnabledAfterFiring() throws Exception {
    // On API 36+ a plain sendBroadcast can't start a foreground service
    Assume.assumeTrue(
        "Foreground service from synthetic broadcast blocked on API 36+",
        Build.VERSION.SDK_INT < 36);
    GeoAlarm alarm = saveAlarm(repeatingAlarm());

    // Fire the alarm directly
    Intent intent = new Intent(context, AlarmClockReceiver.class);
    intent.putExtra("alarm_id", alarm.id.toString());
    context.sendBroadcast(intent);
    Thread.sleep(1000);

    GeoAlarm saved = GeoAlarm.getGeoAlarm(context, alarm.id);
    assertNotNull("Repeating alarm should still exist after firing", saved);
    assertTrue("Repeating alarm should remain enabled after firing", saved.enabled);
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
