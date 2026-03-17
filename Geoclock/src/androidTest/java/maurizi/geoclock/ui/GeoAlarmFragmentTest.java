package maurizi.geoclock.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isNotChecked;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
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
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SdkSuppress(minSdkVersion = 27, maxSdkVersion = 35)
@RunWith(AndroidJUnit4.class)
@LargeTest
public class GeoAlarmFragmentTest {

  @Rule
  public GrantPermissionRule permissionRule = GrantPermissionRule.grant(getRequiredPermissions());

  private static String[] getRequiredPermissions() {
    List<String> perms = new ArrayList<>();
    perms.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      perms.add(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      perms.add(android.Manifest.permission.POST_NOTIFICATIONS);
    }
    return perms.toArray(new String[0]);
  }

  private ActivityScenario<MapActivity> scenario;
  private PowerManager.WakeLock wakeLock;
  private GeoAlarm testAlarm;

  @SuppressWarnings("deprecation")
  @Before
  public void setUp() {
    Context ctx = ApplicationProvider.getApplicationContext();
    PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
    wakeLock =
        pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "geoclock:test");
    wakeLock.acquire(60_000);
  }

  @After
  public void tearDown() {
    if (testAlarm != null) {
      GeoAlarm.remove(ApplicationProvider.getApplicationContext(), testAlarm);
    }
    // Clean up any alarms created by save tests
    Collection<GeoAlarm> alarms =
        GeoAlarm.getGeoAlarms(ApplicationProvider.getApplicationContext());
    for (GeoAlarm a : alarms) {
      GeoAlarm.remove(ApplicationProvider.getApplicationContext(), a);
    }
    if (scenario != null) {
      scenario.close();
    }
    if (wakeLock != null && wakeLock.isHeld()) {
      wakeLock.release();
    }
  }

  private void launchAndShowAdd(LatLng latLng) {
    scenario = ActivityScenario.launch(MapActivity.class);
    scenario.onActivity(activity -> activity.showAddPopup(latLng));
    // Sync: wait for dialog to render before tests proceed
    onView(withId(R.id.add_geo_alarm_time)).inRoot(isDialog()).check(matches(isDisplayed()));
  }

  private void launchAndShowEdit(UUID id) {
    scenario = ActivityScenario.launch(MapActivity.class);
    scenario.onActivity(activity -> activity.showEditPopup(id));
    // Sync: wait for dialog to render before tests proceed
    onView(withId(R.id.add_geo_alarm_time)).inRoot(isDialog()).check(matches(isDisplayed()));
  }

  // --- Add mode tests ---

  @Test
  public void addDialog_displaysAllControls() {
    launchAndShowAdd(new LatLng(37.4220, -122.0841));
    // TimePicker and location preview are above the fold
    onView(withId(R.id.add_geo_alarm_time)).check(matches(isDisplayed()));
    onView(withId(R.id.location_preview)).check(matches(isDisplayed()));
    onView(withId(R.id.change_location_button)).check(matches(isDisplayed()));
    // Checkboxes may be scrolled off-screen on small emulator displays
    onView(withId(R.id.mon)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
    // Button row is outside ScrollView, always present
    onView(withId(R.id.add_geo_alarm_cancel))
        .check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
    onView(withId(R.id.add_geo_alarm_save))
        .check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
  }

  @Test
  public void addDialog_locationPreview_showsCoordinates() {
    launchAndShowAdd(new LatLng(37.4220, -122.0841));
    // Location preview shows either coordinates or a reverse-geocoded address
    onView(withId(R.id.location_preview))
        .check(
            (view, noViewFoundException) -> {
              if (noViewFoundException != null) throw noViewFoundException;
              String text = ((android.widget.EditText) view).getText().toString();
              assertTrue("location_preview should not be empty, got: " + text, !text.isEmpty());
            });
  }

  @Test
  public void addDialog_cancelButton_dismisses() throws Exception {
    launchAndShowAdd(new LatLng(37.4220, -122.0841));
    onView(withId(R.id.add_geo_alarm_cancel)).perform(click());
    Thread.sleep(500);
    // After cancel, the dialog fragment should be dismissed
    scenario.onActivity(
        activity -> {
          assertNull(
              "Fragment should be dismissed",
              activity.getSupportFragmentManager().findFragmentByTag("AddGeoAlarmFragment"));
        });
  }

  @Test
  public void addDialog_saveButton_createsAlarm() throws Exception {
    launchAndShowAdd(new LatLng(37.4220, -122.0841));
    // Check Monday
    onView(withId(R.id.mon)).perform(scrollTo(), click());
    onView(withId(R.id.add_geo_alarm_save)).perform(click());
    // Poll until the alarm appears in SharedPreferences (async geofence + save)
    long deadline = System.currentTimeMillis() + 10_000;
    Collection<GeoAlarm> alarms;
    do {
      Thread.sleep(200);
      alarms = GeoAlarm.getGeoAlarms(ApplicationProvider.getApplicationContext());
    } while (alarms.isEmpty() && System.currentTimeMillis() < deadline);
    assertTrue("Should have created at least one alarm", alarms.size() >= 1);
  }

  @Test
  public void addDialog_dayCheckboxes_toggle() {
    launchAndShowAdd(new LatLng(37.4220, -122.0841));
    onView(withId(R.id.mon)).check(matches(isNotChecked()));
    onView(withId(R.id.mon)).perform(scrollTo(), click());
    onView(withId(R.id.mon)).check(matches(isChecked()));
    onView(withId(R.id.mon)).perform(scrollTo(), click());
    onView(withId(R.id.mon)).check(matches(isNotChecked()));
  }

  @Test
  public void addDialog_deleteButton_isHidden() {
    launchAndShowAdd(new LatLng(37.4220, -122.0841));
    onView(withId(R.id.add_geo_alarm_delete))
        .inRoot(isDialog())
        .check(matches(withEffectiveVisibility(Visibility.GONE)));
  }

  // --- Edit mode tests ---

  private GeoAlarm saveTestAlarm() {
    testAlarm =
        GeoAlarm.builder()
            .id(UUID.randomUUID())
            .place("Office")
            .location(new LatLng(37.4220, -122.0841))
            .radius(100)
            .enabled(true)
            .hour(8)
            .minute(30)
            .days(ImmutableSet.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
            .build();
    GeoAlarm.save(ApplicationProvider.getApplicationContext(), testAlarm);
    return testAlarm;
  }

  @Test
  public void editDialog_populatesExistingValues() {
    GeoAlarm alarm = saveTestAlarm();
    launchAndShowEdit(alarm.id);
    onView(withId(R.id.location_preview)).check(matches(withText(containsString("Office"))));
    onView(withId(R.id.mon)).check(matches(isChecked()));
    onView(withId(R.id.fri)).check(matches(isChecked()));
    onView(withId(R.id.tues)).check(matches(isNotChecked()));
    onView(withId(R.id.add_geo_alarm_delete))
        .check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
  }

  @Test
  public void editDialog_deleteButton_showsConfirmation() {
    GeoAlarm alarm = saveTestAlarm();
    launchAndShowEdit(alarm.id);
    onView(withId(R.id.add_geo_alarm_delete)).perform(click());
    // Confirmation dialog should appear
    onView(withText(R.string.delete_confirm_title)).check(matches(isDisplayed()));
  }

  @Test
  public void editDialog_deleteConfirm_removesAlarm() throws Exception {
    GeoAlarm alarm = saveTestAlarm();
    launchAndShowEdit(alarm.id);
    onView(withId(R.id.add_geo_alarm_delete)).perform(click());
    // Confirm deletion
    onView(withText(R.string.add_geo_alarm_delete)).perform(click());
    Thread.sleep(500);
    GeoAlarm found = GeoAlarm.getGeoAlarm(ApplicationProvider.getApplicationContext(), alarm.id);
    assertNull("Alarm should be removed", found);
    testAlarm = null; // Already deleted
  }

  @Test
  public void editDialog_save_updatesAlarm() throws Exception {
    GeoAlarm alarm = saveTestAlarm();
    launchAndShowEdit(alarm.id);
    // Toggle Wednesday on
    onView(withId(R.id.wed)).perform(scrollTo(), click());
    onView(withId(R.id.add_geo_alarm_save)).perform(click());
    // Poll until the updated alarm appears (async geofence + save)
    long deadline = System.currentTimeMillis() + 10_000;
    boolean found = false;
    while (!found && System.currentTimeMillis() < deadline) {
      Thread.sleep(200);
      Collection<GeoAlarm> alarms =
          GeoAlarm.getGeoAlarms(ApplicationProvider.getApplicationContext());
      for (GeoAlarm a : alarms) {
        if (a.days != null
            && a.days.contains(DayOfWeek.WEDNESDAY)
            && a.days.contains(DayOfWeek.MONDAY)
            && a.days.contains(DayOfWeek.FRIDAY)) {
          found = true;
          break;
        }
      }
    }
    assertTrue("Updated alarm should have MON, WED, FRI", found);
  }
}
