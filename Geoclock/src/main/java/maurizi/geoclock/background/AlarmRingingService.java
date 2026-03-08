package maurizi.geoclock.background;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import androidx.core.app.NotificationCompat;

import java.util.UUID;

import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.R;
import maurizi.geoclock.ui.AlarmRingingActivity;

/**
 * Foreground service that plays alarm audio/vibration independently of the UI.
 * This ensures sounds fire even when the full-screen intent is shown as a banner
 * (screen on) rather than auto-launching the activity.
 */
public class AlarmRingingService extends Service {

    public static final String EXTRA_ALARM_ID = "alarm_id";
    public static final String ACTION_DISMISS = "action_dismiss";
    public static final String ACTION_SNOOZE = "action_snooze";
    private static final String ALARM_RINGING_CHANNEL = "alarm_ringing";
    static long SNOOZE_DURATION_MS = 5 * 60 * 1000L;
    private static final int SNOOZE_REQUEST_CODE = 9001;

    private Ringtone ringtone;
    private Vibrator vibrator;

    public static void start(Context context, String alarmId) {
        Intent intent = new Intent(context, AlarmRingingService.class);
        intent.putExtra(EXTRA_ALARM_ID, alarmId);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, AlarmRingingService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String alarmId = intent != null ? intent.getStringExtra(EXTRA_ALARM_ID) : null;
        GeoAlarm alarm = null;
        if (alarmId != null) {
            alarm = GeoAlarm.getGeoAlarm(this, UUID.fromString(alarmId));
        }

        String action = intent != null ? intent.getAction() : null;
        if (ACTION_DISMISS.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_SNOOZE.equals(action)) {
            if (alarm != null) {
                scheduleSnooze(this, alarm);
            }
            stopSelf();
            return START_NOT_STICKY;
        }

        ensureNotificationChannel();
        Notification notification = buildNotification(alarm);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(AlarmClockReceiver.RINGING_NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(AlarmClockReceiver.RINGING_NOTIFICATION_ID, notification);
        }

        startAlarm();
        return START_STICKY;
    }

    public static void scheduleSnooze(Context context, GeoAlarm alarm) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        long snoozeTime = System.currentTimeMillis() + SNOOZE_DURATION_MS;

        Intent alarmIntent = new Intent(context, AlarmClockReceiver.class);
        alarmIntent.putExtra(EXTRA_ALARM_ID, alarm.id.toString());
        alarmIntent.putExtra(AlarmClockReceiver.EXTRA_IS_SNOOZE, true);
        PendingIntent operationPi = PendingIntent.getBroadcast(context, SNOOZE_REQUEST_CODE, alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent showPi = PendingIntent.getActivity(context, 0,
                new Intent(context, AlarmRingingActivity.class),
                PendingIntent.FLAG_IMMUTABLE);

        alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(snoozeTime, showPi), operationPi);
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    ALARM_RINGING_CHANNEL, "Alarm ringing", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Full-screen alarm");
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(GeoAlarm alarm) {
        Intent ringIntent = new Intent(this, AlarmRingingActivity.class);
        if (alarm != null) {
            ringIntent.putExtra(AlarmRingingActivity.EXTRA_ALARM_ID, alarm.id.toString());
        }
        ringIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);

        PendingIntent fullScreenPi = PendingIntent.getActivity(
                this, alarm != null ? alarm.id.hashCode() : 0, ringIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Snooze action
        Intent snoozeIntent = new Intent(this, AlarmRingingService.class);
        snoozeIntent.setAction(ACTION_SNOOZE);
        if (alarm != null) snoozeIntent.putExtra(EXTRA_ALARM_ID, alarm.id.toString());
        PendingIntent snoozePi = PendingIntent.getService(this, 1, snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Dismiss action
        Intent dismissIntent = new Intent(this, AlarmRingingService.class);
        dismissIntent.setAction(ACTION_DISMISS);
        PendingIntent dismissPi = PendingIntent.getService(this, 2, dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, ALARM_RINGING_CHANNEL)
                .setSmallIcon(R.drawable.ic_alarm_black_24dp)
                .setContentTitle("Alarm")
                .setContentText(alarm != null ? alarm.toString() : "")
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setFullScreenIntent(fullScreenPi, true)
                .setOngoing(true)
                .setAutoCancel(false)
                .addAction(0, "Snooze", snoozePi)
                .addAction(0, "Dismiss", dismissPi)
                .build();
    }

    private void startAlarm() {
        // Stop any previous playback (handles re-delivery via START_STICKY)
        if (ringtone != null && ringtone.isPlaying()) ringtone.stop();
        if (vibrator != null) vibrator.cancel();

        Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarmUri == null) {
            alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
        ringtone = RingtoneManager.getRingtone(this, alarmUri);
        if (ringtone != null) ringtone.play();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = vm.getDefaultVibrator();
        } else {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }
        if (vibrator != null) {
            long[] pattern = {0, 500, 1000};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (ringtone != null && ringtone.isPlaying()) ringtone.stop();
        if (vibrator != null) vibrator.cancel();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
