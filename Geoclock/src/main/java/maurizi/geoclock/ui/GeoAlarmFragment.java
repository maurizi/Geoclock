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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import lombok.Getter;
import maurizi.geoclock.Alarm;
import maurizi.geoclock.utils.Alarms;
import maurizi.geoclock.Location;
import maurizi.geoclock.utils.Locations;
import maurizi.geoclock.R;
import maurizi.geoclock.utils.ActiveAlarmManager;
import maurizi.geoclock.utils.LocationServiceGoogle;

import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.ImmutableSet.copyOf;
import static com.google.common.collect.Iterables.all;
import static com.google.common.collect.Maps.filterValues;

@Getter
public class GeoAlarmFragment extends DialogFragment {

	public static final String INITIAL_LATLNG_KEY = "INITIAL_LATLNG_KEY";
	public static final String INITIAL_ZOOM_KEY = "INITIAL_ZOOM_KEY";
	public static final String EXISTING_ALARM_KEY = "EXISTING_ALARM_KEY";
	private static final int INITIAL_RADIUS = 20;
	private static final int MAX_RADIUS = 200;
	private static final Gson GSON = new Gson();

	@InjectView(R.id.add_geo_alarm_cancel) Button cancelButton;
	@InjectView(R.id.delete_location) Button deleteButton;
	@InjectView(R.id.save_button) Button saveButton;
	@InjectView(R.id.scrollView) LockableScrollView scrollView;
	@InjectView(R.id.add_geo_alarm_radius) SeekBar radiusBar;
	@InjectView(R.id.add_geo_alarm_enabled) SwitchCompat enabledSwitch;
	@InjectView(R.id.add_geo_alarm_name) TextView nameTextBox;
	@InjectView(R.id.add_geo_alarm_time) TimePicker timePicker;

	private SupportMapFragment mapFragment;
	private LocationServiceGoogle locationService;
	private Map<DayOfWeek, CheckBox> checkboxes;
	private Marker marker;
	private boolean isEdit;
	private float initialZoom;
	private LatLng initialPoint;

	private Location location;
	private List<Alarm> alarms;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		final Bundle args = getArguments();
		isEdit = args.containsKey(GeoAlarmFragment.EXISTING_ALARM_KEY);
		initialPoint = args.getParcelable(GeoAlarmFragment.INITIAL_LATLNG_KEY);
		initialZoom = args.getFloat(GeoAlarmFragment.INITIAL_ZOOM_KEY);

		location = getEffectiveLocation(args, isEdit, initialPoint);
		alarms = location.getAlarms(getActivity());
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final View dialogView = inflater.inflate(R.layout.fragment_add_geo_alarm_dialog, container, false);
		ButterKnife.inject(this, dialogView);
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

		final GoogleMap map = mapFragment.getMap();

		if (map == null) {
			Toast.makeText(getActivity(), R.string.fail_map, Toast.LENGTH_SHORT).show();
			return;
		}
		checkboxes = getWeekdaysCheckBoxMap(getView());
		radiusBar.setMax(MAX_RADIUS);

		nameTextBox.setText(location.name);
		radiusBar.setProgress(location.radius);
		map.moveCamera(CameraUpdateFactory.newLatLngZoom(location.location, initialZoom));
		marker = map.addMarker(new MarkerOptions().position(location.location).draggable(true));

		final Circle circle = map.addCircle(new CircleOptions().center(location.location)
		                                                       .radius(location.radius)
		                                                       .fillColor(R.color.geofence_fill_color));

		// TODO: Turn this if into a loop?
		if (alarms.isEmpty()) {
			enabledSwitch.setChecked(true);
		} else {
			Alarm alarm = alarms.get(0);
			enabledSwitch.setChecked(alarm.enabled);

			if (isEdit) {
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
		}

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

		Dialog dialog = getDialog();
		dialog.setTitle(R.string.add_geo_alarm_title);
		if (isEdit) {
			dialog.setTitle(R.string.edit_title);
		} else {
			deleteButton.setVisibility(View.GONE);
		}

		cancelButton.setOnClickListener(view -> dialog.cancel());
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		ButterKnife.reset(this);
	}

	@OnClick(R.id.delete_location)
	public void deleteLocation() {
		Locations.remove(getActivity(), location);
		Alarms.remove(getActivity(), alarms);

		ActiveAlarmManager alarmManager = new ActiveAlarmManager(getActivity());
		alarmManager.removeActiveAlarms(ImmutableSet.copyOf(transform(alarms, alarm -> alarm.id)));
		locationService.removeGeofence(location);
		((MapActivity) getActivity()).onAddGeoAlarmFragmentClose();
		dismiss();
	}

	@OnClick(R.id.save_button)
	public void save() {
		final String name = nameTextBox.getText().toString();

		if (name.isEmpty()) {
			Toast.makeText(getActivity(), R.string.add_geo_alarm_validation, Toast.LENGTH_SHORT).show();
			return;
		}

		final Location newLocation = Location.builder()
		                                     .location(marker.getPosition())
		                                     .name(name)
		                                     .radius(radiusBar.getProgress())
		                                     .id(location.id)
		                                     .build();

		if (isEdit) {
			Locations.remove(getActivity(), location);
			Alarms.remove(getActivity(), alarms);
			if (all(alarms, alarm -> !alarm.enabled)) {
				locationService.removeGeofence(location);
			}
		}

		// TODO: loop?
		final Set<DayOfWeek> days = copyOf(Maps.filterValues(checkboxes, CompoundButton::isChecked).keySet());
		final Alarm.AlarmBuilder builder = Alarm.builder()
		                                        .hour(timePicker.getCurrentHour())
		                                        .minute(timePicker.getCurrentMinute())
		                                        .days(days)
		                                        .enabled(enabledSwitch.isEnabled())
		                                        .parent(newLocation.getId());
		if (alarms.isEmpty()) {
			builder.id(UUID.randomUUID());
		} else {
			builder.id(alarms.get(0).id);
		}
		Alarm newAlarm = builder.build();
		if (newAlarm.enabled) {
			locationService.addGeofence(location).setResultCallback(status -> {
				if (status.isSuccess()) {
					finish(newLocation, newAlarm);
				} else {
					// TODO: toast
				}
			});
		} else {
			finish(newLocation, newAlarm);
		}
	}

	private void finish(Location newLocation, Alarm newAlarm) {
		Locations.save(getActivity(), newLocation);
		Alarms.save(getActivity(), newAlarm);
		((MapActivity) getActivity()).onAddGeoAlarmFragmentClose();
		dismiss();
	}

	public void setLocationService(LocationServiceGoogle ls) {
		this.locationService = ls;
	}

	private Location getEffectiveLocation(final Bundle args, final boolean isEdit, final LatLng initalPoint) {
		return isEdit
		       ? GSON.fromJson(args.getString(GeoAlarmFragment.EXISTING_ALARM_KEY), Location.class)
		       : Location.builder()
		                 .location(initalPoint)
		                 .radius(INITIAL_RADIUS)
		                 .name("")
		                 .id(UUID.randomUUID())
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