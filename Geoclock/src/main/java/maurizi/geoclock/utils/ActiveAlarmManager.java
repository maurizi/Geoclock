package maurizi.geoclock.utils;


import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.gson.Gson;

import org.threeten.bp.LocalDateTime;
import org.threeten.bp.ZonedDateTime;

import java.util.Set;
import java.util.UUID;

import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.background.AlarmClockReceiver;
import maurizi.geoclock.background.NotificationReceiver;

import static com.google.common.collect.Sets.filter;
import static com.google.common.collect.Sets.newHashSet;


public class ActiveAlarmManager {
	private static final String ACTIVE_ALARM_IDS = "active_alarm_prefs";
	private static final Gson gson = new Gson();

	private final SharedPreferences activeAlarmsPrefs;
	private final Context context;
	private final NotificationManager notificationManager;
	private final AlarmManager alarmManager;

	public ActiveAlarmManager(Context context) {
		this.context = context;
		this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		this.activeAlarmsPrefs = context.getSharedPreferences(ACTIVE_ALARM_IDS, Context.MODE_PRIVATE);
	}

	private GeoAlarm getNextAlarm(final Set<GeoAlarm> activeAlarms, final LocalDateTime now) {
		return Ordering.from(ZonedDateTime.timeLineOrder())
		               .onResultOf((GeoAlarm alarm) -> alarm.calculateAlarmTime(now))
		               .min(activeAlarms);
	}

	public void resetActiveAlarms() {
		changeActiveAlarms(getSavedAlarms());
	}

	public void addActiveAlarms(ImmutableSet<UUID> triggerAlarmIds) {
		final Set<UUID> savedAlarmIds = getSavedAlarms();

		final ImmutableSet<UUID> currentAlarms = ImmutableSet.copyOf(Sets.union(savedAlarmIds, triggerAlarmIds));
		changeActiveAlarms(currentAlarms);
	}

	public void removeActiveAlarms(Set<UUID> triggerAlarmIds) {
		final Set<UUID> savedAlarmIds = getSavedAlarms();

		final ImmutableSet<UUID> currentAlarms = ImmutableSet.copyOf(Sets.difference(savedAlarmIds, triggerAlarmIds));
		changeActiveAlarms(currentAlarms);
	}

	public void clearActiveAlarms() {
		changeActiveAlarms(ImmutableSet.of());
	}

	private void changeActiveAlarms(Set<UUID> currentAlarmIds) {
		activeAlarmsPrefs.edit().putString(ACTIVE_ALARM_IDS, gson.toJson(currentAlarmIds.toArray(), UUID[].class)).apply();
		Set<GeoAlarm> currentAlarms = filter(newHashSet(GeoAlarm.getGeoAlarms(context)), alarm -> currentAlarmIds.contains(alarm.id));

		notificationManager.cancelAll();
		alarmManager.cancel(AlarmClockReceiver.getPendingIntent(context));
		alarmManager.cancel(NotificationReceiver.getPendingIntent(context));

		if (!currentAlarms.isEmpty()) {
			final LocalDateTime now = LocalDateTime.now();
			final GeoAlarm nextAlarm = getNextAlarm(currentAlarms, now);
			final ZonedDateTime alarmTime = nextAlarm.calculateAlarmTime(now);

			setNotification(nextAlarm, alarmTime);
			setAlarm(nextAlarm, alarmTime);
		}
	}

	private Set<UUID> getSavedAlarms() {
		final String savedActiveAlarmsJson = activeAlarmsPrefs.getString(ACTIVE_ALARM_IDS, gson.toJson(new UUID[]{}));
		return ImmutableSet.copyOf(gson.fromJson(savedActiveAlarmsJson, UUID[].class));
	}

	private void setNotification(@NonNull final GeoAlarm alarm, final ZonedDateTime alarmTime) {
		final ZonedDateTime now = ZonedDateTime.now();
		final ZonedDateTime notificationTime = alarmTime.minusDays(1);

		if (notificationTime.isBefore(now)) {
			Intent intent = NotificationReceiver.getIntent(context, alarm);
			context.sendBroadcast(intent);
		} else {
			final long millis = notificationTime.toInstant().toEpochMilli();
			final PendingIntent pendingNotificationIntent = NotificationReceiver.getPendingIntent(context, alarm);
			alarmManager.set(AlarmManager.RTC_WAKEUP, millis, pendingNotificationIntent);
		}
	}

	private void setAlarm(GeoAlarm alarm, final ZonedDateTime alarmTime) {
		// We set up our (internal) alarm manager to go off slightly before the actual alarm clock time,
		// so that we can give the exact time to the AlarmClock intent
		final ZonedDateTime justBeforeAlarm = alarmTime.minusMinutes(1);
		final long millis = justBeforeAlarm.toInstant().toEpochMilli();

		final PendingIntent pendingAlarmIntent = AlarmClockReceiver.getPendingIntent(context, alarm);

		if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) {
			alarmManager.setExact(AlarmManager.RTC_WAKEUP, millis, pendingAlarmIntent);
		} else {
			alarmManager.set(AlarmManager.RTC_WAKEUP, millis, pendingAlarmIntent);
		}
	}
}
