package maurizi.geoclock.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import androidx.test.core.app.ApplicationProvider;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class DistanceUtilsTest {

  private Context metricContext() {
    Context base = ApplicationProvider.getApplicationContext();
    Configuration config = new Configuration(base.getResources().getConfiguration());
    config.setLocale(Locale.JAPAN);
    return base.createConfigurationContext(config);
  }

  private Context imperialContext() {
    Context base = ApplicationProvider.getApplicationContext();
    Configuration config = new Configuration(base.getResources().getConfiguration());
    config.setLocale(Locale.US);
    return base.createConfigurationContext(config);
  }

  @Test
  public void formatDistance_metric_subKilometer() {
    String result = DistanceUtils.formatDistance(metricContext(), 250f);
    assertEquals("250m away", result);
  }

  @Test
  public void formatDistance_metric_kilometers() {
    String result = DistanceUtils.formatDistance(metricContext(), 2500f);
    assertEquals("2.5km away", result);
  }

  @Test
  public void formatDistance_metric_exactBoundary_1000m() {
    // 1000m exactly → km branch (not < 1000)
    String result = DistanceUtils.formatDistance(metricContext(), 1000f);
    assertEquals("1.0km away", result);
  }

  @Test
  public void formatDistance_imperial_feet() {
    String result = DistanceUtils.formatDistance(imperialContext(), 100f);
    // 100m * 3.28084 = 328ft
    assertTrue("Should contain 'ft away'", result.contains("ft away"));
    assertTrue("Should be in hundreds of feet", result.startsWith("328"));
  }

  @Test
  public void formatDistance_imperial_miles() {
    String result = DistanceUtils.formatDistance(imperialContext(), 2000f);
    // 2000m * 3.28084 = 6561ft → > 5280, so miles
    assertTrue("Should contain 'mi away'", result.contains("mi away"));
  }

  // ---- formatDiameter ----

  @Test
  public void formatDiameter_metric_subKilometer() {
    String result = DistanceUtils.formatDiameter(metricContext(), 250f);
    // diameter = 500m, rounded to nearest 5 = 500
    assertEquals("500m wide", result);
  }

  @Test
  public void formatDiameter_metric_kilometers() {
    String result = DistanceUtils.formatDiameter(metricContext(), 2500f);
    // diameter = 5000m = 5.0km
    assertEquals("5.0km wide", result);
  }

  @Test
  public void formatDiameter_imperial_feet() {
    String result = DistanceUtils.formatDiameter(imperialContext(), 100f);
    // diameter = 200m * 3.28084 = 656ft, rounded to nearest 5 = 655
    assertTrue("Should contain 'ft wide', got: " + result, result.contains("ft wide"));
  }

  @Test
  public void formatDiameter_imperial_miles() {
    String result = DistanceUtils.formatDiameter(imperialContext(), 2000f);
    // diameter = 4000m * 3.28084 = 13123ft → > 5280, so miles
    assertTrue("Should contain 'mi wide', got: " + result, result.contains("mi wide"));
  }

  @Test
  public void formatDiameter_metric_roundsToNearest5() {
    // radius=123 → diameter=246 → rounded to nearest 5 = 245
    String result = DistanceUtils.formatDiameter(metricContext(), 123f);
    assertEquals("245m wide", result);
  }
}
