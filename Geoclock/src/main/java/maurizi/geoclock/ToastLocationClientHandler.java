package maurizi.geoclock;

import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;


public class ToastLocationClientHandler
		implements GooglePlayServicesClient.ConnectionCallbacks, GooglePlayServicesClient.OnConnectionFailedListener {
	private final Context context;

	public ToastLocationClientHandler(Context context) {
		this.context = context;
	}

	@Override
	public void onConnected(Bundle bundle) {
	}

	@Override
	public void onDisconnected() {
		Toast.makeText(context, R.string.lost_location, Toast.LENGTH_SHORT).show();
	}

	@Override
	public void onConnectionFailed(ConnectionResult connectionResult) {
		Toast.makeText(context, R.string.fail_location, Toast.LENGTH_SHORT).show();
	}
}