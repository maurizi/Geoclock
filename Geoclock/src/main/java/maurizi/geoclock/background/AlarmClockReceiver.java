package maurizi.geoclock.background;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.google.common.collect.ImmutableSet;

import java.util.UUID;

import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.ui.AlarmRingingActivity;
import maurizi.geoclock.utils.ActiveAlarmManager;

public class AlarmClockReceiver extends BroadcastReceiver {
    private static final String ALARM_ID = "alarm_id";

    public static PendingIntent getPendingIntent(Context context, GeoAlarm alarm) {
        Intent intent = new Intent(context, AlarmClockReceiver.class);
        intent.putExtra(ALARM_ID, alarm.id.toString());
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static PendingIntent getPendingIntent(Context context) {
        Intent intent = new Intent(context, AlarmClockReceiver.class);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (intent.hasExtra(ALARM_ID)) {
            GeoAlarm alarm = GeoAlarm.getGeoAlarm(context, UUID.fromString(intent.getStringExtra(ALARM_ID)));
            if (alarm != null && alarm.enabled) {
                ActiveAlarmManager activeAlarmManager = new ActiveAlarmManager(context);

                Intent ringIntent = new Intent(context, AlarmRingingActivity.class);
                ringIntent.putExtra(AlarmRingingActivity.EXTRA_ALARM_ID, alarm.id.toString());
                ringIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
                context.startActivity(ringIntent);

                if (alarm.days == null || alarm.days.isEmpty()) {
                    GeoAlarm.remove(context, alarm);
                    GeoAlarm.save(context, alarm.withEnabled(false));
                    activeAlarmManager.removeActiveAlarms(ImmutableSet.of(alarm.id));
                } else {
                    activeAlarmManager.resetActiveAlarms();
                }
            }
        }
    }
}
