package maurizi.geoclock;

import android.os.Bundle;

public class AlarmManagerReceiver extends AbstractGeoAlarmReceiver {

	@Override
	public void onConnected(Bundle bundle) {
		// TODO: Add this to the AndroidManifest.xml
		// TODO: Remove notification for this alarm.
		//       If repeating alarm, reset alarm (basically identical to geofenceReceiver).
		//       If not, delete it.
		// TODO: Call Alarm Clock Intent and have it go off immediately (or in 1-5 seconds?)
	}
}
