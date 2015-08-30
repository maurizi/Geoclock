package maurizi.geoclock.utils;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.common.collect.Lists;

import java.util.Collection;

import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.R;
import maurizi.geoclock.background.GeofenceReceiver;

import static java.util.Collections.singletonList;

public class LocationServiceGoogle
		implements ConnectionCallbacks, OnConnectionFailedListener {

	@NonNull final private Context context;
	@NonNull final private PendingIntent pendingIntent;
	@NonNull final private GoogleApiClient apiClient;
	private Callback callback;

	public interface Callback {
		void onConnected();
	}

	public LocationServiceGoogle(@NonNull Context context) {
		this.context = context;
		this.pendingIntent = getPendingIntent(context);
		this.apiClient = getApiClient(context);
	}

	@Override
	public void onConnected(final Bundle bundle) {
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

	public void connect(@NonNull Callback cb) {
		this.callback = cb;
		if (!apiClient.isConnected()) {
			apiClient.connect();
		}
	}

	public void disconnect() {
		apiClient.disconnect();
	}

	public PendingResult<Status> addGeofence(@NonNull GeoAlarm alarm) {
		return addGeofences(singletonList(alarm));
	}

	public PendingResult<Status> addGeofences(@NonNull Collection<GeoAlarm> alarms) {
		GeofencingRequest.Builder geofenceRequestBuilder = new GeofencingRequest.Builder();
		geofenceRequestBuilder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
		for (GeoAlarm alarm : alarms) {
			geofenceRequestBuilder.addGeofence(getGeofence(alarm));
		}

		return LocationServices.GeofencingApi.addGeofences(apiClient, geofenceRequestBuilder.build(), pendingIntent);
	}

	public PendingResult<Status> removeGeofence(@NonNull GeoAlarm alarm) {
		return LocationServices.GeofencingApi
				.removeGeofences(apiClient, Lists.<String>newArrayList(alarm.id.toString()));
	}

	public @Nullable LatLng getLastLocation() {
		android.location.Location loc = LocationServices.FusedLocationApi.getLastLocation(apiClient);
		if (loc != null) {
			return new LatLng(loc.getLatitude(), loc.getLongitude());
		}
		return null;
	}

	private Geofence getGeofence(GeoAlarm alarm) {
		return new Geofence.Builder()
		                   .setRequestId(alarm.id.toString())
		                   .setCircularRegion(alarm.location.latitude, alarm.location.longitude, alarm.radius)
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
