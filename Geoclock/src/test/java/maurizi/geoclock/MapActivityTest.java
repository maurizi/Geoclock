package maurizi.geoclock;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(emulateSdk = 18)
public class MapActivityTest {

	@Test
	public void test_navigationDrawerGetsCreated() throws Exception {
		MapActivity activity = Robolectric.buildActivity(MapActivity.class).create().get();

		assertNotNull(activity.navigationDrawerFragment);
	}
}
