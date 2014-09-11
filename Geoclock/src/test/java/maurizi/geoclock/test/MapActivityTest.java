package maurizi.geoclock.test;

import com.google.android.gms.maps.SupportMapFragment;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ActivityController;

import maurizi.geoclock.MapActivity;
import maurizi.geoclock.NavigationDrawerFragment;
import maurizi.geoclock.R;
import maurizi.geoclock.test.shadows.ShadowSupportMenuInflater;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(emulateSdk = 18, shadows = {ShadowSupportMenuInflater.class})
public class MapActivityTest {

	private ActivityController<MapActivity> controller;

	@Before
	public void setUp() {
		controller = Robolectric.buildActivity(MapActivity.class);
	}

	@Test
	public void test_onCreate() {
		MapActivity activity = controller.create().get();
		assertThat(activity.getSupportFragmentManager().findFragmentById(R.id.navigation_drawer)).isInstanceOf(NavigationDrawerFragment.class);
		assertThat(activity.getSupportFragmentManager().findFragmentById(R.id.map)).isInstanceOf(SupportMapFragment.class);
	}

	@Test
	public void test_onResume() {
		assertNotNull(controller.create().start().resume().get());
	}
}
