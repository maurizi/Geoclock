package maurizi.geoclock.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.widget.SwitchCompat;
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
import com.google.common.collect.Maps;
import com.google.gson.Gson;

import org.threeten.bp.DayOfWeek;

import java.util.Map;
import java.util.UUID;

import butterknife.ButterKnife;
import butterknife.InjectView;
import lombok.Getter;
import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.R;
import maurizi.geoclock.services.ActiveAlarmManager;
import maurizi.geoclock.services.LocationServiceGoogle;

@Getter
public class GeoAlarmFragment extends DialogFragment {

	private SupportMapFragment mapFragment;
	private LocationServiceGoogle locationService;

	private static final Gson gson = new Gson();

	public final static String INITIAL_LATLNG = "INITIAL_LATLNG";
	public final static String INITIAL_ZOOM = "INITIAL_ZOOM";
	public static final String EXISTING_ALARM = "ALARM";

	private final static int INITIAL_RADIUS = 20;
	private final static int MAX_RADIUS = 200;


	@InjectView(R.id.scrollView) LockableScrollView scrollView;
	@InjectView(R.id.add_geo_alarm_name) TextView nameTextBox;
	@InjectView(R.id.add_geo_alarm_radius) SeekBar radiusBar;
	@InjectView(R.id.add_geo_alarm_time) TimePicker timePicker;
	@InjectView(R.id.add_geo_alarm_enabled) SwitchCompat enabledSwitch;

	@InjectView(R.id.add_geo_alarm_cancel) Button cancelButton;
	@InjectView(R.id.add_geo_alarm_delete) Button deleteButton;
	@InjectView(R.id.add_geo_alarm_save) Button saveButton;

	@Override
	public void onResume() {
		super.onResume();

		final MapActivity activity = (MapActivity) getActivity();
		final GoogleMap map = mapFragment.getMap();
		final View dialogView = getView();

		if (map == null) {
			Toast.makeText(activity, R.string.fail_map, Toast.LENGTH_SHORT).show();
			return;
		}

		final Bundle args = getArguments();
		final boolean isEdit = args.containsKey(GeoAlarmFragment.EXISTING_ALARM);
		final LatLng initalPoint = args.getParcelable(GeoAlarmFragment.INITIAL_LATLNG);
		final float initalZoom = args.getFloat(GeoAlarmFragment.INITIAL_ZOOM);
		final GeoAlarm alarm = getEffectiveGeoAlarm(args, isEdit, initalPoint);
		final Dialog dialog = getDialog();

		final Map<DayOfWeek, CheckBox> checkboxes = getWeekdaysCheckBoxMap(dialogView);

		radiusBar.setMax(MAX_RADIUS);
		radiusBar.setProgress(alarm.radius);
		enabledSwitch.setChecked(alarm.enabled);

		map.moveCamera(CameraUpdateFactory.newLatLngZoom(alarm.location, initalZoom));
		final Marker marker = map.addMarker(new MarkerOptions().position(alarm.location).draggable(true));

		final Circle circle = map.addCircle(new CircleOptions().center(alarm.location)
		                                                       .radius(alarm.radius)
		                                                       .fillColor(R.color.geofence_fill_color));

		if (isEdit) {
			nameTextBox.setText(alarm.name);
			if (alarm.hour != null) {
				timePicker.setCurrentHour(alarm.hour);
			}
			if (alarm.minute != null) {
				timePicker.setCurrentMinute(alarm.minute);
			}
			if (alarm.days != null) {
				for (DayOfWeek day : alarm.days) {
					checkboxes.get(day).setChecked(true);
				}
			}
		}

		map.getUiSettings().setAllGesturesEnabled(false);
		map.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
			@Override
			public void onMarkerDragStart(Marker marker) {
				circle.setCenter(marker.getPosition());
				map.animateCamera(CameraUpdateFactory.newLatLng(marker.getPosition()));
				scrollView.setScrollingEnabled(false);
			}

			@Override
			public void onMarkerDrag(Marker marker) {
				circle.setCenter(marker.getPosition());
				map.animateCamera(CameraUpdateFactory.newLatLng(marker.getPosition()));
			}

			@Override
			public void onMarkerDragEnd(Marker marker) {
				scrollView.setScrollingEnabled(true);
			}
		});

		radiusBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
				circle.setRadius(progress);
			}

			@Override
			public void onStartTrackingTouch(SeekBar bar) {
			}

			@Override
			public void onStopTrackingTouch(SeekBar bar) {
			}
		});

		cancelButton.setOnClickListener(view -> dialog.cancel());

		dialog.setTitle(R.string.add_geo_alarm_title);
		if (isEdit) {
			dialog.setTitle(R.string.edit_title);
			deleteButton.setOnClickListener(view -> {
				GeoAlarm.remove(activity, alarm);
				ActiveAlarmManager alarmManager = new ActiveAlarmManager(activity);
				alarmManager.removeActiveAlarms(ImmutableSet.of(alarm));

				locationService.removeGeofence(alarm);
				activity.onAddGeoAlarmFragmentClose();
				dialog.dismiss();
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

			final GeoAlarm newAlarm = GeoAlarm.builder()
			                                  .location(marker.getPosition())
			                                  .name(name)
			                                  .radius(radiusBar.getProgress())
			                                  .days(ImmutableSet.copyOf(Maps.filterValues(checkboxes,
			                                                                              CompoundButton::isChecked)
			                                                                .keySet()))
			                                  .hour(timePicker.getCurrentHour())
			                                  .minute(timePicker.getCurrentMinute())
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
				locationService.addGeofence(newAlarm).setResultCallback(status -> {
					if (status.isSuccess()) {
						finish(activity, dialog, newAlarm);
					} else {
						// TODO: toast
					}
				});
			} else {
				finish(activity, dialog, newAlarm);
			}
		});
	}

	private void finish(MapActivity activity, Dialog dialog, GeoAlarm newAlarm) {
		GeoAlarm.save(activity, newAlarm);
		activity.onAddGeoAlarmFragmentClose();

		dialog.dismiss();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final View dialogView = inflater.inflate(R.layout.fragment_add_geo_alarm_dialog, container, false);
		ButterKnife.inject(this, dialogView);
		return dialogView;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		ButterKnife.reset(this);
	}

	@Override
	public void onStart() {
		super.onStart();
		mapFragment = SupportMapFragment.newInstance();
		getChildFragmentManager().beginTransaction().replace(R.id.add_geo_alarm_map_container, mapFragment).commit();
	}

	public void setLocationService(LocationServiceGoogle ls) {
		this.locationService = ls;
	}

	private GeoAlarm getEffectiveGeoAlarm(final Bundle args, final boolean isEdit, final LatLng initalPoint) {
		return isEdit
		       ? gson.fromJson(args.getString(GeoAlarmFragment.EXISTING_ALARM), GeoAlarm.class)
		       : GeoAlarm.builder()
		                 .location(initalPoint)
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
}