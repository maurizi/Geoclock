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
import com.google.gson.Gson;

import java.util.Map;


public class MapActivity extends FragmentActivity
		implements NavigationDrawerFragment.NavigationDrawerCallbacks,
		           AddGeoAlarmFragment.Listener{

	private final static Gson gson = new Gson();

	public final static int DEFAULT_ZOOM_LEVEL = 14;

	private GoogleMap map = null;
	private LocationClient locationClient = null;

	/**
	 * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
	 */
	private NavigationDrawerFragment navigationDrawerFragment;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_map);

		map = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();
		LocationClientHandler handler = new LocationClientHandler();
		locationClient = new LocationClient(this, handler, handler);

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
	public void onNavigationDrawerItemSelected(int position) {
		// update the main content by replacing fragments
//        FragmentManager fragmentManager = getFragmentManager();
//        fragmentManager.beginTransaction()
//                .replace(R.id.container, PlaceholderFragment.newInstance(position + 1))
//                .commit();
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
		if (map != null) {
			map.clear();
			SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
			for(Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
				String name = entry.getKey();
				GeoAlarm alarm = gson.fromJson((String) entry.getValue(), GeoAlarm.class);
				map.addMarker(alarm.getMarkerOptions());
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
				Location loc = locationClient.getLastLocation();
				if (loc != null) {
					map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(loc.getLatitude(), loc.getLongitude()), DEFAULT_ZOOM_LEVEL));
				}
			}
		}
	}
}
