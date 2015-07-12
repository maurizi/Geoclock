package maurizi.geoclock.test;

import android.content.BroadcastReceiver;
import android.content.Intent;

import com.google.android.gms.location.Geofence;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowApplication.Wrapper;

import java.util.List;

import maurizi.geoclock.BuildConfig;
import maurizi.geoclock.GeofenceReceiver;
import maurizi.geoclock.test.support.RobolectricGradleTestRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 18)
public class GeofenceReceiverTest {

	static final Geofence mockGeofence = mock(Geofence.class);

	@Before
	public void setUp() {
		when(mockGeofence.getRequestId()).thenReturn(GeoAlarmTest.testAlarm.name);
	}

	@Test
	public void testBroadcastReceiverRegistered() {
		List<Wrapper> registeredReceivers = ShadowApplication.getInstance().getRegisteredReceivers();

		Assert.assertFalse(registeredReceivers.isEmpty());

		boolean receiverFound = false;
		for (ShadowApplication.Wrapper wrapper : registeredReceivers) {
			if (!receiverFound)
				receiverFound = GeofenceReceiver.class.getSimpleName().equals(wrapper.broadcastReceiver.getClass().getSimpleName());
		}

		Assert.assertTrue(receiverFound);
	}
}
