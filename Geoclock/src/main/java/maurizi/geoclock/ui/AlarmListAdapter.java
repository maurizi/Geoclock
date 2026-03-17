package maurizi.geoclock.ui;

import android.content.Context;
import android.graphics.Color;
import android.icu.util.LocaleData;
import android.icu.util.ULocale;
import android.location.Location;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.R;

public class AlarmListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

  private static final int VIEW_TYPE_HEADER = 0;
  private static final int VIEW_TYPE_ALARM = 1;

  static final EnumSet<DayOfWeek> WEEKDAYS =
      EnumSet.of(
          DayOfWeek.MONDAY,
          DayOfWeek.TUESDAY,
          DayOfWeek.WEDNESDAY,
          DayOfWeek.THURSDAY,
          DayOfWeek.FRIDAY);
  static final EnumSet<DayOfWeek> WEEKENDS = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

  public interface Callbacks {
    void onEdit(GeoAlarm alarm);

    void onToggleEnabled(GeoAlarm alarm, boolean enabled);
  }

  private final List<GeoAlarm> alarms = new ArrayList<>();
  private final List<Object> items = new ArrayList<>(); // String (header) or GeoAlarm
  private @Nullable Location currentLocation;
  private final Callbacks callbacks;

  public AlarmListAdapter(
      List<GeoAlarm> alarms, @Nullable Location currentLocation, Callbacks callbacks) {
    this.alarms.addAll(alarms);
    this.currentLocation = currentLocation;
    this.callbacks = callbacks;
    sortAlarms();
    rebuildItems();
  }

  @android.annotation.SuppressLint("NotifyDataSetChanged") // Full list replacement with re-sort
  public void updateAlarms(List<GeoAlarm> newAlarms, @Nullable Location location) {
    this.alarms.clear();
    this.alarms.addAll(newAlarms);
    this.currentLocation = location;
    sortAlarms();
    rebuildItems();
    notifyDataSetChanged();
  }

  /** Sort: inside-geofence first (sorted by time of day), then outside (sorted by time of day). */
  void sortAlarms() {
    final Location loc = currentLocation;
    Collections.sort(
        alarms,
        Comparator.<GeoAlarm, Boolean>comparing(
                a -> !isInsideGeofence(a, loc)) // inside first (false < true → negate)
            .thenComparing(
                a -> {
                  if (a.hour == null || a.minute == null) return LocalTime.MAX;
                  return LocalTime.of(a.hour, a.minute);
                }));
  }

  private void rebuildItems() {
    items.clear();
    if (currentLocation == null || alarms.isEmpty()) {
      items.addAll(alarms);
      return;
    }

    List<GeoAlarm> insideAlarms = new ArrayList<>();
    List<GeoAlarm> outsideAlarms = new ArrayList<>();
    for (GeoAlarm alarm : alarms) {
      if (isInsideGeofence(alarm, currentLocation)) {
        insideAlarms.add(alarm);
      } else {
        outsideAlarms.add(alarm);
      }
    }

    // Only show headers when both groups have alarms
    if (!insideAlarms.isEmpty() && !outsideAlarms.isEmpty()) {
      items.add(R.string.section_in_range);
      items.addAll(insideAlarms);
      items.add(R.string.section_out_of_range);
      items.addAll(outsideAlarms);
    } else {
      items.addAll(alarms);
    }
  }

  static float distanceToCenter(GeoAlarm alarm, @Nullable Location location) {
    if (location == null) return Float.MAX_VALUE;
    float[] results = new float[1];
    Location.distanceBetween(
        location.getLatitude(),
        location.getLongitude(),
        alarm.location.latitude,
        alarm.location.longitude,
        results);
    return results[0];
  }

  static boolean isInsideGeofence(GeoAlarm alarm, @Nullable Location location) {
    return distanceToCenter(alarm, location) <= alarm.radius;
  }

  static float distanceToEdge(GeoAlarm alarm, @Nullable Location location) {
    return Math.max(0, distanceToCenter(alarm, location) - alarm.radius);
  }

  @Override
  public int getItemViewType(int position) {
    return items.get(position) instanceof Integer ? VIEW_TYPE_HEADER : VIEW_TYPE_ALARM;
  }

  @NonNull
  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    if (viewType == VIEW_TYPE_HEADER) {
      View view =
          LayoutInflater.from(parent.getContext())
              .inflate(R.layout.item_section_header, parent, false);
      return new SectionViewHolder(view);
    }
    View view =
        LayoutInflater.from(parent.getContext()).inflate(R.layout.item_alarm, parent, false);
    return new AlarmViewHolder(view);
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder vh, int position) {
    if (vh instanceof SectionViewHolder) {
      int stringRes = (Integer) items.get(position);
      ((SectionViewHolder) vh).titleView.setText(stringRes);
      return;
    }

    AlarmViewHolder holder = (AlarmViewHolder) vh;
    GeoAlarm alarm = (GeoAlarm) items.get(position);
    Context ctx = holder.itemView.getContext();

    // Time
    if (alarm.hour != null && alarm.minute != null) {
      LocalTime t = LocalTime.of(alarm.hour, alarm.minute);
      holder.timeView.setText(t.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)));
    } else {
      holder.timeView.setText(ctx.getString(R.string.time_placeholder));
    }

    // Days
    holder.daysView.setText(getDaysSummary(alarm.days, ctx));

    // Distance to geofence edge + card styling
    float centerDist = distanceToCenter(alarm, currentLocation);
    boolean inside = centerDist <= alarm.radius;
    if (currentLocation != null && !inside) {
      float edgeDist = Math.max(0, centerDist - alarm.radius);
      holder.distanceView.setText(
          ctx.getString(R.string.distance_separator, formatEdgeDistance(ctx, edgeDist)));
      holder.distanceView.setVisibility(View.VISIBLE);
    } else {
      holder.distanceView.setVisibility(View.GONE);
    }

    // Bell icon tint: green when inside, gray when outside
    int bellColor =
        inside
            ? ContextCompat.getColor(ctx, R.color.bell_inside)
            : ContextCompat.getColor(ctx, R.color.bell_outside);
    android.graphics.drawable.Drawable icon =
        DrawableCompat.wrap(holder.bellIcon.getDrawable()).mutate();
    DrawableCompat.setTint(icon, bellColor);
    holder.bellIcon.setImageDrawable(icon);

    // Card background: white when inside, light gray when outside
    CardView card = (CardView) holder.itemView;
    if (inside || currentLocation == null) {
      card.setCardBackgroundColor(Color.WHITE);
    } else {
      card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.card_outside));
    }

    // Place
    if (alarm.place != null && !alarm.place.isEmpty()) {
      holder.placeView.setText(alarm.place);
    } else {
      holder.placeView.setText(
          String.format(
              Locale.US, "%.4f, %.4f", alarm.location.latitude, alarm.location.longitude));
    }

    // Radius
    holder.radiusView.setText(GeoAlarm.getRadiusSizeLabel(ctx, alarm.radius));

    // Switch (remove old listener first to avoid spurious callbacks during rebind)
    holder.enabledSwitch.setOnCheckedChangeListener(null);
    holder.enabledSwitch.setChecked(alarm.enabled);
    holder.enabledSwitch.setOnCheckedChangeListener(
        (btn, checked) -> callbacks.onToggleEnabled(alarm, checked));

    // Card click
    holder.itemView.setOnClickListener(v -> callbacks.onEdit(alarm));
  }

  @Override
  public int getItemCount() {
    return items.size();
  }

  String getDaysSummary(@Nullable Set<DayOfWeek> days, Context ctx) {
    if (days == null || days.isEmpty()) return ctx.getString(R.string.days_once);
    if (days.size() == 7) return ctx.getString(R.string.days_every_day);
    if (days.equals(WEEKDAYS)) return ctx.getString(R.string.days_weekdays);
    if (days.equals(WEEKENDS)) return ctx.getString(R.string.days_weekends);
    DayOfWeek[] ordered = {
      DayOfWeek.MONDAY,
      DayOfWeek.TUESDAY,
      DayOfWeek.WEDNESDAY,
      DayOfWeek.THURSDAY,
      DayOfWeek.FRIDAY,
      DayOfWeek.SATURDAY,
      DayOfWeek.SUNDAY
    };
    StringBuilder sb = new StringBuilder();
    for (DayOfWeek d : ordered) {
      if (days.contains(d)) {
        if (sb.length() > 0) sb.append(", ");
        sb.append(d.getDisplayName(TextStyle.SHORT, Locale.getDefault()));
      }
    }
    return sb.toString();
  }

  private static boolean useImperial(Context ctx) {
    LocaleData.MeasurementSystem ms =
        LocaleData.getMeasurementSystem(
            ULocale.forLocale(ctx.getResources().getConfiguration().getLocales().get(0)));
    return ms != LocaleData.MeasurementSystem.SI;
  }

  private String formatEdgeDistance(Context ctx, float meters) {
    if (useImperial(ctx)) {
      float feet = meters * 3.28084f;
      if (feet < 5280) {
        return ctx.getString(R.string.distance_edge_feet, (int) feet);
      } else {
        return ctx.getString(R.string.distance_edge_miles, feet / 5280f);
      }
    }
    if (meters < 1000) {
      return ctx.getString(R.string.distance_edge_meters, (int) meters);
    } else {
      return ctx.getString(R.string.distance_edge_km, meters / 1000f);
    }
  }

  static class SectionViewHolder extends RecyclerView.ViewHolder {
    final TextView titleView;

    SectionViewHolder(@NonNull View itemView) {
      super(itemView);
      titleView = itemView.findViewById(R.id.section_title);
    }
  }

  static class AlarmViewHolder extends RecyclerView.ViewHolder {
    final ImageView bellIcon;
    final TextView timeView;
    final TextView daysView;
    final TextView distanceView;
    final TextView placeView;
    final TextView radiusView;
    final SwitchCompat enabledSwitch;

    AlarmViewHolder(@NonNull View itemView) {
      super(itemView);
      bellIcon = itemView.findViewById(R.id.alarm_bell_icon);
      timeView = itemView.findViewById(R.id.alarm_time);
      daysView = itemView.findViewById(R.id.alarm_days);
      distanceView = itemView.findViewById(R.id.alarm_distance);
      placeView = itemView.findViewById(R.id.alarm_place);
      radiusView = itemView.findViewById(R.id.alarm_radius);
      enabledSwitch = itemView.findViewById(R.id.alarm_enabled_switch);
    }
  }
}
