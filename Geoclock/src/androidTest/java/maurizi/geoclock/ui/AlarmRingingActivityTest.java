package maurizi.geoclock.ui;

import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;

import com.google.android.gms.maps.model.LatLng;

import org.junit.After;
import org.junit.Before;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.R;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

// Espresso's InputManagerEventInjectionStrategy uses InputManager.getInstance() via
// reflection, which was restricted in API 36. Skip until Espresso ships a fix.
@SdkSuppress(minSdkVersion = 27, maxSdkVersion = 35)
@RunWith(AndroidJUnit4.class)
@LargeTest
public class AlarmRingingActivityTest {

	private GeoAlarm testAlarm;
	private ActivityScenario<AlarmRingingActivity> scenario;
	private PowerManager.WakeLock wakeLock;

	@SuppressWarnings("deprecation")
	@Before
	public void setUp() {
		Context ctx = ApplicationProvider.getApplicationContext();
		// Keep screen on and dismiss keyguard so Espresso can interact with the activity
		PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
		wakeLock = pm.newWakeLock(
		        PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
		        "geoclock:test");
		wakeLock.acquire(60_000);

		testAlarm = GeoAlarm.builder()
		        .id(UUID.randomUUID())
		        .place("Morning Run")
		        .location(new LatLng(37.4, -122.0))
		        .radius(100)
		        .enabled(true)
		        .hour(7)
		        .minute(30)
		        .build();
		GeoAlarm.save(ApplicationProvider.getApplicationContext(), testAlarm);
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

	private ActivityScenario<AlarmRingingActivity> launch() {
		Intent intent = new Intent(ApplicationProvider.getApplicationContext(), AlarmRingingActivity.class);
		intent.putExtra(AlarmRingingActivity.EXTRA_ALARM_ID, testAlarm.id.toString());
		return ActivityScenario.launch(intent);
	}

	@Test
	public void alarmNameIsDisplayed() {
		scenario = launch();
		onView(withId(R.id.alarm_ringing_name)).check(matches(withText("Morning Run")));
	}

	@Test
	public void dismissButtonIsDisplayed() {
		scenario = launch();
		onView(withId(R.id.alarm_ringing_dismiss)).check(matches(isDisplayed()));
	}

	@Test
	public void snoozeButtonIsDisplayed() {
		scenario = launch();
		onView(withId(R.id.alarm_ringing_snooze)).check(matches(isDisplayed()));
	}

	@Test
	public void dismissButton_closesActivity() throws Exception {
		scenario = launch();
		onView(withId(R.id.alarm_ringing_dismiss)).perform(click());
		Thread.sleep(1000);
		assertActivityFinishedOrDestroyed();
	}

	@Test
	public void snoozeButton_closesActivity() throws Exception {
		scenario = launch();
		onView(withId(R.id.alarm_ringing_snooze)).perform(click());
		Thread.sleep(1000);
		assertActivityFinishedOrDestroyed();
	}

	private void assertActivityFinishedOrDestroyed() {
		androidx.lifecycle.Lifecycle.State state = scenario.getState();
		if (state == androidx.lifecycle.Lifecycle.State.DESTROYED) return;
		// Activity may not be fully destroyed yet but should be finishing
		scenario.onActivity(activity -> assertTrue(
		        "Activity should be finishing", activity.isFinishing()));
	}

	@Test
	public void launchWithNoAlarmId_showsTimeWithoutCrashing() {
		// Should not crash if no alarm ID is in the intent
		Intent intent = new Intent(ApplicationProvider.getApplicationContext(), AlarmRingingActivity.class);
		try (ActivityScenario<AlarmRingingActivity> s = ActivityScenario.launch(intent)) {
			onView(withId(R.id.alarm_ringing_dismiss)).check(matches(isDisplayed()));
		}
	}
}
