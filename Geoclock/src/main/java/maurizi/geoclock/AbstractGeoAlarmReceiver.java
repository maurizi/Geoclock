package maurizi.geoclock;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import com.google.android.gms.location.GeofenceStatusCodes;
import com.google.android.gms.location.GeofencingEvent;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;

import org.threeten.bp.LocalDateTime;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.format.FormatStyle;

import java.util.Set;

public abstract class AbstractGeoAlarmReceiver extends BroadcastReceiver {

	private static final String TAG = AbstractGeoAlarmReceiver.class.getSimpleName();
	protected Context context;
	protected Intent intent;
	protected interface SetOp<T> {
		Set<T> apply(Set<T> a, Set<T> b);
	}

	private static final int NOTIFICATION_ID = 42;

	@Override
	public void onReceive(Context context, Intent intent) {
		GeofencingEvent event = GeofencingEvent.fromIntent(intent);
		if (event.hasError()) {
			String errorMessage = GeofenceStatusCodes.getStatusCodeString(event.getErrorCode());
			Log.e(TAG, errorMessage);
			return;
		}

		// TODO: Do something with the event
	}

	protected void setNotification(final ImmutableSet<GeoAlarm> activeAlarms) {
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
		// TODO: Extract text
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
}