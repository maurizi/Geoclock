package maurizi.geoclock;

import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.Gson;

import java.util.Collection;

import maurizi.geoclock.services.LocationService;
import maurizi.geoclock.services.LocationServiceGoogle;


public class MapActivity extends AppCompatActivity
		implements NavigationDrawerFragment.NavigationDrawerCallbacks {

	private static final Gson gson = new Gson();
	private static final int DEFAULT_ZOOM_LEVEL = 14;

	private NavigationDrawerFragment navigationDrawerFragment;
	private GoogleMap map = null;
	private LocationService locationService = null;
	private BiMap<GeoAlarm, Marker> markers = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_map);

		final SupportMapFragment mapFragment = new SupportMapFragment();
		navigationDrawerFragment = new NavigationDrawerFragment();
		getSupportFragmentManager().beginTransaction()
		                           .replace(R.id.map, mapFragment)
		                           .replace(R.id.navigation_drawer, navigationDrawerFragment)
		                           .commit();

		markers = HashBiMap.create();
		mapFragment.getMapAsync(map -> {
			this.map = map;
			locationService = new LocationServiceGoogle(MapActivity.this, this::centerCamera);
			locationService.connect();
			map.setMyLocationEnabled(true);
			map.setOnMapClickListener(this::showPopup);
			map.setOnMarkerClickListener(this::showPopup);
			redrawGeoAlarms();
		});
	}

	@Override
	public void onResume() {
		super.onResume();

		// Set up the drawer.
		final DrawerLayout drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		navigationDrawerFragment.setUp(R.id.navigation_drawer, drawerLayout);
	}

	@Override
	public void onNavigationDrawerItemSelected(GeoAlarm alarm) {
		if (map != null) {
			map.animateCamera(CameraUpdateFactory.newLatLng(alarm.location));
			markers.get(alarm).showInfoWindow();
		}
	}

	void restoreActionBar() {
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayShowTitleEnabled(true);
		}
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
		int id = item.getItemId();
		return id == R.id.action_settings || super.onOptionsItemSelected(item);
	}

	public void onAddGeoAlarmFragmentClose(DialogFragment dialog) {
		redrawGeoAlarms();
	}

	private void redrawGeoAlarms() {
		final Collection<GeoAlarm> alarms = GeoAlarm.getGeoAlarms(this);
		navigationDrawerFragment.setGeoAlarms(alarms);

		if (map != null) {
			map.clear();
			for (final GeoAlarm alarm : alarms) {
				markers.put(alarm, map.addMarker(alarm.getMarkerOptions()));
				map.addCircle(alarm.getCircleOptions());
			}
		}
	}

	private void centerCamera() {
		if (map != null) {
			map.moveCamera(CameraUpdateFactory.newLatLngZoom(locationService.getLastLocation(), DEFAULT_ZOOM_LEVEL));
		}
	}

	boolean showPopup(LatLng latLng) {
		Bundle args = new Bundle();
		args.putParcelable(GeoAlarmFragment.INITIAL_LATLNG, latLng);
		args.putFloat(GeoAlarmFragment.INITIAL_ZOOM, map.getCameraPosition().zoom);

		return showPopup(args);
	}

	boolean showPopup(Marker marker) {
		final GeoAlarm alarm = markers.inverse().get(marker);
		Bundle args = new Bundle();
		args.putFloat(GeoAlarmFragment.INITIAL_ZOOM, map.getCameraPosition().zoom);
		args.putString(GeoAlarmFragment.EXISTING_ALARM, gson.toJson(alarm, GeoAlarm.class));

		return showPopup(args);
	}

	boolean showPopup(Bundle args) {
		GeoAlarmFragment popup = new GeoAlarmFragment();
		popup.setArguments(args);
		popup.show(getSupportFragmentManager(), "AddGeoAlarmFragment");
		popup.setLocationService(locationService);
		return true;
	}
}
