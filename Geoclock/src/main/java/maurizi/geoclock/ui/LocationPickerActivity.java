package maurizi.geoclock.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.SeekBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import maurizi.geoclock.R;

public class LocationPickerActivity extends AppCompatActivity {

    public static final String EXTRA_LAT = "lat";
    public static final String EXTRA_LNG = "lng";
    public static final String EXTRA_INITIAL_LAT = "initial_lat";
    public static final String EXTRA_INITIAL_LNG = "initial_lng";
    public static final String EXTRA_INITIAL_RADIUS = "initial_radius";
    public static final String EXTRA_RADIUS = "radius";

    private static final int MIN_RADIUS = 20;
    private static final int MAX_RADIUS = 200;

    private Marker marker;
    private Circle circle;
    private LatLng selectedLatLng;
    private int selectedRadius;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_picker);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> {
            setResult(Activity.RESULT_CANCELED);
            finish();
        });

        double initialLat = getIntent().getDoubleExtra(EXTRA_INITIAL_LAT, 0);
        double initialLng = getIntent().getDoubleExtra(EXTRA_INITIAL_LNG, 0);
        selectedLatLng = new LatLng(initialLat, initialLng);
        selectedRadius = getIntent().getIntExtra(EXTRA_INITIAL_RADIUS, MIN_RADIUS);

        SeekBar radiusBar = findViewById(R.id.radius_bar);
        radiusBar.setMax(MAX_RADIUS - MIN_RADIUS);
        radiusBar.setProgress(selectedRadius - MIN_RADIUS);
        radiusBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                selectedRadius = progress + MIN_RADIUS;
                if (circle != null) circle.setRadius(selectedRadius);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        SupportMapFragment mapFragment = SupportMapFragment.newInstance();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.map_container, mapFragment)
                .commit();
        mapFragment.getMapAsync(map -> setupMap(map, selectedLatLng, selectedRadius));

        findViewById(R.id.confirm_button).setOnClickListener(v -> {
            Intent result = new Intent();
            result.putExtra(EXTRA_LAT, selectedLatLng.latitude);
            result.putExtra(EXTRA_LNG, selectedLatLng.longitude);
            result.putExtra(EXTRA_RADIUS, selectedRadius);
            setResult(Activity.RESULT_OK, result);
            finish();
        });
    }

    private void setupMap(GoogleMap map, LatLng initial, int initialRadius) {
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(initial, 14));
        marker = map.addMarker(new MarkerOptions().position(initial).draggable(true));

        circle = map.addCircle(new CircleOptions()
                .center(initial)
                .radius(initialRadius)
                .fillColor(0x3300C5CD)
                .strokeColor(0xFF00C5CD)
                .strokeWidth(2));

        map.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override public void onMarkerDragStart(Marker m) {}
            @Override public void onMarkerDrag(Marker m) {
                selectedLatLng = m.getPosition();
                if (circle != null) circle.setCenter(selectedLatLng);
            }
            @Override public void onMarkerDragEnd(Marker m) {
                selectedLatLng = m.getPosition();
                if (circle != null) circle.setCenter(selectedLatLng);
            }
        });

        map.setOnMapClickListener(latLng -> {
            selectedLatLng = latLng;
            if (marker != null) marker.setPosition(latLng);
            if (circle != null) circle.setCenter(latLng);
        });
    }
}
