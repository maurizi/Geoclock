package maurizi.geoclock;

import android.support.annotation.Nullable;

import com.google.common.collect.ImmutableList;

import org.threeten.bp.DayOfWeek;
import org.threeten.bp.LocalDate;
import org.threeten.bp.LocalDateTime;
import org.threeten.bp.LocalTime;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZonedDateTime;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Builder;
import lombok.experimental.Wither;

import static com.google.common.collect.Collections2.filter;
import static org.threeten.bp.temporal.TemporalAdjusters.next;

@Value
@Builder
@Wither
public class Alarm {
	public final boolean enabled;
	@NonNull public final UUID parent;
	@NonNull public final UUID id;
	@NonNull public final Integer hour;
	@NonNull public final Integer minute;
	@NonNull public final Set<DayOfWeek> days;

	@Nullable public final Long time;

	private LocalTime getAlarmTime() {
		if (hour == null || minute == null) {
			return null;
		}
		return LocalTime.of(hour, minute);
	}

	private boolean isAlarmForToday(LocalDateTime now) {
		LocalTime time = getAlarmTime();
		return now != null && time != null && time.isAfter(now.toLocalTime());
	}

	public Alarm withCalculatedTime() {
		final ZonedDateTime alarmTime = calculateAlarmTime(LocalDateTime.now());
		return withTime(alarmTime.toInstant().toEpochMilli());
	}
	/**
	 * @return A Date object for just before the alarm is due to go off
	 */
	public ZonedDateTime calculateAlarmTime(LocalDateTime now) {
		final LocalTime alarmTime = getAlarmTime();

		if (now == null || alarmTime == null) {
			return null;
		}

		final LocalDateTime alarmDateTime = isNonRepeating()
		                                    ? alarmTime.atDate(isAlarmForToday(now)
		                                                       ? now.toLocalDate()
		                                                       : now.toLocalDate().plusDays(1))
		                                    : alarmTime.atDate(getSoonestDayForRepeatingAlarm(now));

		return alarmDateTime.atZone(ZoneId.systemDefault());
	}

	public boolean isNonRepeating() {
		return days == null || days.isEmpty();
	}

	private LocalDate getSoonestDayForRepeatingAlarm(LocalDateTime now) {
		assert days != null;
		if (isNonRepeating()) {
			throw new AssertionError();
		}

		final DayOfWeek currentDayOfWeek = now.getDayOfWeek();

		if (isAlarmForToday(now) && days.contains(currentDayOfWeek)) {
			return now.toLocalDate();
		}

		final Collection<DayOfWeek> daysAfterToday = filter(days, weekday -> weekday.getValue() > currentDayOfWeek.getValue());

		final Collection<DayOfWeek> daysTodayAndBefore = filter(days, weekday -> weekday.getValue() <= currentDayOfWeek.getValue());

		final ImmutableList<DayOfWeek> allDays = ImmutableList.<DayOfWeek>builder()
		                                                      .addAll(daysAfterToday)
		                                                      .addAll(daysTodayAndBefore).build();

		final DayOfWeek nextDayForAlarm = allDays.get(0);
		return now.toLocalDate().with(next(nextDayForAlarm));
	}

}
