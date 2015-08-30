package maurizi.geoclock.background;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofenceStatusCodes;
import com.google.android.gms.location.GeofencingEvent;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.utils.ActiveAlarmManager;

import static com.google.common.collect.Collections2.filter;
import static com.google.common.collect.Collections2.transform;
import static maurizi.geoclock.GeoAlarm.getGeoAlarmForGeofenceFn;

public class GeofenceReceiver extends BroadcastReceiver {
	private static final String TAG = GeofenceReceiver.class.getSimpleName();

	@Override
	public void onReceive(Context context, Intent intent) {
		final ActiveAlarmManager activeAlarmManager = new ActiveAlarmManager(context);

		GeofencingEvent event = GeofencingEvent.fromIntent(intent);
		if (event.hasError()) {
			final String errorMessage = GeofenceStatusCodes.getStatusCodeString(event.getErrorCode());
			Log.e(TAG, errorMessage);
			return;
		}

		final int transition = event.getGeofenceTransition();
		final List<Geofence> affectedGeofences = event.getTriggeringGeofences();

		if (null != affectedGeofences && !affectedGeofences.isEmpty()) {
			final Collection<GeoAlarm> affectedAlarms = filter(
					Lists.transform(affectedGeofences, getGeoAlarmForGeofenceFn(context)), a -> a != null);
			ImmutableSet<UUID> affectedAlarmIds = ImmutableSet.copyOf(transform(affectedAlarms, alarm -> alarm.id));
			if (transition == Geofence.GEOFENCE_TRANSITION_ENTER) {
				activeAlarmManager.addActiveAlarms(affectedAlarmIds);
			} else {
				activeAlarmManager.removeActiveAlarms(affectedAlarmIds);
			}
		}
	}
}
