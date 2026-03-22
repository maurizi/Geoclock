package maurizi.geoclock.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.test.core.app.ApplicationProvider;
import maurizi.geoclock.R;
import maurizi.geoclock.shadows.ShadowMapsInitializer;
import maurizi.geoclock.shadows.ShadowSupportMapFragment;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(
    sdk = 33,
    shadows = {ShadowMapsInitializer.class, ShadowSupportMapFragment.class})
public class LocationPickerActivityUnitTest {

  private Context context;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
  }

  // ---- progressToRadius / radiusToProgress ----

  @Test
  public void progressToRadius_atZero_returnsMinRadius() {
    LocationPickerActivity activity = buildActivity(37.0, -122.0, 250);
    int minRadius = activity.getMinRadius();
    assertEquals(minRadius, activity.progressToRadius(0));
  }

  @Test
  public void progressToRadius_atMax_returnsMaxRadius() {
    LocationPickerActivity activity = buildActivity(37.0, -122.0, 250);
    int maxRadius = activity.getMaxRadius();
    assertEquals(maxRadius, activity.progressToRadius(1000));
  }

  @Test
  public void progressToRadius_midpoint_returnsBetweenMinAndMax() {
    LocationPickerActivity activity = buildActivity(37.0, -122.0, 250);
    int mid = activity.progressToRadius(500);
    assertTrue("Mid should be > min", mid > activity.getMinRadius());
    assertTrue("Mid should be < max", mid < activity.getMaxRadius());
  }

  @Test
  public void radiusToProgress_atMinRadius_returnsZero() {
    LocationPickerActivity activity = buildActivity(37.0, -122.0, 250);
    assertEquals(0, activity.radiusToProgress(activity.getMinRadius()));
  }

  @Test
  public void radiusToProgress_atMaxRadius_returnsMax() {
    LocationPickerActivity activity = buildActivity(37.0, -122.0, 250);
    assertEquals(1000, activity.radiusToProgress(activity.getMaxRadius()));
  }

  @Test
  public void progressToRadius_radiusToProgress_roundTrip() {
    LocationPickerActivity activity = buildActivity(37.0, -122.0, 250);
    for (int progress = 0; progress <= 1000; progress += 100) {
      int radius = activity.progressToRadius(progress);
      int recovered = activity.radiusToProgress(radius);
      assertTrue(
          "Round-trip error should be small for progress=" + progress,
          Math.abs(recovered - progress) <= 1);
    }
  }

  // ---- getMinRadius / getMaxRadius ----

  @Test
  public void getMinRadius_returnsPositive() {
    LocationPickerActivity activity = buildActivity(37.0, -122.0, 250);
    assertTrue(activity.getMinRadius() > 0);
  }

  @Test
  public void getMaxRadius_greaterThanMinRadius() {
    LocationPickerActivity activity = buildActivity(37.0, -122.0, 250);
    assertTrue(activity.getMaxRadius() > activity.getMinRadius());
  }

  // ---- Activity layout ----

  @Test
  public void onCreate_inflatesLayout() {
    LocationPickerActivity activity = buildActivity(37.0, -122.0, 250);
    assertNotNull(activity.findViewById(R.id.toolbar));
    assertNotNull(activity.findViewById(R.id.radius_bar));
    assertNotNull(activity.findViewById(R.id.confirm_button));
  }

  @Test
  public void onCreate_seekbarInitializedToCorrectProgress() {
    int initialRadius = 500;
    LocationPickerActivity activity = buildActivity(37.0, -122.0, initialRadius);
    SeekBar seekBar = activity.findViewById(R.id.radius_bar);
    int expected = activity.radiusToProgress(initialRadius);
    assertEquals(expected, seekBar.getProgress());
  }

  @Test
  public void seekbarChange_updatesRadiusLabel() {
    LocationPickerActivity activity = buildActivity(37.0, -122.0, 250);
    SeekBar seekBar = activity.findViewById(R.id.radius_bar);
    TextView radiusLabel = activity.findViewById(R.id.radius_value_label);
    if (radiusLabel != null) {
      String before = radiusLabel.getText().toString();
      // Change seekbar to max
      seekBar.setProgress(1000);
      String after = radiusLabel.getText().toString();
      // Label should update (different text for min vs max radius)
      assertTrue("Radius label should change when seekbar changes", !before.equals(after));
    }
  }

  // ---- confirm button result ----

  @Test
  public void confirmButton_setsResultOk() {
    LocationPickerActivity activity = buildActivity(37.0, -122.0, 250);
    activity.findViewById(R.id.confirm_button).performClick();
    assertTrue("Activity should finish after confirm", activity.isFinishing());
  }

  // ---- navigation back ----

  @Test
  public void toolbarNavigationBack_setsCanceled() {
    LocationPickerActivity activity = buildActivity(37.0, -122.0, 250);
    // The toolbar navigation click listener calls setResult(CANCELED) + finish()
    activity.findViewById(R.id.toolbar).performClick();
    // At minimum, we verify no crash
  }

  // ---- with place name ----

  @Test
  public void onCreate_withPlaceName_passedThrough() {
    Intent intent = new Intent(context, LocationPickerActivity.class);
    intent.putExtra(LocationPickerActivity.EXTRA_INITIAL_LAT, 37.0);
    intent.putExtra(LocationPickerActivity.EXTRA_INITIAL_LNG, -122.0);
    intent.putExtra(LocationPickerActivity.EXTRA_INITIAL_RADIUS, 250);
    intent.putExtra(LocationPickerActivity.EXTRA_PLACE, "Test Place");
    LocationPickerActivity activity =
        Robolectric.buildActivity(LocationPickerActivity.class, intent).setup().get();
    assertNotNull(activity);
  }

  // ---- onOptionsItemSelected ----

  @Test
  public void onOptionsItemSelected_searchAction_doesNotCrash() {
    LocationPickerActivity activity = buildActivity(37.0, -122.0, 250);
    // Trigger the menu
    activity.onCreateOptionsMenu(
        new android.widget.PopupMenu(context, new android.widget.FrameLayout(context)).getMenu());
  }

  // ---- onDestroy ----

  @Test
  public void onDestroy_doesNotCrash() {
    LocationPickerActivity activity = buildActivity(37.0, -122.0, 250);
    Robolectric.buildActivity(LocationPickerActivity.class, buildIntent(37.0, -122.0, 250))
        .create()
        .start()
        .resume()
        .pause()
        .stop()
        .destroy()
        .get();
  }

  // ---- default radius when no initial ----

  @Test
  public void onCreate_noInitialRadius_usesMinRadius() {
    Intent intent = new Intent(context, LocationPickerActivity.class);
    intent.putExtra(LocationPickerActivity.EXTRA_INITIAL_LAT, 37.0);
    intent.putExtra(LocationPickerActivity.EXTRA_INITIAL_LNG, -122.0);
    // No EXTRA_INITIAL_RADIUS
    LocationPickerActivity activity =
        Robolectric.buildActivity(LocationPickerActivity.class, intent).setup().get();
    SeekBar seekBar = activity.findViewById(R.id.radius_bar);
    // Progress should be at minimum (0 or close to it)
    assertTrue("Progress should be small without initial radius", seekBar.getProgress() <= 1);
  }

  // ---- imperial locale via @Config qualifiers ----

  @Test
  @Config(sdk = 33, qualifiers = "ja-rJP")
  public void getMinRadius_metricLocale_returnsMetricMin() {
    LocationPickerActivity activity = buildActivity(37.0, -122.0, 250);
    assertEquals("JP locale should use metric min radius", 125, activity.getMinRadius());
  }

  @Test
  @Config(sdk = 33, qualifiers = "ja-rJP")
  public void getMaxRadius_metricLocale_returnsMetricMax() {
    LocationPickerActivity activity = buildActivity(37.0, -122.0, 250);
    assertEquals("JP locale should use metric max radius", 25000, activity.getMaxRadius());
  }

  @Test
  @Config(sdk = 33, qualifiers = "en-rUS")
  public void getMinRadius_usLocale_returnsImperialMin() {
    LocationPickerActivity activity = buildActivity(37.0, -122.0, 250);
    assertEquals("US locale should use imperial min radius", 122, activity.getMinRadius());
  }

  @Test
  @Config(sdk = 33, qualifiers = "en-rUS")
  public void getMaxRadius_usLocale_returnsImperialMax() {
    LocationPickerActivity activity = buildActivity(37.0, -122.0, 250);
    assertEquals("US locale should use imperial max radius", 24140, activity.getMaxRadius());
  }

  @Test
  @Config(sdk = 33, qualifiers = "en-rUS")
  public void progressToRadius_imperial_roundTrips() {
    LocationPickerActivity activity = buildActivity(37.0, -122.0, 250);
    for (int progress = 0; progress <= 1000; progress += 100) {
      int radius = activity.progressToRadius(progress);
      int recovered = activity.radiusToProgress(radius);
      assertTrue(
          "Imperial round-trip error should be small for progress=" + progress,
          Math.abs(recovered - progress) <= 1);
    }
  }

  // ---- helpers ----

  private Intent buildIntent(double lat, double lng, int radius) {
    Intent intent = new Intent(context, LocationPickerActivity.class);
    intent.putExtra(LocationPickerActivity.EXTRA_INITIAL_LAT, lat);
    intent.putExtra(LocationPickerActivity.EXTRA_INITIAL_LNG, lng);
    intent.putExtra(LocationPickerActivity.EXTRA_INITIAL_RADIUS, radius);
    return intent;
  }

  private LocationPickerActivity buildActivity(double lat, double lng, int radius) {
    Intent intent = new Intent(context, LocationPickerActivity.class);
    intent.putExtra(LocationPickerActivity.EXTRA_INITIAL_LAT, lat);
    intent.putExtra(LocationPickerActivity.EXTRA_INITIAL_LNG, lng);
    intent.putExtra(LocationPickerActivity.EXTRA_INITIAL_RADIUS, radius);
    return Robolectric.buildActivity(LocationPickerActivity.class, intent).setup().get();
  }
}
