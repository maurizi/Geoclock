package maurizi.geoclock;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class GeoClockApplicationTest {

  @Test
  public void onCreate_doesNotCrash() {
    // Robolectric creates the Application via the manifest; verify it initializes
    GeoClockApplication app =
        (GeoClockApplication)
            androidx.test.core.app.ApplicationProvider.getApplicationContext()
                .getApplicationContext();
    assertNotNull(app);
  }

  @Test
  @Config(sdk = 34)
  public void onCreate_api34_doesNotCrash() {
    GeoClockApplication app =
        (GeoClockApplication)
            androidx.test.core.app.ApplicationProvider.getApplicationContext()
                .getApplicationContext();
    assertNotNull(app);
  }

  @Test
  @Config(sdk = 28)
  public void onCreate_api28_doesNotCrash() {
    GeoClockApplication app =
        (GeoClockApplication)
            androidx.test.core.app.ApplicationProvider.getApplicationContext()
                .getApplicationContext();
    assertNotNull(app);
  }
}
