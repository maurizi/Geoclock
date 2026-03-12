package maurizi.geoclock.utils;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import maurizi.geoclock.R;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

/**
 * Encapsulates just-in-time permission checks for alarm-related permissions.
 * Foreground location is requested separately at app launch.
 */
public class PermissionHelper {

	public static final int REQUEST_BACKGROUND_LOCATION = 100;
	public static final int REQUEST_NOTIFICATION_PERMISSION = 101;

	public static boolean needsBackgroundLocation(@NonNull Context context) {
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
		        && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
		                != PERMISSION_GRANTED;
	}

	public static boolean needsNotificationPermission(@NonNull Context context) {
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
		        && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
		                != PERMISSION_GRANTED;
	}

	public static boolean needsExactAlarmPermission(@NonNull Context context) {
		// API 31+: SCHEDULE_EXACT_ALARM can be revoked by the user (API 31-32) or
		// may not be pre-granted (API 33+ fresh installs). USE_EXACT_ALARM (API 33+)
		// is auto-granted and can't be revoked, so canScheduleExactAlarms() returns
		// true when either permission is held.
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
			return am != null && !am.canScheduleExactAlarms();
		}
		return false;
	}

	public static boolean needsFullScreenIntentPermission(@NonNull Context context) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
			NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			return nm != null && !nm.canUseFullScreenIntent();
		}
		return false;
	}

	public static boolean hasAllAlarmPermissions(@Nullable Context context) {
		if (context == null) return false;
		return !needsBackgroundLocation(context)
		        && !needsNotificationPermission(context)
		        && !needsExactAlarmPermission(context)
		        && !needsFullScreenIntentPermission(context);
	}

	/**
	 * Requests alarm-related permissions in sequence with rationale dialogs.
	 * Call this when the user saves or enables an alarm.
	 *
	 * @param activity  the hosting activity
	 * @param onComplete called after all permission requests are done (or skipped)
	 */
	public static void requestAlarmPermissions(@NonNull FragmentActivity activity,
	                                            @NonNull Runnable onComplete) {
		requestBackgroundLocation(activity, () ->
		    requestNotificationPermission(activity, () ->
		        requestExactAlarm(activity, () ->
		            requestFullScreenIntent(activity, onComplete))));
	}

	private static void requestBackgroundLocation(@NonNull FragmentActivity activity,
	                                               @NonNull Runnable next) {
		if (!needsBackgroundLocation(activity)) {
			next.run();
			return;
		}
		new AlertDialog.Builder(activity)
		        .setTitle(R.string.perm_rationale_title)
		        .setMessage(R.string.perm_background_location)
		        .setPositiveButton(android.R.string.ok, (d, w) -> {
			        activity.requestPermissions(
			                new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
			                REQUEST_BACKGROUND_LOCATION);
			        // next step runs when onRequestPermissionsResult fires;
			        // but we chain anyway since the user may deny
			        next.run();
		        })
		        .setNegativeButton(android.R.string.cancel, (d, w) -> next.run())
		        .show();
	}

	private static void requestNotificationPermission(@NonNull FragmentActivity activity,
	                                                   @NonNull Runnable next) {
		if (!needsNotificationPermission(activity)) {
			next.run();
			return;
		}
		new AlertDialog.Builder(activity)
		        .setTitle(R.string.perm_rationale_title)
		        .setMessage(R.string.perm_notifications)
		        .setPositiveButton(android.R.string.ok, (d, w) -> {
			        activity.requestPermissions(
			                new String[]{Manifest.permission.POST_NOTIFICATIONS},
			                REQUEST_NOTIFICATION_PERMISSION);
			        next.run();
		        })
		        .setNegativeButton(android.R.string.cancel, (d, w) -> next.run())
		        .show();
	}

	private static void requestExactAlarm(@NonNull FragmentActivity activity,
	                                       @NonNull Runnable next) {
		if (!needsExactAlarmPermission(activity)) {
			next.run();
			return;
		}
		new AlertDialog.Builder(activity)
		        .setTitle(R.string.perm_rationale_title)
		        .setMessage(R.string.perm_exact_alarm)
		        .setPositiveButton(android.R.string.ok, (d, w) -> {
			        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
				        Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
				        intent.setData(android.net.Uri.parse("package:" + activity.getPackageName()));
				        activity.startActivity(intent);
			        }
			        next.run();
		        })
		        .setNegativeButton(android.R.string.cancel, (d, w) -> next.run())
		        .show();
	}

	private static void requestFullScreenIntent(@NonNull FragmentActivity activity,
	                                             @NonNull Runnable next) {
		if (!needsFullScreenIntentPermission(activity)) {
			next.run();
			return;
		}
		new AlertDialog.Builder(activity)
		        .setTitle(R.string.perm_rationale_title)
		        .setMessage(R.string.perm_full_screen_intent)
		        .setPositiveButton(android.R.string.ok, (d, w) -> {
			        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
			        intent.setData(android.net.Uri.parse("package:" + activity.getPackageName()));
			        activity.startActivity(intent);
			        next.run();
		        })
		        .setNegativeButton(android.R.string.cancel, (d, w) -> next.run())
		        .show();
	}
}
