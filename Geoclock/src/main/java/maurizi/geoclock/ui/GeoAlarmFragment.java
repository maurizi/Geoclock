package maurizi.geoclock.ui;

import static com.google.common.collect.ImmutableSet.copyOf;
import static com.google.common.collect.Maps.filterValues;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
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
import maurizi.geoclock.utils.AddressFormatter;
import maurizi.geoclock.utils.DistanceUtils;
import maurizi.geoclock.utils.LocationServiceGoogle;
import maurizi.geoclock.utils.PermissionHelper;

public class GeoAlarmFragment extends DialogFragment {

  @Setter private LocationServiceGoogle locationService;
  private static final Gson gson = new Gson();

  public static final String INITIAL_LATLNG = "INITIAL_LATLNG";
  public static final String INITIAL_ZOOM = "INITIAL_ZOOM";
  public static final String EXISTING_ALARM = "ALARM";

  private static final int INITIAL_RADIUS_METRIC = 250; // 500m wide
  private static final int INITIAL_RADIUS_IMPERIAL = 244; // 1600ft wide

  private EditText locationPreview;
  private TextView radiusPreview;
  private TimePicker timePicker;
  private Button cancelButton;
  private Button deleteButton;
  private Button saveButton;
  private TextView ringtoneNameView;

  private MapView mapThumbnailView;
  private GoogleMap mapThumbnail;

  private LatLng currentLatLng;
  private int currentRadius = INITIAL_RADIUS_METRIC;
  @Nullable private String currentPlaceName;
  private boolean locationChanged = false;
  private boolean setupDone = false;
  @Nullable private String selectedRingtoneUri;
  private boolean ringtoneUriSet = false;

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private ActivityResultLauncher<Intent> locationPickerLauncher;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    locationPickerLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                double lat = result.getData().getDoubleExtra(LocationPickerActivity.EXTRA_LAT, 0);
                double lng = result.getData().getDoubleExtra(LocationPickerActivity.EXTRA_LNG, 0);
                currentLatLng = new LatLng(lat, lng);
                currentRadius =
                    result
                        .getData()
                        .getIntExtra(LocationPickerActivity.EXTRA_RADIUS, currentRadius);
                currentPlaceName =
                    result.getData().getStringExtra(LocationPickerActivity.EXTRA_PLACE);
                locationChanged = true;
                updateLocationPreview();
                updateMapThumbnail();
              }
            });
  }

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    final View dialogView =
        inflater.inflate(R.layout.fragment_add_geo_alarm_dialog, container, false);
    locationPreview = dialogView.findViewById(R.id.location_preview);
    radiusPreview = dialogView.findViewById(R.id.radius_preview);
    timePicker = dialogView.findViewById(R.id.add_geo_alarm_time);
    cancelButton = dialogView.findViewById(R.id.add_geo_alarm_cancel);
    deleteButton = dialogView.findViewById(R.id.add_geo_alarm_delete);
    saveButton = dialogView.findViewById(R.id.add_geo_alarm_save);
    ringtoneNameView = dialogView.findViewById(R.id.ringtone_name);
    hideTimePickerToggle(timePicker);

    mapThumbnailView = dialogView.findViewById(R.id.map_thumbnail);
    mapThumbnailView.onCreate(savedInstanceState);
    mapThumbnailView.getMapAsync(
        map -> {
          mapThumbnail = map;
          map.getUiSettings().setAllGesturesEnabled(false);
          map.getUiSettings().setZoomControlsEnabled(false);
          map.getUiSettings().setMapToolbarEnabled(false);
          updateMapThumbnail();
        });

    dialogView.findViewById(R.id.map_tap_overlay).setOnClickListener(v -> openLocationPicker());

    return dialogView;
  }

  private void setupIfNeeded() {
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
    if (currentPlaceName == null) {
      currentPlaceName = alarm.place;
    }
    updateLocationPreview();

    final Map<DayOfWeek, CheckBox> checkboxes = getWeekdaysCheckBoxMap(getView());

    // Initialize ringtone from alarm
    if (isEdit) {
      selectedRingtoneUri = alarm.ringtoneUri;
    } else {
      selectedRingtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM).toString();
    }
    ringtoneUriSet = true;
    updateRingtoneLabel();

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

    // Ringtone picker — whole row is tappable
    View ringtoneRow = getView() != null ? getView().findViewById(R.id.ringtone_row) : null;
    if (ringtoneRow != null) {
      ringtoneRow.setOnClickListener(v -> showRingtonePicker());
    }

    cancelButton.setOnClickListener(
        v -> {
          if (dialog != null) dialog.cancel();
        });

    if (isEdit) {
      deleteButton.setVisibility(View.VISIBLE);
      deleteButton.setOnClickListener(
          v ->
              new AlertDialog.Builder(activity)
                  .setTitle(R.string.delete_confirm_title)
                  .setMessage(R.string.delete_confirm_message)
                  .setPositiveButton(
                      R.string.add_geo_alarm_delete,
                      (d, w) -> {
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

    saveButton.setOnClickListener(
        v -> {
          if (currentLatLng == null) {
            Toast.makeText(activity, R.string.fail_location, Toast.LENGTH_SHORT).show();
            return;
          }
          final Set<DayOfWeek> days =
              copyOf(filterValues(checkboxes, CompoundButton::isChecked).keySet());
          // Read place from the editable location preview
          String placeText = locationPreview.getText().toString().trim();
          String place = placeText.isEmpty() ? null : placeText;

          // For new alarms, always enabled. For edits, preserve current state.
          boolean enabled = isEdit ? alarm.enabled : true;

          // Ringtone: use selected if explicitly set, otherwise preserve alarm's value
          String ringtone = ringtoneUriSet ? selectedRingtoneUri : alarm.ringtoneUri;

          final GeoAlarm newAlarm =
              GeoAlarm.builder()
                  .location(currentLatLng)
                  .place(place)
                  .radius(currentRadius)
                  .days(days)
                  .hour(getTimePickerHour(timePicker))
                  .minute(getTimePickerMinute(timePicker))
                  .enabled(enabled)
                  .ringtoneUri(ringtone)
                  .id(alarm.id)
                  .build();

          if (isEdit) {
            GeoAlarm.remove(activity, alarm);
            if (alarm.enabled && locationService != null) {
              locationService.removeGeofence(alarm);
            }
          }

          // Request permissions just-in-time when saving an enabled alarm
          if (newAlarm.enabled && !PermissionHelper.hasAllAlarmPermissions(activity)) {
            PermissionHelper.requestAlarmPermissions(
                activity, () -> completeSave(activity, dialog, newAlarm, isEdit));
          } else {
            completeSave(activity, dialog, newAlarm, isEdit);
          }
        });
  }

  private void completeSave(
      MapActivity activity, @Nullable Dialog dialog, GeoAlarm newAlarm, boolean isEdit) {
    if (newAlarm.enabled && locationService != null) {
      locationService
          .addGeofence(newAlarm)
          .addOnSuccessListener(aVoid -> finishSave(activity, dialog, newAlarm))
          .addOnFailureListener(
              e -> {
                // Save the alarm even if geofence registration fails —
                // it will be re-registered on next resume via activateAlarmsInsideGeofence
                finishSave(activity, dialog, newAlarm);
              });
    } else {
      finishSave(activity, dialog, newAlarm);
    }
  }

  private void updateRingtoneLabel() {
    if (ringtoneNameView == null) return;
    if (!ringtoneUriSet) {
      ringtoneNameView.setText(R.string.ringtone_default);
    } else if (selectedRingtoneUri == null) {
      ringtoneNameView.setText(R.string.ringtone_vibrate_only);
    } else {
      android.media.Ringtone r =
          RingtoneManager.getRingtone(
              ringtoneNameView.getContext(), Uri.parse(selectedRingtoneUri));
      if (r != null) {
        ringtoneNameView.setText(r.getTitle(ringtoneNameView.getContext()));
      } else {
        ringtoneNameView.setText(R.string.ringtone_default);
      }
    }
  }

  private void updateLocationPreview() {
    if (locationPreview == null || currentLatLng == null) return;
    if (currentPlaceName != null && !currentPlaceName.isEmpty()) {
      locationPreview.setText(currentPlaceName);
    } else {
      locationPreview.setText(
          String.format(Locale.US, "%.4f, %.4f", currentLatLng.latitude, currentLatLng.longitude));
      reverseGeocodeForPreview();
    }
    if (radiusPreview != null && getContext() != null) {
      radiusPreview.setText(GeoAlarm.getRadiusSizeLabel(getContext(), currentRadius));
    }
    updateMapThumbnail();
  }

  @SuppressWarnings("deprecation")
  private void reverseGeocodeForPreview() {
    Context ctx = getContext();
    if (ctx == null || !Geocoder.isPresent() || currentLatLng == null) return;
    final LatLng target = currentLatLng;
    executor.execute(
        () -> {
          try {
            Geocoder geocoder = new Geocoder(ctx, Locale.getDefault());
            List<Address> addresses =
                geocoder.getFromLocation(target.latitude, target.longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
              Address addr = addresses.get(0);
              String place = AddressFormatter.shortAddress(addr);
              if (place != null) {
                new Handler(Looper.getMainLooper())
                    .post(
                        () -> {
                          if (locationPreview != null && target.equals(currentLatLng)) {
                            currentPlaceName = place;
                            locationPreview.setText(place);
                          }
                        });
              }
            }
          } catch (IOException e) {
            // best-effort
          }
        });
  }

  private void finishSave(MapActivity activity, @Nullable Dialog dialog, GeoAlarm newAlarm) {
    GeoAlarm.save(activity, newAlarm);
    ActiveAlarmManager aam = new ActiveAlarmManager(activity);
    if (newAlarm.enabled) {
      aam.addActiveAlarms(ImmutableSet.of(newAlarm.id));
    } else {
      aam.removeActiveAlarms(ImmutableSet.of(newAlarm.id));
    }
    if (newAlarm.place == null) {
      geocodeAsync(activity, newAlarm);
    }
    activity.onAddGeoAlarmFragmentClose();
    if (dialog != null) dialog.dismiss();
  }

  @SuppressWarnings("deprecation")
  private void geocodeAsync(MapActivity activity, GeoAlarm alarm) {
    if (!Geocoder.isPresent()) return;
    Context appContext = activity.getApplicationContext();
    Geocoder geocoder = new Geocoder(appContext, Locale.getDefault());
    executor.execute(
        () -> {
          try {
            List<Address> addresses =
                geocoder.getFromLocation(alarm.location.latitude, alarm.location.longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
              Address addr = addresses.get(0);
              String place =
                  addr.getLocality() != null
                      ? addr.getLocality()
                      : addr.getSubAdminArea() != null
                          ? addr.getSubAdminArea()
                          : addr.getAddressLine(0);
              if (place != null) {
                GeoAlarm.save(appContext, alarm.withPlace(place));
                new Handler(Looper.getMainLooper())
                    .post(
                        () -> {
                          if (!activity.isFinishing()) {
                            activity.onAddGeoAlarmFragmentClose();
                          }
                        });
              }
            }
          } catch (IOException e) {
            // no-op — geocoding is best-effort
          }
        });
  }

  private void showRingtonePicker() {
    Context context = getContext();
    if (context == null) return;
    RingtoneManager rm = new RingtoneManager(context);
    rm.setType(RingtoneManager.TYPE_ALARM);
    Cursor cursor = rm.getCursor();

    java.util.List<String> names = new java.util.ArrayList<>();
    java.util.List<String> uris = new java.util.ArrayList<>();

    // Prepend special entries
    names.add(getString(R.string.ringtone_vibrate_only));
    uris.add(null);
    names.add(getString(R.string.ringtone_default));
    uris.add(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM).toString());

    java.util.Set<String> seenTitles = new java.util.HashSet<>();
    while (cursor.moveToNext()) {
      String title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX);
      if (!seenTitles.add(title)) continue;
      Uri ringtoneUri = rm.getRingtoneUri(cursor.getPosition());
      names.add(title);
      uris.add(ringtoneUri != null ? ringtoneUri.toString() : null);
    }
    cursor.close();

    // Find currently selected index
    int checkedItem = -1;
    if (!ringtoneUriSet) {
      checkedItem = 1; // default
    } else if (selectedRingtoneUri == null) {
      checkedItem = 0; // vibrate only
    } else {
      for (int i = 0; i < uris.size(); i++) {
        if (selectedRingtoneUri.equals(uris.get(i))) {
          checkedItem = i;
          break;
        }
      }
    }

    // Save current state so cancel can restore it
    final String prevUri = selectedRingtoneUri;
    final boolean prevUriSet = ringtoneUriSet;
    final Ringtone[] preview = {null};

    String[] items = names.toArray(new String[0]);
    AlertDialog dialog =
        new AlertDialog.Builder(context)
            .setTitle(R.string.ringtone_label)
            .setSingleChoiceItems(
                items,
                checkedItem,
                (dlg, which) -> {
                  // Stop any current preview
                  if (preview[0] != null) {
                    preview[0].stop();
                    preview[0] = null;
                  }

                  selectedRingtoneUri = uris.get(which);
                  ringtoneUriSet = true;

                  // Play preview
                  if (selectedRingtoneUri == null) {
                    // Vibrate only — buzz preview
                    Vibrator vibrator = context.getSystemService(Vibrator.class);
                    if (vibrator != null) {
                      vibrator.vibrate(
                          VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                    }
                  } else {
                    Ringtone ringtone =
                        RingtoneManager.getRingtone(context, Uri.parse(selectedRingtoneUri));
                    if (ringtone != null) {
                      ringtone.play();
                      preview[0] = ringtone;
                    }
                  }
                })
            .setPositiveButton(
                android.R.string.ok,
                (dlg, which) -> {
                  updateRingtoneLabel();
                })
            .setNegativeButton(
                R.string.add_geo_alarm_cancel,
                (dlg, which) -> {
                  // Restore previous selection
                  selectedRingtoneUri = prevUri;
                  ringtoneUriSet = prevUriSet;
                })
            .create();

    dialog.setOnDismissListener(
        dlg -> {
          if (preview[0] != null) {
            preview[0].stop();
            preview[0] = null;
          }
        });

    dialog.show();
  }

  private void openLocationPicker() {
    MapActivity activity = (MapActivity) getActivity();
    if (activity == null) return;
    Intent intent = new Intent(activity, LocationPickerActivity.class);
    if (currentLatLng != null) {
      intent.putExtra(LocationPickerActivity.EXTRA_INITIAL_LAT, currentLatLng.latitude);
      intent.putExtra(LocationPickerActivity.EXTRA_INITIAL_LNG, currentLatLng.longitude);
    }
    intent.putExtra(LocationPickerActivity.EXTRA_INITIAL_RADIUS, currentRadius);
    if (currentPlaceName != null) {
      intent.putExtra(LocationPickerActivity.EXTRA_PLACE, currentPlaceName);
    }
    locationPickerLauncher.launch(intent);
  }

  private void updateMapThumbnail() {
    if (mapThumbnail == null || currentLatLng == null) return;
    mapThumbnail.clear();
    mapThumbnail.addMarker(new MarkerOptions().position(currentLatLng));
    mapThumbnail.addCircle(
        new CircleOptions()
            .center(currentLatLng)
            .radius(currentRadius)
            .fillColor(0x3300C5CD)
            .strokeColor(0xFF00C5CD)
            .strokeWidth(2));
    double latOffset = currentRadius / 111_320.0;
    double lngOffset =
        currentRadius / (111_320.0 * Math.cos(Math.toRadians(currentLatLng.latitude)));
    LatLngBounds bounds =
        new LatLngBounds.Builder()
            .include(new LatLng(currentLatLng.latitude + latOffset, currentLatLng.longitude))
            .include(new LatLng(currentLatLng.latitude - latOffset, currentLatLng.longitude))
            .include(new LatLng(currentLatLng.latitude, currentLatLng.longitude + lngOffset))
            .include(new LatLng(currentLatLng.latitude, currentLatLng.longitude - lngOffset))
            .build();
    // Post to ensure map view has been laid out (newLatLngBounds requires non-zero size)
    mapThumbnailView.post(
        () -> {
          if (mapThumbnail == null) return;
          mapThumbnail.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 32));
        });
  }

  @Override
  public void onResume() {
    super.onResume();
    if (mapThumbnailView != null) mapThumbnailView.onResume();
    setupIfNeeded();
  }

  @Override
  public void onPause() {
    super.onPause();
    if (mapThumbnailView != null) mapThumbnailView.onPause();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    if (mapThumbnailView != null) mapThumbnailView.onDestroy();
    mapThumbnail = null;
    executor.shutdownNow();
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle out) {
    super.onSaveInstanceState(out);
    if (mapThumbnailView != null) mapThumbnailView.onSaveInstanceState(out);
  }

  @Override
  public void onLowMemory() {
    super.onLowMemory();
    if (mapThumbnailView != null) mapThumbnailView.onLowMemory();
  }

  @android.annotation.SuppressLint("DiscouragedApi") // No non-reflective way to hide the toggle
  private void hideTimePickerToggle(TimePicker tp) {
    try {
      int id = tp.getResources().getIdentifier("toggle_mode", "id", "android");
      if (id != 0) {
        View toggle = tp.findViewById(id);
        if (toggle != null) {
          toggle.setVisibility(View.GONE);
        }
      }
    } catch (Exception ignored) {
      // Not all devices have this view
    }
  }

  private int getInitialRadius() {
    Context ctx = getContext();
    if (ctx != null && DistanceUtils.useImperial(ctx)) {
      return INITIAL_RADIUS_IMPERIAL;
    }
    return INITIAL_RADIUS_METRIC;
  }

  private GeoAlarm getEffectiveGeoAlarm(final Bundle args, final boolean isEdit) {
    if (isEdit) {
      return gson.fromJson(args.getString(GeoAlarmFragment.EXISTING_ALARM), GeoAlarm.class);
    }
    LatLng initialPoint = args.getParcelable(GeoAlarmFragment.INITIAL_LATLNG, LatLng.class);
    return GeoAlarm.builder()
        .location(initialPoint != null ? initialPoint : new LatLng(0, 0))
        .radius(getInitialRadius())
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
    tp.setHour(hour);
  }

  private void setTimePickerMinute(TimePicker tp, int minute) {
    tp.setMinute(minute);
  }

  private int getTimePickerHour(TimePicker tp) {
    return tp.getHour();
  }

  private int getTimePickerMinute(TimePicker tp) {
    return tp.getMinute();
  }
}
