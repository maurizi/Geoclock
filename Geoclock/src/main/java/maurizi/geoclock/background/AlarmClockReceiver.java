package maurizi.geoclock.background;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.google.common.collect.ImmutableSet;
import java.util.UUID;
import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.utils.ActiveAlarmManager;

public class AlarmClockReceiver extends BroadcastReceiver {
  private static final String ALARM_ID = "alarm_id";
  public static final int RINGING_NOTIFICATION_ID = 1001;
  public static final String ACTION_CANCEL_UPCOMING = "action_cancel_upcoming";
  public static final String EXTRA_IS_SNOOZE = "is_snooze";

  public static PendingIntent getPendingIntent(Context context, GeoAlarm alarm) {
    Intent intent = new Intent(context, AlarmClockReceiver.class);
    intent.putExtra(ALARM_ID, alarm.id.toString());
    // Request code 0 is intentional — ActiveAlarmManager only schedules one alarm
    // at a time (the soonest), so each new schedule overwrites the previous one.
    return PendingIntent.getBroadcast(
        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  public static PendingIntent getPendingIntent(Context context) {
    Intent intent = new Intent(context, AlarmClockReceiver.class);
    return PendingIntent.getBroadcast(
        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  @Override
  public void onReceive(final Context context, final Intent intent) {
    if (ACTION_CANCEL_UPCOMING.equals(intent.getAction())) {
      String alarmId = intent.getStringExtra(ALARM_ID);
      if (alarmId != null) {
        GeoAlarm alarm = GeoAlarm.getGeoAlarm(context, UUID.fromString(alarmId));
        if (alarm != null) {
          GeoAlarm.save(context, alarm.withEnabled(false));
          new ActiveAlarmManager(context).removeActiveAlarms(ImmutableSet.of(alarm.id));
        }
      }
      NotificationManager nm =
          (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
      if (nm != null) nm.cancel(NotificationReceiver.NOTIFICATION_ID);
      return;
    }
    if (intent.hasExtra(ALARM_ID)) {
      boolean isSnooze = intent.getBooleanExtra(EXTRA_IS_SNOOZE, false);
      GeoAlarm alarm =
          GeoAlarm.getGeoAlarm(context, UUID.fromString(intent.getStringExtra(ALARM_ID)));
      if (alarm != null && (alarm.enabled || isSnooze)) {
        ActiveAlarmManager activeAlarmManager = new ActiveAlarmManager(context);

        // Start the foreground service — it plays audio immediately and posts the
        // full-screen intent notification. Sounds fire even if the full-screen
        // intent shows as a banner (screen on) rather than auto-launching.
        AlarmRingingService.start(context, alarm.id.toString());

        if (alarm.days == null || alarm.days.isEmpty()) {
          GeoAlarm.save(context, alarm.withEnabled(false));
          activeAlarmManager.removeActiveAlarms(ImmutableSet.of(alarm.id));
        } else {
          activeAlarmManager.resetActiveAlarms();
        }
      }
    }
  }
}
