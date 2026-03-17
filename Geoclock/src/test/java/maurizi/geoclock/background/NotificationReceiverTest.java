package maurizi.geoclock.background;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.maps.model.LatLng;
import java.util.UUID;
import maurizi.geoclock.GeoAlarm;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowNotificationManager;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class NotificationReceiverTest {

  private Context context;
  private NotificationReceiver receiver;
  private ShadowNotificationManager shadowNotificationManager;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    receiver = new NotificationReceiver();
    NotificationManager nm =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    shadowNotificationManager = Shadows.shadowOf(nm);
  }

  @Test
  public void onReceive_withValidAlarm_postsNotification() {
    GeoAlarm alarm = saveAlarm(enabledAlarm());
    receiver.onReceive(context, NotificationReceiver.getIntent(context, alarm));
    assertEquals(1, shadowNotificationManager.getAllNotifications().size());
    assertNotNull(shadowNotificationManager.getNotification(NotificationReceiver.NOTIFICATION_ID));
  }

  @Test
  public void onReceive_notificationTitleContainsAlarmTime() {
    GeoAlarm alarm = saveAlarm(enabledAlarm());
    receiver.onReceive(context, NotificationReceiver.getIntent(context, alarm));
    android.app.Notification n =
        shadowNotificationManager.getNotification(NotificationReceiver.NOTIFICATION_ID);
    assertNotNull(n);
    // The title is set via extras (NotificationCompat stores it there)
    CharSequence title = n.extras.getCharSequence(android.app.Notification.EXTRA_TITLE);
    assertNotNull("Notification title should not be null", title);
    assertFalse("Notification title should not be empty", title.toString().isEmpty());
  }

  @Test
  public void onReceive_notificationHasCancelAlarmAction() {
    GeoAlarm alarm = saveAlarm(enabledAlarm());
    receiver.onReceive(context, NotificationReceiver.getIntent(context, alarm));
    android.app.Notification n =
        shadowNotificationManager.getNotification(NotificationReceiver.NOTIFICATION_ID);
    assertNotNull(n);
    assertTrue("Notification should have at least one action", n.actions.length >= 1);
    // The action label is "Cancel alarm"
    assertEquals("Cancel alarm", n.actions[0].title.toString());
  }

  @Test
  public void onReceive_cancelAlarmAction_intentTargetsAlarmClockReceiver() {
    GeoAlarm alarm = saveAlarm(enabledAlarm());
    receiver.onReceive(context, NotificationReceiver.getIntent(context, alarm));
    android.app.Notification n =
        shadowNotificationManager.getNotification(NotificationReceiver.NOTIFICATION_ID);
    assertNotNull(n);
    assertTrue(n.actions.length >= 1);
    // The action PendingIntent targets AlarmClockReceiver with ACTION_CANCEL_UPCOMING
    android.app.PendingIntent pi = n.actions[0].actionIntent;
    assertNotNull(pi);
    ShadowApplication sa = Shadows.shadowOf((Application) context);
    try {
      pi.send();
    } catch (android.app.PendingIntent.CanceledException ignored) {
    }
    Intent fired =
        sa.getBroadcastIntents().stream()
            .filter(i -> AlarmClockReceiver.ACTION_CANCEL_UPCOMING.equals(i.getAction()))
            .findFirst()
            .orElse(null);
    assertNotNull("Cancel action should broadcast ACTION_CANCEL_UPCOMING", fired);
  }

  @Test
  public void onReceive_noAlarmIdExtra_doesNotPostNotification() {
    Intent intent = new Intent(context, NotificationReceiver.class);
    receiver.onReceive(context, intent);
    assertEquals(0, shadowNotificationManager.getAllNotifications().size());
  }

  @Test
  public void onReceive_unknownAlarmId_doesNotPostNotification() {
    Intent intent = new Intent(context, NotificationReceiver.class);
    intent.putExtra("alarm_id", UUID.randomUUID().toString());
    receiver.onReceive(context, intent);
    assertEquals(0, shadowNotificationManager.getAllNotifications().size());
  }

  @Test
  public void onReceive_multipleCallsWithSameAlarm_replacesNotification() {
    GeoAlarm alarm = saveAlarm(enabledAlarm());
    Intent alarmIntent = NotificationReceiver.getIntent(context, alarm);
    receiver.onReceive(context, alarmIntent);
    receiver.onReceive(context, alarmIntent);
    // Second call cancels first, then re-posts — still only 1 active
    assertEquals(1, shadowNotificationManager.getAllNotifications().size());
  }

  // ---- helpers ----

  private GeoAlarm enabledAlarm() {
    return GeoAlarm.builder()
        .id(UUID.randomUUID())
        .location(new LatLng(37.4, -122.0))
        .radius(100)
        .enabled(true)
        .hour(8)
        .minute(0)
        .build();
  }

  private GeoAlarm saveAlarm(GeoAlarm alarm) {
    GeoAlarm.save(context, alarm);
    return alarm;
  }
}
