package maurizi.geoclock.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Bundle;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.maps.model.LatLng;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import java.time.DayOfWeek;
import java.util.UUID;
import maurizi.geoclock.GeoAlarm;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class GeoAlarmFragmentUnitTest {

  private static final Gson gson = new Gson();
  private Context context;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
  }

  // ---- getEffectiveGeoAlarm: add mode ----

  @Test
  public void getEffectiveGeoAlarm_addMode_returnsNewAlarmWithInitialLocation() {
    GeoAlarmFragment fragment = new GeoAlarmFragment();
    Bundle args = new Bundle();
    args.putParcelable(GeoAlarmFragment.INITIAL_LATLNG, new LatLng(37.4, -122.0));
    args.putFloat(GeoAlarmFragment.INITIAL_ZOOM, 14f);
    GeoAlarm alarm = fragment.getEffectiveGeoAlarm(args, false);
    assertNotNull(alarm);
    assertEquals(new LatLng(37.4, -122.0), alarm.location);
    assertTrue("New alarm should be enabled", alarm.enabled);
    assertNotNull("New alarm should have an ID", alarm.id);
  }

  @Test
  public void getEffectiveGeoAlarm_addMode_nullLatLng_fallsBackToOrigin() {
    GeoAlarmFragment fragment = new GeoAlarmFragment();
    Bundle args = new Bundle();
    // No INITIAL_LATLNG set — getParcelable returns null
    args.putFloat(GeoAlarmFragment.INITIAL_ZOOM, 14f);
    GeoAlarm alarm = fragment.getEffectiveGeoAlarm(args, false);
    assertNotNull(alarm);
    assertEquals(new LatLng(0, 0), alarm.location);
  }

  // ---- getEffectiveGeoAlarm: edit mode ----

  @Test
  public void getEffectiveGeoAlarm_editMode_returnsExistingAlarm() {
    GeoAlarmFragment fragment = new GeoAlarmFragment();
    GeoAlarm original =
        GeoAlarm.builder()
            .id(UUID.randomUUID())
            .location(new LatLng(40.7, -74.0))
            .radius(500)
            .enabled(true)
            .hour(7)
            .minute(30)
            .place("NYC")
            .days(ImmutableSet.of(DayOfWeek.MONDAY))
            .build();
    Bundle args = new Bundle();
    args.putString(GeoAlarmFragment.EXISTING_ALARM, gson.toJson(original, GeoAlarm.class));
    GeoAlarm alarm = fragment.getEffectiveGeoAlarm(args, true);
    assertNotNull(alarm);
    assertEquals(original.id, alarm.id);
    assertEquals(original.place, alarm.place);
    assertEquals(original.radius, alarm.radius);
    assertEquals(original.hour, alarm.hour);
    assertEquals(original.minute, alarm.minute);
  }

  // ---- lifecycle methods with null mapThumbnailView ----

  @Test
  public void onPause_beforeViewCreated_doesNotCrash() {
    GeoAlarmFragment fragment = new GeoAlarmFragment();
    // mapThumbnailView is null before onCreateView — the null check should return early
    fragment.onPause();
  }

  @Test
  public void onLowMemory_beforeViewCreated_doesNotCrash() {
    GeoAlarmFragment fragment = new GeoAlarmFragment();
    fragment.onLowMemory();
  }

  @Test
  public void onSaveInstanceState_beforeViewCreated_doesNotCrash() {
    GeoAlarmFragment fragment = new GeoAlarmFragment();
    fragment.onSaveInstanceState(new Bundle());
  }

  // ---- getInitialRadius with imperial locale ----

  @Test
  public void getInitialRadius_noContext_returnsMetricDefault() {
    // Unattached fragment has null context → falls through to metric default
    GeoAlarmFragment fragment = new GeoAlarmFragment();
    int radius = fragment.getInitialRadius();
    assertEquals("Null context should return metric default", 250, radius);
  }

  // ---- getInitialRadius ----

  @Test
  public void getInitialRadius_returnsPositive() {
    GeoAlarmFragment fragment = new GeoAlarmFragment();
    int radius = fragment.getInitialRadius();
    assertTrue("Initial radius should be positive", radius > 0);
  }

  @Test
  public void getInitialRadius_isReasonableValue() {
    GeoAlarmFragment fragment = new GeoAlarmFragment();
    int radius = fragment.getInitialRadius();
    // Should be between 100 and 500 meters (metric or imperial initial)
    assertTrue("Initial radius should be >= 100", radius >= 100);
    assertTrue("Initial radius should be <= 500", radius <= 500);
  }
}
