package maurizi.geoclock.background;

import android.app.IntentService;
import android.content.Intent;

import java.util.Collection;

import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.services.ActiveAlarmManager;
import maurizi.geoclock.services.LocationServiceGoogle;

public class InitializationService extends IntentService {
	public InitializationService() {
		super(InitializationService.class.getName());
	}

	@Override
	public void onHandleIntent(final Intent intent) {
		Collection<GeoAlarm> alarms = GeoAlarm.getGeoAlarms(this);

		ActiveAlarmManager activeAlarmManager = new ActiveAlarmManager(this);
		activeAlarmManager.clearActiveAlarms();

		LocationServiceGoogle locationService = new LocationServiceGoogle(this);
		// TODO: We need to handle errors somehow...
		locationService.connect(() -> locationService.addGeofences(alarms).await());
	}
}