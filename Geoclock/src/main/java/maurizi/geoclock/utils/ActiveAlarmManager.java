package maurizi.geoclock.utils;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gson.Gson;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Comparator;
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

	private final android.content.SharedPreferences activeAlarmsPrefs;
	private final Context context;
	private final NotificationManager notificationManager;
	private final AlarmManager alarmManager;

	public ActiveAlarmManager(Context context) {
		this.context = context;
		this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		this.activeAlarmsPrefs = context.getSharedPreferences(ACTIVE_ALARM_IDS, Context.MODE_PRIVATE);
	}

	@Nullable
	private GeoAlarm getNextAlarm(final Set<GeoAlarm> activeAlarms, final LocalDateTime now) {
		return activeAlarms.stream()
		        .filter(alarm -> alarm.calculateAlarmTime(now) != null)
		        .min(Comparator.comparingLong(alarm -> alarm.calculateAlarmTime(now).toInstant().toEpochMilli()))
		        .orElse(null);
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

		alarmManager.cancel(AlarmClockReceiver.getPendingIntent(context));
		alarmManager.cancel(NotificationReceiver.getPendingIntent(context));
		notificationManager.cancel(NotificationReceiver.NOTIFICATION_ID);

		if (!currentAlarms.isEmpty()) {
			final LocalDateTime now = LocalDateTime.now();
			final GeoAlarm nextAlarm = getNextAlarm(currentAlarms, now);
			if (nextAlarm != null) {
				final ZonedDateTime alarmTime = nextAlarm.calculateAlarmTime(now);
				setNotification(nextAlarm, alarmTime);
				setAlarm(nextAlarm, alarmTime);
			}
		}
	}

	private Set<UUID> getSavedAlarms() {
		final String savedActiveAlarmsJson = activeAlarmsPrefs.getString(ACTIVE_ALARM_IDS, gson.toJson(new UUID[]{}));
		return ImmutableSet.copyOf(gson.fromJson(savedActiveAlarmsJson, UUID[].class));
	}

	private void setNotification(@NonNull final GeoAlarm alarm, final ZonedDateTime alarmTime) {
		final ZonedDateTime now = ZonedDateTime.now();
		final ZonedDateTime notificationTime = alarmTime.minusMinutes(15);

		if (notificationTime.isBefore(now)) {
			android.content.Intent intent = NotificationReceiver.getIntent(context, alarm);
			context.sendBroadcast(intent);
		} else {
			final long millis = notificationTime.toInstant().toEpochMilli();
			final PendingIntent pendingNotificationIntent = NotificationReceiver.getPendingIntent(context, alarm);
			alarmManager.set(AlarmManager.RTC_WAKEUP, millis, pendingNotificationIntent);
		}
	}

	private void setAlarm(GeoAlarm alarm, final ZonedDateTime alarmTime) {
		final long millis = alarmTime.toInstant().toEpochMilli();
		final PendingIntent pendingAlarmIntent = AlarmClockReceiver.getPendingIntent(context, alarm);

		// setAlarmClock() is always exact, fires through Doze, and — critically — grants
		// the broadcast receiver permission to call startForegroundService(). The other
		// exact-alarm APIs (setExactAndAllowWhileIdle, setAndAllowWhileIdle) do NOT grant
		// that permission, causing ForegroundServiceStartNotAllowedException on API 31+.
		// It also shows the alarm in the system clock UI, appropriate for an alarm app.
		// Requires USE_EXACT_ALARM permission (auto-granted for alarm clock apps).
		android.content.Intent showIntent = maurizi.geoclock.ui.MapActivity.getIntent(context, alarm);
		android.app.PendingIntent showPi = android.app.PendingIntent.getActivity(
		        context, alarm.id.hashCode(), showIntent,
		        android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
		alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(millis, showPi), pendingAlarmIntent);
	}
}
