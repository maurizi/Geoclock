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
import android.widget.EditText;
import android.widget.SeekBar;
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
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Map;

import static com.google.common.collect.Collections2.transform;


/**
 * A simple {@link Fragment} subclass.
 */
public class AddGeoAlarmFragment extends DialogFragment {

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
		final boolean isEdit = args.containsKey(AddGeoAlarmFragment.EXISTING_ALARM);
		final LatLng initalPoint = args.getParcelable(AddGeoAlarmFragment.INITIAL_LATLNG);
		final float initalZoom = args.getFloat(AddGeoAlarmFragment.INITIAL_ZOOM);
		final GeoAlarm alarm = isEdit
		                       ? gson.fromJson(args.getString(AddGeoAlarmFragment.EXISTING_ALARM), GeoAlarm.class)
		                       : GeoAlarm.builder().location(initalPoint).radius(INITIAL_RADIUS).name("").build();

		final LockableScrollView scrollView = (LockableScrollView) dialogView.findViewById(R.id.scrollView);

		final EditText nameTextBox = (EditText) dialogView.findViewById(R.id.add_geo_alarm_name);
		final SeekBar radiusBar = (SeekBar) dialogView.findViewById(R.id.add_geo_alarm_radius);
		final Map<Integer, CheckBox> checkboxes =
				ImmutableMap.<Integer, CheckBox>builder()
				            .put(Calendar.SUNDAY, (CheckBox) dialogView.findViewById(R.id.sun))
				            .put(Calendar.MONDAY, (CheckBox) dialogView.findViewById(R.id.mon))
				            .put(Calendar.TUESDAY, (CheckBox) dialogView.findViewById(R.id.tues))
				            .put(Calendar.WEDNESDAY, (CheckBox) dialogView.findViewById(R.id.wed))
				            .put(Calendar.THURSDAY, (CheckBox) dialogView.findViewById(R.id.thu))
				            .put(Calendar.FRIDAY, (CheckBox) dialogView.findViewById(R.id.fri))
				            .put(Calendar.SATURDAY, (CheckBox) dialogView.findViewById(R.id.sat))
				            .build();

		map.moveCamera(CameraUpdateFactory.newLatLngZoom(alarm.location, initalZoom));
		radiusBar.setProgress((int) alarm.radius);
		radiusBar.setMax(MAX_RADIUS);

		final Marker marker = map.addMarker(new MarkerOptions().position(alarm.location).draggable(true));

		final Circle circle = map.addCircle(new CircleOptions().center(alarm.location)
		                                                       .radius(alarm.radius)
		                                                       .fillColor(R.color.geofence_fill_color));

		final TimePicker timePicker = (TimePicker) dialogView.findViewById(R.id.add_geo_alarm_time);
		if (isEdit) {
			timePicker.setCurrentHour(alarm.hour);
			timePicker.setCurrentMinute(alarm.minute);
			nameTextBox.setText(alarm.name);
			for (int day : alarm.days) {
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
		dialogView.findViewById(R.id.add_geo_alarm_cancel).setOnClickListener(view -> dialog.cancel());

		Button deleteButton = (Button) dialogView.findViewById(R.id.add_geo_alarm_delete);
		if (isEdit) {
			deleteButton.setOnClickListener(view -> {
				// TODO: Remove any existing notifications and geofences and alarms
				GeoAlarm.remove(activity, alarm);
				activity.onAddGeoAlarmFragmentClose(AddGeoAlarmFragment.this);
				dialog.dismiss();
			});
		} else {
			deleteButton.setVisibility(View.GONE);
		}

		final Collection<String> savedNames = transform(GeoAlarm.getGeoAlarms(activity), savedAlarm -> savedAlarm.name);
		dialogView.findViewById(R.id.add_geo_alarm_save).setOnClickListener(view -> {
			final String name = nameTextBox.getText().toString();

			if ("".equals(name) || savedNames.contains(name)) {
				Toast.makeText(activity, R.string.add_geo_alarm_validation, Toast.LENGTH_SHORT).show();
				return;
			}

			final GeoAlarm newAlarm = GeoAlarm.builder()
			                                  .location(marker.getPosition())
			                                  .name(name)
			                                  .radius(radiusBar.getProgress())
			                                  .days(Lists.newArrayList(Maps.filterValues(checkboxes,
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
					activity.onAddGeoAlarmFragmentClose(AddGeoAlarmFragment.this);

					dialog.dismiss();
				}
				locationClient.disconnect();
			});
		});
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final View dialogView = inflater.inflate(R.layout.fragment_add_geo_alarm_dialog, null);
		getDialog().setTitle(R.string.add_geo_alarm_title);

		final ToastLocationClientHandler handler = new ToastLocationClientHandler(getActivity());
		locationClient = new LocationClient(getActivity(), handler, handler);

		return dialogView;
	}

	@Override
	public void onStart() {
		super.onStart();
		locationClient.connect();

		mapFragment = SupportMapFragment.newInstance();
		getChildFragmentManager().beginTransaction().replace(R.id.add_geo_alarm_map_container, mapFragment).commit();
	}
}