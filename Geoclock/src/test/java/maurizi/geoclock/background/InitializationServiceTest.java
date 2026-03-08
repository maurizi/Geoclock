package maurizi.geoclock.background;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.gms.maps.model.LatLng;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Method;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;

import maurizi.geoclock.GeoAlarm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class InitializationServiceTest {

    private static final String ALARM_PREFS = "alarms";
    private static final Gson gson = new Gson();

    private Context context;
    private InitializationService service;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        service = Robolectric.setupService(InitializationService.class);
    }

    /**
     * Regression test for the bug fix: non-repeating expired alarms must be saved as disabled.
     * Previously GeoAlarm.save(this, alarm) was called without .withEnabled(false), leaving the
     * alarm perpetually enabled.
     */
    @Test
    public void disableExpiredAlarms_expiredNonRepeating_savesAsDisabled() throws Exception {
        long oneHourAgo = Instant.now().minusSeconds(3600).toEpochMilli();
        GeoAlarm alarm = GeoAlarm.builder()
                .id(UUID.randomUUID())
                .location(new LatLng(0, 0))
                .radius(100)
                .enabled(true)
                .hour(8)
                .minute(0)
                .time(oneHourAgo)
                .build();
        // Write directly to bypass GeoAlarm.save()'s time-recalculation logic
        saveRaw(alarm);

        invokeDisableExpiredAlarms(Arrays.asList(alarm));

        GeoAlarm saved = GeoAlarm.getGeoAlarm(context, alarm.id);
        assertNotNull(saved);
        assertEquals("Expired non-repeating alarm should be disabled", false, saved.enabled);
    }

    @Test
    public void disableExpiredAlarms_unexpiredNonRepeating_remainsEnabled() throws Exception {
        long oneHourFromNow = Instant.now().plusSeconds(3600).toEpochMilli();
        GeoAlarm alarm = GeoAlarm.builder()
                .id(UUID.randomUUID())
                .location(new LatLng(0, 0))
                .radius(100)
                .enabled(true)
                .hour(8)
                .minute(0)
                .time(oneHourFromNow)
                .build();
        saveRaw(alarm);

        invokeDisableExpiredAlarms(Arrays.asList(alarm));

        GeoAlarm saved = GeoAlarm.getGeoAlarm(context, alarm.id);
        assertNotNull(saved);
        assertEquals("Unexpired alarm should remain enabled", true, saved.enabled);
    }

    @Test
    public void disableExpiredAlarms_repeatingAlarm_neverExpired() throws Exception {
        long oneHourAgo = Instant.now().minusSeconds(3600).toEpochMilli();
        GeoAlarm alarm = GeoAlarm.builder()
                .id(UUID.randomUUID())
                .location(new LatLng(0, 0))
                .radius(100)
                .enabled(true)
                .hour(8)
                .minute(0)
                .time(oneHourAgo)
                .days(ImmutableSet.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
                .build();
        saveRaw(alarm);

        invokeDisableExpiredAlarms(Arrays.asList(alarm));

        GeoAlarm saved = GeoAlarm.getGeoAlarm(context, alarm.id);
        assertNotNull(saved);
        assertEquals("Repeating alarm should never be disabled by expiry", true, saved.enabled);
    }

    @Test
    public void disableExpiredAlarms_nullTime_notDisabled() throws Exception {
        GeoAlarm alarm = GeoAlarm.builder()
                .id(UUID.randomUUID())
                .location(new LatLng(0, 0))
                .radius(100)
                .enabled(true)
                .hour(8)
                .minute(0)
                // time is null — no epoch millis
                .build();
        saveRaw(alarm);

        invokeDisableExpiredAlarms(Arrays.asList(alarm));

        GeoAlarm saved = GeoAlarm.getGeoAlarm(context, alarm.id);
        assertNotNull(saved);
        assertEquals("Alarm with null time should not be disabled", true, saved.enabled);
    }

    @Test
    public void disableExpiredAlarms_multipleAlarms_onlyDisablesExpired() throws Exception {
        long oneHourAgo = Instant.now().minusSeconds(3600).toEpochMilli();
        long oneHourFromNow = Instant.now().plusSeconds(3600).toEpochMilli();

        GeoAlarm expiredAlarm = GeoAlarm.builder()
                .id(UUID.randomUUID())
                .location(new LatLng(0, 0))
                .radius(100)
                .enabled(true)
                .hour(8)
                .minute(0)
                .time(oneHourAgo)
                .build();
        GeoAlarm futureAlarm = GeoAlarm.builder()
                .id(UUID.randomUUID())
                .location(new LatLng(0, 0))
                .radius(100)
                .enabled(true)
                .hour(9)
                .minute(0)
                .time(oneHourFromNow)
                .build();

        saveRaw(expiredAlarm);
        saveRaw(futureAlarm);

        invokeDisableExpiredAlarms(Arrays.asList(expiredAlarm, futureAlarm));

        assertEquals(false, GeoAlarm.getGeoAlarm(context, expiredAlarm.id).enabled);
        assertEquals(true, GeoAlarm.getGeoAlarm(context, futureAlarm.id).enabled);
    }

    // ---- helpers ----

    /** Write alarm directly to SharedPreferences without GeoAlarm.save()'s time recalculation. */
    private void saveRaw(GeoAlarm alarm) {
        SharedPreferences prefs = context.getSharedPreferences(ALARM_PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(alarm.id.toString(), gson.toJson(alarm, GeoAlarm.class)).commit();
    }

    private void invokeDisableExpiredAlarms(Collection<GeoAlarm> alarms) throws Exception {
        Method m = InitializationService.class.getDeclaredMethod("disableExpiredAlarms", Collection.class);
        m.setAccessible(true);
        m.invoke(service, alarms);
    }
}
