package maurizi.geoclock;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.LocationClient;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.Gson;

import java.util.List;
import java.util.Set;

import static maurizi.geoclock.GeoAlarm.getGeoAlarmForGeofenceFn;

public class GeofenceReceiver extends AbstractGeoAlarmReceiver {
	private static final Gson gson = new Gson();
	private static final String ACTIVE_ALARM_PREFS = "active_alarm_prefs";

	@Override
	public void onConnected(Bundle bundle) {
		final int transition = LocationClient.getGeofenceTransition(this.intent);

		final List<Geofence> affectedGeofences = LocationClient.getTriggeringGeofences(intent);
		final ImmutableSet<GeoAlarm> affectedAlarms = ImmutableSet.copyOf(Lists.transform(affectedGeofences,
		                                                                                  getGeoAlarmForGeofenceFn(context)));

		if ((transition == Geofence.GEOFENCE_TRANSITION_ENTER)) {
			ImmutableSet<GeoAlarm> currentAlarms = changeActiveAlarms(affectedAlarms, Sets::union);

			// TODO: Use Alarm Manager to set alarms, using GeoAlarm.getAlarmManagerTime
//			final AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
//			for (Geofence geofence : affectedGeofences) {
//				manager.setExact(AlarmManager.RTC_WAKEUP, 1, PendingIntent.get);
//			}

		} else if (transition == Geofence.GEOFENCE_TRANSITION_EXIT) {
			ImmutableSet<GeoAlarm> currentAlarms = changeActiveAlarms(affectedAlarms, Sets::difference);
			// TODO: Reset by iterating through geofences?? It's unclear
			// TODO: Remove AlarmMAnager alarms for geofences we are leaving
		}
	}

	public static PendingIntent getPendingIntent(Context context) {
		Intent intent = new Intent(context, GeofenceReceiver.class);
		return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
	}

	protected ImmutableSet<GeoAlarm> changeActiveAlarms(ImmutableSet<GeoAlarm> triggerAlarms, SetOp<GeoAlarm> op) {
		final SharedPreferences activeAlarmsPrefs = context.getSharedPreferences(ACTIVE_ALARM_PREFS, Context.MODE_PRIVATE);

		final String savedActiveAlarmsJson = activeAlarmsPrefs.getString(ACTIVE_ALARM_PREFS, gson.toJson(new GeoAlarm[] {}));
		final Set<GeoAlarm> savedAlarms = ImmutableSet.copyOf(gson.fromJson(savedActiveAlarmsJson, GeoAlarm[].class));

		final ImmutableSet<GeoAlarm> currentAlarms = ImmutableSet.copyOf(op.apply(savedAlarms, triggerAlarms));
		activeAlarmsPrefs.edit().putString(ACTIVE_ALARM_PREFS, gson.toJson(currentAlarms.toArray())).apply();
		setNotification(currentAlarms);

		return currentAlarms;
	}
}
