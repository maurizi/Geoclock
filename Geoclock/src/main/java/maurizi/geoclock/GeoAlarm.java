package maurizi.geoclock;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.Nullable;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import org.threeten.bp.Duration;
import org.threeten.bp.LocalDate;
import org.threeten.bp.LocalDateTime;
import org.threeten.bp.LocalTime;
import org.threeten.bp.ZoneId;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;

import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Builder;
import lombok.experimental.Wither;

import static com.google.common.collect.Collections2.filter;
import static com.google.common.collect.Collections2.transform;
import static java.util.Collections.min;

@Value
@Builder
@Wither
public class GeoAlarm {

	private static final Gson gson = new Gson();
	private static final String ALARM_PREFS = "alarms";

	@NonNull public final String name;
	public final float radius;
	@NonNull public final LatLng location;

	@Nullable public final Integer hour;
	@Nullable public final Integer minute;
	@Nullable public final List<Integer> days;
	@Nullable public final String geofenceId;

	@Override
	public String toString() {
		return name;
	}

	public MarkerOptions getMarkerOptions() {
		return new MarkerOptions()
				.position(location)
				.title(name);
	}

	public Geofence getGeofence() {
		return new Geofence.Builder()
		                   .setCircularRegion(location.latitude, location.longitude, radius)
		                   .setRequestId(name)
		                   .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
		                   .setExpirationDuration(Duration.ofDays(1).toMillis())
		                   .build();
	}

	public CircleOptions getCircleOptions() {
		return new CircleOptions()
				.center(location)
				.radius(radius)
				.fillColor(R.color.geofence_fill_color);
	}

	private LocalTime getAlarmTime() {
		return LocalTime.of(hour, minute);
	}

	private boolean isAlarmForToday() {
		return getAlarmTime().isAfter(LocalTime.now());
	}

	/**
	 * @return A Date object for just before the alarm is due to go off
	 */
	public long getAlarmManagerTime() {
		final LocalTime alarmTime = getAlarmTime();

		final LocalDateTime alarmDateTime = days.isEmpty()
				? alarmTime.atDate(isAlarmForToday() ? LocalDate.now() : LocalDate.now().plusDays(1))
				: alarmTime.atDate(getSoonestDayForRepeatingAlarm());

		return alarmDateTime.atZone(ZoneId.systemDefault()).toEpochSecond();
	}

	private LocalDate getSoonestDayForRepeatingAlarm() {
		if (days.isEmpty()) {
			throw new AssertionError();
		}

		final int currentDayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);

		if (isAlarmForToday() && days.contains(currentDayOfWeek)) {
			return LocalDate.now();
		}

		final Collection<Integer> daysAfterToday = filter(days, weekday -> weekday > currentDayOfWeek);

		final Collection<Integer> daysTodayAndBefore = filter(days, weekday -> weekday <= currentDayOfWeek);
		final Collection<Integer> daysNextWeek = transform(daysTodayAndBefore, weekday -> weekday + 7);

		final Collection<Integer> allDays = new ArrayList<Integer>() {{
			addAll(daysAfterToday);
			addAll(daysNextWeek);
		}};

		final int daysFromNow = min(allDays) - currentDayOfWeek;

		return LocalDate.now().plusDays(daysFromNow);
	}

	static Collection<GeoAlarm> getGeoAlarms(Context context) {
		SharedPreferences prefs = getSharedAlarmPreferences(context);
		return ImmutableList.<GeoAlarm>builder().addAll(
				filter(transform(prefs.getAll().values(), GeoAlarm::parse),
						(GeoAlarm geoAlarm) -> geoAlarm != null)
		).build();
	}

	static Function<Geofence, GeoAlarm> getGeoAlarmForGeofenceFn(Context context) {
		SharedPreferences prefs = getSharedAlarmPreferences(context);
		return geofence -> parse(prefs.getString(geofence.getRequestId(), null));
	}

	static void replace(Context context, GeoAlarm oldAlarm, GeoAlarm newAlarm) {
		SharedPreferences prefs = getSharedAlarmPreferences(context);
		prefs.edit()
		     .remove(oldAlarm.geofenceId)
		     .putString(newAlarm.geofenceId, gson.toJson(newAlarm, GeoAlarm.class))
		     .commit();
	}

	static void remove(Context context, GeoAlarm oldAlarm) {
		SharedPreferences prefs = getSharedAlarmPreferences(context);
		prefs.edit()
		     .remove(oldAlarm.geofenceId)
		     .commit();
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