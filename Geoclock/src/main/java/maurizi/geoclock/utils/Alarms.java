package maurizi.geoclock.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.util.Collection;
import java.util.UUID;

import maurizi.geoclock.Alarm;

import static com.google.common.collect.Collections2.filter;
import static com.google.common.collect.Collections2.transform;

public class Alarms {
	private static final Gson gson = new Gson();
	private static final String ALARM_PREFS = "alarms";

	@Nullable
	public static Alarm get(Context context, UUID id) {
		SharedPreferences prefs = getLocationPreferences(context);
		String json = prefs.getString(id.toString(), null);
		if (json != null) {
			return parse(json);
		}
		return null;
	}

	public static Collection<Alarm> get(Context context) {
		SharedPreferences prefs = getLocationPreferences(context);
		Collection<?> json = prefs.getAll().values();
		return ImmutableList.<Alarm>builder()
		                    .addAll(filter(transform(json, Alarms::parse), (Alarm alarm) -> alarm != null))
		                    .build();
	}

	public static void save(Context context, Alarm alarm) {
		if (alarm.enabled) {
			// We will check the time in a boot receiver so that we know if we missed any alarms
			alarm =  alarm.withCalculatedTime();
		}

		SharedPreferences prefs = getLocationPreferences(context);
		Editor editor = prefs.edit();
		editor.putString(alarm.id.toString(), gson.toJson(alarm, Alarm.class)).commit();
	}

	public static void remove(Context context, @NonNull Alarm oldAlarm) {
		getLocationPreferences(context).edit().remove(oldAlarm.id.toString()).commit();
	}

	public static void remove(Context context, @NonNull Collection<Alarm> oldAlarms) {
		SharedPreferences prefs = getLocationPreferences(context);
		SharedPreferences.Editor editor = prefs.edit();
		for (Alarm alarm : oldAlarms) {
			editor.remove(alarm.id.toString());
		}
		editor.commit();
	}

	private static Alarm parse(Object json) {
		try {
			return gson.fromJson((String) json, Alarm.class);
		} catch (JsonSyntaxException e) {
			return null;
		}
	}

	private static SharedPreferences getLocationPreferences(Context context) {
		return context.getSharedPreferences(ALARM_PREFS, Context.MODE_PRIVATE);
	}
}
