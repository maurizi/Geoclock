package maurizi.geoclock.background;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.AlarmClock;
import android.util.Log;

import com.google.common.collect.ImmutableSet;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.UUID;

import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.services.ActiveAlarmManager;

public class AlarmManagerReceiver extends BroadcastReceiver {
	private static final String ALARM_ID = "alarm_id";
	private static final String TAG = GeofenceReceiver.class.getSimpleName();

	public static PendingIntent getPendingIntent(Context context, GeoAlarm alarm) {
		Intent intent = new Intent(context, AlarmManagerReceiver.class);
		intent.putExtra(ALARM_ID, alarm.id.toString());
		return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
	}

	public static PendingIntent getPendingIntent(Context context) {
		Intent intent = new Intent(context, AlarmManagerReceiver.class);
		return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
	}

	@Override
	public void onReceive(final Context context, final Intent intent) {
		if (intent.hasExtra(ALARM_ID)) {
			GeoAlarm alarm = GeoAlarm.getGeoAlarm(context, UUID.fromString(intent.getStringExtra(ALARM_ID)));
			if (alarm != null && alarm.enabled) {
				ActiveAlarmManager activeAlarmManager = new ActiveAlarmManager(context);

				Intent alarmClockIntent = new Intent(AlarmClock.ACTION_SET_ALARM);
				Calendar cal = new GregorianCalendar();
				alarmClockIntent.putExtra(AlarmClock.EXTRA_HOUR, cal.get(Calendar.HOUR_OF_DAY));
				alarmClockIntent.putExtra(AlarmClock.EXTRA_MINUTES, cal.get(Calendar.MINUTE) + 1);
				alarmClockIntent.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
				alarmClockIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				if (intent.resolveActivity(context.getPackageManager()) != null) {
					context.startActivity(alarmClockIntent);
				} else {
					// TODO: I have no idea how to handle this
					Log.e(TAG, "There is no alarm clock app");
				}

				if (alarm.days == null || alarm.days.isEmpty())  {
					GeoAlarm.remove(context, alarm);
					GeoAlarm.save(context, alarm.withEnabled(false));
					activeAlarmManager.removeActiveAlarms(ImmutableSet.of(alarm));
				} else {
					activeAlarmManager.resetActiveAlarms();
				}
			}
		}
	}
}
