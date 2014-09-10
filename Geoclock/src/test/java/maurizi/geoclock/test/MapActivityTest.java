package maurizi.geoclock.test;

import android.os.Build;
import android.os.Build.VERSION_CODES;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ActivityController;

import maurizi.geoclock.MapActivity;

import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(emulateSdk = 18)
public class MapActivityTest {

	private ActivityController<MapActivity> controller;

	@Before
	public void setUp() {
		controller = Robolectric.buildActivity(MapActivity.class);
	}

	@Test
	public void test_onCreate() {
		MapActivity activity = controller.create().get();
		assertNotNull(activity.getNavigationDrawerFragment());
	}
}
