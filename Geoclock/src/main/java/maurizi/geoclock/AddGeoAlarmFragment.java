package maurizi.geoclock;


import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.view.View;
import android.widget.SeekBar;

import com.google.android.gms.location.LocationClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;


/**
 * A simple {@link Fragment} subclass.
 */
public class AddGeoAlarmFragment extends DialogFragment {

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
		radiusBar = (SeekBar) dialogView.findViewById(R.id.add_geo_alarm_radius);
		radiusBar.setProgress(INITIAL_RADIUS);
		radiusBar.setMax(MAX_RADIUS);

		ToastLocationClientHandler handler = new ToastLocationClientHandler(getActivity());
		locationClient = new LocationClient(getActivity(), handler, handler);

		return new AlertDialog.Builder(getActivity())
				.setTitle(R.string.add_geo_alarm_title)
				.setNegativeButton(R.string.add_geo_alarm_cancel, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						AddGeoAlarmFragment.this.getDialog().cancel();
					}
				})
				.setPositiveButton(R.string.add_geo_alarm_continue, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						Intent alarmIntent = new Intent(AlarmClock.ACTION_SET_ALARM);
					}
				})
				.setView(dialogView)
				.create();
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
				}

				@Override
				public void onMarkerDrag(Marker marker) {
					circle.setCenter(marker.getPosition());
					map.animateCamera(CameraUpdateFactory.newLatLng(marker.getPosition()));
				}

				@Override
				public void onMarkerDragEnd(Marker marker) {
				}
			});

			radiusBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
				@Override
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					circle.setRadius(progress);
				}

				@Override
				public void onStartTrackingTouch(SeekBar seekBar) {
				}

				@Override
				public void onStopTrackingTouch(SeekBar seekBar) {
				}
			});
		}
	}
}
