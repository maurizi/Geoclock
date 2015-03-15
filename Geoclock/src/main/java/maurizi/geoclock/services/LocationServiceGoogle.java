package maurizi.geoclock.services;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;

import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.GeofenceReceiver;
import maurizi.geoclock.R;

public class LocationServiceGoogle extends LocationService
		implements ConnectionCallbacks, OnConnectionFailedListener {

	@NonNull final private Context context;
	@NonNull final private PendingIntent pendingIntent;
	@NonNull final private GoogleApiClient apiClient;
	@NonNull final private Callback callback;

	public interface Callback {
		void onConnected();
	}

	public LocationServiceGoogle(@NonNull Context context, @NonNull Callback cb) {
		this.context = context;
		this.pendingIntent = getPendingIntent(context);
		this.apiClient = getApiClient(context);
		this.callback = cb;
	}

	@Override
	public void onConnected(final Bundle bundle) {
		startMonitoring();
		callback.onConnected();
	}

	@Override
	public void onConnectionSuspended(final int i) {
		Toast.makeText(context, R.string.lost_location, Toast.LENGTH_SHORT).show();
	}

	@Override
	public void onConnectionFailed(final ConnectionResult connectionResult) {
		Toast.makeText(context, R.string.fail_location, Toast.LENGTH_SHORT).show();
	}

	@Override
	public void startMonitoring() {
		// TODO: Add geofences on first startup
	}

	@Override
	public void connect() {
		if (!apiClient.isConnected()) {
			apiClient.connect();
		}
	}

	@Override
	public void disconnect() {
		apiClient.disconnect();
	}

	@Override
	public void addGeofence(GeoAlarm location, LocationResultListener listener) {
		GeofencingRequest.Builder geofenceRequestBuilder = new GeofencingRequest.Builder();
		geofenceRequestBuilder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
		geofenceRequestBuilder.addGeofence(getGeofence(location));

		LocationServices.GeofencingApi
				.addGeofences(apiClient, geofenceRequestBuilder.build(), pendingIntent)
				.setResultCallback(status -> {
					if (status.isSuccess()) {
						listener.onResult();
					}
				});
	}

	@Override
	public LatLng getLastLocation() {
		android.location.Location loc = LocationServices.FusedLocationApi.getLastLocation(apiClient);
		return new LatLng(loc.getLatitude(), loc.getLongitude());
	}

	private Geofence getGeofence(GeoAlarm location) {
		return new Geofence.Builder()
				       .setRequestId(location.getName())
				       .setCircularRegion(location.getLocation().latitude, location.getLocation().longitude, location.getRadius())
				       .setExpirationDuration(Geofence.NEVER_EXPIRE)
				       .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
				       .build();
	}

	private PendingIntent getPendingIntent(Context context) {
		Intent intent = new Intent(context, GeofenceReceiver.class);
		return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
	}

	private GoogleApiClient getApiClient(Context context) {
		GoogleApiClient apiClient = new GoogleApiClient.Builder(context)
				                            .addApi(LocationServices.API)
				                            .build();
		apiClient.registerConnectionCallbacks(this);
		apiClient.registerConnectionFailedListener(this);
		return apiClient;
	}
}
