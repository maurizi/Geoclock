package maurizi.geoclock.background;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;
import com.google.android.gms.maps.model.LatLng;
import java.util.UUID;
import maurizi.geoclock.GeoAlarm;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests AlarmRingingService with AUDIO_DISABLED=false so that startAlarm() actually exercises the
 * ringtone and vibration code paths. These tests produce real audio/vibration on the device.
 */
@SdkSuppress(minSdkVersion = 27, maxSdkVersion = 35)
@RunWith(AndroidJUnit4.class)
@LargeTest
public class AlarmRingingServiceAudioTest {

  private Context ctx;

  @Before
  public void setUp() {
    ctx = ApplicationProvider.getApplicationContext();
    AlarmRingingService.AUDIO_DISABLED = false;
  }

  @After
  public void tearDown() {
    AlarmRingingService.stop(ctx);
    AlarmRingingService.AUDIO_DISABLED = false;
    // Clean up alarms
    for (GeoAlarm a : GeoAlarm.getGeoAlarms(ctx)) {
      GeoAlarm.remove(ctx, a);
    }
  }

  @Test
  public void startAlarm_withRingtoneUri_playsAudio() throws Exception {
    GeoAlarm alarm =
        GeoAlarm.builder()
            .id(UUID.randomUUID())
            .location(new LatLng(37.4, -122.0))
            .radius(100)
            .enabled(true)
            .hour(8)
            .minute(0)
            .ringtoneUri(android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI.toString())
            .build();
    GeoAlarm.save(ctx, alarm);

    AlarmRingingService.start(ctx, alarm.id.toString());
    Thread.sleep(2000); // Let the ringtone + vibration start

    // Stop the service — exercises onDestroy with active ringtone/vibrator
    AlarmRingingService.stop(ctx);
    Thread.sleep(500);
    // If we got here without crash, startAlarm() and onDestroy() ran successfully
  }

  @Test
  public void startAlarm_vibrateOnly_vibratesWithoutRingtone() throws Exception {
    GeoAlarm alarm =
        GeoAlarm.builder()
            .id(UUID.randomUUID())
            .location(new LatLng(37.4, -122.0))
            .radius(100)
            .enabled(true)
            .hour(8)
            .minute(0)
            .ringtoneUri(null) // vibrate only
            .build();
    GeoAlarm.save(ctx, alarm);

    AlarmRingingService.start(ctx, alarm.id.toString());
    Thread.sleep(1500);

    // Service is running with vibration only
    AlarmRingingService.stop(ctx);
    Thread.sleep(500);
  }

  @Test
  public void startAlarm_noAlarm_handlesGracefully() throws Exception {
    // Start service with a non-existent alarm ID
    AlarmRingingService.start(ctx, UUID.randomUUID().toString());
    Thread.sleep(1000);
    AlarmRingingService.stop(ctx);
    Thread.sleep(500);
  }
}
