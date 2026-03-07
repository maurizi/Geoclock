package maurizi.geoclock.ui;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.UUID;

import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.R;
import maurizi.geoclock.background.AlarmClockReceiver;

public class AlarmRingingActivity extends AppCompatActivity {

    public static final String EXTRA_ALARM_ID = "alarm_id";
    private static final int SNOOZE_REQUEST_CODE = 9001;
    private static final long SNOOZE_DURATION_MS = 5 * 60 * 1000L;

    private Ringtone ringtone;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Show over the lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }

        setContentView(R.layout.activity_alarm_ringing);

        GeoAlarm alarm = null;
        String alarmId = getIntent().getStringExtra(EXTRA_ALARM_ID);
        if (alarmId != null) {
            alarm = GeoAlarm.getGeoAlarm(this, UUID.fromString(alarmId));
        }

        TextView nameView = findViewById(R.id.alarm_ringing_name);
        TextView timeView = findViewById(R.id.alarm_ringing_time);
        Button dismissButton = findViewById(R.id.alarm_ringing_dismiss);
        Button snoozeButton = findViewById(R.id.alarm_ringing_snooze);

        if (alarm != null) {
            nameView.setText(alarm.name);
        }
        timeView.setText(LocalTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)));

        startAlarm();

        final GeoAlarm finalAlarm = alarm;
        dismissButton.setOnClickListener(v -> {
            stopAlarm();
            finish();
        });

        snoozeButton.setOnClickListener(v -> {
            stopAlarm();
            if (finalAlarm != null) {
                scheduleSnooze(finalAlarm);
            }
            finish();
        });
    }

    private void startAlarm() {
        // Play ringtone
        Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarmUri == null) {
            alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
        ringtone = RingtoneManager.getRingtone(this, alarmUri);
        if (ringtone != null) {
            ringtone.play();
        }

        // Vibrate
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

    private void stopAlarm() {
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
    }

    private void scheduleSnooze(GeoAlarm alarm) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        long snoozeTime = System.currentTimeMillis() + SNOOZE_DURATION_MS;

        Intent intent = new Intent(this, AlarmRingingActivity.class);
        intent.putExtra(EXTRA_ALARM_ID, alarm.id.toString());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, SNOOZE_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (alarmManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeTime, pi);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeTime, pi);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, snoozeTime, pi);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAlarm();
    }
}
