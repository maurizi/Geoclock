package maurizi.geoclock;

import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.List;

import lombok.Value;
import lombok.experimental.Builder;

/**
 * Created by Mike on 5/12/2014.
 */
@Value
@Builder
public class GeoAlarm {
	public final LatLng location;

	@Override
	public String toString() {
		return name;
	}

	public final String name;
	public final double radius;
	public final List<Integer> days;
	public final Integer hour;
	public final Integer minute;

	public MarkerOptions getMarkerOptions() {
		return new MarkerOptions()
				.position(location)
				.title(name);
	}

	public CircleOptions getCircleOptions() {
		return new CircleOptions()
				.center(location)
				.radius(radius)
				.fillColor(R.color.geofence_fill_color);
	}
}