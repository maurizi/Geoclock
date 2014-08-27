package maurizi.geoclock.test;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import maurizi.geoclock.MapActivity;

import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(emulateSdk = 18)
public class MapActivityTest {

	@Test
	public void test_navigationDrawerGetsCreated() {
		MapActivity activity = Robolectric.buildActivity(MapActivity.class).create().get();

		assertNotNull(activity.getNavigationDrawerFragment());
	}
}
