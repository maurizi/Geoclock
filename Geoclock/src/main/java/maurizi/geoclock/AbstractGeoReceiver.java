package maurizi.geoclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.location.LocationClient;

public abstract class AbstractGeoReceiver extends BroadcastReceiver
		implements GooglePlayServicesClient.ConnectionCallbacks, GooglePlayServicesClient.OnConnectionFailedListener {

	protected Context context;
	protected LocationClient client;
	protected Intent intent;

	@Override
	public void onReceive(Context context, Intent intent) {
		if (!LocationClient.hasError(intent)) {
			this.context = context;
			this.client = new LocationClient(context, this, this);
		}
	}

	@Override
	public abstract void onConnected(Bundle bundle);

	@Override
	public void onDisconnected() {
		// TODO: insert logging here
	}

	@Override
	public void onConnectionFailed(ConnectionResult connectionResult) {
		// TODO: insert logging here
	}
}