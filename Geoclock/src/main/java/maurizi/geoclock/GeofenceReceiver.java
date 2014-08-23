package maurizi.geoclock;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.LocationClient;
import com.google.common.collect.Lists;

import java.util.List;

import static maurizi.geoclock.GeoAlarm.getGeoAlarmForGeofenceFn;

public class GeofenceReceiver extends AbstractGeoReceiver {

	private static final int NOTIFICATION_ID = 42;

	@Override
	public void onConnected(Bundle bundle) {
		int transition = LocationClient.getGeofenceTransition(this.intent);
		List<Geofence> affectedGeofences = LocationClient.getTriggeringGeofences(intent);
		List<GeoAlarm> affectedAlarms = Lists.transform(affectedGeofences, getGeoAlarmForGeofenceFn(context));

		/* TODO: Need to keep track of which notifications are being shown currently
		 * When you leave a GeoFence, you may still have some alarms left due to overlapping geofences
		 * So we need to know if (current alarms - removedAlarms) is empty before removing the notifications
		 */
		final NotificationManager notificationManager =
				(NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		if ((transition == Geofence.GEOFENCE_TRANSITION_ENTER)) {
			setNotification(notificationManager);

			// TODO: Use Alarm Manager to set alarms, using GeoAlarm.getAlarmManagerTime
//			final AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
//			for (Geofence geofence : affectedGeofences) {
//				manager.setExact(AlarmManager.RTC_WAKEUP, 1, PendingIntent.get);
//			}

		} else if (transition == Geofence.GEOFENCE_TRANSITION_EXIT) {
			notificationManager.cancelAll();
			// TODO: Reset by iterating through geofences?? It's unclear
			// TODO: Remove AlarmMAnager alarms for geofences we are leaving
		}
	}

	private void setNotification(final NotificationManager manager) {
		// Create an content intent that comes with a back stack
		// This makes hitting back from the activity go to the home screen
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
		stackBuilder.addParentStack(MapActivity.class);
		stackBuilder.addNextIntent(new Intent(context, MapActivity.class));

		PendingIntent notificationPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

		// TODO: Fill in <ENTER TIME HERE>
		// TODO: Add a cancel button
		// TODO: Make clicking the notification open the GeoAlarmFragment
		Notification notification = new NotificationCompat
				.Builder(context)
				.setSmallIcon(R.drawable.ic_launcher)
				.setOngoing(true)
				.setContentTitle("Alarm")
				.setContentText("Next alarm goes off in <ENTER TIME HERE>")
				.setContentIntent(notificationPendingIntent)
				.build();

		// Issue the notification
		manager.notify(NOTIFICATION_ID, notification);
	}

	public static PendingIntent getPendingIntent(Context context) {
		Intent intent = new Intent(context, GeofenceReceiver.class);
		return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
	}
}
