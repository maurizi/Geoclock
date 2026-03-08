package maurizi.geoclock.test;

import com.google.android.gms.maps.model.LatLng;
import com.google.common.collect.ImmutableSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.TimeZone;
import java.util.UUID;

import maurizi.geoclock.GeoAlarm;

import static java.time.temporal.TemporalAdjusters.nextOrSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GeoAlarmTest {

    static final GeoAlarm testAlarm = GeoAlarm.builder()
            .location(new LatLng(0, 0))
            .radius(100)
            .id(UUID.randomUUID())
            .build();

    private TimeZone originalTimeZone;

    @Before
    public void saveTimeZone() {
        originalTimeZone = TimeZone.getDefault();
    }

    @After
    public void restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone);
    }

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

    // ---- DST: spring forward (2024-03-10, America/New_York: 2:00 AM → 3:00 AM) ----

    @Test
    public void calculateAlarmTime_dstSpringForward_alarmInGap_returnsValidDateTime() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        // now = 1:30 AM ET on spring-forward day; alarm at 2:30 (gap hour)
        LocalDateTime now = LocalDateTime.of(2024, 3, 10, 1, 30);
        GeoAlarm alarm = testAlarm.withHour(2).withMinute(30);
        ZonedDateTime result = alarm.calculateAlarmTime(now);
        assertNotNull("Should return a valid ZonedDateTime even for gap hour", result);
        // Java adjusts 2:30 AM (non-existent) to 3:30 AM EDT
        assertEquals(3, result.getHour());
        assertEquals(30, result.getMinute());
    }

    // ---- DST: fall back (2024-11-03, America/New_York: 2:00 AM → 1:00 AM) ----

    @Test
    public void calculateAlarmTime_dstFallBack_alarmInAmbiguousHour_returnsPreFoldTime() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        // now = 12:30 AM ET before fall-back; alarm at 1:30 (ambiguous — occurs twice)
        LocalDateTime now = LocalDateTime.of(2024, 11, 3, 0, 30);
        GeoAlarm alarm = testAlarm.withHour(1).withMinute(30);
        ZonedDateTime result = alarm.calculateAlarmTime(now);
        assertNotNull(result);
        assertEquals(1, result.getHour());
        assertEquals(30, result.getMinute());
        // Java defaults to the pre-fold (EDT = UTC-4) occurrence
        assertEquals(-4 * 3600, result.getOffset().getTotalSeconds());
    }

    // ---- Timezone change ----

    @Test
    public void calculateAlarmTime_differentSystemTimezone_reflectsZone() {
        LocalDateTime now = LocalDateTime.of(2024, 6, 1, 7, 0);

        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        ZonedDateTime utcResult = testAlarm.withHour(8).withMinute(0).calculateAlarmTime(now);

        TimeZone.setDefault(TimeZone.getTimeZone("America/Chicago"));
        ZonedDateTime chicagoResult = testAlarm.withHour(8).withMinute(0).calculateAlarmTime(now);

        assertNotNull(utcResult);
        assertNotNull(chicagoResult);
        // Same local time "08:00" should represent different UTC instants in different zones
        assertNotEquals("Different timezones should produce different UTC instants",
                utcResult.toInstant(), chicagoResult.toInstant());
    }

    // ---- Repeating edge cases ----

    @Test
    public void calculateAlarmTime_repeating_sundayOnly_onSundayAfterTime_firesNextSunday() {
        LocalDate sunday = LocalDate.now().with(nextOrSame(DayOfWeek.SUNDAY));
        // On Sunday, alarm time has already passed — next fire should be 7 days forward
        assertAlarmTime(5, 0, sunday.atTime(6, 0), sunday.plusDays(7).atTime(5, 0),
                new DayOfWeek[]{DayOfWeek.SUNDAY},
                "Sunday-only alarm after time should fire next Sunday (+7 days)");
    }

    @Test
    public void calculateAlarmTime_repeating_allDays_firesWithin24Hours() {
        LocalDateTime now = LocalDateTime.now();
        GeoAlarm alarm = testAlarm
                .withDays(ImmutableSet.copyOf(DayOfWeek.values()))
                .withHour(now.getHour())
                .withMinute(now.getMinute() + 1 > 59 ? 0 : now.getMinute() + 1);
        ZonedDateTime result = alarm.calculateAlarmTime(now);
        assertNotNull(result);
        long hoursUntilAlarm = java.time.Duration.between(
                ZonedDateTime.now(), result).toHours();
        assertTrue("Every-day alarm should fire within 24 hours", hoursUntilAlarm < 24);
    }

    @Test
    public void calculateAlarmTime_repeating_wrapAroundWeek_correctDaysForward() {
        // Friday, alarm only on Monday → fires next Monday (+3 days)
        LocalDate friday = LocalDate.now().with(nextOrSame(DayOfWeek.FRIDAY));
        assertAlarmTime(8, 0, friday.atTime(9, 0), friday.plusDays(3).atTime(8, 0),
                new DayOfWeek[]{DayOfWeek.MONDAY},
                "Mon-only alarm on Friday should fire next Monday (+3 days)");
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
