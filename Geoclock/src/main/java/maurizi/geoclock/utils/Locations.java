package maurizi.geoclock.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.support.annotation.Nullable;

import com.google.android.gms.location.Geofence;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.util.Collection;
import java.util.UUID;

import maurizi.geoclock.Location;

import static com.google.common.collect.Collections2.filter;
import static com.google.common.collect.Collections2.transform;

public class Locations {
	private static final Gson gson = new Gson();
	private static final String LOCATION_PREFS = "locations";

	@Nullable
	public static Location get(Context context, UUID id) {
		SharedPreferences prefs = getLocationPreferences(context);
		String json = prefs.getString(id.toString(), null);
		if (json != null) {
			return parse(json);
		}
		return null;
	}

	public static Collection<Location> get(Context context) {
		SharedPreferences prefs = getLocationPreferences(context);
		Collection<?> json = prefs.getAll().values();
		return ImmutableList.<Location>builder()
		                    .addAll(filter(transform(json, Locations::parse), (Location location) -> location != null))
		                    .build();
	}

	public static Function<Geofence, Location> fromGeofence(Context context) {
		final SharedPreferences prefs = getLocationPreferences(context);
		return geofence -> parse(prefs.getString(geofence.getRequestId(), null));
	}

	public static void save(Context context, Location newAlarm) {
//		newAlarm = newAlarm.withAlarms(ImmutableList.copyOf(transform(newAlarm.alarms, (Alarm alarm) -> {
//			if (alarm.enabled) {
//				final ZonedDateTime alarmTime = alarm.calculateAlarmTime(LocalDateTime.now());
//
//				// We will check the time in a boot receiver so that we know if we missed any alarms
//				return alarm.withTime(alarmTime.toInstant().toEpochMilli());
//			}
//			return alarm;
//		})));
		SharedPreferences prefs = getLocationPreferences(context);
		Editor editor = prefs.edit();
		editor.putString(newAlarm.id.toString(), gson.toJson(newAlarm, Location.class)).commit();
	}

	public static void remove(Context context, Location oldAlarm) {
		SharedPreferences prefs = getLocationPreferences(context);
		prefs.edit().remove(oldAlarm.id.toString()).commit();
	}

	private static Location parse(Object json) {
		try {
			return gson.fromJson((String) json, Location.class);
		} catch (JsonSyntaxException e) {
			return null;
		}
	}

	private static SharedPreferences getLocationPreferences(Context context) {
		return context.getSharedPreferences(LOCATION_PREFS, Context.MODE_PRIVATE);
	}
}
