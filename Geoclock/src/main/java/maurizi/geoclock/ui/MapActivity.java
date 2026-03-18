package maurizi.geoclock.ui;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.TransitionManager;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.R;
import maurizi.geoclock.utils.ActiveAlarmManager;
import maurizi.geoclock.utils.LocationServiceGoogle;
import maurizi.geoclock.utils.PermissionHelper;

public class MapActivity extends AppCompatActivity {

  private static final String ALARM_ID = "ALARM_ID";
  private static final int REQUEST_LOCATION_PERMISSION = 1;

  private static final int DEFAULT_ZOOM_LEVEL = 14;
  private static final int MAP_BOUNDS_PADDING = 60;

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
    EdgeToEdge.enable(this);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_map);

    mainConstraint = findViewById(R.id.main_constraint);
    alarmListView = findViewById(R.id.alarm_list);
    emptyState = findViewById(R.id.empty_state);
    FloatingActionButton fab = findViewById(R.id.fab_add);

    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayShowHomeEnabled(true);
      // Tint appbar icon white so it's visible on the dark action bar
      Drawable icon = ContextCompat.getDrawable(this, R.drawable.ic_alarm_black_24dp);
      if (icon != null) {
        icon = DrawableCompat.wrap(icon).mutate();
        DrawableCompat.setTint(icon, 0xFFFFFFFF);
        int insetPx = (int) (8 * getResources().getDisplayMetrics().density);
        getSupportActionBar().setIcon(new InsetDrawable(icon, 0, 0, insetPx, 0));
      }
    }

    adapter = new AlarmListAdapter(new ArrayList<>(), null, createAdapterCallbacks());
    alarmListView.setLayoutManager(new LinearLayoutManager(this));
    alarmListView.setAdapter(adapter);

    fab.setOnClickListener(v -> showAddDialog());

    View dragHandle = findViewById(R.id.drag_handle);
    dragHandle.setOnClickListener(
        v -> {
          if (mapExpanded) collapseMap();
          else expandMap();
        });

    markers = HashBiMap.create();
    final SupportMapFragment mapFragment = new SupportMapFragment();
    getSupportFragmentManager()
        .beginTransaction()
        .replace(R.id.map_container, mapFragment)
        .commit();

    final Intent intent = getIntent();
    if (intent.hasExtra(ALARM_ID)) {
      pendingAlarmId = intent.getStringExtra(ALARM_ID);
    }

    mapFragment.getMapAsync(
        googleMap -> {
          this.map = googleMap;
          locationService = new LocationServiceGoogle(MapActivity.this);
          map.getUiSettings().setAllGesturesEnabled(false);
          map.getUiSettings().setMyLocationButtonEnabled(false);
          map.setOnMarkerClickListener(marker -> true);
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
        Runnable doToggle =
            () -> {
              GeoAlarm updated = alarm.withEnabled(enabled);
              GeoAlarm.save(MapActivity.this, updated);
              ActiveAlarmManager aam = new ActiveAlarmManager(MapActivity.this);
              if (locationService != null) {
                if (enabled) {
                  locationService
                      .addGeofence(updated)
                      .addOnFailureListener(
                          e ->
                              Toast.makeText(
                                      MapActivity.this, R.string.fail_location, Toast.LENGTH_SHORT)
                                  .show());
                  aam.addActiveAlarms(ImmutableSet.of(updated.id));
                } else {
                  locationService.removeGeofence(updated);
                  aam.removeActiveAlarms(ImmutableSet.of(updated.id));
                }
              }
            };

        // Request permissions just-in-time when enabling an alarm
        if (enabled && !PermissionHelper.hasAllAlarmPermissions(MapActivity.this)) {
          PermissionHelper.requestAlarmPermissions(MapActivity.this, doToggle);
        } else {
          doToggle.run();
        }
      }
    };
  }

  private void requestLocationPermissions() {
    // Only request foreground location on launch — other permissions are just-in-time
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        == PERMISSION_GRANTED) {
      onLocationPermissionGranted();
    } else {
      ActivityCompat.requestPermissions(
          this,
          new String[] {Manifest.permission.ACCESS_FINE_LOCATION},
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
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == REQUEST_LOCATION_PERMISSION) {
      if (grantResults.length > 0 && grantResults[0] == PERMISSION_GRANTED) {
        onLocationPermissionGranted();
      } else {
        Toast.makeText(this, R.string.fail_location, Toast.LENGTH_LONG).show();
      }
    }
  }

  @NonNull
  public static Intent getIntent(
      final @NonNull Context context, final @NonNull GeoAlarm nextAlarm) {
    Intent showAlarmIntent = new Intent(context, MapActivity.class);
    showAlarmIntent.putExtra(MapActivity.ALARM_ID, nextAlarm.id.toString());
    return showAlarmIntent;
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (locationService != null
        && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PERMISSION_GRANTED) {
      centerCamera();
      activateAlarmsInsideGeofence();
    } else {
      redrawGeoAlarms();
    }
  }

  /**
   * When returning from the permission-grant flow the geofence may not have been registered yet
   * (background-location wasn't granted when completeSave ran). Re-register geofences for enabled
   * alarms and, if we're already inside the radius, activate them so the notification appears
   * immediately.
   */
  private void activateAlarmsInsideGeofence() {
    if (locationService == null) return;
    locationService.getLastLocation(
        loc -> {
          if (loc == null) return;
          Collection<GeoAlarm> alarms = GeoAlarm.getGeoAlarms(this);
          ImmutableSet.Builder<UUID> toActivate = ImmutableSet.builder();
          for (GeoAlarm alarm : alarms) {
            if (!alarm.enabled) continue;
            locationService.addGeofence(alarm);
            if (isInsideGeofence(loc, alarm)) {
              toActivate.add(alarm.id);
            }
          }
          ImmutableSet<UUID> ids = toActivate.build();
          if (!ids.isEmpty()) {
            new ActiveAlarmManager(this).addActiveAlarms(ids);
          }
        });
  }

  static boolean isInsideGeofence(LatLng location, GeoAlarm alarm) {
    float[] results = new float[1];
    android.location.Location.distanceBetween(
        location.latitude,
        location.longitude,
        alarm.location.latitude,
        alarm.location.longitude,
        results);
    return results[0] <= alarm.radius;
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
      LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
      boolean hasMarkers = false;
      for (final GeoAlarm alarm : alarms) {
        Marker m = map.addMarker(alarm.getMarkerOptions());
        if (m != null) {
          markers.put(alarm.id, m);
          // Include points at the edges of the geofence circle so bounds
          // encompass the full radius, not just the center point
          double latOffset = alarm.radius / 111_320.0;
          double lngOffset =
              alarm.radius / (111_320.0 * Math.cos(Math.toRadians(alarm.location.latitude)));
          boundsBuilder.include(
              new LatLng(alarm.location.latitude + latOffset, alarm.location.longitude));
          boundsBuilder.include(
              new LatLng(alarm.location.latitude - latOffset, alarm.location.longitude));
          boundsBuilder.include(
              new LatLng(alarm.location.latitude, alarm.location.longitude + lngOffset));
          boundsBuilder.include(
              new LatLng(alarm.location.latitude, alarm.location.longitude - lngOffset));
          hasMarkers = true;
        }
        map.addCircle(alarm.getCircleOptions());
      }
      // Auto-fit map to show all alarms
      if (hasMarkers) {
        try {
          map.moveCamera(
              CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), MAP_BOUNDS_PADDING));
        } catch (Exception ignored) {
          // Map may not be laid out yet
        }
      }
    }
  }

  private void centerCamera() {
    if (map != null && locationService != null) {
      locationService.getLastLocation(
          latLng -> {
            currentLocation = toAndroidLocation(latLng);
            Collection<GeoAlarm> alarms = GeoAlarm.getGeoAlarms(this);
            if (alarms.isEmpty() && latLng != null && map != null) {
              map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 10));
            }
            redrawGeoAlarms();
          });
    }
  }

  void showAddDialog() {
    if (locationService == null) {
      Toast.makeText(this, R.string.fail_location, Toast.LENGTH_SHORT).show();
      return;
    }
    locationService.getLastLocation(
        latLng -> {
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

  void showEditPopup(UUID id) {
    GeoAlarm alarm = GeoAlarm.getGeoAlarm(this, id);
    if (alarm != null) {
      Bundle args = new Bundle();
      args.putString(GeoAlarmFragment.EXISTING_ALARM, alarm.toJson());
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
    if (map == null) return;
    double latOffset = alarm.radius / 111_320.0;
    double lngOffset =
        alarm.radius / (111_320.0 * Math.cos(Math.toRadians(alarm.location.latitude)));
    LatLngBounds bounds =
        new LatLngBounds.Builder()
            .include(new LatLng(alarm.location.latitude + latOffset, alarm.location.longitude))
            .include(new LatLng(alarm.location.latitude - latOffset, alarm.location.longitude))
            .include(new LatLng(alarm.location.latitude, alarm.location.longitude + lngOffset))
            .include(new LatLng(alarm.location.latitude, alarm.location.longitude - lngOffset))
            .build();
    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, MAP_BOUNDS_PADDING));
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
