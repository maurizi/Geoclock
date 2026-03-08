package maurizi.geoclock.ui;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.TransitionManager;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.R;
import maurizi.geoclock.utils.ActiveAlarmManager;
import maurizi.geoclock.utils.LocationServiceGoogle;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

public class MapActivity extends AppCompatActivity {

    private static final String ALARM_ID = "ALARM_ID";
    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private static final int REQUEST_BACKGROUND_LOCATION = 2;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 3;

    private static final Gson gson = new Gson();
    private static final int DEFAULT_ZOOM_LEVEL = 14;

    private GoogleMap map = null;
    private LocationServiceGoogle locationService = null;
    private BiMap<UUID, Marker> markers = null;
    private @Nullable Location currentLocation = null;
    private String pendingAlarmId = null;

    private ConstraintLayout mainConstraint;
    private RecyclerView alarmListView;
    private View emptyState;
    private AlarmListAdapter adapter;
    private boolean mapExpanded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        mainConstraint = findViewById(R.id.main_constraint);
        alarmListView = findViewById(R.id.alarm_list);
        emptyState = findViewById(R.id.empty_state);
        FloatingActionButton fab = findViewById(R.id.fab_add);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setIcon(R.drawable.ic_alarm_black_24dp);
        }

        adapter = new AlarmListAdapter(new ArrayList<>(), null, createAdapterCallbacks());
        alarmListView.setLayoutManager(new LinearLayoutManager(this));
        alarmListView.setAdapter(adapter);

        fab.setOnClickListener(v -> showAddDialog());

        View dragHandle = findViewById(R.id.drag_handle);
        dragHandle.setOnClickListener(v -> { if (mapExpanded) collapseMap(); else expandMap(); });

        markers = HashBiMap.create();
        final SupportMapFragment mapFragment = new SupportMapFragment();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.map_container, mapFragment)
                .commit();

        final Intent intent = getIntent();
        if (intent.hasExtra(ALARM_ID)) {
            pendingAlarmId = intent.getStringExtra(ALARM_ID);
        }

        mapFragment.getMapAsync(googleMap -> {
            this.map = googleMap;
            locationService = new LocationServiceGoogle(MapActivity.this);
            map.setOnMarkerClickListener(m -> {
                showEditPopupForMarker(m);
                return true;
            });
            redrawGeoAlarms();
            requestLocationPermissions();
        });
    }

    private AlarmListAdapter.Callbacks createAdapterCallbacks() {
        return new AlarmListAdapter.Callbacks() {
            @Override
            public void onEdit(GeoAlarm alarm) {
                showEditPopup(alarm.id);
                showAlarmOnMap(alarm);
                expandMap();
            }

            @Override
            public void onToggleEnabled(GeoAlarm alarm, boolean enabled) {
                GeoAlarm updated = alarm.withEnabled(enabled);
                GeoAlarm.save(MapActivity.this, updated);
                ActiveAlarmManager aam = new ActiveAlarmManager(MapActivity.this);
                if (locationService != null) {
                    if (enabled) {
                        locationService.addGeofence(updated).addOnFailureListener(e ->
                                Toast.makeText(MapActivity.this, R.string.fail_location, Toast.LENGTH_SHORT).show());
                        aam.addActiveAlarms(ImmutableSet.of(updated.id));
                    } else {
                        locationService.removeGeofence(updated);
                        aam.removeActiveAlarms(ImmutableSet.of(updated.id));
                    }
                }
            }

            @Override
            public void onRenamePlace(GeoAlarm alarm, String newPlace) {
                GeoAlarm updated = alarm.withPlace(newPlace.isEmpty() ? null : newPlace);
                GeoAlarm.save(MapActivity.this, updated);
                onAddGeoAlarmFragmentClose();
            }
        };
    }

    private void requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PERMISSION_GRANTED) {
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
                        != PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                    REQUEST_BACKGROUND_LOCATION);
        } else {
            requestNotificationPermissionIfNeeded();
            requestFullScreenIntentPermissionIfNeeded();
            requestExactAlarmPermissionIfNeeded();
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATION_PERMISSION);
        } else {
            requestFullScreenIntentPermissionIfNeeded();
        }
    }

    private void requestFullScreenIntentPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && !nm.canUseFullScreenIntent()) {
                Toast.makeText(this,
                        "Grant \"Display over other apps\" permission for alarms to ring on locked screen",
                        Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }

    private void requestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                Toast.makeText(this,
                        "Grant \"Alarms & reminders\" permission so alarms fire at the right time",
                        Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PERMISSION_GRANTED) {
                onLocationPermissionGranted();
            } else {
                Toast.makeText(this, R.string.fail_location, Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_BACKGROUND_LOCATION) {
            requestNotificationPermissionIfNeeded();
        }
    }

    @NonNull
    public static Intent getIntent(final @NonNull Context context, final @NonNull GeoAlarm nextAlarm) {
        Intent showAlarmIntent = new Intent(context, MapActivity.class);
        showAlarmIntent.putExtra(MapActivity.ALARM_ID, nextAlarm.id.toString());
        return showAlarmIntent;
    }

    @Override
    protected void onResume() {
        super.onResume();
        redrawGeoAlarms();
    }

    public void onAddGeoAlarmFragmentClose() {
        redrawGeoAlarms();
    }

    private void redrawGeoAlarms() {
        final Collection<GeoAlarm> alarms = GeoAlarm.getGeoAlarms(this);
        final List<GeoAlarm> alarmList = new ArrayList<>(alarms);

        adapter.updateAlarms(alarmList, currentLocation);

        if (alarms.isEmpty()) {
            alarmListView.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            alarmListView.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
        }

        if (map != null) {
            map.clear();
            markers.clear();
            for (final GeoAlarm alarm : alarms) {
                Marker m = map.addMarker(alarm.getMarkerOptions());
                if (m != null) markers.put(alarm.id, m);
                map.addCircle(alarm.getCircleOptions());
            }
        }
    }

    private void centerCamera() {
        if (map != null && locationService != null) {
            locationService.getLastLocation(latLng -> {
                currentLocation = toAndroidLocation(latLng);
                if (latLng != null && map != null) {
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM_LEVEL));
                }
                adapter.updateAlarms(new ArrayList<>(GeoAlarm.getGeoAlarms(this)), currentLocation);
            });
        }
    }

    void showAddDialog() {
        if (locationService == null) {
            Toast.makeText(this, R.string.fail_location, Toast.LENGTH_SHORT).show();
            return;
        }
        locationService.getLastLocation(latLng -> {
            showAddPopup(latLng != null ? latLng : new LatLng(0, 0));
        });
    }

    @Nullable
    private static Location toAndroidLocation(@Nullable LatLng latLng) {
        if (latLng == null) return null;
        Location loc = new Location("");
        loc.setLatitude(latLng.latitude);
        loc.setLongitude(latLng.longitude);
        return loc;
    }

    void showAddPopup(LatLng latLng) {
        Bundle args = new Bundle();
        args.putParcelable(GeoAlarmFragment.INITIAL_LATLNG, latLng);
        float zoom = (map != null) ? map.getCameraPosition().zoom : DEFAULT_ZOOM_LEVEL;
        args.putFloat(GeoAlarmFragment.INITIAL_ZOOM, zoom);
        showPopup(args);
    }

    void showEditPopupForMarker(Marker marker) {
        UUID id = markers.inverse().get(marker);
        if (id != null) showEditPopup(id);
    }

    void showEditPopup(UUID id) {
        GeoAlarm alarm = GeoAlarm.getGeoAlarm(this, id);
        if (alarm != null) {
            Bundle args = new Bundle();
            args.putString(GeoAlarmFragment.EXISTING_ALARM, gson.toJson(alarm, GeoAlarm.class));
            float zoom = (map != null) ? map.getCameraPosition().zoom : DEFAULT_ZOOM_LEVEL;
            args.putFloat(GeoAlarmFragment.INITIAL_ZOOM, zoom);
            showPopup(args);
        }
    }

    void showPopup(Bundle args) {
        GeoAlarmFragment popup = new GeoAlarmFragment();
        popup.setArguments(args);
        popup.show(getSupportFragmentManager(), "AddGeoAlarmFragment");
        popup.setLocationService(locationService);
    }

    void showAlarmOnMap(GeoAlarm alarm) {
        if (map != null) {
            map.animateCamera(CameraUpdateFactory.newLatLng(alarm.location));
        }
    }

    void expandMap() {
        if (mapExpanded) return;
        mapExpanded = true;
        ConstraintSet cs = new ConstraintSet();
        cs.clone(mainConstraint);
        cs.constrainPercentHeight(R.id.map_container, 0.4f);
        TransitionManager.beginDelayedTransition(mainConstraint);
        cs.applyTo(mainConstraint);
    }

    void collapseMap() {
        if (!mapExpanded) return;
        mapExpanded = false;
        ConstraintSet cs = new ConstraintSet();
        cs.clone(mainConstraint);
        cs.constrainHeight(R.id.map_container, dpToPx(160));
        TransitionManager.beginDelayedTransition(mainConstraint);
        cs.applyTo(mainConstraint);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
