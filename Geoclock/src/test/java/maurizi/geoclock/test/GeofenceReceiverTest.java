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


	@Test
	public void testIntentHandling() {
		/** TEST 1
		 ----------
		 We defined the Broadcast receiver with a certain action, so we should check if we have
		 receivers listening to the defined action
		 */
		Intent intent = new Intent("maurizi.geoclock.RECEIVE_GEOFENCE");

		ShadowApplication shadowApplication = ShadowApplication.getInstance();
		Assert.assertTrue(shadowApplication.hasReceiverForIntent(intent));

		/**
		 * TEST 2
		 * ----------
		 * Lets be sure that we only have a single receiver assigned for this intent
		 */
		List<BroadcastReceiver> receiversForIntent = shadowApplication.getReceiversForIntent(intent);

		assertEquals("Expected one broadcast receiver", 1, receiversForIntent.size());

		/**
		 * TEST 3
		 * ----------
		 * Fetch the Broadcast receiver and cast it to the correct class.
		 * Next call the "onReceive" method and check if the MyBroadcastIntentService was started
		 */
		GeofenceReceiver receiver = (GeofenceReceiver) receiversForIntent.get(0);
		receiver.onReceive(shadowApplication.getApplicationContext(), intent);
	}

	@Test
	public void testNotificationIsAdded() {
		// TODO: stubbed
	}
}
