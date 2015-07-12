package maurizi.geoclock.services;

import com.google.android.gms.maps.model.LatLng;

import maurizi.geoclock.GeoAlarm;

abstract public class LocationService {

	public interface LocationResultListener {
		void onResult();
	}

	protected LocationResultListener locationResultListener;

	public abstract void startMonitoring();
	public abstract void connect();
	public abstract void disconnect();
	public abstract void addGeofence(GeoAlarm location, LocationResultListener listener);
	public abstract LatLng getLastLocation();

}
