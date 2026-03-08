package maurizi.geoclock.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.DialogFragment;

import com.google.android.gms.maps.model.LatLng;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;

import java.io.IOException;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lombok.Setter;
import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.R;
import maurizi.geoclock.utils.ActiveAlarmManager;
import maurizi.geoclock.utils.LocationServiceGoogle;

import static com.google.common.collect.ImmutableSet.copyOf;
import static com.google.common.collect.Maps.filterValues;

public class GeoAlarmFragment extends DialogFragment {

    @Setter private LocationServiceGoogle locationService;
    private static final Gson gson = new Gson();

    public final static String INITIAL_LATLNG = "INITIAL_LATLNG";
    public final static String INITIAL_ZOOM = "INITIAL_ZOOM";
    public static final String EXISTING_ALARM = "ALARM";

    private final static int INITIAL_RADIUS = 20;

    private TextView locationPreview;
    private TextView radiusPreview;
    private TimePicker timePicker;
    private SwitchCompat enabledSwitch;
    private Button cancelButton;
    private Button deleteButton;
    private Button saveButton;

    private LatLng currentLatLng;
    private int currentRadius = INITIAL_RADIUS;
    private boolean locationChanged = false;
    private boolean setupDone = false;

    private ActivityResultLauncher<Intent> locationPickerLauncher;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        locationPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        double lat = result.getData().getDoubleExtra(LocationPickerActivity.EXTRA_LAT, 0);
                        double lng = result.getData().getDoubleExtra(LocationPickerActivity.EXTRA_LNG, 0);
                        currentLatLng = new LatLng(lat, lng);
                        currentRadius = result.getData().getIntExtra(LocationPickerActivity.EXTRA_RADIUS, currentRadius);
                        locationChanged = true;
                        updateLocationPreview();
                    }
                }
        );
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View dialogView = inflater.inflate(R.layout.fragment_add_geo_alarm_dialog, container, false);
        locationPreview = dialogView.findViewById(R.id.location_preview);
        radiusPreview = dialogView.findViewById(R.id.radius_preview);
        timePicker = dialogView.findViewById(R.id.add_geo_alarm_time);
        enabledSwitch = dialogView.findViewById(R.id.add_geo_alarm_enabled);
        cancelButton = dialogView.findViewById(R.id.add_geo_alarm_cancel);
        deleteButton = dialogView.findViewById(R.id.add_geo_alarm_delete);
        saveButton = dialogView.findViewById(R.id.add_geo_alarm_save);
        return dialogView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (setupDone) return;
        setupDone = true;

        final MapActivity activity = (MapActivity) getActivity();
        if (activity == null) return;

        final Bundle args = getArguments();
        if (args == null) return;

        final boolean isEdit = args.containsKey(GeoAlarmFragment.EXISTING_ALARM);
        final GeoAlarm alarm = getEffectiveGeoAlarm(args, isEdit);
        final Dialog dialog = getDialog();

        if (currentLatLng == null) {
            currentLatLng = alarm.location;
        }
        currentRadius = alarm.radius;
        updateLocationPreview();

        final Map<DayOfWeek, CheckBox> checkboxes = getWeekdaysCheckBoxMap(getView());

        enabledSwitch.setChecked(alarm.enabled);

        if (isEdit) {
            if (alarm.hour != null) setTimePickerHour(timePicker, alarm.hour);
            if (alarm.minute != null) setTimePickerMinute(timePicker, alarm.minute);
            if (alarm.days != null) {
                for (DayOfWeek day : alarm.days) {
                    CheckBox cb = checkboxes.get(day);
                    if (cb != null) cb.setChecked(true);
                }
            }
        }

        if (dialog != null) {
            if (isEdit) {
                dialog.setTitle("Edit · " + (alarm.enabled ? "Active" : "Paused"));
            } else {
                dialog.setTitle(R.string.add_geo_alarm_title);
            }
        }

        View changeLocationButton = getView() != null ? getView().findViewById(R.id.change_location_button) : null;
        if (changeLocationButton != null) {
            changeLocationButton.setOnClickListener(v -> {
                Intent intent = new Intent(activity, LocationPickerActivity.class);
                if (currentLatLng != null) {
                    intent.putExtra(LocationPickerActivity.EXTRA_INITIAL_LAT, currentLatLng.latitude);
                    intent.putExtra(LocationPickerActivity.EXTRA_INITIAL_LNG, currentLatLng.longitude);
                }
                intent.putExtra(LocationPickerActivity.EXTRA_INITIAL_RADIUS, currentRadius);
                locationPickerLauncher.launch(intent);
            });
        }

        cancelButton.setOnClickListener(v -> { if (dialog != null) dialog.cancel(); });

        if (isEdit) {
            deleteButton.setVisibility(View.VISIBLE);
            deleteButton.setOnClickListener(v ->
                new AlertDialog.Builder(activity)
                    .setTitle(R.string.delete_confirm_title)
                    .setMessage(R.string.delete_confirm_message)
                    .setPositiveButton(R.string.add_geo_alarm_delete, (d, w) -> {
                        GeoAlarm.remove(activity, alarm);
                        ActiveAlarmManager alarmManager = new ActiveAlarmManager(activity);
                        alarmManager.removeActiveAlarms(ImmutableSet.of(alarm.id));
                        if (locationService != null) locationService.removeGeofence(alarm);
                        activity.onAddGeoAlarmFragmentClose();
                        if (dialog != null) dialog.dismiss();
                    })
                    .setNegativeButton(R.string.add_geo_alarm_cancel, null)
                    .show());
        }

        saveButton.setOnClickListener(v -> {
            if (currentLatLng == null) {
                Toast.makeText(activity, R.string.fail_location, Toast.LENGTH_SHORT).show();
                return;
            }
            final Set<DayOfWeek> days = copyOf(filterValues(checkboxes, CompoundButton::isChecked).keySet());
            // Preserve place unless location changed
            String place = (alarm.place != null && !locationChanged) ? alarm.place : null;
            final GeoAlarm newAlarm = GeoAlarm.builder()
                    .location(currentLatLng)
                    .place(place)
                    .radius(currentRadius)
                    .days(days)
                    .hour(getTimePickerHour(timePicker))
                    .minute(getTimePickerMinute(timePicker))
                    .enabled(enabledSwitch.isChecked())
                    .id(alarm.id)
                    .build();

            if (isEdit) {
                GeoAlarm.remove(activity, alarm);
                if (alarm.enabled && locationService != null) {
                    locationService.removeGeofence(alarm);
                }
            }

            if (newAlarm.enabled && locationService != null) {
                locationService.addGeofence(newAlarm)
                        .addOnSuccessListener(aVoid -> finishSave(activity, dialog, newAlarm))
                        .addOnFailureListener(e ->
                                Toast.makeText(activity, R.string.fail_location, Toast.LENGTH_SHORT).show());
            } else {
                finishSave(activity, dialog, newAlarm);
            }
        });
    }

    private void updateLocationPreview() {
        if (locationPreview == null || currentLatLng == null) return;
        locationPreview.setText(String.format(Locale.US, "%.4f, %.4f",
                currentLatLng.latitude, currentLatLng.longitude));
        if (radiusPreview != null) {
            radiusPreview.setText(currentRadius + "m radius");
        }
    }

    private void finishSave(MapActivity activity, @Nullable Dialog dialog, GeoAlarm newAlarm) {
        GeoAlarm.save(activity, newAlarm);
        if (newAlarm.place == null) {
            geocodeAsync(activity, newAlarm);
        }
        activity.onAddGeoAlarmFragmentClose();
        if (dialog != null) dialog.dismiss();
    }

    private void geocodeAsync(MapActivity activity, GeoAlarm alarm) {
        if (!Geocoder.isPresent()) return;
        Geocoder geocoder = new Geocoder(activity, Locale.getDefault());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                List<Address> addresses;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Use blocking variant on background thread for simplicity
                    addresses = geocoder.getFromLocation(
                            alarm.location.latitude, alarm.location.longitude, 1);
                } else {
                    //noinspection deprecation
                    addresses = geocoder.getFromLocation(
                            alarm.location.latitude, alarm.location.longitude, 1);
                }
                if (addresses != null && !addresses.isEmpty()) {
                    Address addr = addresses.get(0);
                    String place = addr.getLocality() != null ? addr.getLocality()
                            : addr.getSubAdminArea() != null ? addr.getSubAdminArea()
                            : addr.getAddressLine(0);
                    if (place != null) {
                        GeoAlarm.save(activity, alarm.withPlace(place));
                        new Handler(Looper.getMainLooper()).post(activity::onAddGeoAlarmFragmentClose);
                    }
                }
            } catch (IOException e) {
                // no-op — geocoding is best-effort
            }
        });
    }

	private GeoAlarm getEffectiveGeoAlarm(final Bundle args, final boolean isEdit) {
        if (isEdit) {
            return gson.fromJson(args.getString(GeoAlarmFragment.EXISTING_ALARM), GeoAlarm.class);
        }
        LatLng initialPoint = args.getParcelable(GeoAlarmFragment.INITIAL_LATLNG);
        return GeoAlarm.builder()
                .location(initialPoint != null ? initialPoint : new LatLng(0, 0))
                .radius(INITIAL_RADIUS)
                .id(UUID.randomUUID())
                .enabled(true)
                .build();
    }

    private Map<DayOfWeek, CheckBox> getWeekdaysCheckBoxMap(final View dialogView) {
        if (dialogView == null) return ImmutableMap.of();
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

    private void setTimePickerHour(TimePicker tp, int hour) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            tp.setHour(hour);
        } else {
            tp.setCurrentHour(hour);
        }
    }

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
