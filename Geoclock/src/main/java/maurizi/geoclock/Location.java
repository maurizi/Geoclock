package maurizi.geoclock;

import android.content.Context;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import org.threeten.bp.Duration;

import java.util.List;
import java.util.UUID;

import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Builder;
import lombok.experimental.Wither;
import maurizi.geoclock.utils.Alarms;

import static com.google.common.collect.Collections2.filter;
import static com.google.common.collect.Lists.newArrayList;

@Value
@Builder
@Wither
public class Location {
	private static final String ALARM_PREFS = "alarms";

	@NonNull public final UUID id;
	@NonNull public final String name;
	public final int radius;
	@NonNull public final LatLng location;

	@Override
	public String toString() {
		return name;
	}

	public List<Alarm> getAlarms(Context context) {
		return newArrayList(filter(Alarms.get(context), alarm -> id.equals(alarm.parent)));
	}

	public MarkerOptions getMarkerOptions() {
		return new MarkerOptions().position(location).title(name);
	}

	public Geofence getGeofence() {
		return new Geofence.Builder().setCircularRegion(location.latitude, location.longitude, radius)
		                             .setRequestId(name)
		                             .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER |
		                                                 Geofence.GEOFENCE_TRANSITION_EXIT)
		                             .setExpirationDuration(Duration.ofDays(1).toMillis())
		                             .build();
	}

	public CircleOptions getCircleOptions() {
		return new CircleOptions().center(location).radius(radius).fillColor(R.color.geofence_fill_color);
	}
}
