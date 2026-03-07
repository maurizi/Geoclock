package maurizi.geoclock.background;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.gms.maps.model.LatLng;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

import java.util.UUID;

import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.ui.AlarmRingingActivity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class AlarmClockReceiverTest {

    private Context context;
    private AlarmClockReceiver receiver;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        receiver = new AlarmClockReceiver();
    }

    @Test
    public void onReceive_noAlarmIdExtra_doesNotStartActivity() {
        receiver.onReceive(context, new Intent(context, AlarmClockReceiver.class));
        assertNull(Shadows.shadowOf((android.app.Application) context).getNextStartedActivity());
    }

    @Test
    public void onReceive_unknownAlarmId_doesNotStartActivity() {
        Intent intent = new Intent(context, AlarmClockReceiver.class);
        intent.putExtra("alarm_id", UUID.randomUUID().toString());

        receiver.onReceive(context, intent);

        assertNull(Shadows.shadowOf((android.app.Application) context).getNextStartedActivity());
    }

    @Test
    public void onReceive_enabledAlarm_startsAlarmRingingActivity() {
        GeoAlarm alarm = saveAlarm(enabledAlarm());
        Intent intent = alarmIntent(alarm);

        receiver.onReceive(context, intent);

        Intent started = Shadows.shadowOf((android.app.Application) context).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(AlarmRingingActivity.class.getName(), started.getComponent().getClassName());
        assertEquals(alarm.id.toString(), started.getStringExtra(AlarmRingingActivity.EXTRA_ALARM_ID));
    }

    @Test
    public void onReceive_disabledAlarm_doesNotStartActivity() {
        GeoAlarm alarm = saveAlarm(enabledAlarm().withEnabled(false));
        receiver.onReceive(context, alarmIntent(alarm));
        assertNull(Shadows.shadowOf((android.app.Application) context).getNextStartedActivity());
    }

    @Test
    public void onReceive_nonRepeatingAlarm_disablesAlarmAfterFiring() {
        GeoAlarm alarm = saveAlarm(enabledAlarm()); // no days = non-repeating
        receiver.onReceive(context, alarmIntent(alarm));

        // Alarm should be saved as disabled
        GeoAlarm saved = GeoAlarm.getGeoAlarm(context, alarm.id);
        assertNotNull(saved);
        assertEquals(false, saved.enabled);
    }

    @Test
    public void onReceive_repeatingAlarm_remainsEnabled() {
        GeoAlarm alarm = saveAlarm(enabledRepeatingAlarm());
        receiver.onReceive(context, alarmIntent(alarm));

        GeoAlarm saved = GeoAlarm.getGeoAlarm(context, alarm.id);
        assertNotNull(saved);
        assertEquals(true, saved.enabled);
    }

    // ---- helpers ----

    private GeoAlarm enabledAlarm() {
        return GeoAlarm.builder()
                .id(UUID.randomUUID())
                .name("test")
                .location(new LatLng(0, 0))
                .radius(100)
                .enabled(true)
                .hour(8)
                .minute(0)
                .build();
    }

    private GeoAlarm enabledRepeatingAlarm() {
        return enabledAlarm().withDays(
                com.google.common.collect.ImmutableSet.of(
                        java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.FRIDAY));
    }

    private GeoAlarm saveAlarm(GeoAlarm alarm) {
        GeoAlarm.save(context, alarm);
        return alarm;
    }

    private Intent alarmIntent(GeoAlarm alarm) {
        Intent intent = new Intent(context, AlarmClockReceiver.class);
        intent.putExtra("alarm_id", alarm.id.toString());
        return intent;
    }
}
