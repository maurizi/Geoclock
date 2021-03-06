package maurizi.geoclock;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.support.annotation.Nullable;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import org.threeten.bp.DayOfWeek;
import org.threeten.bp.Duration;
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
import static com.google.common.collect.Collections2.transform;
import static org.threeten.bp.temporal.TemporalAdjusters.next;

@Value
@Builder
@Wither
public class GeoAlarm {

	private static final Gson gson = new Gson();
	private static final String ALARM_PREFS = "alarms";

	@NonNull public final UUID id;
	@NonNull public final String name;
	public final int radius;
	@NonNull public final LatLng location;
	public final boolean enabled;

	@Nullable public final Long time;
	@Nullable public final Integer hour;
	@Nullable public final Integer minute;
	@Nullable public final Set<DayOfWeek> days;

	@Override
	public String toString() {
		return name;
	}

	public MarkerOptions getMarkerOptions() {
		return new MarkerOptions().position(location).title(name);
	}

	public Geofence getGeofence() {
		return new Geofence.Builder().setCircularRegion(location.latitude, location.longitude, radius)
		                             .setRequestId(name)
		                             .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER |
		                                                 Geofence.GEOFENCE_TRANSITION_EXIT)
		                             .setExpirationDuration(Duration.ofDays(1).toMillis())
		                             .build();
	}

	public CircleOptions getCircleOptions() {
		return new CircleOptions().center(location).radius(radius).fillColor(R.color.geofence_fill_color);
	}

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

		final Collection<DayOfWeek> daysTodayAndBefore = filter(
				days, weekday -> weekday.getValue() <= currentDayOfWeek.getValue());

		final ImmutableList<DayOfWeek> allDays = ImmutableList.<DayOfWeek>builder()
		                                                      .addAll(daysAfterToday)
		                                                      .addAll(daysTodayAndBefore).build();

		final DayOfWeek nextDayForAlarm = allDays.get(0);
		return now.toLocalDate().with(next(nextDayForAlarm));
	}

	@Nullable
	public static GeoAlarm getGeoAlarm(Context context, UUID id) {
		SharedPreferences prefs = getSharedAlarmPreferences(context);
		String json = prefs.getString(id.toString(), null);
		if (json != null) {
			return parse(json);
		}
		return null;
	}

	public static Collection<GeoAlarm> getGeoAlarms(Context context) {
		SharedPreferences prefs = getSharedAlarmPreferences(context);
		Collection<?> json = prefs.getAll().values();
		return ImmutableList.<GeoAlarm>builder()
		                    .addAll(filter(transform(json, GeoAlarm::parse), (GeoAlarm geoAlarm) -> geoAlarm != null))
		                    .build();
	}

	public static Function<Geofence, GeoAlarm> getGeoAlarmForGeofenceFn(Context context) {
		final SharedPreferences prefs = getSharedAlarmPreferences(context);
		return geofence -> parse(prefs.getString(geofence.getRequestId(), null));
	}

	public static void save(Context context, GeoAlarm newAlarm) {
		if (newAlarm.enabled) {
			final ZonedDateTime alarmTime = newAlarm.calculateAlarmTime(LocalDateTime.now());

			// We will check the time in a boot receiver so that we know if we missed any alarms
			newAlarm = newAlarm.withTime(alarmTime.toInstant().toEpochMilli());
		}
		SharedPreferences prefs = getSharedAlarmPreferences(context);
		Editor editor = prefs.edit();
		editor.putString(newAlarm.id.toString(), gson.toJson(newAlarm, GeoAlarm.class))
		      .commit();
	}

	public static void remove(Context context, GeoAlarm oldAlarm) {
		SharedPreferences prefs = getSharedAlarmPreferences(context);
		prefs.edit().remove(oldAlarm.id.toString()).commit();
	}

	private static GeoAlarm parse(Object json) {
		try {
			return gson.fromJson((String) json, GeoAlarm.class);
		} catch (JsonSyntaxException e) {
			return null;
		}
	}

	private static SharedPreferences getSharedAlarmPreferences(Context context) {
		return context.getSharedPreferences(ALARM_PREFS, Context.MODE_PRIVATE);
	}
}