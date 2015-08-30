package maurizi.geoclock.test;

import com.google.android.gms.maps.model.LatLng;
import com.google.common.collect.ImmutableSet;

import org.junit.Test;
import org.threeten.bp.DayOfWeek;
import org.threeten.bp.LocalDate;
import org.threeten.bp.LocalDateTime;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZonedDateTime;

import java.util.UUID;

import maurizi.geoclock.GeoAlarm;

import static org.junit.Assert.assertEquals;
import static org.threeten.bp.temporal.TemporalAdjusters.nextOrSame;

@SuppressWarnings("ConstantConditions")
public class GeoAlarmTest {

	static final GeoAlarm testAlarm = GeoAlarm.builder()
			.name("test")
			.location(new LatLng(0, 0))
			.radius(1000)
			.id(UUID.randomUUID())
			.build();

	private void assertAlarmManager(int alarmHour, int alarmDay, LocalDateTime currentTime, LocalDateTime expectedTime, String message) {
		assertAlarmManager(alarmHour, alarmDay, currentTime, expectedTime, new DayOfWeek[] {}, message);
	}

	private void assertAlarmManager(int alarmHour, int alarmMinutes, LocalDateTime currentTime, LocalDateTime expectedLocalTime, DayOfWeek[] days, String message) {
		final GeoAlarm timedTestAlarm = testAlarm.withDays(ImmutableSet.copyOf(days))
		                                         .withHour(alarmHour)
		                                         .withMinute(alarmMinutes);

		final ZonedDateTime expectedTime = expectedLocalTime.atZone(ZoneId.systemDefault());

		final ZonedDateTime actualTime = timedTestAlarm.calculateAlarmTime(currentTime);

		assertEquals(message, expectedTime, actualTime);
	}
	@Test
	public void testAlarmManagerTime() {
		final LocalDate today = LocalDate.now();

		assertAlarmManager(5, 0, today.atTime(4, 0), today.atTime(5, 0), "Should go off today if current time before alarm time");
		assertAlarmManager(5, 0, today.atTime(6, 0), today.plusDays(1).atTime(5, 0), "Should go off tomorrow if current time after alarm time");

		final LocalDate monday = today.with(nextOrSame(DayOfWeek.MONDAY));
		assertAlarmManager(5, 0, monday.atTime(6, 0), monday.plusDays(7).atTime(5, 0), new DayOfWeek[] {DayOfWeek.MONDAY}, "Should go off next monday at 5");
		assertAlarmManager(5, 0, monday.atTime(6, 0), monday.plusDays(6).atTime(5, 0), new DayOfWeek[] {DayOfWeek.MONDAY, DayOfWeek.SUNDAY}, "Should go off sunday at 5");
		assertAlarmManager(5, 0, monday.atTime(4, 0), monday.atTime(5, 0), new DayOfWeek[] {DayOfWeek.MONDAY, DayOfWeek.FRIDAY}, "Should go off today at 5");
	}
}
