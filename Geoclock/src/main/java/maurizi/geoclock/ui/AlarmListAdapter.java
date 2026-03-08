package maurizi.geoclock.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.location.Location;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.R;

public class AlarmListAdapter extends RecyclerView.Adapter<AlarmListAdapter.AlarmViewHolder> {

    public interface Callbacks {
        void onEdit(GeoAlarm alarm);
        void onToggleEnabled(GeoAlarm alarm, boolean enabled);
        void onRenamePlace(GeoAlarm alarm, String newPlace);
    }

    private final List<GeoAlarm> alarms = new ArrayList<>();
    private @Nullable Location currentLocation;
    private final Callbacks callbacks;

    public AlarmListAdapter(List<GeoAlarm> alarms, @Nullable Location currentLocation, Callbacks callbacks) {
        this.alarms.addAll(alarms);
        this.currentLocation = currentLocation;
        this.callbacks = callbacks;
        sortByDistance();
    }

    public void updateAlarms(List<GeoAlarm> newAlarms, @Nullable Location location) {
        this.alarms.clear();
        this.alarms.addAll(newAlarms);
        this.currentLocation = location;
        sortByDistance();
        notifyDataSetChanged();
    }

    private void sortByDistance() {
        if (currentLocation == null) return;
        final double fromLat = currentLocation.getLatitude();
        final double fromLng = currentLocation.getLongitude();
        Collections.sort(alarms, (a, b) -> {
            float[] ra = new float[1];
            float[] rb = new float[1];
            Location.distanceBetween(fromLat, fromLng, a.location.latitude, a.location.longitude, ra);
            Location.distanceBetween(fromLat, fromLng, b.location.latitude, b.location.longitude, rb);
            return Float.compare(ra[0], rb[0]);
        });
    }

    @NonNull
    @Override
    public AlarmViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_alarm, parent, false);
        return new AlarmViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlarmViewHolder holder, int position) {
        GeoAlarm alarm = alarms.get(position);
        Context ctx = holder.itemView.getContext();

        // Time
        if (alarm.hour != null && alarm.minute != null) {
            LocalTime t = LocalTime.of(alarm.hour, alarm.minute);
            holder.timeView.setText(t.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)));
        } else {
            holder.timeView.setText("—");
        }

        // Days
        holder.daysView.setText(getDaysSummary(alarm.days, ctx));

        // Distance
        if (currentLocation != null) {
            float[] results = new float[1];
            Location.distanceBetween(currentLocation.getLatitude(), currentLocation.getLongitude(),
                    alarm.location.latitude, alarm.location.longitude, results);
            holder.distanceView.setText(formatDistance(ctx, results[0]));
            holder.distanceView.setVisibility(View.VISIBLE);
        } else {
            holder.distanceView.setVisibility(View.GONE);
        }

        // Place
        if (alarm.place != null && !alarm.place.isEmpty()) {
            holder.placeView.setText(alarm.place);
        } else {
            holder.placeView.setText(String.format(Locale.US, "%.4f, %.4f",
                    alarm.location.latitude, alarm.location.longitude));
        }

        // Radius
        holder.radiusView.setText(ctx.getString(R.string.radius_label, alarm.radius));

        // Switch (remove old listener first to avoid spurious callbacks during rebind)
        holder.enabledSwitch.setOnCheckedChangeListener(null);
        holder.enabledSwitch.setChecked(alarm.enabled);
        holder.enabledSwitch.setOnCheckedChangeListener((btn, checked) ->
                callbacks.onToggleEnabled(alarm, checked));

        // Card click
        holder.itemView.setOnClickListener(v -> callbacks.onEdit(alarm));

        // Pencil icon
        holder.placeEditIcon.setOnClickListener(v -> showRenameDialog(ctx, alarm));
    }

    private void showRenameDialog(Context ctx, GeoAlarm alarm) {
        EditText input = new EditText(ctx);
        input.setText(alarm.place != null ? alarm.place : "");
        input.setSingleLine(true);
        input.setHint(R.string.rename_place_hint);
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.rename_place)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String newPlace = input.getText().toString().trim();
                    callbacks.onRenamePlace(alarm, newPlace);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public int getItemCount() {
        return alarms.size();
    }

    private String getDaysSummary(@Nullable Set<DayOfWeek> days, Context ctx) {
        if (days == null || days.isEmpty()) return ctx.getString(R.string.days_once);
        if (days.size() == 7) return ctx.getString(R.string.days_every_day);
        DayOfWeek[] ordered = {
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
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

    private String formatDistance(Context ctx, float meters) {
        if (meters < 1000) {
            return ctx.getString(R.string.distance_meters, (int) meters);
        } else {
            return ctx.getString(R.string.distance_km, meters / 1000f);
        }
    }

    static class AlarmViewHolder extends RecyclerView.ViewHolder {
        final TextView timeView;
        final TextView daysView;
        final TextView distanceView;
        final TextView placeView;
        final ImageView placeEditIcon;
        final TextView radiusView;
        final SwitchCompat enabledSwitch;

        AlarmViewHolder(@NonNull View itemView) {
            super(itemView);
            timeView = itemView.findViewById(R.id.alarm_time);
            daysView = itemView.findViewById(R.id.alarm_days);
            distanceView = itemView.findViewById(R.id.alarm_distance);
            placeView = itemView.findViewById(R.id.alarm_place);
            placeEditIcon = itemView.findViewById(R.id.alarm_place_edit);
            radiusView = itemView.findViewById(R.id.alarm_radius);
            enabledSwitch = itemView.findViewById(R.id.alarm_enabled_switch);
        }
    }
}
