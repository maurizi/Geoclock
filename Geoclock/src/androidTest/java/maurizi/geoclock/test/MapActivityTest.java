package maurizi.geoclock.test;

import android.test.ActivityInstrumentationTestCase2;

import maurizi.geoclock.MapActivity;

public class MapActivityTest extends ActivityInstrumentationTestCase2<MapActivity> {

	public MapActivityTest() {
		super(MapActivity.class);
	}

	public void test_navigationDrawerGetsCreated() {
		MapActivity activity = getActivity();

		assertNotNull(activity.getNavigationDrawerFragment());
	}
}
