package maurizi.geoclock.ui;

import android.app.Activity;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import maurizi.geoclock.R;
import maurizi.geoclock.utils.AddressFormatter;
import maurizi.geoclock.utils.DistanceUtils;

public class LocationPickerActivity extends AppCompatActivity {

  public static final String EXTRA_LAT = "lat";
  public static final String EXTRA_LNG = "lng";
  public static final String EXTRA_INITIAL_LAT = "initial_lat";
  public static final String EXTRA_INITIAL_LNG = "initial_lng";
  public static final String EXTRA_INITIAL_RADIUS = "initial_radius";
  public static final String EXTRA_RADIUS = "radius";
  public static final String EXTRA_PLACE = "place";

  private static final int MIN_RADIUS_METRIC = 125; // 250m wide
  private static final int MIN_RADIUS_IMPERIAL = 122; // 800ft wide
  private static final int MAX_RADIUS_METRIC = 25000; // 50km wide
  private static final int MAX_RADIUS_IMPERIAL = 24140; // 30mi wide
  private static final int SEEKBAR_MAX = 1000;

  private int getMinRadius() {
    return DistanceUtils.useImperial(this) ? MIN_RADIUS_IMPERIAL : MIN_RADIUS_METRIC;
  }

  private int getMaxRadius() {
    return DistanceUtils.useImperial(this) ? MAX_RADIUS_IMPERIAL : MAX_RADIUS_METRIC;
  }

  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  private GoogleMap googleMap;
  private Marker marker;
  private Circle circle;
  private LatLng selectedLatLng;
  private int selectedRadius;
  @Nullable private String placeName;

  private final ActivityResultLauncher<Intent> autocompleteLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
              Place place = Autocomplete.getPlaceFromIntent(result.getData());
              if (place.getLocation() != null) {
                moveToLocation(place.getLocation());
              }
              placeName = place.getDisplayName();
            }
          });

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    EdgeToEdge.enable(this);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_location_picker);

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }
    toolbar.setNavigationOnClickListener(
        v -> {
          setResult(Activity.RESULT_CANCELED);
          finish();
        });

    double initialLat = getIntent().getDoubleExtra(EXTRA_INITIAL_LAT, 0);
    double initialLng = getIntent().getDoubleExtra(EXTRA_INITIAL_LNG, 0);
    selectedLatLng = new LatLng(initialLat, initialLng);
    selectedRadius = getIntent().getIntExtra(EXTRA_INITIAL_RADIUS, getMinRadius());
    placeName = getIntent().getStringExtra(EXTRA_PLACE);

    TextView radiusValueLabel = findViewById(R.id.radius_value_label);
    SeekBar radiusBar = findViewById(R.id.radius_bar);
    radiusBar.setMax(SEEKBAR_MAX);
    radiusBar.setProgress(radiusToProgress(selectedRadius));
    if (radiusValueLabel != null) {
      radiusValueLabel.setText(DistanceUtils.formatDiameter(this, selectedRadius));
    }
    radiusBar.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          @Override
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            selectedRadius = progressToRadius(progress);
            if (circle != null) circle.setRadius(selectedRadius);
            if (radiusValueLabel != null) {
              radiusValueLabel.setText(
                  DistanceUtils.formatDiameter(LocationPickerActivity.this, selectedRadius));
            }
          }

          @Override
          public void onStartTrackingTouch(SeekBar seekBar) {}

          @Override
          public void onStopTrackingTouch(SeekBar seekBar) {
            fitCameraToCircle();
          }
        });

    SupportMapFragment mapFragment = SupportMapFragment.newInstance();
    getSupportFragmentManager()
        .beginTransaction()
        .replace(R.id.map_container, mapFragment)
        .commit();
    mapFragment.getMapAsync(map -> setupMap(map, selectedLatLng, selectedRadius));

    findViewById(R.id.confirm_button)
        .setOnClickListener(
            v -> {
              Intent result = new Intent();
              result.putExtra(EXTRA_LAT, selectedLatLng.latitude);
              result.putExtra(EXTRA_LNG, selectedLatLng.longitude);
              result.putExtra(EXTRA_RADIUS, selectedRadius);
              if (placeName != null && !placeName.isEmpty()) {
                result.putExtra(EXTRA_PLACE, placeName);
              }
              setResult(Activity.RESULT_OK, result);
              finish();
            });
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.location_picker, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    if (item.getItemId() == R.id.action_search) {
      launchAutocomplete();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  private int progressToRadius(int progress) {
    int min = getMinRadius();
    double ratio = (double) progress / SEEKBAR_MAX;
    return (int) (min * Math.pow((double) getMaxRadius() / min, ratio));
  }

  private int radiusToProgress(int radius) {
    int min = getMinRadius();
    double ratio = Math.log((double) radius / min) / Math.log((double) getMaxRadius() / min);
    return (int) Math.round(ratio * SEEKBAR_MAX);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    executor.shutdownNow();
  }

  private void launchAutocomplete() {
    Intent intent =
        new Autocomplete.IntentBuilder(
                AutocompleteActivityMode.OVERLAY,
                Arrays.asList(Place.Field.LOCATION, Place.Field.DISPLAY_NAME))
            .build(this);
    autocompleteLauncher.launch(intent);
  }

  private void fitCameraToCircle() {
    fitCameraToCircle(true);
  }

  private void fitCameraToCircle(boolean animate) {
    if (googleMap == null || selectedLatLng == null) return;
    double latOffset = selectedRadius / 111_320.0;
    double lngOffset =
        selectedRadius / (111_320.0 * Math.cos(Math.toRadians(selectedLatLng.latitude)));
    LatLngBounds bounds =
        new LatLngBounds.Builder()
            .include(new LatLng(selectedLatLng.latitude + latOffset, selectedLatLng.longitude))
            .include(new LatLng(selectedLatLng.latitude - latOffset, selectedLatLng.longitude))
            .include(new LatLng(selectedLatLng.latitude, selectedLatLng.longitude + lngOffset))
            .include(new LatLng(selectedLatLng.latitude, selectedLatLng.longitude - lngOffset))
            .build();
    // Post to ensure map view has been laid out (newLatLngBounds requires non-zero size)
    View mapContainer = findViewById(R.id.map_container);
    if (mapContainer == null) return;
    mapContainer.post(
        () -> {
          if (googleMap == null) return;
          if (animate) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 64));
          } else {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 64));
          }
        });
  }

  private void moveToLocation(LatLng latLng) {
    selectedLatLng = latLng;
    if (marker != null) marker.setPosition(latLng);
    if (circle != null) circle.setCenter(latLng);
    fitCameraToCircle();
  }

  private void reverseGeocodePlace(LatLng latLng) {
    if (!Geocoder.isPresent()) return;
    Handler handler = new Handler(Looper.getMainLooper());
    executor.execute(
        () -> {
          try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses =
                geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
              Address addr = addresses.get(0);
              String place = AddressFormatter.shortAddress(addr);
              if (place != null) {
                handler.post(() -> placeName = place);
              }
            }
          } catch (IOException e) {
            // best-effort
          }
        });
  }

  private void setupMap(GoogleMap map, LatLng initial, int initialRadius) {
    googleMap = map;
    map.getUiSettings().setZoomControlsEnabled(true);
    map.getUiSettings().setMapToolbarEnabled(false);
    try {
      map.setMyLocationEnabled(true);
    } catch (SecurityException e) {
      // Location permission not granted — skip my-location layer
    }
    marker = map.addMarker(new MarkerOptions().position(initial).draggable(true));

    circle =
        map.addCircle(
            new CircleOptions()
                .center(initial)
                .radius(initialRadius)
                .fillColor(0x3300C5CD)
                .strokeColor(0xFF00C5CD)
                .strokeWidth(2));

    fitCameraToCircle(false);

    map.setOnMarkerDragListener(
        new GoogleMap.OnMarkerDragListener() {
          @Override
          public void onMarkerDragStart(Marker m) {}

          @Override
          public void onMarkerDrag(Marker m) {
            selectedLatLng = m.getPosition();
            if (circle != null) circle.setCenter(selectedLatLng);
          }

          @Override
          public void onMarkerDragEnd(Marker m) {
            selectedLatLng = m.getPosition();
            if (circle != null) circle.setCenter(selectedLatLng);
            reverseGeocodePlace(selectedLatLng);
          }
        });

    map.setOnMapClickListener(
        latLng -> {
          selectedLatLng = latLng;
          if (marker != null) marker.setPosition(latLng);
          if (circle != null) circle.setCenter(latLng);
          reverseGeocodePlace(latLng);
        });
  }
}
