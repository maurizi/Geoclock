package maurizi.geoclock.ui;

import android.app.Dialog;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.DialogFragment;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;

import java.time.DayOfWeek;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.R;
import maurizi.geoclock.utils.ActiveAlarmManager;
import maurizi.geoclock.utils.LocationServiceGoogle;

import static com.google.common.collect.ImmutableSet.copyOf;
import static com.google.common.collect.Maps.filterValues;

public class GeoAlarmFragment extends DialogFragment {

    private SupportMapFragment mapFragment;
    private LocationServiceGoogle locationService;
    private GoogleMap fragmentMap;

    private static final Gson gson = new Gson();

    public final static String INITIAL_LATLNG = "INITIAL_LATLNG";
    public final static String INITIAL_ZOOM = "INITIAL_ZOOM";
    public static final String EXISTING_ALARM = "ALARM";

    private final static int INITIAL_RADIUS = 20;
    private final static int MAX_RADIUS = 200;

    private LockableScrollView scrollView;
    private TextView nameTextBox;
    private SeekBar radiusBar;
    private TimePicker timePicker;
    private SwitchCompat enabledSwitch;
    private Button cancelButton;
    private Button deleteButton;
    private Button saveButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View dialogView = inflater.inflate(R.layout.fragment_add_geo_alarm_dialog, container, false);
        scrollView = dialogView.findViewById(R.id.scrollView);
        nameTextBox = dialogView.findViewById(R.id.add_geo_alarm_name);
        radiusBar = dialogView.findViewById(R.id.add_geo_alarm_radius);
        timePicker = dialogView.findViewById(R.id.add_geo_alarm_time);
        enabledSwitch = dialogView.findViewById(R.id.add_geo_alarm_enabled);
        cancelButton = dialogView.findViewById(R.id.add_geo_alarm_cancel);
        deleteButton = dialogView.findViewById(R.id.add_geo_alarm_delete);
        saveButton = dialogView.findViewById(R.id.add_geo_alarm_save);
        return dialogView;
    }

    @Override
    public void onStart() {
        super.onStart();
        mapFragment = SupportMapFragment.newInstance();
        getChildFragmentManager().beginTransaction().replace(R.id.add_geo_alarm_map_container, mapFragment).commit();
    }

    @Override
    public void onResume() {
        super.onResume();

        final MapActivity activity = (MapActivity) getActivity();

        if (mapFragment == null) {
            Toast.makeText(activity, R.string.fail_map, Toast.LENGTH_SHORT).show();
            return;
        }

        mapFragment.getMapAsync(map -> {
            fragmentMap = map;
            setupMap(activity, map);
        });
    }

    private void setupMap(MapActivity activity, GoogleMap map) {
        final View dialogView = getView();
        if (dialogView == null) return;

        final Bundle args = getArguments();
        final boolean isEdit = args.containsKey(GeoAlarmFragment.EXISTING_ALARM);
        final LatLng initialPoint = args.getParcelable(GeoAlarmFragment.INITIAL_LATLNG);
        final float initialZoom = args.getFloat(GeoAlarmFragment.INITIAL_ZOOM);
        final GeoAlarm alarm = getEffectiveGeoAlarm(args, isEdit, initialPoint);
        final Dialog dialog = getDialog();

        final Map<DayOfWeek, CheckBox> checkboxes = getWeekdaysCheckBoxMap(dialogView);

        radiusBar.setMax(MAX_RADIUS);
        radiusBar.setProgress(alarm.radius);
        enabledSwitch.setChecked(alarm.enabled);

        map.moveCamera(CameraUpdateFactory.newLatLngZoom(alarm.location, initialZoom));
        final Marker marker = map.addMarker(new MarkerOptions().position(alarm.location).draggable(true));

        final Circle circle = map.addCircle(new CircleOptions().center(alarm.location)
                .radius(alarm.radius)
                .fillColor(R.color.geofence_fill_color));

        if (isEdit) {
            nameTextBox.setText(alarm.name);
            if (alarm.hour != null) {
                setTimePickerHour(timePicker, alarm.hour);
            }
            if (alarm.minute != null) {
                setTimePickerMinute(timePicker, alarm.minute);
            }
            if (alarm.days != null) {
                for (DayOfWeek day : alarm.days) {
                    CheckBox cb = checkboxes.get(day);
                    if (cb != null) cb.setChecked(true);
                }
            }
        }

        map.getUiSettings().setAllGesturesEnabled(false);
        map.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(Marker marker) {
                circle.setCenter(marker.getPosition());
                map.animateCamera(CameraUpdateFactory.newLatLng(marker.getPosition()));
                if (scrollView != null) scrollView.setScrollingEnabled(false);
            }

            @Override
            public void onMarkerDrag(Marker marker) {
                circle.setCenter(marker.getPosition());
                map.animateCamera(CameraUpdateFactory.newLatLng(marker.getPosition()));
            }

            @Override
            public void onMarkerDragEnd(Marker marker) {
                if (scrollView != null) scrollView.setScrollingEnabled(true);
            }
        });

        radiusBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                circle.setRadius(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });

        cancelButton.setOnClickListener(view -> { if (dialog != null) dialog.cancel(); });

        if (dialog != null) {
            dialog.setTitle(R.string.add_geo_alarm_title);
        }
        if (isEdit) {
            if (dialog != null) dialog.setTitle(R.string.edit_title);
            deleteButton.setOnClickListener(view -> {
                GeoAlarm.remove(activity, alarm);
                ActiveAlarmManager alarmManager = new ActiveAlarmManager(activity);
                alarmManager.removeActiveAlarms(ImmutableSet.of(alarm.id));
                locationService.removeGeofence(alarm);
                activity.onAddGeoAlarmFragmentClose();
                if (dialog != null) dialog.dismiss();
            });
        } else {
            deleteButton.setVisibility(View.GONE);
        }

        saveButton.setOnClickListener(view -> {
            final String name = nameTextBox.getText().toString();

            if (name.isEmpty()) {
                Toast.makeText(activity, R.string.add_geo_alarm_validation, Toast.LENGTH_SHORT).show();
                return;
            }

            final Set<DayOfWeek> days = copyOf(filterValues(checkboxes, CompoundButton::isChecked).keySet());
            final GeoAlarm newAlarm = GeoAlarm.builder()
                    .location(marker.getPosition())
                    .name(name)
                    .radius(radiusBar.getProgress())
                    .days(days)
                    .hour(getTimePickerHour(timePicker))
                    .minute(getTimePickerMinute(timePicker))
                    .enabled(enabledSwitch.isChecked())
                    .id(alarm.id)
                    .build();

            if (isEdit) {
                GeoAlarm.remove(activity, alarm);
                if (alarm.enabled) {
                    locationService.removeGeofence(alarm);
                }
            }
            if (newAlarm.enabled) {
                locationService.addGeofence(newAlarm)
                        .addOnSuccessListener(aVoid -> finish(activity, dialog, newAlarm))
                        .addOnFailureListener(e ->
                                Toast.makeText(activity, R.string.fail_location, Toast.LENGTH_SHORT).show());
            } else {
                finish(activity, dialog, newAlarm);
            }
        });
    }

    private void finish(MapActivity activity, Dialog dialog, GeoAlarm newAlarm) {
        GeoAlarm.save(activity, newAlarm);
        activity.onAddGeoAlarmFragmentClose();
        if (dialog != null) dialog.dismiss();
    }

    public void setLocationService(LocationServiceGoogle ls) {
        this.locationService = ls;
    }

    private GeoAlarm getEffectiveGeoAlarm(final Bundle args, final boolean isEdit, final LatLng initialPoint) {
        return isEdit
                ? gson.fromJson(args.getString(GeoAlarmFragment.EXISTING_ALARM), GeoAlarm.class)
                : GeoAlarm.builder()
                        .location(initialPoint)
                        .radius(INITIAL_RADIUS)
                        .name("")
                        .id(UUID.randomUUID())
                        .enabled(true)
                        .build();
    }

    private Map<DayOfWeek, CheckBox> getWeekdaysCheckBoxMap(final View dialogView) {
        return ImmutableMap.<DayOfWeek, CheckBox>builder()
                .put(DayOfWeek.SUNDAY, (CheckBox) dialogView.findViewById(R.id.sun))
                .put(DayOfWeek.MONDAY, (CheckBox) dialogView.findViewById(R.id.mon))
                .put(DayOfWeek.TUESDAY, (CheckBox) dialogView.findViewById(R.id.tues))
                .put(DayOfWeek.WEDNESDAY, (CheckBox) dialogView.findViewById(R.id.wed))
                .put(DayOfWeek.THURSDAY, (CheckBox) dialogView.findViewById(R.id.thu))
                .put(DayOfWeek.FRIDAY, (CheckBox) dialogView.findViewById(R.id.fri))
                .put(DayOfWeek.SATURDAY, (CheckBox) dialogView.findViewById(R.id.sat))
                .build();
    }

    @SuppressWarnings("deprecation")
    private void setTimePickerHour(TimePicker tp, int hour) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            tp.setHour(hour);
        } else {
            tp.setCurrentHour(hour);
        }
    }

    @SuppressWarnings("deprecation")
    private void setTimePickerMinute(TimePicker tp, int minute) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            tp.setMinute(minute);
        } else {
            tp.setCurrentMinute(minute);
        }
    }

    @SuppressWarnings("deprecation")
    private int getTimePickerHour(TimePicker tp) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return tp.getHour();
        } else {
            return tp.getCurrentHour();
        }
    }

    @SuppressWarnings("deprecation")
    private int getTimePickerMinute(TimePicker tp) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return tp.getMinute();
        } else {
            return tp.getCurrentMinute();
        }
    }
}
