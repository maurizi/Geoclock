package maurizi.geoclock.integration;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import com.google.android.gms.maps.model.LatLng;
import java.util.UUID;
import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.background.AlarmClockReceiver;
import maurizi.geoclock.background.AlarmRingingService;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumented tests for alarm display over the lock screen. Requires a real device or emulator —
 * cannot run in Robolectric.
 */
@SdkSuppress(minSdkVersion = 27) // setShowWhenLocked/setTurnScreenOn require API 27+
@RunWith(AndroidJUnit4.class)
@LargeTest
public class LockScreenAlarmTest {

  private static final int TIMEOUT_MS = 10_000;

  @Rule(order = 0)
  public RetryRule retryRule = new RetryRule(2);

  @Rule(order = 1)
  public GrantPermissionRule notificationPermission =
      Build.VERSION.SDK_INT >= 33
          ? GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
          : GrantPermissionRule.grant();

  private UiDevice device;
  private Context context;
  private PowerManager.WakeLock wakeLock;

  @SuppressWarnings("deprecation")
  @Before
  public void setUp() {
    device = UiDevice.getInstance(getInstrumentation());
    context = ApplicationProvider.getApplicationContext();
    AlarmRingingService.AUDIO_DISABLED = true;

    // Ensure screen is on and keyguard is dismissed before testing lock screen behavior
    PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    wakeLock =
        pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "geoclock:test");
    wakeLock.acquire(120_000);
  }

  @After
  public void tearDown() {
    AlarmRingingService.AUDIO_DISABLED = false;
    AlarmRingingService.stop(context);
    if (wakeLock != null && wakeLock.isHeld()) {
      wakeLock.release();
    }
  }

  @Test
  public void alarm_showsOverLockScreen() throws Exception {
    GeoAlarm alarm = saveAlarm(enabledAlarm());

    // Release wake lock so the device can actually sleep
    if (wakeLock.isHeld()) wakeLock.release();

    // Lock the screen BEFORE firing the alarm so the full-screen intent
    // auto-launches (posting while screen is on shows a heads-up instead).
    device.sleep();
    Thread.sleep(1000);

    // Fire the alarm while locked
    Intent alarmIntent = new Intent(context, AlarmClockReceiver.class);
    alarmIntent.putExtra(AlarmRingingService.EXTRA_ALARM_ID, alarm.id.toString());
    context.sendBroadcast(alarmIntent);

    Thread.sleep(2000);
    device.wakeUp();

    // Verify AlarmRingingActivity is visible
    UiObject2 dismissButton =
        device.wait(
            Until.findObject(By.res(context.getPackageName(), "alarm_ringing_dismiss")),
            TIMEOUT_MS);
    assertNotNull("Alarm dismiss button should be visible over lock screen", dismissButton);

    // Clean up
    AlarmRingingService.stop(context);
  }

  @Test
  public void dismissOnLockScreen_closesActivity() throws Exception {
    GeoAlarm alarm = saveAlarm(enabledAlarm());

    // Release wake lock so the device can actually sleep
    if (wakeLock.isHeld()) wakeLock.release();

    // Lock first, then fire alarm for full-screen intent
    device.sleep();
    Thread.sleep(1000);

    Intent alarmIntent = new Intent(context, AlarmClockReceiver.class);
    alarmIntent.putExtra(AlarmRingingService.EXTRA_ALARM_ID, alarm.id.toString());
    context.sendBroadcast(alarmIntent);

    Thread.sleep(2000);
    device.wakeUp();

    UiObject2 dismissButton =
        device.wait(
            Until.findObject(By.res(context.getPackageName(), "alarm_ringing_dismiss")),
            TIMEOUT_MS);
    assertNotNull("Dismiss button should be visible", dismissButton);
    dismissButton.click();

    // Activity should be gone
    Thread.sleep(1000);
    UiObject2 dismissAfter =
        device.findObject(By.res(context.getPackageName(), "alarm_ringing_dismiss"));
    assertNull("Activity should be dismissed", dismissAfter);
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
}
