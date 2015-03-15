package maurizi.geoclock;

import android.app.PendingIntent;
import android.content.Context;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;

public class GoogleApiManager {

	private GoogleApiClient mGoogleApiClient;

	public GoogleApiManager(Context context) {

	}

	public void connect() {
		mGoogleApiClient.connect();
	}

	public void disconnect() {
		mGoogleApiClient.disconnect();
	}

	public boolean isConnecting() {
		return mGoogleApiClient.isConnecting();
	}

	public boolean isConnected() {
		return mGoogleApiClient.isConnected();
	}

	public void addGeofences(GeofencingRequest geofencingRequest, PendingIntent pendingIntent, ResultCallback resultCallback) {

	}

	public void registerConnectionCallbacks(GoogleApiClient.ConnectionCallbacks connectionCallbacksListener) {
		mGoogleApiClient.registerConnectionCallbacks(connectionCallbacksListener);
	}

	public void registerConnectionFailedListener(GoogleApiClient.OnConnectionFailedListener connectionFailedListener) {
		mGoogleApiClient.registerConnectionFailedListener(connectionFailedListener);
	}
}