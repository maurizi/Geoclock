package maurizi.geoclock.background;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.UUID;

import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.R;
import maurizi.geoclock.ui.MapActivity;

public class NotificationReceiver extends BroadcastReceiver {
    private static final String ALARM_ID = "alarm_id";
    public static final int NOTIFICATION_ID = 42;
    private static final String CHANNEL_ID = "geoclock_upcoming";

    private NotificationManager notificationManager;
    private Context context;

    public static PendingIntent getPendingIntent(Context context, GeoAlarm alarm) {
        Intent intent = getIntent(context, alarm);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static PendingIntent getPendingIntent(Context context) {
        Intent intent = new Intent(context, NotificationReceiver.class);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @NonNull
    public static Intent getIntent(final Context context, final GeoAlarm alarm) {
        Intent intent = new Intent(context, NotificationReceiver.class);
        intent.putExtra(ALARM_ID, alarm.id.toString());
        return intent;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        this.context = context;
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
        if (intent.hasExtra(ALARM_ID)) {
            GeoAlarm alarm = GeoAlarm.getGeoAlarm(context, UUID.fromString(intent.getStringExtra(ALARM_ID)));
            if (alarm != null) {
                setNotification(alarm);
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Upcoming Alarms",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Notifications for upcoming Geoclock alarms");
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void setNotification(@NonNull final GeoAlarm nextAlarm) {
        final ZonedDateTime alarmTime = nextAlarm.calculateAlarmTime(LocalDateTime.now());
        final String alarmFormattedTime = alarmTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT));
        final String title = String.format(context.getString(R.string.alarm_notification_text), alarmFormattedTime);

        Intent showAlarmIntent = MapActivity.getIntent(context, nextAlarm);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addParentStack(MapActivity.class);
        stackBuilder.addNextIntent(showAlarmIntent);

        PendingIntent notificationPendingIntent = stackBuilder.getPendingIntent(0,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent cancelIntent = new Intent(context, AlarmClockReceiver.class);
        cancelIntent.setAction(AlarmClockReceiver.ACTION_CANCEL_UPCOMING);
        cancelIntent.putExtra(ALARM_ID, nextAlarm.id.toString());
        PendingIntent cancelPi = PendingIntent.getBroadcast(context, nextAlarm.id.hashCode(),
                cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Bitmap icon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_launcher);
        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alarm_black_24dp)
                .setLargeIcon(icon)
                .setContentTitle(title)
                .setContentText(nextAlarm.toString())
                .setContentIntent(notificationPendingIntent)
                .addAction(0, "Cancel alarm", cancelPi)
                .build();

        notificationManager.cancel(NOTIFICATION_ID);
        notificationManager.notify(NOTIFICATION_ID, notification);
    }
}
