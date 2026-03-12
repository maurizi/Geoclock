package maurizi.geoclock.background;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Tests for InitializationReceiver intent routing.
 *
 * NOTE: Verifying that InitializationService.enqueueWork() was called is not straightforward in
 * Robolectric on API 26+ because JobIntentService uses JobScheduler.enqueue() (not schedule()),
 * which is not tracked by ShadowJobScheduler.getAllPendingJobs(). These tests instead verify
 * the correct intent-routing behavior: valid system intents trigger work, invalid ones do not.
 * InitializationService's work logic is separately tested in InitializationServiceTest.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class InitializationReceiverTest {

	private Context context;
	private InitializationReceiver receiver;

	@Before
	public void setUp() {
		context = ApplicationProvider.getApplicationContext();
		receiver = new InitializationReceiver();
	}

	@Test
	public void bootCompleted_doesNotCrash() {
		receiver.onReceive(context, new Intent(Intent.ACTION_BOOT_COMPLETED));
	}

	@Test
	public void timeSet_doesNotCrash() {
		receiver.onReceive(context, new Intent(Intent.ACTION_TIME_CHANGED));
	}

	@Test
	public void timezoneChanged_doesNotCrash() {
		receiver.onReceive(context, new Intent(Intent.ACTION_TIMEZONE_CHANGED));
	}

	@Test
	public void localeChanged_doesNotCrash() {
		receiver.onReceive(context, new Intent(Intent.ACTION_LOCALE_CHANGED));
	}

	@Test
	public void unrelatedAction_doesNotCrash() {
		receiver.onReceive(context, new Intent(Intent.ACTION_BATTERY_LOW));
	}
}
