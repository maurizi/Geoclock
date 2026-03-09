package maurizi.geoclock.ui;

import android.app.KeyguardManager;
import android.os.Build;
import android.os.Bundle;
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
import maurizi.geoclock.background.AlarmRingingService;

public class AlarmRingingActivity extends AppCompatActivity {

	public static final String EXTRA_ALARM_ID = "alarm_id";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Show over the lock screen and dismiss keyguard so the activity gets full focus
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
			setShowWhenLocked(true);
			setTurnScreenOn(true);
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
			if (km != null) {
				km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {});
			}
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

		if (alarm != null && alarm.place != null) {
			nameView.setText(alarm.place);
		}
		timeView.setText(LocalTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)));

		final GeoAlarm finalAlarm = alarm;
		dismissButton.setOnClickListener(v -> {
			AlarmRingingService.stop(this);
			finish();
		});

		snoozeButton.setOnClickListener(v -> {
			AlarmRingingService.stop(this);
			if (finalAlarm != null) {
				AlarmRingingService.scheduleSnooze(this, finalAlarm);
			}
			finish();
		});
	}
}
