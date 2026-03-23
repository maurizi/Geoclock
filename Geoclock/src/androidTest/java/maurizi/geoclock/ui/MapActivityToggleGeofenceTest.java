package maurizi.geoclock.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.AlarmManager;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.rule.GrantPermissionRule;
import com.google.android.gms.maps.model.LatLng;
import com.google.common.collect.ImmutableSet;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.R;
import maurizi.geoclock.background.AlarmRingingService;
import maurizi.geoclock.integration.RetryRule;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Bug #3: Enabling an alarm when the device is outside the geofence should NOT schedule it in
 * AlarmManager. The alarm should only fire after a geofence ENTER event.
 */
@SdkSuppress(minSdkVersion = 27, maxSdkVersion = 35)
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MapActivityToggleGeofenceTest {

  @Rule(order = 0)
  public RetryRule retryRule = new RetryRule(3);

  @Rule(order = 1)
  public GrantPermissionRule permissionRule = GrantPermissionRule.grant(getRequiredPermissions());

  private static String[] getRequiredPermissions() {
    List<String> perms = new ArrayList<>();
    perms.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
    perms.add(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      perms.add(android.Manifest.permission.POST_NOTIFICATIONS);
    }
    return perms.toArray(new String[0]);
  }

  private ActivityScenario<MapActivity> scenario;
  private PowerManager.WakeLock wakeLock;
  private Context ctx;

  @SuppressWarnings("deprecation")
  @Before
  public void setUp() {
    ctx = ApplicationProvider.getApplicationContext();
    AlarmRingingService.AUDIO_DISABLED = true;
    PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
    wakeLock =
        pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "geoclock:test");
    wakeLock.acquire(60_000);
  }

  @After
  public void tearDown() {
    Collection<GeoAlarm> alarms = GeoAlarm.getGeoAlarms(ctx);
    for (GeoAlarm a : alarms) {
      GeoAlarm.remove(ctx, a);
    }
    if (scenario != null) {
      scenario.close();
    }
    if (wakeLock != null && wakeLock.isHeld()) {
      wakeLock.release();
    }
    AlarmRingingService.AUDIO_DISABLED = false;
  }

  @Test
  public void toggleEnabled_alarmFarAway_shouldNotScheduleInAlarmManager() throws Exception {
    // Create a disabled alarm at the North Pole — device is definitely outside
    GeoAlarm alarm =
        GeoAlarm.builder()
            .id(UUID.randomUUID())
            .place("North Pole")
            .location(new LatLng(90.0, 0.0))
            .radius(100)
            .enabled(false)
            .hour(8)
            .minute(0)
            .days(ImmutableSet.copyOf(DayOfWeek.values()))
            .build();
    GeoAlarm.save(ctx, alarm);

    scenario = ActivityScenario.launch(MapActivity.class);
    Thread.sleep(3000);

    // Toggle the alarm ON via the switch
    onView(withId(R.id.alarm_list))
        .perform(actionOnItemAtPosition(0, clickChildView(R.id.alarm_enabled_switch)));
    Thread.sleep(1000);

    // Verify alarm was enabled
    GeoAlarm saved = GeoAlarm.getGeoAlarm(ctx, alarm.id);
    assertNotNull(saved);
    assertTrue("Alarm should be enabled after toggle", saved.enabled);

    // BUG: the alarm is scheduled in AlarmManager even though the device is
    // outside the geofence (North Pole). It should NOT be scheduled — it should
    // only be scheduled after the device enters the geofence.
    AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
    AlarmManager.AlarmClockInfo clockInfo = am.getNextAlarmClock();
    assertNull(
        "Alarm should NOT be scheduled in AlarmManager when device is outside the geofence. "
            + "The alarm should only fire after a geofence ENTER event.",
        clockInfo);
  }

  private static ViewAction clickChildView(int childId) {
    return new ViewAction() {
      @Override
      public Matcher<View> getConstraints() {
        return isDisplayed();
      }

      @Override
      public String getDescription() {
        return "Click child view with id " + childId;
      }

      @Override
      public void perform(UiController uiController, View view) {
        View child = view.findViewById(childId);
        if (child != null) {
          child.performClick();
        }
      }
    };
  }
}
