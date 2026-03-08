package maurizi.geoclock;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import androidx.annotation.Nullable;

import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import java.util.Locale;

import lombok.NonNull;
import lombok.Value;
import lombok.Builder;
import lombok.With;

import static com.google.common.collect.Collections2.filter;
import static com.google.common.collect.Collections2.transform;
import static java.time.temporal.TemporalAdjusters.next;

@Value
@Builder
@With
public class GeoAlarm {

    private static final Gson gson = new Gson();
    private static final String ALARM_PREFS = "alarms";

    @NonNull public UUID id;
    @Nullable public String place;
    public int radius;
    @NonNull public LatLng location;
    public boolean enabled;

    @Nullable public Long time;
    @Nullable public Integer hour;
    @Nullable public Integer minute;
    @Nullable public Set<DayOfWeek> days;

    @Override
    public String toString() {
        return place != null ? place : String.format(Locale.US, "%.4f,%.4f", location.latitude, location.longitude);
    }

    public MarkerOptions getMarkerOptions() {
        String title = place != null ? place : "";
        return new MarkerOptions().position(location).title(title);
    }

    public CircleOptions getCircleOptions() {
        return new CircleOptions().center(location).radius(radius).fillColor(R.color.geofence_fill_color);
    }

    private LocalTime getAlarmTime() {
        if (hour == null || minute == null) {
            return null;
        }
        return LocalTime.of(hour, minute);
    }

    private boolean isAlarmForToday(LocalDateTime now) {
        LocalTime time = getAlarmTime();
        return now != null && time != null && time.isAfter(now.toLocalTime());
    }

    /**
     * @return A ZonedDateTime for when the alarm is due to go off
     */
    public ZonedDateTime calculateAlarmTime(LocalDateTime now) {
        final LocalTime alarmTime = getAlarmTime();

        if (now == null || alarmTime == null) {
            return null;
        }

        final LocalDateTime alarmDateTime = isNonRepeating()
                ? alarmTime.atDate(isAlarmForToday(now)
                        ? now.toLocalDate()
                        : now.toLocalDate().plusDays(1))
                : alarmTime.atDate(getSoonestDayForRepeatingAlarm(now));

        return alarmDateTime.atZone(ZoneId.systemDefault());
    }

    public boolean isNonRepeating() {
        return days == null || days.isEmpty();
    }

    private LocalDate getSoonestDayForRepeatingAlarm(LocalDateTime now) {
        assert days != null;
        if (isNonRepeating()) {
            throw new AssertionError();
        }

        final DayOfWeek currentDayOfWeek = now.getDayOfWeek();

        if (isAlarmForToday(now) && days.contains(currentDayOfWeek)) {
            return now.toLocalDate();
        }

        final Collection<DayOfWeek> daysAfterToday = filter(days, weekday -> weekday.getValue() > currentDayOfWeek.getValue());

        final Collection<DayOfWeek> daysTodayAndBefore = filter(
                days, weekday -> weekday.getValue() <= currentDayOfWeek.getValue());

        final ImmutableList<DayOfWeek> allDays = ImmutableList.<DayOfWeek>builder()
                .addAll(daysAfterToday)
                .addAll(daysTodayAndBefore).build();

        final DayOfWeek nextDayForAlarm = allDays.get(0);
        return now.toLocalDate().with(next(nextDayForAlarm));
    }

    @Nullable
    public static GeoAlarm getGeoAlarm(Context context, UUID id) {
        SharedPreferences prefs = getSharedAlarmPreferences(context);
        String json = prefs.getString(id.toString(), null);
        if (json != null) {
            return parse(json);
        }
        return null;
    }

    public static Collection<GeoAlarm> getGeoAlarms(Context context) {
        SharedPreferences prefs = getSharedAlarmPreferences(context);
        Collection<?> json = prefs.getAll().values();
        return ImmutableList.<GeoAlarm>builder()
                .addAll(filter(transform(json, GeoAlarm::parse), (GeoAlarm geoAlarm) -> geoAlarm != null))
                .build();
    }

    public static Function<com.google.android.gms.location.Geofence, GeoAlarm> getGeoAlarmForGeofenceFn(Context context) {
        final SharedPreferences prefs = getSharedAlarmPreferences(context);
        return geofence -> parse(prefs.getString(geofence.getRequestId(), null));
    }

    public static void save(Context context, GeoAlarm newAlarm) {
        if (newAlarm.enabled) {
            final ZonedDateTime alarmTime = newAlarm.calculateAlarmTime(LocalDateTime.now());
            if (alarmTime != null) {
                newAlarm = newAlarm.withTime(alarmTime.toInstant().toEpochMilli());
            }
        }
        SharedPreferences prefs = getSharedAlarmPreferences(context);
        Editor editor = prefs.edit();
        editor.putString(newAlarm.id.toString(), gson.toJson(newAlarm, GeoAlarm.class))
                .commit();
    }

    public static void remove(Context context, GeoAlarm oldAlarm) {
        SharedPreferences prefs = getSharedAlarmPreferences(context);
        prefs.edit().remove(oldAlarm.id.toString()).commit();
    }

    private static GeoAlarm parse(Object json) {
        try {
            return gson.fromJson((String) json, GeoAlarm.class);
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    private static SharedPreferences getSharedAlarmPreferences(Context context) {
        return context.getSharedPreferences(ALARM_PREFS, Context.MODE_PRIVATE);
    }
}
