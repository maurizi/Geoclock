package maurizi.geoclock.ui;

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

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.R;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility;
import static org.hamcrest.Matchers.containsString;

@SdkSuppress(minSdkVersion = 27, maxSdkVersion = 35)
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MapActivityTest {

	@Rule
	public GrantPermissionRule permissionRule = GrantPermissionRule.grant(getRequiredPermissions());

	private static String[] getRequiredPermissions() {
		List<String> perms = new ArrayList<>();
		perms.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
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
		wakeLock = pm.newWakeLock(
		        PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
		        "geoclock:test");
		wakeLock.acquire(60_000);
	}

	@After
	public void tearDown() {
		if (testAlarm != null) {
			GeoAlarm.remove(ApplicationProvider.getApplicationContext(), testAlarm);
		}
		if (scenario != null) {
			scenario.close();
		}
		if (wakeLock != null && wakeLock.isHeld()) {
			wakeLock.release();
		}
	}

	private GeoAlarm saveTestAlarm() {
		testAlarm = GeoAlarm.builder()
		        .id(UUID.randomUUID())
		        .place("Test Place")
		        .location(new LatLng(37.4220, -122.0841))
		        .radius(100)
		        .enabled(true)
		        .hour(9)
		        .minute(0)
		        .build();
		GeoAlarm.save(ApplicationProvider.getApplicationContext(), testAlarm);
		return testAlarm;
	}

	@Test
	public void activityLaunches_withoutCrashing() {
		scenario = ActivityScenario.launch(MapActivity.class);
		onView(withId(R.id.fab_add)).check(matches(isDisplayed()));
	}

	@Test
	public void emptyState_shownWhenNoAlarms() {
		// Ensure no alarms exist
		Collection<GeoAlarm> alarms = GeoAlarm.getGeoAlarms(ApplicationProvider.getApplicationContext());
		for (GeoAlarm a : alarms) {
			GeoAlarm.remove(ApplicationProvider.getApplicationContext(), a);
		}
		scenario = ActivityScenario.launch(MapActivity.class);
		onView(withId(R.id.empty_state))
		        .check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
		onView(withId(R.id.alarm_list))
		        .check(matches(withEffectiveVisibility(Visibility.GONE)));
	}

	@Test
	public void alarmList_shownWhenAlarmsExist() {
		saveTestAlarm();
		scenario = ActivityScenario.launch(MapActivity.class);
		onView(withId(R.id.alarm_list))
		        .check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
		onView(withId(R.id.empty_state))
		        .check(matches(withEffectiveVisibility(Visibility.GONE)));
	}

	@Test
	public void fab_isDisplayed() {
		scenario = ActivityScenario.launch(MapActivity.class);
		onView(withId(R.id.fab_add)).check(matches(isDisplayed()));
	}

	@Test
	public void addDialog_opensViaShowAddPopup() {
		scenario = ActivityScenario.launch(MapActivity.class);
		scenario.onActivity(activity -> activity.showAddPopup(new LatLng(37.4220, -122.0841)));
		onView(withId(R.id.add_geo_alarm_time)).inRoot(isDialog())
		        .check(matches(isDisplayed()));
	}

	@Test
	public void editDialog_opensForExistingAlarm() {
		GeoAlarm alarm = saveTestAlarm();
		scenario = ActivityScenario.launch(MapActivity.class);
		scenario.onActivity(activity -> activity.showEditPopup(alarm.id));
		onView(withId(R.id.add_geo_alarm_delete))
		        .inRoot(isDialog())
		        .check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
	}
}
