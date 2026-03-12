package maurizi.geoclock;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.google.android.libraries.places.api.Places;

import org.acra.ACRA;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.DialogConfigurationBuilder;
import org.acra.config.MailSenderConfigurationBuilder;
import org.acra.data.StringFormat;

public class GeoClockApplication extends Application {
	@Override
	public void onCreate() {
		super.onCreate();
		if (!Places.isInitialized()) {
			try {
				ApplicationInfo ai = getPackageManager()
					.getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
				String apiKey = ai.metaData.getString("com.google.android.geo.API_KEY");
				if (apiKey != null && !apiKey.isEmpty()) {
					Places.initialize(getApplicationContext(), apiKey);
				}
			} catch (PackageManager.NameNotFoundException e) {
				// best-effort
			}
		}
	}

	@Override
	protected void attachBaseContext(Context base) {
		super.attachBaseContext(base);
		if (!ACRA.isACRASenderServiceProcess()) {
			ACRA.init(this, new CoreConfigurationBuilder()
				.withReportFormat(StringFormat.JSON)
				.withPluginConfigurations(
					new DialogConfigurationBuilder()
						.withText(getString(R.string.crash_dialog_text))
						.withTitle(getString(R.string.crash_dialog_title))
						.withCommentPrompt(getString(R.string.crash_dialog_comment))
						.build(),
					new MailSenderConfigurationBuilder()
						.withMailTo("geoclock@maurizi.org")
						.withSubject("Geoclock Crash Report")
						.withReportAsFile(true)
						.withReportFileName("geoclock-crash.json")
						.build()
				)
			);
		}
	}
}
