package maurizi.geoclock.background;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;

import org.threeten.bp.LocalDateTime;
import org.threeten.bp.ZonedDateTime;

import java.util.Collection;
import java.util.Collections;

import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.services.ActiveAlarmManager;
import maurizi.geoclock.services.LocationServiceGoogle;

import static com.google.common.collect.Collections2.filter;

public class InitializationService extends IntentService {
	public InitializationService() {
		super(InitializationService.class.getName());
	}

	@Override
	public void onHandleIntent(final Intent intent) {
		Collection<GeoAlarm> alarms = GeoAlarm.getGeoAlarms(this);
		disableExpiredAlarms(alarms);

		ActiveAlarmManager activeAlarmManager = new ActiveAlarmManager(this);
		activeAlarmManager.clearActiveAlarms();

		LocationServiceGoogle locationService = new LocationServiceGoogle(this);
		locationService.connect(() -> locationService.addGeofences(alarms).setResultCallback(status -> {
			// TODO: We need to handle errors somehow...
		}));
	}

	public void disableExpiredAlarms(Collection<GeoAlarm> alarms) {
		// When we set alarms, we store the time they will go off at.
		// If the alarm does not repeat, and we missed it, we need to disable it.
		ZonedDateTime now = ZonedDateTime.now();
		Collection<GeoAlarm> disabledAlarms = filter(alarms, alarm ->
			alarm.isNonRepeating() && alarm.time != null && now.isAfter(alarm.time)
		);
		for (GeoAlarm alarm : disabledAlarms) {
			GeoAlarm.save(this, alarm);
		}
	}
}