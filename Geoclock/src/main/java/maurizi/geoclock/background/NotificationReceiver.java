package maurizi.geoclock.background;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import android.support.v4.app.TaskStackBuilder;
import android.support.v7.app.NotificationCompat;

import org.threeten.bp.LocalDateTime;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.format.FormatStyle;

import java.util.UUID;

import maurizi.geoclock.Alarm;
import maurizi.geoclock.utils.Alarms;
import maurizi.geoclock.Location;
import maurizi.geoclock.utils.Locations;
import maurizi.geoclock.R;
import maurizi.geoclock.ui.MapActivity;

public class NotificationReceiver extends BroadcastReceiver {
	private static final String ALARM_ID = "alarm_id";
	private static final int NOTIFICATION_ID = 42;

	private NotificationManager notificationManager;
	private Context context;

	public static PendingIntent getPendingIntent(Context context, Alarm alarm) {
		Intent intent = getIntent(context, alarm);
		return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
	}

	public static PendingIntent getPendingIntent(Context context) {
		Intent intent = new Intent(context, NotificationReceiver.class);
		return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
	}

	@NonNull
	public static Intent getIntent(final Context context, final Alarm alarm) {
		Intent intent = new Intent(context, NotificationReceiver.class);
		intent.putExtra(ALARM_ID, alarm.id.toString());
		return intent;
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		this.context = context;
		this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		if (intent.hasExtra(ALARM_ID)) {
			Alarm alarm = Alarms.get(context, UUID.fromString(intent.getStringExtra(ALARM_ID)));
			if (alarm != null) {
				setNotification(alarm);
			}
		}
	}

	private void setNotification(@NonNull final Alarm nextAlarm) {
		final Location location = Locations.get(context, nextAlarm.parent);
		final ZonedDateTime alarmTime = nextAlarm.calculateAlarmTime(LocalDateTime.now());
		final String alarmFormattedTime = alarmTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT));
		final String title = String.format(context.getString(R.string.alarm_notification_text), alarmFormattedTime);

		Intent showAlarmIntent = MapActivity.getIntent(context, location);

		// Create an content intent that comes with a back stack
		// This makes hitting back from the activity go to the home screen
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
		stackBuilder.addParentStack(MapActivity.class);
		stackBuilder.addNextIntent(showAlarmIntent);

		PendingIntent notificationPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

		// TODO: Add a dismiss button
		Bitmap icon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_launcher);
		Notification notification = new NotificationCompat.Builder(context)
		                                                  .setSmallIcon(R.drawable.ic_alarm_black_24dp)
		                                                  .setLargeIcon(icon)
		                                                  .setContentTitle(title)
		                                                  .setContentText(location.name)
		                                                  .setContentIntent(notificationPendingIntent)
		                                                  .build();

		// Issue the notification
		notificationManager.cancelAll();
		notificationManager.notify(NOTIFICATION_ID, notification);
	}
}
