package maurizi.geoclock.test;

import com.google.android.gms.maps.model.LatLng;
import com.google.common.collect.ImmutableSet;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import maurizi.geoclock.GeoAlarm;

import static java.time.temporal.TemporalAdjusters.nextOrSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GeoAlarmTest {

    static final GeoAlarm testAlarm = GeoAlarm.builder()
            .name("test")
            .location(new LatLng(0, 0))
            .radius(100)
            .id(UUID.randomUUID())
            .build();

    // ---- null / missing-time guards ----

    @Test
    public void calculateAlarmTime_nullNow_returnsNull() {
        assertNull(testAlarm.withHour(8).withMinute(0).calculateAlarmTime(null));
    }

    @Test
    public void calculateAlarmTime_noHourOrMinute_returnsNull() {
        // testAlarm has no hour/minute set
        assertNull(testAlarm.calculateAlarmTime(LocalDateTime.now()));
    }

    @Test
    public void calculateAlarmTime_hourSetButNoMinute_returnsNull() {
        assertNull(testAlarm.withHour(8).calculateAlarmTime(LocalDateTime.now()));
    }

    // ---- Non-repeating alarms ----

    @Test
    public void calculateAlarmTime_nonRepeating_beforeAlarmTime_firesToday() {
        LocalDate today = LocalDate.now();
        assertAlarmTime(5, 0, today.atTime(4, 0), today.atTime(5, 0),
                "Should fire today if current time is before alarm time");
    }

    @Test
    public void calculateAlarmTime_nonRepeating_afterAlarmTime_firesTomorrow() {
        LocalDate today = LocalDate.now();
        assertAlarmTime(5, 0, today.atTime(6, 0), today.plusDays(1).atTime(5, 0),
                "Should fire tomorrow if current time is after alarm time");
    }

    // ---- Repeating alarms ----

    @Test
    public void calculateAlarmTime_repeating_todayIncluded_beforeTime_firesToday() {
        LocalDate monday = LocalDate.now().with(nextOrSame(DayOfWeek.MONDAY));
        assertAlarmTime(5, 0, monday.atTime(4, 0), monday.atTime(5, 0),
                new DayOfWeek[]{DayOfWeek.MONDAY, DayOfWeek.FRIDAY},
                "Should fire today (Monday) since alarm time hasn't passed yet");
    }

    @Test
    public void calculateAlarmTime_repeating_laterDayThisWeek_firesNext() {
        LocalDate monday = LocalDate.now().with(nextOrSame(DayOfWeek.MONDAY));
        // Monday 6am, alarm for {Mon, Sun} at 5am — Monday's 5am passed, Sunday (value=7) comes next
        assertAlarmTime(5, 0, monday.atTime(6, 0), monday.plusDays(6).atTime(5, 0),
                new DayOfWeek[]{DayOfWeek.MONDAY, DayOfWeek.SUNDAY},
                "Should fire Sunday — next day with value > Monday");
    }

    @Test
    public void calculateAlarmTime_repeating_onlyDayIsToday_afterTime_firesNextWeek() {
        LocalDate monday = LocalDate.now().with(nextOrSame(DayOfWeek.MONDAY));
        assertAlarmTime(5, 0, monday.atTime(6, 0), monday.plusDays(7).atTime(5, 0),
                new DayOfWeek[]{DayOfWeek.MONDAY},
                "Should fire next Monday when only day is Monday and alarm already passed");
    }

    // ---- Helpers ----

    private void assertAlarmTime(int hour, int minute, LocalDateTime now,
                                  LocalDateTime expected, String message) {
        assertAlarmTime(hour, minute, now, expected, new DayOfWeek[]{}, message);
    }

    private void assertAlarmTime(int hour, int minute, LocalDateTime now,
                                  LocalDateTime expectedLocal, DayOfWeek[] days, String message) {
        GeoAlarm alarm = testAlarm
                .withDays(ImmutableSet.copyOf(days))
                .withHour(hour)
                .withMinute(minute);
        ZonedDateTime expected = expectedLocal.atZone(ZoneId.systemDefault());
        ZonedDateTime actual = alarm.calculateAlarmTime(now);
        assertEquals(message, expected, actual);
    }
}
