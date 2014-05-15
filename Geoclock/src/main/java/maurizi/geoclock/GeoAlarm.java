package maurizi.geoclock;

import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by Mike on 5/12/2014.
 */
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

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		GeoAlarm geoAlarm = (GeoAlarm) o;

		if (Double.compare(geoAlarm.radius, radius) != 0) return false;
		if (days != null ? !days.equals(geoAlarm.days) : geoAlarm.days != null) return false;
		if (!hour.equals(geoAlarm.hour)) return false;
		if (!location.equals(geoAlarm.location)) return false;
		if (!minute.equals(geoAlarm.minute)) return false;
		if (!name.equals(geoAlarm.name)) return false;

		return true;
	}

	@Override
	public int hashCode() {
		int result;
		long temp;
		result = location.hashCode();
		result = 31 * result + name.hashCode();
		temp = Double.doubleToLongBits(radius);
		result = 31 * result + (int) (temp ^ (temp >>> 32));
		result = 31 * result + (days != null ? days.hashCode() : 0);
		result = 31 * result + hour.hashCode();
		result = 31 * result + minute.hashCode();
		return result;
	}

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

	private GeoAlarm(LatLng location, String name, double radius, List<Integer> days, Integer hour, Integer minute) {
		this.location = location;
		this.name = name;
		this.radius = radius;
		this.days = days;
		this.hour = hour;
		this.minute = minute;
	}

	public static class Builder {
		private LatLng location;
		private String name;
		private double radius;
		private List<Integer> days;
		private Integer hour;
		private Integer minute;

		public Builder location(LatLng location) {
			this.location = location;
			return this;
		}

		public Builder name(String name) {
			this.name = name;
			return this;
		}

		public Builder radius(double radius) {
			this.radius = radius;
			return this;
		}

		public Builder days(ArrayList<Integer> days) {
			this.days = Collections.unmodifiableList(days);
			return this;
		}

		public Builder hour(Integer hour) {
			this.hour = hour;
			return this;
		}

		public Builder minute(Integer minute) {
			this.minute = minute;
			return this;
		}

		public GeoAlarm create() {
			return new GeoAlarm(location, name, radius, days, hour, minute);
		}
	}
}