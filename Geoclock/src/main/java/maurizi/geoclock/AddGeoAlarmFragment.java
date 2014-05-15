package maurizi.geoclock;


import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
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
import com.google.common.base.Predicate;
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

	public final static int INITIAL_RADIUS = 20;
	public final static int MAX_RADIUS = 200;

	private GoogleMap map = null;

	private LocationClient locationClient = null;

	private View dialogView = null;

	private SeekBar radiusBar = null;

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		dialogView = getActivity().getLayoutInflater().inflate(R.layout.fragment_add_geo_alarm_dialog, null);
		final LockableScrollView scrollView = (LockableScrollView) dialogView.findViewById(R.id.scrollView);
		radiusBar = (SeekBar) dialogView.findViewById(R.id.add_geo_alarm_radius);
		radiusBar.setProgress(INITIAL_RADIUS);
		radiusBar.setMax(MAX_RADIUS);

		map = ((MapFragment)getFragmentManager().findFragmentById(R.id.add_geo_alarm_map)).getMap();
		if (map != null) {
			final LatLng initalPoint = getArguments().getParcelable(AddGeoAlarmFragment.INITIAL_LATLNG);
			final float initalZoom = getArguments().getFloat(AddGeoAlarmFragment.INITIAL_ZOOM);

			map.moveCamera(CameraUpdateFactory.newLatLngZoom(initalPoint, initalZoom));
			final Marker marker = map.addMarker(new MarkerOptions()
					.position(initalPoint)
					.draggable(true));
			final Circle circle = map.addCircle(new CircleOptions()
					.center(initalPoint)
					.radius(INITIAL_RADIUS)
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

			radiusBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
				@Override
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					circle.setRadius(progress);
				}

				@Override
				public void onStartTrackingTouch(SeekBar seekBar) { }

				@Override
				public void onStopTrackingTouch(SeekBar seekBar) { }
			});

			ToastLocationClientHandler handler = new ToastLocationClientHandler(getActivity());
			locationClient = new LocationClient(getActivity(), handler, handler);

			final AlertDialog dialog= new AlertDialog.Builder(getActivity())
					.setTitle(R.string.add_geo_alarm_title)
					.setNegativeButton(R.string.add_geo_alarm_cancel, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							((Listener)getActivity()).onAddGeoAlarmFragmentClose(AddGeoAlarmFragment.this);
							AddGeoAlarmFragment.this.getDialog().cancel();
						}
					})
					.setPositiveButton(R.string.add_geo_alarm_continue, null) // Implemented later to allow conditional dismissal
					.setView(dialogView)
					.create();
			dialog.setOnShowListener(new DialogInterface.OnShowListener() {
				@Override
				public void onShow(DialogInterface _) {
					dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View _) {
							final String name = ((EditText)dialogView.findViewById(R.id.add_geo_alarm_name)).getText().toString();
							SharedPreferences prefs = getActivity().getPreferences(Context.MODE_PRIVATE);
							if ("".equals(name) || prefs.contains(name)) {
								Toast.makeText(getActivity(), R.string.add_geo_alarm_validation, Toast.LENGTH_SHORT).show();
								return;
							}
							final Map<Integer, CheckBox> checkboxes = ImmutableMap.<Integer, CheckBox>builder()
									.put(Calendar.SUNDAY, (CheckBox)dialogView.findViewById(R.id.sun))
									.put(Calendar.MONDAY, (CheckBox)dialogView.findViewById(R.id.mon))
									.put(Calendar.TUESDAY, (CheckBox)dialogView.findViewById(R.id.tues))
									.put(Calendar.WEDNESDAY, (CheckBox)dialogView.findViewById(R.id.wed))
									.put(Calendar.THURSDAY, (CheckBox)dialogView.findViewById(R.id.thu))
									.put(Calendar.FRIDAY, (CheckBox)dialogView.findViewById(R.id.fri))
									.put(Calendar.SATURDAY, (CheckBox)dialogView.findViewById(R.id.sat))
									.build();
							final TimePicker timePicker = (TimePicker)dialogView.findViewById(R.id.add_geo_alarm_time);
							GeoAlarm alarm = new GeoAlarm.Builder()
									.location(marker.getPosition())
									.name(name)
									.radius(radiusBar.getProgress())
									.days(Lists.newArrayList(Maps.filterValues(checkboxes, new Predicate<CheckBox>() {
										@Override
										public boolean apply(CheckBox cb) {
											return cb.isChecked();
										}
									}).keySet()))
									.hour(timePicker.getCurrentHour())
									.minute(timePicker.getCurrentMinute())
									.create();


							prefs.edit().putString(alarm.name, gson.toJson(alarm, GeoAlarm.class)).commit();
							((Listener)getActivity()).onAddGeoAlarmFragmentClose(AddGeoAlarmFragment.this);

							dialog.dismiss();
						}
					});
				}
			});

			return dialog;
		}
		return null;
	}

	public void onDestroyView() {
		super.onDestroyView();

		// When you have fragments in fragments, you need to remove the children before you can re-inflate the view later
		try {
			Fragment fragment = (getFragmentManager().findFragmentById(R.id.add_geo_alarm_map));
			FragmentTransaction ft = getActivity().getFragmentManager().beginTransaction();
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