package maurizi.geoclock.ui;

import android.app.AlarmManager;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.gms.maps.model.LatLng;
import com.google.common.collect.ImmutableSet;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlarmManager;

import java.time.DayOfWeek;
import java.util.UUID;

import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.utils.ActiveAlarmManager;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class MapActivityGeofenceTest {

	private Context context;
	private ActiveAlarmManager activeAlarmManager;
	private ShadowAlarmManager shadowAlarmManager;

	@Before
	public void setUp() {
		context = ApplicationProvider.getApplicationContext();
		activeAlarmManager = new ActiveAlarmManager(context);
		AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		shadowAlarmManager = Shadows.shadowOf(am);
		ShadowAlarmManager.setCanScheduleExactAlarms(true);
	}

	// ---- isInsideGeofence ----

	@Test
	public void isInsideGeofence_exactCenter_returnsTrue() {
		LatLng loc = new LatLng(37.4219, -122.0840);
		GeoAlarm alarm = alarmAt(loc, 100);
		assertTrue(MapActivity.isInsideGeofence(loc, alarm));
	}

	@Test
	public void isInsideGeofence_withinRadius_returnsTrue() {
		LatLng alarmLoc = new LatLng(37.4219, -122.0840);
		// ~50m north of the alarm center
		LatLng deviceLoc = new LatLng(37.42235, -122.0840);
		GeoAlarm alarm = alarmAt(alarmLoc, 100);
		assertTrue(MapActivity.isInsideGeofence(deviceLoc, alarm));
	}

	@Test
	public void isInsideGeofence_outsideRadius_returnsFalse() {
		LatLng alarmLoc = new LatLng(37.4219, -122.0840);
		// ~1km away
		LatLng deviceLoc = new LatLng(37.4310, -122.0840);
		GeoAlarm alarm = alarmAt(alarmLoc, 100);
		assertFalse(MapActivity.isInsideGeofence(deviceLoc, alarm));
	}

	// ---- activation scenario: enabled alarm inside geofence gets activated ----

	@Test
	public void enabledAlarm_insideGeofence_getsActivated() {
		LatLng loc = new LatLng(37.4219, -122.0840);
		GeoAlarm alarm = saveAlarm(alarmAt(loc, 100));

		// Simulate what activateAlarmsInsideGeofence does:
		// check location, find alarm is inside, call addActiveAlarms
		assertTrue(MapActivity.isInsideGeofence(loc, alarm));
		activeAlarmManager.addActiveAlarms(ImmutableSet.of(alarm.id));

		assertNotNull("Alarm inside geofence should be scheduled",
		        shadowAlarmManager.getNextScheduledAlarm());
	}

	@Test
	public void enabledAlarm_outsideGeofence_notActivated() {
		LatLng alarmLoc = new LatLng(37.4219, -122.0840);
		LatLng deviceLoc = new LatLng(38.0, -122.0); // far away
		GeoAlarm alarm = saveAlarm(alarmAt(alarmLoc, 100));

		// Device is outside — should not activate
		assertFalse(MapActivity.isInsideGeofence(deviceLoc, alarm));
		// Don't call addActiveAlarms — verify nothing is scheduled
		assertNull("Alarm outside geofence should not be scheduled",
		        shadowAlarmManager.getNextScheduledAlarm());
	}

	@Test
	public void disabledAlarm_insideGeofence_notActivated() {
		LatLng loc = new LatLng(37.4219, -122.0840);
		GeoAlarm alarm = saveAlarm(alarmAt(loc, 100).withEnabled(false));

		// Even though we're inside the geofence, disabled alarms are skipped
		assertTrue(MapActivity.isInsideGeofence(loc, alarm));
		// activateAlarmsInsideGeofence skips disabled alarms, so nothing scheduled
		assertNull("Disabled alarm should not be scheduled",
		        shadowAlarmManager.getNextScheduledAlarm());
	}

	// ---- helpers ----

	private GeoAlarm alarmAt(LatLng location, int radius) {
		return GeoAlarm.builder()
		        .id(UUID.randomUUID())
		        .location(location)
		        .radius(radius)
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
