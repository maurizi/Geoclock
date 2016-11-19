package maurizi.geoclock.ui;

import android.os.Bundle;
import android.support.annotation.NonNull;
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
import butterknife.OnClick;
import lombok.Getter;
import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.R;
import maurizi.geoclock.utils.ActiveAlarmManager;
import maurizi.geoclock.utils.LocationServiceGoogle;

@Getter
public class GeoAlarmFragment extends DialogFragment {

	public static final String INITIAL_LATLNG_KEY = "INITIAL_LATLNG_KEY";
	public static final String INITIAL_ZOOM_KEY = "INITIAL_ZOOM_KEY";
	public static final String EXISTING_ALARM_KEY = "EXISTING_ALARM_KEY";
	private static final int INITIAL_RADIUS = 20;
	private static final int MAX_RADIUS = 200;
	private static final Gson GSON = new Gson();

	@InjectView(R.id.add_geo_alarm_cancel) Button cancelButton;
	@InjectView(R.id.add_geo_alarm_delete) Button deleteButton;
	@InjectView(R.id.add_geo_alarm_save) Button saveButton;
	@InjectView(R.id.scrollView) LockableScrollView scrollView;
	@InjectView(R.id.add_geo_alarm_radius) SeekBar radiusBar;
	@InjectView(R.id.add_geo_alarm_enabled) SwitchCompat enabledSwitch;
	@InjectView(R.id.add_geo_alarm_name) TextView nameTextBox;
	@InjectView(R.id.add_geo_alarm_time) TimePicker timePicker;

	private SupportMapFragment mapFragment;
	private LocationServiceGoogle locationService;
	private GeoAlarm alarm;
	private Map<DayOfWeek, CheckBox> checkboxes;
	private Marker marker;
	private boolean isEdit;
	private float initialZoom;
	private LatLng initialPoint;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		final Bundle args = getArguments();
		isEdit = args.containsKey(GeoAlarmFragment.EXISTING_ALARM_KEY);
		initialPoint = args.getParcelable(GeoAlarmFragment.INITIAL_LATLNG_KEY);
		initialZoom = args.getFloat(GeoAlarmFragment.INITIAL_ZOOM_KEY);

		alarm = getEffectiveGeoAlarm(args, isEdit, initialPoint);
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

		checkboxes = getWeekdaysCheckBoxMap(getView());

		mapFragment.getMapAsync(googleMap -> {
			if (isAdded()) {
				if (googleMap == null) {
					Toast.makeText(getActivity(), R.string.fail_map, Toast.LENGTH_SHORT).show();
					return;
				}

				initMap(googleMap);
			}
		});
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		ButterKnife.reset(this);
	}

	@OnClick(R.id.add_geo_alarm_delete)
	public void deleteGeoAlarm() {
		GeoAlarm.remove(getActivity(), alarm);
		ActiveAlarmManager alarmManager = new ActiveAlarmManager(getActivity());
		alarmManager.removeActiveAlarms(ImmutableSet.of(alarm));

		locationService.removeGeofence(alarm);
		((MapActivity) getActivity()).onAddGeoAlarmFragmentClose();
		dismiss();
	}

	@OnClick(R.id.add_geo_alarm_save)
	public void saveGeoAlarm() {
		final String name = nameTextBox.getText().toString();

		if (name.isEmpty()) {
			Toast.makeText(getActivity(), R.string.add_geo_alarm_validation, Toast.LENGTH_SHORT).show();
			return;
		}

		final GeoAlarm newAlarm = GeoAlarm.builder()
				.location(marker.getPosition())
				.name(name)
				.radius(radiusBar.getProgress())
				.days(ImmutableSet.copyOf(Maps.filterValues(checkboxes,
						CompoundButton::isChecked).keySet()))
				.hour(timePicker.getCurrentHour())
				.minute(timePicker.getCurrentMinute())
				.enabled(enabledSwitch.isChecked())
				.id(alarm.id)
				.build();

		if (isEdit) {
			GeoAlarm.remove(getActivity(), alarm);
			if (alarm.enabled) {
				locationService.removeGeofence(alarm);
			}
		}
		if (newAlarm.enabled) {
			locationService.addGeofence(newAlarm).setResultCallback(status -> {
				if (status.isSuccess()) {
					finish(newAlarm);
				} else {
					// TODO: toast
				}
			});
		} else {
			finish(newAlarm);
		}
	}

	private void initMap(@NonNull GoogleMap map) {

		map.moveCamera(CameraUpdateFactory.newLatLngZoom(alarm.location, initialZoom));
		marker = map.addMarker(new MarkerOptions().position(alarm.location).draggable(true));

		final Circle circle = map.addCircle(new CircleOptions().center(alarm.location)
				.radius(alarm.radius)
				.fillColor(R.color.geofence_fill_color));

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

		final Map<DayOfWeek, CheckBox> checkboxes = getWeekdaysCheckBoxMap(getView());

		radiusBar.setMax(MAX_RADIUS);
		radiusBar.setProgress(alarm.radius);
		enabledSwitch.setChecked(alarm.enabled);

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

		cancelButton.setOnClickListener(view -> getDialog().cancel());

		getDialog().setTitle(R.string.add_geo_alarm_title);

		if (!isEdit) {
			deleteButton.setVisibility(View.GONE);
		}
	}

	private void finish(@NonNull GeoAlarm newAlarm) {
		GeoAlarm.save(getActivity(), newAlarm);
		((MapActivity) getActivity()).onAddGeoAlarmFragmentClose();

		dismiss();
	}

	public void setLocationService(LocationServiceGoogle ls) {
		this.locationService = ls;
	}

	private GeoAlarm getEffectiveGeoAlarm(final Bundle args, final boolean isEdit, final LatLng initalPoint) {
		return isEdit
				? GSON.fromJson(args.getString(GeoAlarmFragment.EXISTING_ALARM_KEY), GeoAlarm.class)
				: GeoAlarm.builder()
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