package maurizi.geoclock.services;


import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.gson.Gson;

import org.threeten.bp.LocalDateTime;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.format.FormatStyle;

import java.util.Set;

import maurizi.geoclock.background.AlarmManagerReceiver;
import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.ui.MapActivity;
import maurizi.geoclock.R;

public class ActiveAlarmManager {
	private static final Gson gson = new Gson();
	private static final String ACTIVE_ALARM_PREFS = "active_alarm_prefs";
	private static final int NOTIFICATION_ID = 42;

	private final SharedPreferences activeAlarmsPrefs;
	private final Context context;
	private final NotificationManager notificationManager;
	private final AlarmManager alarmManager;

	public ActiveAlarmManager(Context context) {
		this.context = context;
		this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		this.activeAlarmsPrefs = context.getSharedPreferences(ACTIVE_ALARM_PREFS, Context.MODE_PRIVATE);
	}

	private GeoAlarm getNextAlarm(final Set<GeoAlarm> activeAlarms, final LocalDateTime now) {
		return Ordering.from(ZonedDateTime.timeLineOrder())
		               .onResultOf((GeoAlarm alarm) -> alarm.getAlarmManagerTime(now))
		               .min(activeAlarms);
	}

	public void resetActiveAlarms() {
		changeActiveAlarms(getSavedAlarms());
	}

	public void addActiveAlarms(ImmutableSet<GeoAlarm> triggerAlarms) {
		final Set<GeoAlarm> savedAlarms = getSavedAlarms();

		final ImmutableSet<GeoAlarm> currentAlarms = ImmutableSet.copyOf(Sets.union(savedAlarms, triggerAlarms));
		changeActiveAlarms(currentAlarms);
	}

	public void removeActiveAlarms(ImmutableSet<GeoAlarm> triggerAlarms) {
		final Set<GeoAlarm> savedAlarms = getSavedAlarms();

		final ImmutableSet<GeoAlarm> currentAlarms = ImmutableSet.copyOf(Sets.difference(savedAlarms, triggerAlarms));
		changeActiveAlarms(currentAlarms);
	}

	public void clearActiveAlarms() {
		changeActiveAlarms(ImmutableSet.of());
	}

	private void changeActiveAlarms(Set<GeoAlarm> currentAlarms) {
		activeAlarmsPrefs.edit().putString(ACTIVE_ALARM_PREFS, gson.toJson(currentAlarms.toArray())).apply();

		notificationManager.cancelAll();
		alarmManager.cancel(AlarmManagerReceiver.getPendingIntent(context));

		if (!currentAlarms.isEmpty()) {
			final LocalDateTime now = LocalDateTime.now();
			final GeoAlarm nextAlarm = getNextAlarm(currentAlarms, now);

			setNotification(nextAlarm, now);
			setAlarm(nextAlarm, now);
		}
	}

	private Set<GeoAlarm> getSavedAlarms() {
		final String savedActiveAlarmsJson = activeAlarmsPrefs.getString(ACTIVE_ALARM_PREFS,
		                                                                 gson.toJson(new GeoAlarm[]{}));
		return ImmutableSet.copyOf(gson.fromJson(savedActiveAlarmsJson, GeoAlarm[].class));
	}

	private void setNotification(@NonNull final GeoAlarm nextAlarm, final LocalDateTime now) {

		final ZonedDateTime nextAlarmTime = nextAlarm.getAlarmManagerTime(now);
		final String alarmFormattedTime = nextAlarmTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT));
		final String title = String.format(context.getString(R.string.alarm_notification_text), alarmFormattedTime);

		Intent showAlarmIntent = MapActivity.getIntent(context, nextAlarm);

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
				                            .setContentText(nextAlarm.name)
				                            .setContentIntent(notificationPendingIntent)
				                            .build();

		// Issue the notification
		notificationManager.notify(NOTIFICATION_ID, notification);
	}

	private void setAlarm(GeoAlarm alarm, LocalDateTime now) {
		final ZonedDateTime alarmTime = alarm.getAlarmManagerTime(now);
		// We set up our (internal) alarm manager to go off slightly before the actual alarm clock time,
		// so that we can give the exact time to the AlarmClock intent
		final ZonedDateTime justBeforeAlarm = alarmTime.minusMinutes(1);
		final long millis = justBeforeAlarm.toInstant().toEpochMilli();

		final PendingIntent pendingAlarmIntent = AlarmManagerReceiver.getPendingIntent(context, alarm);

		if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) {
			alarmManager.setExact(AlarmManager.RTC_WAKEUP, millis, pendingAlarmIntent);
		} else {
			alarmManager.set(AlarmManager.RTC_WAKEUP, millis, pendingAlarmIntent);
		}
	}
}
