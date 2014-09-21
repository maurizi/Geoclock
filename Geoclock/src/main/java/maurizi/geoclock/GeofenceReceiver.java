package maurizi.geoclock;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.LocationClient;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.gson.Gson;

import org.threeten.bp.LocalDateTime;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.format.FormatStyle;

import java.util.List;
import java.util.Set;

import static com.google.common.collect.Collections2.transform;
import static maurizi.geoclock.GeoAlarm.getGeoAlarmForGeofenceFn;

public class GeofenceReceiver extends AbstractGeoReceiver {
	private static final Gson gson = new Gson();

	private static final int NOTIFICATION_ID = 42;

	private static final String ACTIVE_ALARM_PREFS = "active_alarm_prefs";

	private interface SetOp<T> {
		Set<T> apply(Set<T> a, Set<T> b);
	}

	@Override
	public void onConnected(Bundle bundle) {
		final int transition = LocationClient.getGeofenceTransition(this.intent);

		final List<Geofence> affectedGeofences = LocationClient.getTriggeringGeofences(intent);
		final ImmutableSet<GeoAlarm> affectedAlarms = ImmutableSet.copyOf(Lists.transform(affectedGeofences,
		                                                                                  getGeoAlarmForGeofenceFn(context)));

		/* TODO: Need to keep track of which notifications are being shown currently
		 * When you leave a GeoFence, you may still have some alarms left due to overlapping geofences
		 * So we need to know if (current alarms - removedAlarms) is empty before removing the notifications
		 */
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

	private void setNotification(final ImmutableSet<GeoAlarm> activeAlarms) {
		final NotificationManager notificationManager =
				(NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		if (activeAlarms.isEmpty()) {
			notificationManager.cancelAll();
			return;
		}

		final LocalDateTime now = LocalDateTime.now();
		final GeoAlarm nextAlarm = Ordering.from(ZonedDateTime.timeLineOrder())
		                                   .onResultOf((GeoAlarm alarm) -> alarm.getAlarmManagerTime(now))
		                                   .min(activeAlarms);
		final ZonedDateTime nextAlarmTime = nextAlarm.getAlarmManagerTime(now);
		final String alarmFormattedTime = nextAlarmTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT));

		// Create an content intent that comes with a back stack
		// This makes hitting back from the activity go to the home screen
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
		stackBuilder.addParentStack(MapActivity.class);
		stackBuilder.addNextIntent(new Intent(context, MapActivity.class));

		PendingIntent notificationPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

		// TODO: Add a cancel button
		// TODO: Make clicking the notification open the GeoAlarmFragment
		Notification notification = new NotificationCompat
				.Builder(context)
				.setSmallIcon(R.drawable.ic_launcher)
				.setOngoing(true)
				.setContentTitle("Geo Alarm")
				.setContentText(String.format("Alarm at %s for %s", alarmFormattedTime, nextAlarm.name))
				.setContentIntent(notificationPendingIntent)
				.build();

		// Issue the notification
		notificationManager.notify(NOTIFICATION_ID, notification);
	}

	public static PendingIntent getPendingIntent(Context context) {
		Intent intent = new Intent(context, GeofenceReceiver.class);
		return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
	}

	private ImmutableSet<GeoAlarm> changeActiveAlarms(ImmutableSet<GeoAlarm> triggerAlarms, SetOp<GeoAlarm> op) {
		final SharedPreferences activeAlarmsPrefs = context.getSharedPreferences(ACTIVE_ALARM_PREFS, Context.MODE_PRIVATE);

		final String savedActiveAlarmsJson = activeAlarmsPrefs.getString(ACTIVE_ALARM_PREFS, gson.toJson(new GeoAlarm[] {}));
		final Set<GeoAlarm> savedAlarms = ImmutableSet.copyOf(gson.fromJson(savedActiveAlarmsJson, GeoAlarm[].class));

		final ImmutableSet<GeoAlarm> currentAlarms = ImmutableSet.copyOf(op.apply(savedAlarms, triggerAlarms));
		activeAlarmsPrefs.edit().putString(ACTIVE_ALARM_PREFS, gson.toJson(currentAlarms.toArray())).apply();
		setNotification(currentAlarms);

		return currentAlarms;
	}
}
