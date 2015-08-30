package maurizi.geoclock.background;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.google.common.collect.ImmutableSet;

import java.util.Set;

public class InitializationReceiver extends BroadcastReceiver {
	private static final Set<String> INTENTS = ImmutableSet.of(
			Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
			Intent.ACTION_LOCALE_CHANGED);

	@Override
	public void onReceive(final Context context, final Intent intent) {
		if (INTENTS.contains(intent.getAction())) {
			final Intent serviceIntent = new Intent(context, InitializationService.class);
			context.startService(serviceIntent);
		}
	}
}