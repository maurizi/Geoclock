package maurizi.geoclock.utils;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.background.GeofenceReceiver;

public class LocationServiceGoogle {

    public interface LocationCallback {
        void onLocation(@Nullable LatLng location);
    }

    @NonNull private final Context context;
    @NonNull private final GeofencingClient geofencingClient;

    public LocationServiceGoogle(@NonNull Context context) {
        this(context, LocationServices.getGeofencingClient(context));
    }

    @VisibleForTesting
    LocationServiceGoogle(@NonNull Context context, @NonNull GeofencingClient geofencingClient) {
        this.context = context;
        this.geofencingClient = geofencingClient;
    }

    public Task<Void> addGeofence(@NonNull GeoAlarm alarm) {
        return addGeofences(Collections.singletonList(alarm));
    }

    @SuppressLint("MissingPermission")
    public Task<Void> addGeofences(@NonNull Collection<GeoAlarm> alarms) {
        if (alarms.isEmpty()) {
            return com.google.android.gms.tasks.Tasks.forResult(null);
        }

        List<Geofence> geofences = new ArrayList<>();
        for (GeoAlarm alarm : alarms) {
            geofences.add(buildGeofence(alarm));
        }

        GeofencingRequest request = new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(geofences)
                .build();

        return geofencingClient.addGeofences(request, getGeofencePendingIntent());
    }

    public Task<Void> removeGeofence(@NonNull GeoAlarm alarm) {
        return geofencingClient.removeGeofences(
                Collections.singletonList(alarm.id.toString()));
    }

    @SuppressLint("MissingPermission")
    public void getLastLocation(@NonNull LocationCallback callback) {
        LocationServices.getFusedLocationProviderClient(context)
                .getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        callback.onLocation(new LatLng(location.getLatitude(), location.getLongitude()));
                    } else {
                        callback.onLocation(null);
                    }
                })
                .addOnFailureListener(e -> callback.onLocation(null));
    }

    private Geofence buildGeofence(GeoAlarm alarm) {
        float radius = Math.max(1f, alarm.radius); // setCircularRegion throws IllegalArgumentException if radius <= 0
        return new Geofence.Builder()
                .setRequestId(alarm.id.toString())
                .setCircularRegion(alarm.location.latitude, alarm.location.longitude, radius)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                .build();
    }

    private PendingIntent getGeofencePendingIntent() {
        Intent intent = new Intent(context, GeofenceReceiver.class);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
    }
}
