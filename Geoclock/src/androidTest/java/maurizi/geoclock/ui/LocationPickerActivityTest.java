package maurizi.geoclock.ui;

import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import maurizi.geoclock.R;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@SdkSuppress(minSdkVersion = 27, maxSdkVersion = 35)
@RunWith(AndroidJUnit4.class)
@LargeTest
public class LocationPickerActivityTest {

	private ActivityScenario<LocationPickerActivity> scenario;
	private PowerManager.WakeLock wakeLock;

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
		if (scenario != null) {
			scenario.close();
		}
		if (wakeLock != null && wakeLock.isHeld()) {
			wakeLock.release();
		}
	}

	private Intent makeIntent() {
		return new Intent(ApplicationProvider.getApplicationContext(), LocationPickerActivity.class);
	}

	@Test
	public void activityLaunches_withoutCrashing() {
		scenario = ActivityScenario.launch(makeIntent());
		onView(withId(R.id.confirm_button)).check(matches(isDisplayed()));
	}

	@Test
	public void placeInput_showsInitialPlace() {
		Intent intent = makeIntent();
		intent.putExtra(LocationPickerActivity.EXTRA_PLACE, "Central Park");
		intent.putExtra(LocationPickerActivity.EXTRA_INITIAL_LAT, 40.7829);
		intent.putExtra(LocationPickerActivity.EXTRA_INITIAL_LNG, -73.9654);
		scenario = ActivityScenario.launch(intent);
		onView(withId(R.id.place_input)).check(matches(withText("Central Park")));
	}

	@Test
	public void placeInput_emptyWhenNoPlaceProvided() {
		Intent intent = makeIntent();
		intent.putExtra(LocationPickerActivity.EXTRA_INITIAL_LAT, 37.4220);
		intent.putExtra(LocationPickerActivity.EXTRA_INITIAL_LNG, -122.0841);
		scenario = ActivityScenario.launch(intent);
		onView(withId(R.id.place_input)).check(matches(withText("")));
	}

	@Test
	public void radiusBar_showsInitialRadius() {
		Intent intent = makeIntent();
		intent.putExtra(LocationPickerActivity.EXTRA_INITIAL_RADIUS, 100);
		scenario = ActivityScenario.launch(intent);
		scenario.onActivity(activity -> {
			android.widget.SeekBar bar = activity.findViewById(R.id.radius_bar);
			// SeekBar progress = radius - MIN_RADIUS (20), so 100 - 20 = 80
			assertEquals("SeekBar progress should reflect initial radius", 80, bar.getProgress());
		});
	}

	@Test
	public void confirmButton_finishesActivity() throws Exception {
		Intent intent = makeIntent();
		intent.putExtra(LocationPickerActivity.EXTRA_INITIAL_LAT, 37.4220);
		intent.putExtra(LocationPickerActivity.EXTRA_INITIAL_LNG, -122.0841);
		intent.putExtra(LocationPickerActivity.EXTRA_INITIAL_RADIUS, 50);
		intent.putExtra(LocationPickerActivity.EXTRA_PLACE, "Googleplex");
		scenario = ActivityScenario.launch(intent);
		onView(withId(R.id.confirm_button)).perform(click());
		Thread.sleep(500);
		assertEquals("Activity should be destroyed after confirm",
		        Lifecycle.State.DESTROYED, scenario.getState());
	}

	@Test
	public void toolbar_backButton_finishesActivity() throws Exception {
		scenario = ActivityScenario.launch(makeIntent());
		onView(withId(R.id.toolbar)).check(matches(isDisplayed()));
		onView(withContentDescription(androidx.appcompat.R.string.abc_action_bar_up_description))
		        .perform(click());
		// Poll until the activity is destroyed
		long deadline = System.currentTimeMillis() + 5_000;
		while (scenario.getState() != Lifecycle.State.DESTROYED && System.currentTimeMillis() < deadline) {
			Thread.sleep(100);
		}
		assertEquals("Activity should be destroyed after back",
		        Lifecycle.State.DESTROYED, scenario.getState());
	}
}
