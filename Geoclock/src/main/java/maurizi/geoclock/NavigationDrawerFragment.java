package maurizi.geoclock;


import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.Fragment;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.google.common.collect.Lists;

import java.util.Collection;

/**
 * Fragment used for managing interactions for and presentation of a navigation drawer.
 * See the <a href="https://developer.android.com/design/patterns/navigation-drawer.html#Interaction">
 * design guidelines</a> for a complete explanation of the behaviors implemented here.
 */
public class NavigationDrawerFragment extends Fragment {

	/**
	 * Remember the position of the selected item.
	 */
	private static final String STATE_SELECTED_POSITION = "selected_navigation_drawer_position";

	/**
	 * A pointer to the current callbacks instance (the Activity).
	 */
	private NavigationDrawerCallbacks mCallbacks;

	/**
	 * Helper component that ties the action bar to the navigation drawer.
	 */
	private ActionBarDrawerToggle mDrawerToggle;

	private DrawerLayout mDrawerLayout;
	private ListView mDrawerListView;
	private ArrayAdapter<GeoAlarm> geoAlarmAdapter;
	private View mFragmentContainerView;

	private int mCurrentSelectedPosition = 0;

	public NavigationDrawerFragment() {
	}


	public void setGeoAlarms(Collection<GeoAlarm> alarms) {
		geoAlarmAdapter.clear();
		for (GeoAlarm alarm : alarms) {
			geoAlarmAdapter.add(alarm);
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (savedInstanceState != null) {
			mCurrentSelectedPosition = savedInstanceState.getInt(STATE_SELECTED_POSITION);
		}

		// Select either the default item (0) or the last selected item.
		selectItem(mCurrentSelectedPosition);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		// Indicate that this fragment would like to influence the set of actions in the action bar.
		setHasOptionsMenu(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
	                         Bundle savedInstanceState) {
		mDrawerListView = (ListView) inflater.inflate(
				R.layout.fragment_navigation_drawer, container, false);
		mDrawerListView.setOnItemClickListener((parent, view, position, id) -> selectItem(position));
		geoAlarmAdapter = new ArrayAdapter<>(
				getActionBar().getThemedContext(),
				android.R.layout.simple_list_item_activated_1,
				android.R.id.text1,
				Lists.newArrayList(GeoAlarm.getGeoAlarms(getActivity()))
		);
		mDrawerListView.setAdapter(geoAlarmAdapter);
		mDrawerListView.setItemChecked(mCurrentSelectedPosition, true);
		return mDrawerListView;
	}

	public boolean isDrawerOpen() {
		return mDrawerLayout != null && mDrawerLayout.isDrawerOpen(mFragmentContainerView);
	}

	/**
	 * Users of this fragment must call this method to set up the navigation drawer interactions.
	 *
	 * @param fragmentId   The android:id of this fragment in its activity's layout.
	 * @param drawerLayout The DrawerLayout containing this fragment's UI.
	 */
	public void setUp(int fragmentId, DrawerLayout drawerLayout) {
		mFragmentContainerView = getActivity().findViewById(fragmentId);
		mDrawerLayout = drawerLayout;

		// set a custom shadow that overlays the main content when the drawer opens
		mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

		ActionBar actionBar = getActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setHomeButtonEnabled(true);

		// ActionBarDrawerToggle ties together the the proper interactions
		// between the navigation drawer and the action bar app icon.
		mDrawerToggle = new ActionBarDrawerToggle(
				getActivity(),
				mDrawerLayout,
				R.drawable.ic_drawer,
				R.string.navigation_drawer_open,
				R.string.navigation_drawer_close
		);

		// Defer code dependent on restoration of previous instance state.
		mDrawerLayout.post(mDrawerToggle::syncState);

		mDrawerLayout.setDrawerListener(mDrawerToggle);
	}

	private void selectItem(int position) {
		mCurrentSelectedPosition = position;
		if (mDrawerListView != null) {
			mDrawerListView.setItemChecked(position, true);
		}
		if (mDrawerLayout != null) {
			mDrawerLayout.closeDrawer(mFragmentContainerView);
		}
		if (mCallbacks != null) {
			if (geoAlarmAdapter != null) {
				mCallbacks.onNavigationDrawerItemSelected(geoAlarmAdapter.getItem(position));
			}
		}
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		try {
			mCallbacks = (NavigationDrawerCallbacks) activity;
		} catch (ClassCastException e) {
			throw new ClassCastException("Activity must implement NavigationDrawerCallbacks.");
		}
	}

	@Override
	public void onDetach() {
		super.onDetach();
		mCallbacks = null;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(STATE_SELECTED_POSITION, mCurrentSelectedPosition);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		// Forward the new configuration the drawer toggle component.
		mDrawerToggle.onConfigurationChanged(newConfig);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		// If the drawer is open, show the global app actions in the action bar. See also
		// showGlobalContextActionBar, which controls the top-left area of the action bar.
		if (mDrawerLayout != null && isDrawerOpen()) {
			inflater.inflate(R.menu.global, menu);
			showGlobalContextActionBar();
		}
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (mDrawerToggle.onOptionsItemSelected(item)) {
			return true;
		}

		if (item.getItemId() == R.id.action_example) {
			Toast.makeText(getActivity(), "Example action.", Toast.LENGTH_SHORT).show();
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	/**
	 * Per the navigation drawer design guidelines, updates the action bar to show the global app
	 * 'context', rather than just what's in the current screen.
	 */
	private void showGlobalContextActionBar() {
		ActionBar actionBar = getActionBar();
		actionBar.setDisplayShowTitleEnabled(true);
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
		actionBar.setTitle(R.string.app_name);
	}

	public ActionBarActivity getSupportActivity() {
		return (ActionBarActivity)super.getActivity();
	}

	private ActionBar getActionBar() {
		return getSupportActivity().getSupportActionBar();
	}

	/**
	 * Callbacks interface that all activities using this fragment must implement.
	 */
	public static interface NavigationDrawerCallbacks {
		/**
		 * Called when an item in the navigation drawer is selected.
		 */
		void onNavigationDrawerItemSelected(GeoAlarm alarm);
	}
}
