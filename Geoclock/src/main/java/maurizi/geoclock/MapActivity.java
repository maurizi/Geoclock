package maurizi.geoclock;

import android.app.ActionBar;
import android.app.DialogFragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.widget.DrawerLayout;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.location.LocationClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.gson.Gson;

import java.util.Collection;


public class MapActivity extends FragmentActivity
		implements NavigationDrawerFragment.NavigationDrawerCallbacks,
		           AddGeoAlarmFragment.Listener{

	private static final Gson gson = new Gson();

	public static final int DEFAULT_ZOOM_LEVEL = 14;

	private GoogleMap map = null;
	private LocationClient locationClient = null;
	private BiMap<GeoAlarm, Marker> markers = null;

	/**
	 * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
	 */
	private NavigationDrawerFragment navigationDrawerFragment;

	public static Collection<GeoAlarm> getGeoAlarms(SharedPreferences prefs) {
		return new ImmutableSortedMap.Builder<String, GeoAlarm>(Ordering.natural()).putAll(
			Maps.filterValues(
				Maps.transformValues(prefs.getAll(), new Function<Object, GeoAlarm>() {
					@Override
					public GeoAlarm apply(Object json) {
						try {
							return gson.fromJson((String) json, GeoAlarm.class);
						} catch (Exception _) {
							return null;
						}
					}
				}), new Predicate<GeoAlarm>() {

				@Override
				public boolean apply(GeoAlarm geoAlarm) {
					return geoAlarm != null;
				}
			})
		).build().values();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_map);

		map = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();
		final LocationClientHandler handler = new LocationClientHandler();
		locationClient = new LocationClient(this, handler, handler);
		markers = HashBiMap.create();

		if (map != null) {
			map.setMyLocationEnabled(true);
			map.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
				@Override
				public void onMapClick(LatLng latLng) {
					AddGeoAlarmFragment popup = new AddGeoAlarmFragment();
					Bundle args = new Bundle();
					args.putParcelable(AddGeoAlarmFragment.INITIAL_LATLNG, latLng);
					args.putFloat(AddGeoAlarmFragment.INITIAL_ZOOM, map.getCameraPosition().zoom);
					popup.setArguments(args);
					popup.show(getFragmentManager(), "AddGeoAlarmFragment");
				}
			});
			map.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
				@Override
				public boolean onMarkerClick(Marker marker) {
					final GeoAlarm alarm = markers.inverse().get(marker);
					AddGeoAlarmFragment popup = new AddGeoAlarmFragment();
					Bundle args = new Bundle();
					args.putFloat(AddGeoAlarmFragment.INITIAL_ZOOM, map.getCameraPosition().zoom);
					args.putString(AddGeoAlarmFragment.EXISTING_ALARM, gson.toJson(alarm, GeoAlarm.class));
					popup.setArguments(args);
					popup.show(getFragmentManager(), "AddGeoAlarmFragment");
					return true;
				}
			});
		}

		navigationDrawerFragment = (NavigationDrawerFragment)
				getFragmentManager().findFragmentById(R.id.navigation_drawer);

		// Set up the drawer.
		navigationDrawerFragment.setUp(
				R.id.navigation_drawer,
				(DrawerLayout) findViewById(R.id.drawer_layout));
	}

	@Override
	public void onStart() {
		super.onStart();
		locationClient.connect();
	}

	@Override
	public void onResume() {
		super.onResume();
		redrawGeoAlarms();
	}

	@Override
	public void onNavigationDrawerItemSelected(GeoAlarm alarm) {
		if (map != null) {
			map.animateCamera(CameraUpdateFactory.newLatLng(alarm.location));
			markers.get(alarm).showInfoWindow();
		}
	}

	public void restoreActionBar() {
		ActionBar actionBar = getActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
		actionBar.setDisplayShowTitleEnabled(true);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if (!navigationDrawerFragment.isDrawerOpen()) {
			// Only show items in the action bar relevant to this screen
			// if the drawer is not showing. Otherwise, let the drawer
			// decide what to show in the action bar.
			getMenuInflater().inflate(R.menu.map, menu);
			restoreActionBar();
			return true;
		}
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onAddGeoAlarmFragmentClose(DialogFragment dialog) {
		redrawGeoAlarms();
	}

	private void redrawGeoAlarms() {
		final Collection<GeoAlarm> alarms  = getGeoAlarms(getPreferences(Context.MODE_PRIVATE));
		navigationDrawerFragment.setGeoAlarms(alarms);
		if (map != null) {
			map.clear();
			for(final GeoAlarm alarm : alarms) {
				markers.put(alarm, map.addMarker(alarm.getMarkerOptions()));
				map.addCircle(alarm.getCircleOptions());
			}
		}
	}

	private class LocationClientHandler extends ToastLocationClientHandler {
		public LocationClientHandler() { super(MapActivity.this); }

		@Override
		public void onConnected(Bundle bundle) {
			super.onConnected(bundle);
			if (map != null) {
				final Location loc = locationClient.getLastLocation();
				if (loc != null) {
					map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(loc.getLatitude(), loc.getLongitude()), DEFAULT_ZOOM_LEVEL));
				}
			}
		}
	}
}
