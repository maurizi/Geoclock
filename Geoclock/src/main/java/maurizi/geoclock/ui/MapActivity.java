package maurizi.geoclock.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
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
import java.util.UUID;

import maurizi.geoclock.Location;
import maurizi.geoclock.utils.Locations;
import maurizi.geoclock.R;
import maurizi.geoclock.utils.LocationServiceGoogle;


public class MapActivity extends AppCompatActivity
		implements NavigationDrawerFragment.NavigationDrawerCallbacks {

	private static final String ALARM_ID = "ALARM_ID";

	private static final Gson gson = new Gson();
	private static final int DEFAULT_ZOOM_LEVEL = 14;

	private NavigationDrawerFragment navigationDrawerFragment;
	private GoogleMap map = null;
	private LocationServiceGoogle locationService = null;
	private BiMap<UUID, Marker> markers = null;

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
			locationService = new LocationServiceGoogle(MapActivity.this);
			locationService.connect(() -> {
				centerCamera();
				final Intent intent = getIntent();

				if (intent.hasExtra(ALARM_ID)) {
					showEditPopup(UUID.fromString(intent.getStringExtra(ALARM_ID)));
				}
			});
			map.setMyLocationEnabled(true);
			map.setOnMapClickListener(this::showAddPopup);
			map.setOnMarkerClickListener(marker -> {
				showEditPopup(marker);
				return true;
			});
			redrawGeoAlarms();
		});
	}

	@Override
	public void onResume() {
		super.onResume();

		// Set up the drawer.
		final DrawerLayout drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		navigationDrawerFragment.setUp(R.id.navigation_drawer, drawerLayout);

		redrawGeoAlarms();
	}

	@Override
	public void onNavigationDrawerItemSelected(Location alarm) {
		if (map != null) {
			map.animateCamera(CameraUpdateFactory.newLatLng(alarm.location));
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

	@NonNull
	public static Intent getIntent(final @NonNull Context context, final @NonNull Location nextAlarm) {
		Intent showAlarmIntent = new Intent(context, MapActivity.class);
		showAlarmIntent.putExtra(MapActivity.ALARM_ID, nextAlarm.id.toString());
		return showAlarmIntent;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		return id == R.id.action_settings || super.onOptionsItemSelected(item);
	}

	public void onAddGeoAlarmFragmentClose() {
		redrawGeoAlarms();
	}

	private void redrawGeoAlarms() {
		final Collection<Location> locations = Locations.get(this);
		navigationDrawerFragment.setGeoAlarms(locations);

		if (map != null) {
			map.clear();
			for (final Location location : locations) {
				markers.put(location.id, map.addMarker(location.getMarkerOptions()));
				map.addCircle(location.getCircleOptions());
			}
		}
	}

	private void centerCamera() {
		if (map != null) {
			LatLng loc = locationService.getLastLocation();
			if (loc != null) {
				map.moveCamera(CameraUpdateFactory.newLatLngZoom(loc, DEFAULT_ZOOM_LEVEL));
			}
		}
	}

	void showAddPopup(LatLng latLng) {
		Bundle args = new Bundle();
		args.putParcelable(GeoAlarmFragment.INITIAL_LATLNG_KEY, latLng);
		args.putFloat(GeoAlarmFragment.INITIAL_ZOOM_KEY, map.getCameraPosition().zoom);

		showPopup(args);
	}

	void showEditPopup(Marker marker) {
		showEditPopup(markers.inverse().get(marker));
	}

	void showEditPopup(UUID id) {
		Location alarm = Locations.get(this, id);
		if (alarm != null) {
			Bundle args = new Bundle();
			String alarmJson = gson.toJson(alarm, Location.class);
			args.putFloat(GeoAlarmFragment.INITIAL_ZOOM_KEY, map.getCameraPosition().zoom);
			args.putString(GeoAlarmFragment.EXISTING_ALARM_KEY, alarmJson);

			showPopup(args);
		}
	}

	void showPopup(Bundle args) {
		GeoAlarmFragment popup = new GeoAlarmFragment();
		popup.setArguments(args);
		popup.show(getSupportFragmentManager(), "AddGeoAlarmFragment");
		popup.setLocationService(locationService);
	}
}
