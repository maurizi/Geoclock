package maurizi.geoclock.test;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.google.android.gms.location.Geofence;
import com.google.common.collect.ImmutableList;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowApplication.Wrapper;
import org.robolectric.shadows.ShadowNotification;
import org.robolectric.shadows.ShadowNotificationManager;

import java.util.List;

import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.GeofenceReceiver;
import maurizi.geoclock.test.shadows.ShadowLocationClient;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.robolectric.Robolectric.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(emulateSdk = 18, shadows = {ShadowLocationClient.class})
public class GeofenceReceiverTest {

	static final Geofence mockGeofence = mock(Geofence.class);

	@Before
	public void setUp() {
		when(mockGeofence.getRequestId()).thenReturn(GeoAlarmTest.testAlarm.geofenceId);
	}

	@Test
	public void testBroadcastReceiverRegistered() {
		List<Wrapper> registeredReceivers = Robolectric.getShadowApplication().getRegisteredReceivers();

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

		ShadowApplication shadowApplication = Robolectric.getShadowApplication();
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
		receiver.onReceive(Robolectric.getShadowApplication().getApplicationContext(), intent);
	}

	@Test
	public void testNotificationIsAdded() {
		final NotificationManager notificationManager = (NotificationManager) Robolectric.application.getSystemService(Context.NOTIFICATION_SERVICE);

		final GeoAlarm testAlarm = GeoAlarmTest.testAlarm.withHour(5).withMinute(0).withName("Place");
		GeoAlarm.add(Robolectric.application, testAlarm);

		final GeoAlarm laterTestAlarm = testAlarm.withHour(6).withName("Later").withGeofenceId("Different");
		GeoAlarm.add(Robolectric.application, laterTestAlarm);
		ShadowLocationClient.setGeofences(ImmutableList.of(mockGeofence));

		final GeofenceReceiver receiver = setupGeofenceReceiver();
		receiver.onConnected(new Bundle());

		final ShadowNotificationManager notificationManagerShadow = shadowOf(notificationManager);
		assertEquals(1, notificationManagerShadow.size());
		final ShadowNotification notificationShadow = shadowOf(notificationManagerShadow.getAllNotifications().get(0));
		assertEquals("Alarm at 5:00 AM for Place", notificationShadow.getContentText());
	}

	private GeofenceReceiver setupGeofenceReceiver() {
		ShadowApplication shadowApplication = Robolectric.getShadowApplication();
		Intent intent = new Intent("maurizi.geoclock.RECEIVE_GEOFENCE");
		List<BroadcastReceiver> receiversForIntent = shadowApplication.getReceiversForIntent(intent);
		GeofenceReceiver receiver = (GeofenceReceiver) receiversForIntent.get(0);
		receiver.onReceive(Robolectric.getShadowApplication().getApplicationContext(), intent);
		return receiver;
	}
}
