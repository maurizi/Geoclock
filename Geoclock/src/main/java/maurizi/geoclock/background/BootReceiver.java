package maurizi.geoclock.background;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(final Context context, final Intent intent) {
		if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
			final Intent serviceIntent = new Intent(context, BootupService.class);
			context.startService(serviceIntent);
		}
	}
}