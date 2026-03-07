package maurizi.geoclock.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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

import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.R;
import maurizi.geoclock.utils.LocationServiceGoogle;

import androidx.drawerlayout.widget.DrawerLayout;
import android.view.Menu;
import android.view.MenuItem;

public class MapActivity extends AppCompatActivity
        implements NavigationDrawerFragment.NavigationDrawerCallbacks {

    private static final String ALARM_ID = "ALARM_ID";
    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private static final int REQUEST_BACKGROUND_LOCATION = 2;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 3;

    private static final Gson gson = new Gson();
    private static final int DEFAULT_ZOOM_LEVEL = 14;

    private NavigationDrawerFragment navigationDrawerFragment;
    private GoogleMap map = null;
    private LocationServiceGoogle locationService = null;
    private BiMap<UUID, Marker> markers = null;
    private String pendingAlarmId = null;

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

        final Intent intent = getIntent();
        if (intent.hasExtra(ALARM_ID)) {
            pendingAlarmId = intent.getStringExtra(ALARM_ID);
        }

        markers = HashBiMap.create();
        mapFragment.getMapAsync(googleMap -> {
            this.map = googleMap;
            locationService = new LocationServiceGoogle(MapActivity.this);
            map.setOnMapClickListener(this::showAddPopup);
            map.setOnMarkerClickListener(marker -> {
                showEditPopup(marker);
                return true;
            });
            redrawGeoAlarms();
            requestLocationPermissions();
        });
    }

    private void requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            onLocationPermissionGranted();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
        }
    }

    @SuppressWarnings("MissingPermission")
    private void onLocationPermissionGranted() {
        if (map != null) {
            map.setMyLocationEnabled(true);
        }
        centerCamera();

        if (pendingAlarmId != null) {
            showEditPopup(UUID.fromString(pendingAlarmId));
            pendingAlarmId = null;
        }

        requestBackgroundLocationIfNeeded();
    }

    private void requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                    REQUEST_BACKGROUND_LOCATION);
        } else {
            requestNotificationPermissionIfNeeded();
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATION_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onLocationPermissionGranted();
            } else {
                Toast.makeText(this, R.string.fail_location, Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_BACKGROUND_LOCATION) {
            // Proceed to notifications regardless of whether background location was granted
            requestNotificationPermissionIfNeeded();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        final DrawerLayout drawerLayout =
                (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawerLayout != null) {
            navigationDrawerFragment.setUp(R.id.navigation_drawer, drawerLayout);
        }
    }

    @Override
    public void onNavigationDrawerItemSelected(GeoAlarm alarm) {
        if (map != null) {
            map.animateCamera(CameraUpdateFactory.newLatLng(alarm.location));
        }
    }

    void restoreActionBar() {
        androidx.appcompat.app.ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!navigationDrawerFragment.isDrawerOpen()) {
            getMenuInflater().inflate(R.menu.map, menu);
            restoreActionBar();
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @NonNull
    public static Intent getIntent(final @NonNull Context context, final @NonNull GeoAlarm nextAlarm) {
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
        final Collection<GeoAlarm> alarms = GeoAlarm.getGeoAlarms(this);
        navigationDrawerFragment.setGeoAlarms(alarms);

        if (map != null) {
            map.clear();
            for (final GeoAlarm alarm : alarms) {
                markers.put(alarm.id, map.addMarker(alarm.getMarkerOptions()));
                map.addCircle(alarm.getCircleOptions());
            }
        }
    }

    private void centerCamera() {
        if (map != null && locationService != null) {
            locationService.getLastLocation(loc -> {
                if (loc != null && map != null) {
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(loc, DEFAULT_ZOOM_LEVEL));
                }
            });
        }
    }

    void showAddPopup(LatLng latLng) {
        Bundle args = new Bundle();
        args.putParcelable(GeoAlarmFragment.INITIAL_LATLNG, latLng);
        args.putFloat(GeoAlarmFragment.INITIAL_ZOOM, map.getCameraPosition().zoom);
        showPopup(args);
    }

    void showEditPopup(Marker marker) {
        showEditPopup(markers.inverse().get(marker));
    }

    void showEditPopup(UUID id) {
        GeoAlarm alarm = GeoAlarm.getGeoAlarm(this, id);
        if (alarm != null) {
            Bundle args = new Bundle();
            String alarmJson = gson.toJson(alarm, GeoAlarm.class);
            args.putFloat(GeoAlarmFragment.INITIAL_ZOOM, map.getCameraPosition().zoom);
            args.putString(GeoAlarmFragment.EXISTING_ALARM, alarmJson);
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
