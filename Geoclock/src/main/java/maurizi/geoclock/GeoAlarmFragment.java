package maurizi.geoclock;


import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
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

import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationStatusCodes;
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

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import butterknife.ButterKnife;
import butterknife.InjectView;
import lombok.Getter;

import static com.google.common.collect.Collections2.transform;


/**
 * A simple {@link Fragment} subclass.
 */
@Getter
public class GeoAlarmFragment extends DialogFragment {

	private SupportMapFragment mapFragment;

	public interface Listener {
		public void onAddGeoAlarmFragmentClose(DialogFragment dialog);
	}

	private static final Gson gson = new Gson();

	public final static String INITIAL_LATLNG = "INITIAL_LATLNG";
	public final static String INITIAL_ZOOM = "INITIAL_ZOOM";
	public static final String EXISTING_ALARM = "ALARM";

	private final static int INITIAL_RADIUS = 20;
	private final static int MAX_RADIUS = 200;

	private LocationClient locationClient = null;

	@InjectView(R.id.scrollView) LockableScrollView scrollView;
	@InjectView(R.id.add_geo_alarm_name) TextView nameTextBox;
	@InjectView(R.id.add_geo_alarm_radius) SeekBar radiusBar;
	@InjectView(R.id.add_geo_alarm_time) TimePicker timePicker;

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

		final Map<DayOfWeek, CheckBox> checkboxes = getWeekdaysCheckBoxMap(dialogView);

		map.moveCamera(CameraUpdateFactory.newLatLngZoom(alarm.location, initalZoom));
		radiusBar.setProgress((int) alarm.radius);
		radiusBar.setMax(MAX_RADIUS);

		final Marker marker = map.addMarker(new MarkerOptions().position(alarm.location).draggable(true));

		final Circle circle = map.addCircle(new CircleOptions().center(alarm.location)
		                                                       .radius(alarm.radius)
		                                                       .fillColor(R.color.geofence_fill_color));

		if (isEdit) {
			timePicker.setCurrentHour(alarm.hour);
			timePicker.setCurrentMinute(alarm.minute);
			nameTextBox.setText(alarm.name);
			for (DayOfWeek day : alarm.days) {
				checkboxes.get(day).setChecked(true);
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

		final Dialog dialog = getDialog();
		cancelButton.setOnClickListener(view -> dialog.cancel());

		if (isEdit) {
			deleteButton.setOnClickListener(view -> {
				// TODO: Remove any existing notifications and geofences and alarms
				GeoAlarm.remove(activity, alarm);
				activity.onAddGeoAlarmFragmentClose(GeoAlarmFragment.this);
				dialog.dismiss();
			});
		} else {
			deleteButton.setVisibility(View.GONE);
		}

		final Collection<String> savedNames = transform(GeoAlarm.getGeoAlarms(activity), savedAlarm -> savedAlarm.name);
		saveButton.setOnClickListener(view -> {
			final String name = nameTextBox.getText().toString();

			if ("".equals(name) || savedNames.contains(name)) {
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
			                                  .geofenceId(alarm.geofenceId)
			                                  .build();

			locationClient.addGeofences(Arrays.asList(newAlarm.getGeofence()),
			                            GeofenceReceiver.getPendingIntent(getActivity()), (status, ids) -> {
						if (status == LocationStatusCodes.SUCCESS) {
							GeoAlarm addedAlarm = newAlarm.withGeofenceId(ids[0]);
							GeoAlarm.replace(activity, alarm, addedAlarm);
							activity.onAddGeoAlarmFragmentClose(GeoAlarmFragment.this);

							dialog.dismiss();
						}
						locationClient.disconnect();
					});
		});
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final View dialogView = inflater.inflate(R.layout.fragment_add_geo_alarm_dialog, container, false);

		final ToastLocationClientHandler handler = new ToastLocationClientHandler(getActivity());
		locationClient = new LocationClient(getActivity(), handler, handler);
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
		locationClient.connect();

		mapFragment = SupportMapFragment.newInstance();
		getChildFragmentManager().beginTransaction().replace(R.id.add_geo_alarm_map_container, mapFragment).commit();
	}

	private GeoAlarm getEffectiveGeoAlarm(final Bundle args, final boolean isEdit, final LatLng initalPoint) {
		return isEdit
		       ? gson.fromJson(args.getString(GeoAlarmFragment.EXISTING_ALARM), GeoAlarm.class)
		       : GeoAlarm.builder().location(initalPoint).radius(INITIAL_RADIUS).name("").build();
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