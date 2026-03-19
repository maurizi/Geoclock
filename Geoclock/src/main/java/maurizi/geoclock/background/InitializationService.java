package maurizi.geoclock.background;

import static com.google.common.collect.Collections2.filter;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;
import java.time.Instant;
import java.util.Collection;
import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.utils.ActiveAlarmManager;
import maurizi.geoclock.utils.LocationServiceGoogle;

@SuppressWarnings("deprecation") // TODO: migrate to WorkManager
public class InitializationService extends JobIntentService {
  private static final int JOB_ID = 1000;

  public static void enqueueWork(Context context, Intent work) {
    enqueueWork(context, InitializationService.class, JOB_ID, work);
  }

  @Override
  protected void onHandleWork(@NonNull Intent intent) {
    Collection<GeoAlarm> alarms = GeoAlarm.getGeoAlarms(this);
    disableExpiredAlarms(alarms);

    ActiveAlarmManager activeAlarmManager = new ActiveAlarmManager(this);
    activeAlarmManager.clearActiveAlarms();

    LocationServiceGoogle locationService = new LocationServiceGoogle(this);
    locationService
        .addGeofences(filter(alarms, alarm -> alarm.enabled))
        .addOnSuccessListener(
            aVoid -> {
              /* geofences registered */
            })
        .addOnFailureListener(
            e -> {
              /* TODO: handle registration failure */
            });
  }

  private void disableExpiredAlarms(Collection<GeoAlarm> alarms) {
    Instant now = Instant.now();
    Collection<GeoAlarm> disabledAlarms =
        filter(
            alarms,
            alarm ->
                alarm.isNonRepeating()
                    && alarm.time != null
                    && now.isAfter(Instant.ofEpochMilli(alarm.time)));
    for (GeoAlarm alarm : disabledAlarms) {
      GeoAlarm.save(this, alarm.withEnabled(false));
    }
  }
}
