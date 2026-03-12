package maurizi.geoclock.integration;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.service.notification.StatusBarNotification;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.GrantPermissionRule;

import com.google.android.gms.maps.model.LatLng;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.background.AlarmClockReceiver;
import maurizi.geoclock.background.AlarmRingingService;
import maurizi.geoclock.background.NotificationReceiver;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Instrumented tests for notification action buttons (Snooze, Dismiss, Cancel alarm).
 *
 * Uses getActiveNotifications() + PendingIntent.send() rather than notification-shade UI
 * interaction, which is fragile across Android API versions.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class NotificationActionsTest {

	@Rule
	public GrantPermissionRule notificationPermission = Build.VERSION.SDK_INT >= 33
	        ? GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
	        : GrantPermissionRule.grant();

	private Context context;
	private NotificationManager notificationManager;

	@Before
	public void setUp() {
		context = ApplicationProvider.getApplicationContext();
		notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		AlarmRingingService.SNOOZE_DURATION_MS = 5 * 60 * 1000L;
		AlarmRingingService.AUDIO_DISABLED = true;
	}

	@After
	public void tearDown() {
		AlarmRingingService.stop(context);
		AlarmRingingService.SNOOZE_DURATION_MS = 5 * 60 * 1000L;
		AlarmRingingService.AUDIO_DISABLED = false;
	}

	@Test
	public void snoozeAction_notificationButton_schedulesSnooze() throws Exception {
		AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			Assume.assumeTrue("SCHEDULE_EXACT_ALARM not granted; skipping snooze test",
			        alarmManager.canScheduleExactAlarms());
		}

		GeoAlarm alarm = saveAlarm(enabledAlarm());
		startRingingService(alarm.id.toString());

		Notification.Action snoozeAction = findRingingAction(context.getString(maurizi.geoclock.R.string.snooze_5min));
		assertNotNull("Snooze action should be in ringing notification", snoozeAction);

		snoozeAction.actionIntent.send(context, 0, null);
		Thread.sleep(1000);

		assertNotNull("Snooze should schedule an alarm clock",
		        alarmManager.getNextAlarmClock());
	}

	@Test
	public void dismissAction_notificationButton_stopsService() throws Exception {
		GeoAlarm alarm = saveAlarm(enabledAlarm());
		startRingingService(alarm.id.toString());

		Notification.Action dismissAction = findRingingAction(context.getString(maurizi.geoclock.R.string.dismiss));
		assertNotNull("Dismiss action should be in ringing notification", dismissAction);

		dismissAction.actionIntent.send(context, 0, null);
		Thread.sleep(2000);

		assertNull("Ringing notification should be gone after dismiss",
		        findNotification(AlarmClockReceiver.RINGING_NOTIFICATION_ID));
	}

	@Test
	public void cancelAlarm_upcomingNotification_disablesAlarm() throws Exception {
		GeoAlarm alarm = saveAlarm(enabledAlarm());

		context.sendBroadcast(NotificationReceiver.getIntent(context, alarm));
		Thread.sleep(1000);

		StatusBarNotification sbn = findNotification(NotificationReceiver.NOTIFICATION_ID);
		assertNotNull("Upcoming notification should be posted", sbn);

		Notification.Action cancelAction = findAction(sbn.getNotification().actions, context.getString(maurizi.geoclock.R.string.cancel_alarm));
		assertNotNull("Cancel alarm action should be in upcoming notification", cancelAction);

		cancelAction.actionIntent.send(context, 0, null);
		Thread.sleep(1000);

		GeoAlarm saved = GeoAlarm.getGeoAlarm(context, alarm.id);
		assertNotNull(saved);
		assertFalse("Alarm should be disabled after cancel", saved.enabled);
	}

	// ---- helpers ----

	/** Starts the ringing service via AlarmClockReceiver broadcast (grants foreground-service start privilege). */
	private void startRingingService(String alarmId) throws Exception {
		android.content.Intent intent = new android.content.Intent(context, AlarmClockReceiver.class);
		intent.putExtra("alarm_id", alarmId);
		context.sendBroadcast(intent);
		Thread.sleep(1500);
	}

	private Notification.Action findRingingAction(String label) {
		StatusBarNotification sbn = findNotification(AlarmClockReceiver.RINGING_NOTIFICATION_ID);
		if (sbn == null) return null;
		return findAction(sbn.getNotification().actions, label);
	}

	private StatusBarNotification findNotification(int notifId) {
		StatusBarNotification[] active = notificationManager.getActiveNotifications();
		if (active == null) return null;
		for (StatusBarNotification sbn : active) {
			if (sbn.getId() == notifId) return sbn;
		}
		return null;
	}

	private static Notification.Action findAction(Notification.Action[] actions, String label) {
		if (actions == null) return null;
		for (Notification.Action action : actions) {
			if (label.contentEquals(action.title)) return action;
		}
		return null;
	}

	private GeoAlarm enabledAlarm() {
		return GeoAlarm.builder()
		        .id(UUID.randomUUID())
		        .location(new LatLng(37.4, -122.0))
		        .radius(100)
		        .enabled(true)
		        .hour(8)
		        .minute(0)
		        .build();
	}

	private GeoAlarm saveAlarm(GeoAlarm alarm) {
		GeoAlarm.save(context, alarm);
		return alarm;
	}
}
