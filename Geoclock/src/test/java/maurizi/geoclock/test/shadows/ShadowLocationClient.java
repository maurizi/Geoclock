package maurizi.geoclock.test.shadows;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;

import com.google.android.gms.common.GooglePlayServicesClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.maps.model.LatLng;
import com.google.common.collect.ImmutableList;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Implements(LocationClient.class)
public class ShadowLocationClient {
	@Getter @Setter static LatLng location = new LatLng(0, 0);
	@Getter @Setter static int transitionType = Geofence.GEOFENCE_TRANSITION_ENTER;
	@Getter @Setter static ImmutableList<Geofence> geofences = ImmutableList.of();

	public void __constructor__(Context c, ConnectionCallbacks cb, OnConnectionFailedListener f) {
		cb.onConnected(new Bundle());
	}

	@Implementation
	public static int getGeofenceTransition(Intent i) {
		return transitionType;
	}

	@Implementation
	public static List<Geofence> getTriggeringGeofences(Intent i) {
		return geofences;
	}
}
