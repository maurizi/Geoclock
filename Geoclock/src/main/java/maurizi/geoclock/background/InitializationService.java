package maurizi.geoclock.background;

import android.app.IntentService;
import android.content.Intent;

import org.threeten.bp.Instant;

import java.util.Collection;

import maurizi.geoclock.Alarm;
import maurizi.geoclock.utils.Alarms;
import maurizi.geoclock.Location;
import maurizi.geoclock.utils.Locations;
import maurizi.geoclock.utils.ActiveAlarmManager;
import maurizi.geoclock.utils.LocationServiceGoogle;

import static com.google.common.collect.Collections2.filter;

public class InitializationService extends IntentService {
	public InitializationService() {
		super(InitializationService.class.getName());
	}

	@Override
	public void onHandleIntent(final Intent intent) {
		Collection<Alarm> alarms = Alarms.get(this);
		disableExpiredAlarms(alarms);

		ActiveAlarmManager activeAlarmManager = new ActiveAlarmManager(this);
		activeAlarmManager.clearActiveAlarms();

		Collection<Location> locations = Locations.get(this);
		if (!locations.isEmpty()) {
			LocationServiceGoogle locationService = new LocationServiceGoogle(this);
			locationService.connect(() -> locationService.addGeofences(locations).setResultCallback(status -> {
				// TODO: We need to handle errors somehow...
			}));
		}
	}

	public void disableExpiredAlarms(Collection<Alarm> alarms) {
		// When we set alarms, we store the time they will go off at.
		// If the alarm does not repeat, and we missed it, we need to disable it.
		Instant now = Instant.now();
		Collection<Alarm> disabledAlarms = filter(alarms, alarm ->
			alarm.isNonRepeating() && alarm.time != null && now.isAfter(Instant.ofEpochMilli(alarm.time))
		);
		for (Alarm alarm : disabledAlarms) {
			Alarms.save(this, alarm.withEnabled(false));
		}
	}
}