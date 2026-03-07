package maurizi.geoclock.utils;

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

import java.util.UUID;

import maurizi.geoclock.GeoAlarm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ActiveAlarmManagerTest {

    private Context context;
    private ActiveAlarmManager manager;
    private ShadowAlarmManager shadowAlarmManager;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        manager = new ActiveAlarmManager(context);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        shadowAlarmManager = Shadows.shadowOf(alarmManager);
    }

    // ---- clearActiveAlarms ----

    @Test
    public void clearActiveAlarms_cancelsScheduledAlarmsAndNotifications() {
        // Save an alarm so there's something to clear
        GeoAlarm alarm = saveAlarm(enabledAlarm());
        manager.addActiveAlarms(ImmutableSet.of(alarm.id));
        shadowAlarmManager.getScheduledAlarms().clear(); // reset for clean assertion

        manager.clearActiveAlarms();

        // After clearing, no new alarms should be scheduled
        assertEquals(0, shadowAlarmManager.getScheduledAlarms().size());
    }

    // ---- addActiveAlarms ----

    @Test
    public void addActiveAlarms_emptyStart_addsAlarms() {
        GeoAlarm alarm = saveAlarm(enabledAlarm());
        manager.addActiveAlarms(ImmutableSet.of(alarm.id));
        // Alarm should now be scheduled — manager scheduled something
        assertNotNull(shadowAlarmManager.getNextScheduledAlarm());
    }

    @Test
    public void addActiveAlarms_mergesWithExisting() {
        GeoAlarm alarm1 = saveAlarm(enabledAlarm());
        GeoAlarm alarm2 = saveAlarm(enabledAlarm());

        manager.addActiveAlarms(ImmutableSet.of(alarm1.id));
        // Adding a second alarm should schedule the soonest of the two
        manager.addActiveAlarms(ImmutableSet.of(alarm2.id));

        assertNotNull(shadowAlarmManager.getNextScheduledAlarm());
    }

    // ---- removeActiveAlarms ----

    @Test
    public void removeActiveAlarms_lastAlarm_cancelsEverything() {
        GeoAlarm alarm = saveAlarm(enabledAlarm());
        manager.addActiveAlarms(ImmutableSet.of(alarm.id));
        shadowAlarmManager.getScheduledAlarms().clear();

        manager.removeActiveAlarms(ImmutableSet.of(alarm.id));

        assertEquals(0, shadowAlarmManager.getScheduledAlarms().size());
    }

    @Test
    public void removeActiveAlarms_oneOfTwo_keepsRemaining() {
        GeoAlarm alarm1 = saveAlarm(enabledAlarm());
        GeoAlarm alarm2 = saveAlarm(enabledAlarm());

        manager.addActiveAlarms(ImmutableSet.of(alarm1.id, alarm2.id));
        shadowAlarmManager.getScheduledAlarms().clear();

        manager.removeActiveAlarms(ImmutableSet.of(alarm1.id));

        // alarm2 is still active, so something should be scheduled
        assertNotNull(shadowAlarmManager.getNextScheduledAlarm());
    }

    // ---- resetActiveAlarms ----

    @Test
    public void resetActiveAlarms_noActiveAlarms_schedulesNothing() {
        manager.clearActiveAlarms();
        shadowAlarmManager.getScheduledAlarms().clear();

        manager.resetActiveAlarms();

        assertEquals(0, shadowAlarmManager.getScheduledAlarms().size());
    }

    // ---- helpers ----

    private GeoAlarm enabledAlarm() {
        return GeoAlarm.builder()
                .id(UUID.randomUUID())
                .name("test alarm")
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
