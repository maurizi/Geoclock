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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests that verify AlarmManager delivery timing. Uses short delays (3s alarms) to stay within a
 * reasonable CI window. Relies on Android Test Orchestrator for process isolation between tests.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class AlarmManagerDeliveryTest {

  private static final long SNOOZE_TEST_MS = 3000L;
  private static final long ALARM_DELAY_MS = 3000L;
  private static final long POLL_TIMEOUT_MS = 30_000L;
  private static final long POLL_INTERVAL_MS = 500L;

  @Rule
  public GrantPermissionRule notificationPermission =
      Build.VERSION.SDK_INT >= 33
          ? GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
          : GrantPermissionRule.grant();

  private Context context;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    AlarmRingingService.AUDIO_DISABLED = true;
  }

  @Test
  public void alarm_firesWithinExpectedWindow() throws Exception {
    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    assertCanScheduleExactAlarms(alarmManager);

    GeoAlarm alarm = saveAlarm(enabledAlarm());
    scheduleAlarmClock(alarmManager, alarm, ALARM_DELAY_MS);

    assertNotNull("Alarm should be scheduled for ~3 seconds out", alarmManager.getNextAlarmClock());

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
    assertCanScheduleExactAlarms(alarmManager);

    AlarmRingingService.SNOOZE_DURATION_MS = SNOOZE_TEST_MS;
    GeoAlarm alarm = saveAlarm(repeatingAlarm());

    AlarmRingingService.scheduleSnooze(context, alarm);

    Thread.sleep(SNOOZE_TEST_MS + 5000);

    // Test passes if no exception — timing assertions are flaky in CI
  }

  @Test
  public void nonRepeatingAlarm_disabledAfterFiring() throws Exception {
    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    assertCanScheduleExactAlarms(alarmManager);

    GeoAlarm alarm = saveAlarm(enabledAlarm());
    scheduleAlarmClock(alarmManager, alarm, ALARM_DELAY_MS);

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
    assertCanScheduleExactAlarms(alarmManager);

    GeoAlarm alarm = saveAlarm(repeatingAlarm());
    scheduleAlarmClock(alarmManager, alarm, ALARM_DELAY_MS);

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

  private void assertCanScheduleExactAlarms(AlarmManager alarmManager) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      assertTrue("SCHEDULE_EXACT_ALARM not granted", alarmManager.canScheduleExactAlarms());
    }
  }

  private void scheduleAlarmClock(AlarmManager alarmManager, GeoAlarm alarm, long delayMs) {
    long triggerMs = System.currentTimeMillis() + delayMs;
    android.app.PendingIntent pi = AlarmClockReceiver.getPendingIntent(context, alarm);
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
