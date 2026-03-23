package maurizi.geoclock.background;

import android.content.Context;
import android.content.Intent;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumentation test for GeofenceReceiver error path. Constructs an intent that produces a
 * non-null GeofencingEvent with hasError()=true, covering the Log.e branch on line 31-32.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class GeofenceReceiverInstrumentationTest {

  @Test
  public void onReceive_geofencingEventWithError_logsErrorAndReturns() {
    Context context = ApplicationProvider.getApplicationContext();
    GeofenceReceiver receiver = new GeofenceReceiver();

    // GeofencingEvent.fromIntent reads "gms_error_code" extra from the intent.
    // Setting a non-zero error code makes hasError() return true and event non-null.
    Intent intent = new Intent(context, GeofenceReceiver.class);
    intent.putExtra("gms_error_code", 1000); // GEOFENCE_NOT_AVAILABLE
    receiver.onReceive(context, intent);
    // No crash = success. This covers the Log.e(TAG, ...) branch.
  }
}
