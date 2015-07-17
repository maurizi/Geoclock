package maurizi.geoclock;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.support.v4.app.TaskStackBuilder;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofenceStatusCodes;
import com.google.android.gms.location.GeofencingEvent;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.gson.Gson;

import org.threeten.bp.LocalDateTime;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.format.FormatStyle;

import java.util.List;
import java.util.Set;

import static maurizi.geoclock.GeoAlarm.getGeoAlarmForGeofenceFn;

public class GeofenceReceiver extends BroadcastReceiver {

	private SharedPreferences activeAlarmsPrefs;

	private interface SetOp<T> {
		Set<T> apply(Set<T> a, Set<T> b);
	}

	private Context context;
	private NotificationManager notificationManager;
	private AlarmManager alarmManager;

	private static final String TAG = GeofenceReceiver.class.getSimpleName();
	private static final Gson gson = new Gson();
	private static final String ACTIVE_ALARM_PREFS = "active_alarm_prefs";
	private static final int NOTIFICATION_ID = 42;

	@Override
	public void onReceive(Context context, Intent intent) {
		this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		this.activeAlarmsPrefs = context.getSharedPreferences(ACTIVE_ALARM_PREFS, Context.MODE_PRIVATE);
		this.context = context;

		GeofencingEvent event = GeofencingEvent.fromIntent(intent);
		if (event.hasError()) {
			final String errorMessage = GeofenceStatusCodes.getStatusCodeString(event.getErrorCode());
			Log.e(TAG, errorMessage);
			return;
		}

		final int transition = event.getGeofenceTransition();
		final List<Geofence> affectedGeofences = event.getTriggeringGeofences();

		if (null != affectedGeofences && !affectedGeofences.isEmpty()) {
			final ImmutableSet<GeoAlarm> affectedAlarms = ImmutableSet.copyOf(
					Lists.transform(affectedGeofences, getGeoAlarmForGeofenceFn(context)));
			final ImmutableSet<GeoAlarm> activeAlarms = changeActiveAlarms(affectedAlarms, transition);

			notificationManager.cancelAll();
			alarmManager.cancel(AlarmManagerReceiver.getPendingIntent(context));

			if (!activeAlarms.isEmpty()) {
				final LocalDateTime now = LocalDateTime.now();
				final GeoAlarm nextAlarm = getNextAlarm(activeAlarms, now);

				setNotification(nextAlarm, now);
				setAlarm(nextAlarm, now);
			}
		}
	}

	private GeoAlarm getNextAlarm(final ImmutableSet<GeoAlarm> activeAlarms, final LocalDateTime now) {
		return Ordering.from(ZonedDateTime.timeLineOrder())
		               .onResultOf((GeoAlarm alarm) -> alarm.getAlarmManagerTime(now))
		               .min(activeAlarms);
	}

	private ImmutableSet<GeoAlarm> changeActiveAlarms(ImmutableSet<GeoAlarm> triggerAlarms, final int transition) {
		final Set<GeoAlarm> savedAlarms = getSavedAlarms();

		final SetOp<GeoAlarm> op = transition == Geofence.GEOFENCE_TRANSITION_ENTER ? Sets::union : Sets::difference;
		final ImmutableSet<GeoAlarm> currentAlarms = ImmutableSet.copyOf(op.apply(savedAlarms, triggerAlarms));

		activeAlarmsPrefs.edit().putString(ACTIVE_ALARM_PREFS, gson.toJson(currentAlarms.toArray())).apply();

		return currentAlarms;
	}

	private Set<GeoAlarm> getSavedAlarms() {
		final String savedActiveAlarmsJson = activeAlarmsPrefs.getString(ACTIVE_ALARM_PREFS,
		                                                                 gson.toJson(new GeoAlarm[]{}));
		return ImmutableSet.copyOf(gson.fromJson(savedActiveAlarmsJson, GeoAlarm[].class));
	}

	protected void setNotification(@NonNull final GeoAlarm nextAlarm, final LocalDateTime now) {

		final ZonedDateTime nextAlarmTime = nextAlarm.getAlarmManagerTime(now);
		final String alarmFormattedTime = nextAlarmTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT));

		Intent showAlarmIntent = new Intent(context, MapActivity.class);
		showAlarmIntent.putExtra(MapActivity.ALARM_JSON, gson.toJson(nextAlarm, GeoAlarm.class));

		// Create an content intent that comes with a back stack
		// This makes hitting back from the activity go to the home screen
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
		stackBuilder.addParentStack(MapActivity.class);
		stackBuilder.addNextIntent(showAlarmIntent);

		PendingIntent notificationPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

		// TODO: Add a cancel button
		// TODO: Make clicking the notification open the GeoAlarmFragment
		// TODO: Extract text
		Bitmap icon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_launcher);
		Notification notification = new NotificationCompat.Builder(context)
		                            .setSmallIcon(R.drawable.ic_alarm_black_24dp)
		                            .setLargeIcon(icon)
		                            .setContentTitle(String.format("Alarm %s", nextAlarm.name))
		                            .setContentText(alarmFormattedTime)
		                            .setContentIntent(notificationPendingIntent)
		                            .build();

		// Issue the notification
		notificationManager.notify(NOTIFICATION_ID, notification);
	}

	public void setAlarm(GeoAlarm alarm, LocalDateTime now) {
		final long alarmTime = alarm.getAlarmManagerTime(now).toInstant().toEpochMilli();
		final PendingIntent pendingAlarmIntent = AlarmManagerReceiver.getPendingIntent(context);

		if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) {
			alarmManager.setExact(AlarmManager.RTC_WAKEUP, alarmTime, pendingAlarmIntent);
		} else {
			alarmManager.set(AlarmManager.RTC_WAKEUP, alarmTime, pendingAlarmIntent);
		}
	}
}
