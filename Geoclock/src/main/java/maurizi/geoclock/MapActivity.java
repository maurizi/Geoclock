package maurizi.geoclock;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.location.LocationClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.Gson;

import java.util.Collection;


public class MapActivity extends ActionBarActivity
		implements NavigationDrawerFragment.NavigationDrawerCallbacks, AddGeoAlarmFragment.Listener {

	private static final Gson gson = new Gson();

	private static final int DEFAULT_ZOOM_LEVEL = 14;

	/**
	 * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
	 */
	NavigationDrawerFragment navigationDrawerFragment;
	GoogleMap map = null;
	private LocationClient locationClient = null;
	private BiMap<GeoAlarm, Marker> markers = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_map);

		map = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map)).getMap();
		final LocationClientHandler handler = new LocationClientHandler();
		locationClient = new LocationClient(this, handler, handler);
		locationClient.connect();
		markers = HashBiMap.create();

		if (map != null) {
			map.setMyLocationEnabled(true);
			map.setOnMapClickListener(this::showPopup);
			map.setOnMarkerClickListener(this::showPopup);
		}

		navigationDrawerFragment =
				(NavigationDrawerFragment) getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);

		// Set up the drawer.
		navigationDrawerFragment.setUp(R.id.navigation_drawer, (DrawerLayout) findViewById(R.id.drawer_layout));
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

	void restoreActionBar() {
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
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
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		return id == R.id.action_settings || super.onOptionsItemSelected(item);
	}

	@Override
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

	private class LocationClientHandler extends ToastLocationClientHandler {
		public LocationClientHandler() {
			super(MapActivity.this);
		}

		@Override
		public void onConnected(Bundle bundle) {
			super.onConnected(bundle);
			if (map != null) {
				final Location loc = locationClient.getLastLocation();
				if (loc != null) {
					map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(loc.getLatitude(), loc.getLongitude()),
					                                                 DEFAULT_ZOOM_LEVEL));
				}
			}
		}
	}

	boolean showPopup(LatLng latLng) {
		Bundle args = new Bundle();
		args.putParcelable(AddGeoAlarmFragment.INITIAL_LATLNG, latLng);
		args.putFloat(AddGeoAlarmFragment.INITIAL_ZOOM, map.getCameraPosition().zoom);

		return showPopup(args);
	}

	boolean showPopup(Marker marker) {
		final GeoAlarm alarm = markers.inverse().get(marker);
		Bundle args = new Bundle();
		args.putFloat(AddGeoAlarmFragment.INITIAL_ZOOM, map.getCameraPosition().zoom);
		args.putString(AddGeoAlarmFragment.EXISTING_ALARM, gson.toJson(alarm, GeoAlarm.class));

		return showPopup(args);
	}

	boolean showPopup(Bundle args) {
		AddGeoAlarmFragment popup = new AddGeoAlarmFragment();
		popup.setArguments(args);
		popup.show(getSupportFragmentManager(), "AddGeoAlarmFragment");

		return true;
	}
}
