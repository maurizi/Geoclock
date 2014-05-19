package maurizi.geoclock;


import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TimePicker;
import android.widget.Toast;

import com.google.android.gms.location.LocationClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;

import java.util.Calendar;
import java.util.Map;


/**
 * A simple {@link Fragment} subclass.
 */
public class AddGeoAlarmFragment extends DialogFragment {

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
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		final MapActivity activity = (MapActivity) getActivity();
		final View dialogView = activity.getLayoutInflater().inflate(R.layout.fragment_add_geo_alarm_dialog, null);
		final GoogleMap map = ((MapFragment) getFragmentManager().findFragmentById(R.id.add_geo_alarm_map)).getMap();

		if (map == null) {
			Toast.makeText(activity, R.string.fail_map, Toast.LENGTH_SHORT).show();
			return null;
		}

		final SharedPreferences prefs = activity.getPreferences(Context.MODE_PRIVATE);
		final Bundle args = getArguments();
		final boolean isEdit = args.containsKey(AddGeoAlarmFragment.EXISTING_ALARM);
		final LatLng initalPoint = args.getParcelable(AddGeoAlarmFragment.INITIAL_LATLNG);
		final float initalZoom = args.getFloat(AddGeoAlarmFragment.INITIAL_ZOOM);
		final GeoAlarm alarm = isEdit
				? gson.fromJson(args.getString(AddGeoAlarmFragment.EXISTING_ALARM), GeoAlarm.class)
				: GeoAlarm.builder()
				          .location(initalPoint)
				          .radius(INITIAL_RADIUS)
				          .name("")
				          .build();

		final ToastLocationClientHandler handler = new ToastLocationClientHandler(activity);
		locationClient = new LocationClient(activity, handler, handler);
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

		final Marker marker = map.addMarker(new MarkerOptions()
				.position(alarm.location)
				.draggable(true));

		final Circle circle = map.addCircle(new CircleOptions()
				.center(alarm.location)
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

		final AlertDialog.Builder b = new AlertDialog.Builder(activity)
				.setTitle(R.string.add_geo_alarm_title)
				.setNegativeButton(R.string.add_geo_alarm_cancel, (dialog, which) -> this.getDialog().dismiss())
				.setPositiveButton(R.string.add_geo_alarm_continue, null) // Implemented later to allow conditional dismissal
				.setView(dialogView);
		if (isEdit) {
			b.setNeutralButton(R.string.add_geo_alarm_delete, (dialog, which) -> {
				prefs.edit().remove(alarm.name).commit();
				activity.onAddGeoAlarmFragmentClose(AddGeoAlarmFragment.this);
				this.getDialog().dismiss();
			});
		}
		final AlertDialog dialog = b.create();
		dialog.setOnShowListener(dialogInterface ->
			dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
				final String name = nameTextBox.getText().toString();

				if ("".equals(name) || (prefs.contains(name) && !name.equals(alarm.name))) {
					Toast.makeText(activity, R.string.add_geo_alarm_validation, Toast.LENGTH_SHORT).show();
					return;
				}
				GeoAlarm newAlarm = GeoAlarm.builder()
				                            .location(marker.getPosition())
				                            .name(name)
				                            .radius(radiusBar.getProgress())
				                            .days(Lists.newArrayList(Maps.filterValues(checkboxes, CompoundButton::isChecked).keySet()))
				                            .hour(timePicker.getCurrentHour())
				                            .minute(timePicker.getCurrentMinute())
				                            .build();

				prefs.edit()
				     .remove(alarm.name)
				     .putString(newAlarm.name, gson.toJson(newAlarm, GeoAlarm.class)).commit();
				activity.onAddGeoAlarmFragmentClose(AddGeoAlarmFragment.this);

				dialog.dismiss();
			})
		);

		return dialog;
	}

	public void onDestroyView() {
		super.onDestroyView();

		// When you have fragments in fragments, you need to remove the children before you can re-inflate the view later
		try {
			final Fragment fragment = (getFragmentManager().findFragmentById(R.id.add_geo_alarm_map));
			final FragmentTransaction ft = getActivity().getFragmentManager().beginTransaction();
			ft.remove(fragment);
			ft.commit();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		locationClient.connect();
	}
}