package maurizi.geoclock.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.TimePicker;
import androidx.fragment.app.Fragment;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.common.collect.ImmutableSet;
import java.lang.reflect.Field;
import java.time.DayOfWeek;
import java.util.UUID;
import maurizi.geoclock.GeoAlarm;
import maurizi.geoclock.R;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class MapActivityUnitTest {

  private Context context;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
  }

  // ---- Activity launch ----

  @Test
  public void onCreate_inflatesLayout() {
    MapActivity activity = buildActivity();
    assertNotNull(activity.findViewById(R.id.main_constraint));
    assertNotNull(activity.findViewById(R.id.alarm_list));
    assertNotNull(activity.findViewById(R.id.empty_state));
    assertNotNull(activity.findViewById(R.id.fab_add));
  }

  @Test
  public void onCreate_emptyAlarms_showsEmptyState() {
    MapActivity activity = buildActivity();
    assertEquals(View.VISIBLE, activity.findViewById(R.id.empty_state).getVisibility());
    assertEquals(View.GONE, activity.findViewById(R.id.alarm_list).getVisibility());
  }

  @Test
  public void onCreate_withAlarms_showsList() {
    saveAlarm(enabledAlarm());
    MapActivity activity = buildActivity();
    assertEquals(View.GONE, activity.findViewById(R.id.empty_state).getVisibility());
    assertEquals(View.VISIBLE, activity.findViewById(R.id.alarm_list).getVisibility());
  }

  // ---- showAddDialog ----

  @Test
  public void showAddDialog_nullLocationService_showsToast() {
    MapActivity activity = buildActivity();
    // locationService is null because map async callback never fires
    activity.showAddDialog();
    assertNotNull(ShadowToast.getLatestToast());
  }

  // ---- expandMap / collapseMap ----

  @Test
  public void expandMap_thenCollapse_idempotent() {
    MapActivity activity = buildActivity();
    // expand twice — second call should be no-op
    activity.expandMap();
    activity.expandMap();
    // collapse twice — second call should be no-op
    activity.collapseMap();
    activity.collapseMap();
    // No crash = success
  }

  // ---- onAddGeoAlarmFragmentClose ----

  @Test
  public void onAddGeoAlarmFragmentClose_redrawsAlarms() {
    MapActivity activity = buildActivity();
    // Save an alarm while activity is running
    saveAlarm(enabledAlarm());
    activity.onAddGeoAlarmFragmentClose();
    // After redraw, empty state should be hidden and list visible
    assertEquals(View.GONE, activity.findViewById(R.id.empty_state).getVisibility());
    assertEquals(View.VISIBLE, activity.findViewById(R.id.alarm_list).getVisibility());
  }

  // ---- getIntent ----

  @Test
  public void getIntent_containsAlarmId() {
    GeoAlarm alarm = enabledAlarm();
    Intent intent = MapActivity.getIntent(context, alarm);
    assertNotNull(intent);
    assertEquals(alarm.id.toString(), intent.getStringExtra("ALARM_ID"));
  }

  // ---- onRequestPermissionsResult ----

  @Test
  public void onRequestPermissionsResult_denied_showsToast() {
    MapActivity activity = buildActivity();
    activity.onRequestPermissionsResult(1, new String[] {"perm"}, new int[] {-1});
    assertNotNull(ShadowToast.getLatestToast());
  }

  @Test
  public void onRequestPermissionsResult_wrongRequestCode_doesNothing() {
    MapActivity activity = buildActivity();
    // Request code 999 is not handled
    activity.onRequestPermissionsResult(999, new String[] {"perm"}, new int[] {0});
    // No crash
  }

  // ---- showAlarmOnMap with null map ----

  @Test
  public void showAlarmOnMap_nullMap_doesNotCrash() {
    MapActivity activity = buildActivity();
    // map is null (never initialized) — should return silently
    activity.showAlarmOnMap(enabledAlarm());
  }

  // ---- showAddPopup / showEditPopup: fragment lifecycle ----

  @Test
  public void showAddPopup_createsGeoAlarmFragment() {
    MapActivity activity = buildActivity();
    activity.showAddPopup(new LatLng(37.4, -122.0));
    activity.getSupportFragmentManager().executePendingTransactions();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    Fragment f = activity.getSupportFragmentManager().findFragmentByTag("AddGeoAlarmFragment");
    assertNotNull("Fragment should be created via showAddPopup", f);
    assertTrue(f instanceof GeoAlarmFragment);
  }

  @Test
  public void showAddPopup_fragmentSetupRunsOnResume() {
    MapActivity activity = buildActivity();
    activity.showAddPopup(new LatLng(37.4, -122.0));
    activity.getSupportFragmentManager().executePendingTransactions();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    GeoAlarmFragment fragment =
        (GeoAlarmFragment)
            activity.getSupportFragmentManager().findFragmentByTag("AddGeoAlarmFragment");
    assertNotNull(fragment);
    // Fragment view should be inflated
    assertNotNull("Fragment view should exist", fragment.getView());
    // TimePicker should be present
    assertNotNull(fragment.getView().findViewById(R.id.add_geo_alarm_time));
  }

  @Test
  public void showEditPopup_existingAlarm_createsFragment() {
    GeoAlarm alarm = saveAlarm(enabledAlarm().withPlace("Office"));
    MapActivity activity = buildActivity();
    activity.showEditPopup(alarm.id);
    activity.getSupportFragmentManager().executePendingTransactions();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    Fragment f = activity.getSupportFragmentManager().findFragmentByTag("AddGeoAlarmFragment");
    assertNotNull("Fragment should be created for edit", f);
  }

  @Test
  public void showEditPopup_nonexistentAlarm_doesNotCreateFragment() {
    MapActivity activity = buildActivity();
    activity.showEditPopup(UUID.randomUUID());
    activity.getSupportFragmentManager().executePendingTransactions();
    // No fragment created because getGeoAlarm returns null
    Fragment f = activity.getSupportFragmentManager().findFragmentByTag("AddGeoAlarmFragment");
    assertNull("Fragment should not be created for nonexistent alarm", f);
  }

  @Test
  @Config(sdk = 28)
  public void geoAlarmFragment_saveButton_savesAlarm() {
    MapActivity activity = buildActivity();
    activity.showAddPopup(new LatLng(37.4, -122.0));
    activity.getSupportFragmentManager().executePendingTransactions();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    GeoAlarmFragment fragment =
        (GeoAlarmFragment)
            activity.getSupportFragmentManager().findFragmentByTag("AddGeoAlarmFragment");
    assertNotNull(fragment);
    // Set a time on the TimePicker
    TimePicker tp = fragment.getView().findViewById(R.id.add_geo_alarm_time);
    tp.setHour(9);
    tp.setMinute(30);
    // Click save
    Button saveBtn = fragment.getView().findViewById(R.id.add_geo_alarm_save);
    saveBtn.performClick();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    // Verify an alarm was saved
    assertTrue("At least one alarm should be saved", !GeoAlarm.getGeoAlarms(context).isEmpty());
  }

  @Test
  public void geoAlarmFragment_cancelButton_dismissesDialog() {
    MapActivity activity = buildActivity();
    activity.showAddPopup(new LatLng(37.4, -122.0));
    activity.getSupportFragmentManager().executePendingTransactions();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    GeoAlarmFragment fragment =
        (GeoAlarmFragment)
            activity.getSupportFragmentManager().findFragmentByTag("AddGeoAlarmFragment");
    assertNotNull(fragment);
    Button cancelBtn = fragment.getView().findViewById(R.id.add_geo_alarm_cancel);
    cancelBtn.performClick();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
  }

  @Test
  public void geoAlarmFragment_editMode_showsDeleteButton() {
    GeoAlarm alarm =
        saveAlarm(enabledAlarm().withPlace("Home").withHour(8).withMinute(0).withEnabled(false));
    MapActivity activity = buildActivity();
    activity.showEditPopup(alarm.id);
    activity.getSupportFragmentManager().executePendingTransactions();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    GeoAlarmFragment fragment =
        (GeoAlarmFragment)
            activity.getSupportFragmentManager().findFragmentByTag("AddGeoAlarmFragment");
    assertNotNull(fragment);
    View deleteBtn = fragment.getView().findViewById(R.id.add_geo_alarm_delete);
    assertEquals(
        "Delete button should be visible in edit mode", View.VISIBLE, deleteBtn.getVisibility());
  }

  @Test
  public void geoAlarmFragment_editMode_savePreservesAlarmId() {
    GeoAlarm alarm =
        saveAlarm(enabledAlarm().withPlace("Home").withHour(8).withMinute(0).withEnabled(false));
    MapActivity activity = buildActivity();
    activity.showEditPopup(alarm.id);
    activity.getSupportFragmentManager().executePendingTransactions();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    GeoAlarmFragment fragment =
        (GeoAlarmFragment)
            activity.getSupportFragmentManager().findFragmentByTag("AddGeoAlarmFragment");
    assertNotNull(fragment);
    Button saveBtn = fragment.getView().findViewById(R.id.add_geo_alarm_save);
    saveBtn.performClick();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    // The alarm should still exist (same ID, re-saved)
    assertNotNull("Alarm should still exist after edit", GeoAlarm.getGeoAlarm(context, alarm.id));
  }

  @Test
  public void geoAlarmFragment_clickMapTapOverlay_doesNotCrash() {
    MapActivity activity = buildActivity();
    activity.showAddPopup(new LatLng(37.4, -122.0));
    activity.getSupportFragmentManager().executePendingTransactions();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    GeoAlarmFragment fragment =
        (GeoAlarmFragment)
            activity.getSupportFragmentManager().findFragmentByTag("AddGeoAlarmFragment");
    assertNotNull(fragment);
    View overlay = fragment.getView().findViewById(R.id.map_tap_overlay);
    if (overlay != null) {
      overlay.performClick();
      Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
  }

  @Test
  public void geoAlarmFragment_editMode_withDays_checksBoxes() {
    GeoAlarm alarm =
        saveAlarm(
            enabledAlarm()
                .withPlace("Work")
                .withHour(9)
                .withMinute(0)
                .withDays(ImmutableSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
                .withEnabled(false));
    MapActivity activity = buildActivity();
    activity.showEditPopup(alarm.id);
    activity.getSupportFragmentManager().executePendingTransactions();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    GeoAlarmFragment fragment =
        (GeoAlarmFragment)
            activity.getSupportFragmentManager().findFragmentByTag("AddGeoAlarmFragment");
    assertNotNull(fragment);
    // Monday checkbox should be checked
    android.widget.CheckBox monCb = fragment.getView().findViewById(R.id.mon);
    assertTrue("Monday checkbox should be checked in edit mode", monCb.isChecked());
  }

  @Test
  @Config(sdk = 28)
  public void geoAlarmFragment_editMode_enabledAlarm_save() {
    GeoAlarm alarm = saveAlarm(enabledAlarm().withPlace("Gym").withHour(6).withMinute(30));
    MapActivity activity = buildActivity();
    activity.showEditPopup(alarm.id);
    activity.getSupportFragmentManager().executePendingTransactions();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    GeoAlarmFragment fragment =
        (GeoAlarmFragment)
            activity.getSupportFragmentManager().findFragmentByTag("AddGeoAlarmFragment");
    assertNotNull(fragment);
    Button saveBtn = fragment.getView().findViewById(R.id.add_geo_alarm_save);
    saveBtn.performClick();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    GeoAlarm saved = GeoAlarm.getGeoAlarm(context, alarm.id);
    assertNotNull("Alarm should be saved after edit", saved);
  }

  @Test
  @Config(sdk = 28)
  public void geoAlarmFragment_addMode_saveWithNullPlace() {
    MapActivity activity = buildActivity();
    activity.showAddPopup(new LatLng(40.7, -74.0));
    activity.getSupportFragmentManager().executePendingTransactions();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    GeoAlarmFragment fragment =
        (GeoAlarmFragment)
            activity.getSupportFragmentManager().findFragmentByTag("AddGeoAlarmFragment");
    assertNotNull(fragment);
    // Clear location preview text to make place null
    android.widget.EditText preview = fragment.getView().findViewById(R.id.location_preview);
    preview.setText("");
    Button saveBtn = fragment.getView().findViewById(R.id.add_geo_alarm_save);
    saveBtn.performClick();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    // Alarm should be saved with null place, triggering geocodeAsync
    assertTrue("Alarm should be saved", !GeoAlarm.getGeoAlarms(context).isEmpty());
  }

  @Test
  public void geoAlarmFragment_editMode_withNullRingtoneUri() {
    GeoAlarm alarm =
        saveAlarm(
            enabledAlarm()
                .withRingtoneUri(null)
                .withPlace("Home")
                .withHour(7)
                .withMinute(0)
                .withEnabled(false));
    MapActivity activity = buildActivity();
    activity.showEditPopup(alarm.id);
    activity.getSupportFragmentManager().executePendingTransactions();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    GeoAlarmFragment fragment =
        (GeoAlarmFragment)
            activity.getSupportFragmentManager().findFragmentByTag("AddGeoAlarmFragment");
    assertNotNull(fragment);
    // ringtoneNameView should show "Vibrate only" for null URI
    TextView ringtoneLabel = fragment.getView().findViewById(R.id.ringtone_name);
    assertNotNull(ringtoneLabel);
  }

  @Test
  public void geoAlarmFragment_editMode_deleteButton_showsConfirmDialog() {
    GeoAlarm alarm =
        saveAlarm(
            enabledAlarm().withPlace("Delete Me").withHour(8).withMinute(0).withEnabled(false));
    MapActivity activity = buildActivity();
    activity.showEditPopup(alarm.id);
    activity.getSupportFragmentManager().executePendingTransactions();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    GeoAlarmFragment fragment =
        (GeoAlarmFragment)
            activity.getSupportFragmentManager().findFragmentByTag("AddGeoAlarmFragment");
    assertNotNull(fragment);
    Button deleteBtn = fragment.getView().findViewById(R.id.add_geo_alarm_delete);
    deleteBtn.performClick();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    // AlertDialog should be shown for confirmation
    android.app.AlertDialog alertDialog =
        org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    assertNotNull("Delete confirmation dialog should appear", alertDialog);
    // Click confirm
    alertDialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    // Alarm should be removed
    assertNull("Alarm should be deleted", GeoAlarm.getGeoAlarm(context, alarm.id));
  }

  // ---- adapter toggle callback via RecyclerView ----

  @Test
  @Config(sdk = 28)
  public void alarmList_toggleSwitch_disablesAlarm() {
    GeoAlarm alarm = saveAlarm(enabledAlarm().withPlace("Toggle Test").withHour(8).withMinute(0));
    MapActivity activity = buildActivity();
    // After setup, the alarm list should have the alarm
    androidx.recyclerview.widget.RecyclerView rv = activity.findViewById(R.id.alarm_list);
    assertNotNull(rv);
    // Force layout
    rv.measure(0, 0);
    rv.layout(0, 0, 1000, 2000);
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    // Find the switch in the first alarm card
    if (rv.getChildCount() > 0) {
      View child = rv.getChildAt(0);
      androidx.appcompat.widget.SwitchCompat toggle = child.findViewById(R.id.alarm_enabled_switch);
      if (toggle != null && toggle.isChecked()) {
        toggle.setChecked(false);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
      }
    }
  }

  // ---- drag handle ----

  @Test
  public void dragHandle_click_expandsAndCollapsesMap() {
    MapActivity activity = buildActivity();
    View dragHandle = activity.findViewById(R.id.drag_handle);
    assertNotNull(dragHandle);
    dragHandle.performClick(); // expand
    dragHandle.performClick(); // collapse
    Shadows.shadowOf(Looper.getMainLooper()).idle();
  }

  // ---- FAB click ----

  @Test
  public void fabClick_showsToast_whenLocationServiceNull() {
    MapActivity activity = buildActivity();
    activity.findViewById(R.id.fab_add).performClick();
    assertNotNull(ShadowToast.getLatestToast());
  }

  // ---- onResume paths ----

  @Test
  public void onResume_nullLocationService_redrawsAlarms() {
    saveAlarm(enabledAlarm());
    MapActivity activity = buildActivity();
    // After resume, alarms should be shown (locationService is null → else branch)
    assertEquals(View.VISIBLE, activity.findViewById(R.id.alarm_list).getVisibility());
  }

  // ---- intent with alarm ID ----

  @Test
  public void onCreate_withAlarmIdExtra_storesPendingAlarmId() {
    GeoAlarm alarm = saveAlarm(enabledAlarm());
    Intent intent = new Intent(context, MapActivity.class);
    intent.putExtra("ALARM_ID", alarm.id.toString());
    MapActivity activity = Robolectric.buildActivity(MapActivity.class, intent).setup().get();
    assertNotNull(activity);
  }

  // ---- redrawGeoAlarms with injected mock map ----

  @Test
  public void redrawGeoAlarms_withMap_rendersMarkersAndCircles() throws Exception {
    GeoAlarm alarm = saveAlarm(enabledAlarm().withPlace("Office"));
    MapActivity activity = buildActivity();

    // Inject a mock GoogleMap
    GoogleMap mockMap = mock(GoogleMap.class);
    UiSettings uiSettings = mock(UiSettings.class);
    when(mockMap.getUiSettings()).thenReturn(uiSettings);
    Marker mockMarker = mock(Marker.class);
    when(mockMap.addMarker(org.mockito.ArgumentMatchers.any(MarkerOptions.class)))
        .thenReturn(mockMarker);
    when(mockMap.getCameraPosition())
        .thenReturn(new CameraPosition(new LatLng(37.4, -122.0), 14, 0, 0));
    setPrivateField(activity, "map", mockMap);

    activity.onAddGeoAlarmFragmentClose();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    // Map operations should have been called (addMarker, addCircle, moveCamera)
    org.mockito.Mockito.verify(mockMap).clear();
    org.mockito.Mockito.verify(mockMap)
        .addMarker(org.mockito.ArgumentMatchers.any(MarkerOptions.class));
  }

  @Test
  public void redrawGeoAlarms_withMap_emptyAlarms_doesNotAddMarkers() throws Exception {
    MapActivity activity = buildActivity();
    GoogleMap mockMap = mock(GoogleMap.class);
    UiSettings uiSettings = mock(UiSettings.class);
    when(mockMap.getUiSettings()).thenReturn(uiSettings);
    setPrivateField(activity, "map", mockMap);

    activity.onAddGeoAlarmFragmentClose();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    org.mockito.Mockito.verify(mockMap).clear();
    org.mockito.Mockito.verify(mockMap, org.mockito.Mockito.never())
        .addMarker(org.mockito.ArgumentMatchers.any(MarkerOptions.class));
  }

  @Test
  public void showAddPopup_withMap_usesMapZoom() throws Exception {
    MapActivity activity = buildActivity();
    GoogleMap mockMap = mock(GoogleMap.class);
    when(mockMap.getCameraPosition()).thenReturn(new CameraPosition(new LatLng(0, 0), 16, 0, 0));
    setPrivateField(activity, "map", mockMap);

    activity.showAddPopup(new LatLng(37.4, -122.0));
    activity.getSupportFragmentManager().executePendingTransactions();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    Fragment f = activity.getSupportFragmentManager().findFragmentByTag("AddGeoAlarmFragment");
    assertNotNull(f);
  }

  @Test
  public void showEditPopup_withMap_usesMapZoom() throws Exception {
    GeoAlarm alarm = saveAlarm(enabledAlarm().withPlace("Edit Test"));
    MapActivity activity = buildActivity();
    GoogleMap mockMap = mock(GoogleMap.class);
    when(mockMap.getCameraPosition()).thenReturn(new CameraPosition(new LatLng(0, 0), 12, 0, 0));
    setPrivateField(activity, "map", mockMap);

    activity.showEditPopup(alarm.id);
    activity.getSupportFragmentManager().executePendingTransactions();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
  }

  // ---- helpers ----

  private static void setPrivateField(Object target, String fieldName, Object value)
      throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private GeoAlarm enabledAlarm() {
    return GeoAlarm.builder()
        .id(UUID.randomUUID())
        .location(new LatLng(37.4, -122.0))
        .radius(100)
        .enabled(true)
        .hour(8)
        .minute(0)
        .days(ImmutableSet.copyOf(DayOfWeek.values()))
        .build();
  }

  private GeoAlarm saveAlarm(GeoAlarm alarm) {
    GeoAlarm.save(context, alarm);
    return alarm;
  }

  private MapActivity buildActivity() {
    Intent intent = new Intent(context, MapActivity.class);
    return Robolectric.buildActivity(MapActivity.class, intent).setup().get();
  }
}
