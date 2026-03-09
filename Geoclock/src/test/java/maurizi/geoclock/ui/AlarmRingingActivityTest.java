package maurizi.geoclock.ui;

import android.app.AlarmManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.widget.Button;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.gms.maps.model.LatLng;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowApplication;

import java.util.UUID;

import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.R;
import maurizi.geoclock.background.AlarmRingingService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class AlarmRingingActivityTest {

	private Context context;

	@Before
	public void setUp() {
		context = ApplicationProvider.getApplicationContext();
		ShadowAlarmManager.setCanScheduleExactAlarms(true);
	}

	@Test
	public void onCreate_withAlarmHavingPlace_showsPlaceName() {
		GeoAlarm alarm = saveAlarm(enabledAlarm().withPlace("Home"));
		AlarmRingingActivity activity = buildActivity(alarm.id.toString());
		TextView nameView = activity.findViewById(R.id.alarm_ringing_name);
		assertEquals("Home", nameView.getText().toString());
	}

	@Test
	public void onCreate_withAlarmNoPlace_nameViewEmpty() {
		GeoAlarm alarm = saveAlarm(enabledAlarm()); // no place
		AlarmRingingActivity activity = buildActivity(alarm.id.toString());
		TextView nameView = activity.findViewById(R.id.alarm_ringing_name);
		// Without a place, setText is not called — default empty text
		assertTrue(nameView.getText().toString().isEmpty());
	}

	@Test
	public void onCreate_timeViewShowsCurrentTime() {
		GeoAlarm alarm = saveAlarm(enabledAlarm());
		AlarmRingingActivity activity = buildActivity(alarm.id.toString());
		TextView timeView = activity.findViewById(R.id.alarm_ringing_time);
		assertNotNull("Time view should not be null", timeView);
		assertFalse("Time view should not be empty", timeView.getText().toString().isEmpty());
	}

	@Test
	@Config(sdk = 26)
	public void onCreate_setsWindowFlagsShowWhenLocked_preSdk27() {
		// On SDK < 27, the activity uses window flags to show over lock screen
		GeoAlarm alarm = saveAlarm(enabledAlarm());
		AlarmRingingActivity activity = buildActivity(alarm.id.toString());
		int flags = activity.getWindow().getAttributes().flags;
		assertTrue("Window should have FLAG_SHOW_WHEN_LOCKED on SDK < 27",
		        (flags & android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED) != 0);
	}

	@Test
	public void dismissButton_stopsAlarmRingingService() {
		GeoAlarm alarm = saveAlarm(enabledAlarm());
		AlarmRingingActivity activity = buildActivity(alarm.id.toString());
		Button dismissButton = activity.findViewById(R.id.alarm_ringing_dismiss);
		dismissButton.performClick();
		ShadowApplication sa = Shadows.shadowOf((Application) context);
		Intent stopped = sa.getNextStoppedService();
		assertNotNull("Dismiss should stop a service", stopped);
		assertEquals(AlarmRingingService.class.getName(), stopped.getComponent().getClassName());
	}

	@Test
	public void dismissButton_finishesActivity() {
		GeoAlarm alarm = saveAlarm(enabledAlarm());
		AlarmRingingActivity activity = buildActivity(alarm.id.toString());
		Button dismissButton = activity.findViewById(R.id.alarm_ringing_dismiss);
		dismissButton.performClick();
		assertTrue("Dismiss should finish the activity", activity.isFinishing());
	}

	@Test
	public void snoozeButton_schedulesSnooze() {
		GeoAlarm alarm = saveAlarm(enabledAlarm());
		AlarmRingingActivity activity = buildActivity(alarm.id.toString());
		Button snoozeButton = activity.findViewById(R.id.alarm_ringing_snooze);
		snoozeButton.performClick();
		AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		ShadowAlarmManager shadowAlarmManager = Shadows.shadowOf(alarmManager);
		assertNotNull("Snooze should schedule an alarm", shadowAlarmManager.getNextScheduledAlarm());
	}

	@Test
	public void snoozeButton_stopsService() {
		GeoAlarm alarm = saveAlarm(enabledAlarm());
		AlarmRingingActivity activity = buildActivity(alarm.id.toString());
		Button snoozeButton = activity.findViewById(R.id.alarm_ringing_snooze);
		snoozeButton.performClick();
		ShadowApplication sa = Shadows.shadowOf((Application) context);
		Intent stopped = sa.getNextStoppedService();
		assertNotNull("Snooze should stop the ringing service", stopped);
		assertEquals(AlarmRingingService.class.getName(), stopped.getComponent().getClassName());
	}

	@Test
	public void snoozeButton_finishesActivity() {
		GeoAlarm alarm = saveAlarm(enabledAlarm());
		AlarmRingingActivity activity = buildActivity(alarm.id.toString());
		Button snoozeButton = activity.findViewById(R.id.alarm_ringing_snooze);
		snoozeButton.performClick();
		assertTrue("Snooze should finish the activity", activity.isFinishing());
	}

	@Test
	public void snoozeButton_withNoAlarm_doesNotCrash() {
		// Launch with no alarm ID — snooze button click should not crash
		Intent intent = new Intent(context, AlarmRingingActivity.class);
		AlarmRingingActivity activity = Robolectric.buildActivity(AlarmRingingActivity.class, intent)
		        .setup().get();
		Button snoozeButton = activity.findViewById(R.id.alarm_ringing_snooze);
		snoozeButton.performClick(); // should not throw
		assertTrue("Activity should finish after snooze even with no alarm", activity.isFinishing());
	}

	@Test
	public void dismissButton_withNoAlarm_doesNotCrash() {
		Intent intent = new Intent(context, AlarmRingingActivity.class);
		AlarmRingingActivity activity = Robolectric.buildActivity(AlarmRingingActivity.class, intent)
		        .setup().get();
		Button dismissButton = activity.findViewById(R.id.alarm_ringing_dismiss);
		dismissButton.performClick(); // should not throw
		assertTrue("Activity should finish after dismiss even with no alarm", activity.isFinishing());
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

	private AlarmRingingActivity buildActivity(String alarmId) {
		Intent intent = new Intent(context, AlarmRingingActivity.class);
		intent.putExtra(AlarmRingingActivity.EXTRA_ALARM_ID, alarmId);
		return Robolectric.buildActivity(AlarmRingingActivity.class, intent).setup().get();
	}
}
