package maurizi.geoclock.test;

import static java.time.temporal.TemporalAdjusters.nextOrSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.maps.model.LatLng;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.TimeZone;
import java.util.UUID;
import maurizi.geoclock.GeoAlarm;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class GeoAlarmTest {

  static final GeoAlarm testAlarm =
      GeoAlarm.builder().location(new LatLng(0, 0)).radius(100).id(UUID.randomUUID()).build();

  private TimeZone originalTimeZone;

  @Before
  public void saveTimeZone() {
    originalTimeZone = TimeZone.getDefault();
  }

  @After
  public void restoreTimeZone() {
    TimeZone.setDefault(originalTimeZone);
  }

  // ---- null / missing-time guards ----

  @Test
  public void calculateAlarmTime_nullNow_returnsNull() {
    assertNull(testAlarm.withHour(8).withMinute(0).calculateAlarmTime(null));
  }

  @Test
  public void calculateAlarmTime_noHourOrMinute_returnsNull() {
    // testAlarm has no hour/minute set
    assertNull(testAlarm.calculateAlarmTime(LocalDateTime.now()));
  }

  @Test
  public void calculateAlarmTime_hourSetButNoMinute_returnsNull() {
    assertNull(testAlarm.withHour(8).calculateAlarmTime(LocalDateTime.now()));
  }

  // ---- Non-repeating alarms ----

  @Test
  public void calculateAlarmTime_nonRepeating_beforeAlarmTime_firesToday() {
    LocalDate today = LocalDate.now();
    assertAlarmTime(
        5,
        0,
        today.atTime(4, 0),
        today.atTime(5, 0),
        "Should fire today if current time is before alarm time");
  }

  @Test
  public void calculateAlarmTime_nonRepeating_afterAlarmTime_firesTomorrow() {
    LocalDate today = LocalDate.now();
    assertAlarmTime(
        5,
        0,
        today.atTime(6, 0),
        today.plusDays(1).atTime(5, 0),
        "Should fire tomorrow if current time is after alarm time");
  }

  // ---- Repeating alarms ----

  @Test
  public void calculateAlarmTime_repeating_todayIncluded_beforeTime_firesToday() {
    LocalDate monday = LocalDate.now().with(nextOrSame(DayOfWeek.MONDAY));
    assertAlarmTime(
        5,
        0,
        monday.atTime(4, 0),
        monday.atTime(5, 0),
        new DayOfWeek[] {DayOfWeek.MONDAY, DayOfWeek.FRIDAY},
        "Should fire today (Monday) since alarm time hasn't passed yet");
  }

  @Test
  public void calculateAlarmTime_repeating_laterDayThisWeek_firesNext() {
    LocalDate monday = LocalDate.now().with(nextOrSame(DayOfWeek.MONDAY));
    // Monday 6am, alarm for {Mon, Sun} at 5am — Monday's 5am passed, Sunday (value=7) comes next
    assertAlarmTime(
        5,
        0,
        monday.atTime(6, 0),
        monday.plusDays(6).atTime(5, 0),
        new DayOfWeek[] {DayOfWeek.MONDAY, DayOfWeek.SUNDAY},
        "Should fire Sunday — next day with value > Monday");
  }

  @Test
  public void calculateAlarmTime_repeating_onlyDayIsToday_afterTime_firesNextWeek() {
    LocalDate monday = LocalDate.now().with(nextOrSame(DayOfWeek.MONDAY));
    assertAlarmTime(
        5,
        0,
        monday.atTime(6, 0),
        monday.plusDays(7).atTime(5, 0),
        new DayOfWeek[] {DayOfWeek.MONDAY},
        "Should fire next Monday when only day is Monday and alarm already passed");
  }

  // ---- DST: spring forward (2024-03-10, America/New_York: 2:00 AM → 3:00 AM) ----

  @Test
  public void calculateAlarmTime_dstSpringForward_alarmInGap_returnsValidDateTime() {
    TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
    // now = 1:30 AM ET on spring-forward day; alarm at 2:30 (gap hour)
    LocalDateTime now = LocalDateTime.of(2024, 3, 10, 1, 30);
    GeoAlarm alarm = testAlarm.withHour(2).withMinute(30);
    ZonedDateTime result = alarm.calculateAlarmTime(now);
    assertNotNull("Should return a valid ZonedDateTime even for gap hour", result);
    // Java adjusts 2:30 AM (non-existent) to 3:30 AM EDT
    assertEquals(3, result.getHour());
    assertEquals(30, result.getMinute());
  }

  // ---- DST: fall back (2024-11-03, America/New_York: 2:00 AM → 1:00 AM) ----

  @Test
  public void calculateAlarmTime_dstFallBack_alarmInAmbiguousHour_returnsPreFoldTime() {
    TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
    // now = 12:30 AM ET before fall-back; alarm at 1:30 (ambiguous — occurs twice)
    LocalDateTime now = LocalDateTime.of(2024, 11, 3, 0, 30);
    GeoAlarm alarm = testAlarm.withHour(1).withMinute(30);
    ZonedDateTime result = alarm.calculateAlarmTime(now);
    assertNotNull(result);
    assertEquals(1, result.getHour());
    assertEquals(30, result.getMinute());
    // Java defaults to the pre-fold (EDT = UTC-4) occurrence
    assertEquals(-4 * 3600, result.getOffset().getTotalSeconds());
  }

  // ---- Timezone change ----

  @Test
  public void calculateAlarmTime_differentSystemTimezone_reflectsZone() {
    LocalDateTime now = LocalDateTime.of(2024, 6, 1, 7, 0);

    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    ZonedDateTime utcResult = testAlarm.withHour(8).withMinute(0).calculateAlarmTime(now);

    TimeZone.setDefault(TimeZone.getTimeZone("America/Chicago"));
    ZonedDateTime chicagoResult = testAlarm.withHour(8).withMinute(0).calculateAlarmTime(now);

    assertNotNull(utcResult);
    assertNotNull(chicagoResult);
    // Same local time "08:00" should represent different UTC instants in different zones
    assertNotEquals(
        "Different timezones should produce different UTC instants",
        utcResult.toInstant(),
        chicagoResult.toInstant());
  }

  // ---- Repeating edge cases ----

  @Test
  public void calculateAlarmTime_repeating_sundayOnly_onSundayAfterTime_firesNextSunday() {
    LocalDate sunday = LocalDate.now().with(nextOrSame(DayOfWeek.SUNDAY));
    // On Sunday, alarm time has already passed — next fire should be 7 days forward
    assertAlarmTime(
        5,
        0,
        sunday.atTime(6, 0),
        sunday.plusDays(7).atTime(5, 0),
        new DayOfWeek[] {DayOfWeek.SUNDAY},
        "Sunday-only alarm after time should fire next Sunday (+7 days)");
  }

  @Test
  public void calculateAlarmTime_repeating_allDays_firesWithin24Hours() {
    LocalDateTime now = LocalDateTime.now();
    GeoAlarm alarm =
        testAlarm
            .withDays(ImmutableSet.copyOf(DayOfWeek.values()))
            .withHour(now.getHour())
            .withMinute(now.getMinute() + 1 > 59 ? 0 : now.getMinute() + 1);
    ZonedDateTime result = alarm.calculateAlarmTime(now);
    assertNotNull(result);
    long hoursUntilAlarm = java.time.Duration.between(ZonedDateTime.now(), result).toHours();
    assertTrue("Every-day alarm should fire within 24 hours", hoursUntilAlarm < 24);
  }

  @Test
  public void calculateAlarmTime_repeating_wrapAroundWeek_correctDaysForward() {
    // Friday, alarm only on Monday → fires next Monday (+3 days)
    LocalDate friday = LocalDate.now().with(nextOrSame(DayOfWeek.FRIDAY));
    assertAlarmTime(
        8,
        0,
        friday.atTime(9, 0),
        friday.plusDays(3).atTime(8, 0),
        new DayOfWeek[] {DayOfWeek.MONDAY},
        "Mon-only alarm on Friday should fire next Monday (+3 days)");
  }

  // ---- ringtoneUri field ----

  @Test
  public void ringtoneUri_defaultsToNull() {
    assertNull("ringtoneUri should default to null", testAlarm.ringtoneUri);
  }

  @Test
  public void ringtoneUri_roundTrips_throughSaveLoad() {
    Context context = ApplicationProvider.getApplicationContext();
    String uri = "content://media/internal/audio/media/42";
    GeoAlarm alarm = testAlarm.withRingtoneUri(uri).withEnabled(false);
    GeoAlarm.save(context, alarm);
    GeoAlarm loaded = GeoAlarm.getGeoAlarm(context, alarm.id);
    assertNotNull(loaded);
    assertEquals(uri, loaded.ringtoneUri);
  }

  @Test
  public void ringtoneUri_null_roundTrips_throughSaveLoad() {
    Context context = ApplicationProvider.getApplicationContext();
    GeoAlarm alarm = testAlarm.withRingtoneUri(null).withEnabled(false);
    GeoAlarm.save(context, alarm);
    GeoAlarm loaded = GeoAlarm.getGeoAlarm(context, alarm.id);
    assertNotNull(loaded);
    assertNull(loaded.ringtoneUri);
  }

  // ---- getRadiusSizeLabel ----

  @Test
  public void getRadiusSizeLabel_xs_atBoundary() {
    Context context = ApplicationProvider.getApplicationContext();
    assertTrue(GeoAlarm.getRadiusSizeLabel(context, 200).startsWith("Extra small"));
  }

  @Test
  public void getRadiusSizeLabel_xs_belowBoundary() {
    Context context = ApplicationProvider.getApplicationContext();
    assertTrue(GeoAlarm.getRadiusSizeLabel(context, 50).startsWith("Extra small"));
  }

  @Test
  public void getRadiusSizeLabel_xs_justBelow() {
    Context context = ApplicationProvider.getApplicationContext();
    assertTrue(GeoAlarm.getRadiusSizeLabel(context, 151).startsWith("Extra small"));
  }

  @Test
  public void getRadiusSizeLabel_small_atUpperBoundary() {
    Context context = ApplicationProvider.getApplicationContext();
    assertTrue(GeoAlarm.getRadiusSizeLabel(context, 500).startsWith("Small"));
  }

  @Test
  public void getRadiusSizeLabel_medium_atLowerBoundary() {
    Context context = ApplicationProvider.getApplicationContext();
    assertTrue(GeoAlarm.getRadiusSizeLabel(context, 501).startsWith("Medium"));
  }

  @Test
  public void getRadiusSizeLabel_medium_atUpperBoundary() {
    Context context = ApplicationProvider.getApplicationContext();
    assertTrue(GeoAlarm.getRadiusSizeLabel(context, 2000).startsWith("Medium"));
  }

  @Test
  public void getRadiusSizeLabel_large_atLowerBoundary() {
    Context context = ApplicationProvider.getApplicationContext();
    assertTrue(GeoAlarm.getRadiusSizeLabel(context, 2001).startsWith("Large"));
  }

  @Test
  public void getRadiusSizeLabel_large_atUpperBoundary() {
    Context context = ApplicationProvider.getApplicationContext();
    assertTrue(GeoAlarm.getRadiusSizeLabel(context, 10000).startsWith("Large"));
  }

  @Test
  public void getRadiusSizeLabel_xl_atLowerBoundary() {
    Context context = ApplicationProvider.getApplicationContext();
    assertTrue(GeoAlarm.getRadiusSizeLabel(context, 10001).startsWith("Extra large"));
  }

  @Test
  public void getRadiusSizeLabel_xl_large() {
    Context context = ApplicationProvider.getApplicationContext();
    assertTrue(GeoAlarm.getRadiusSizeLabel(context, 30000).startsWith("Extra large"));
  }

  @Test
  public void getRadiusSizeLabel_xl_veryLarge() {
    Context context = ApplicationProvider.getApplicationContext();
    assertTrue(GeoAlarm.getRadiusSizeLabel(context, 50000).startsWith("Extra large"));
  }

  @Test
  public void getRadiusSizeLabel_containsDiameterSuffix() {
    Context context = ApplicationProvider.getApplicationContext();
    String label = GeoAlarm.getRadiusSizeLabel(context, 250);
    assertTrue("Label should contain ' · '", label.contains(" \u00B7 "));
    assertTrue("Label should contain 'wide'", label.contains("wide"));
  }

  // ---- getCircleOptions ----

  @Test
  public void getCircleOptions_hasCorrectCenter() {
    GeoAlarm alarm =
        GeoAlarm.builder()
            .location(new LatLng(37.4220, -122.0841))
            .radius(500)
            .id(UUID.randomUUID())
            .build();
    assertEquals(new LatLng(37.4220, -122.0841), alarm.getCircleOptions().getCenter());
  }

  @Test
  public void getCircleOptions_hasCorrectRadius() {
    GeoAlarm alarm =
        GeoAlarm.builder()
            .location(new LatLng(37.4220, -122.0841))
            .radius(500)
            .id(UUID.randomUUID())
            .build();
    assertEquals(500.0, alarm.getCircleOptions().getRadius(), 0.01);
  }

  @Test
  public void getCircleOptions_hasCorrectColors() {
    GeoAlarm alarm =
        GeoAlarm.builder().location(new LatLng(0, 0)).radius(100).id(UUID.randomUUID()).build();
    assertEquals(0x3300C5CD, alarm.getCircleOptions().getFillColor());
    assertEquals(0xFF00C5CD, alarm.getCircleOptions().getStrokeColor());
    assertEquals(2f, alarm.getCircleOptions().getStrokeWidth(), 0.01);
  }

  // ---- getGeoAlarms ----

  @Test
  public void getGeoAlarms_emptyPrefs_returnsEmpty() {
    Context context = ApplicationProvider.getApplicationContext();
    Collection<GeoAlarm> alarms = GeoAlarm.getGeoAlarms(context);
    assertTrue(alarms.isEmpty());
  }

  @Test
  public void getGeoAlarms_singleAlarm_returnsOne() {
    Context context = ApplicationProvider.getApplicationContext();
    GeoAlarm alarm = testAlarm.withEnabled(false);
    GeoAlarm.save(context, alarm);
    Collection<GeoAlarm> alarms = GeoAlarm.getGeoAlarms(context);
    assertEquals(1, alarms.size());
  }

  @Test
  public void getGeoAlarms_multipleAlarms_returnsAll() {
    Context context = ApplicationProvider.getApplicationContext();
    GeoAlarm a1 =
        GeoAlarm.builder()
            .location(new LatLng(1, 1))
            .radius(100)
            .id(UUID.randomUUID())
            .enabled(false)
            .build();
    GeoAlarm a2 =
        GeoAlarm.builder()
            .location(new LatLng(2, 2))
            .radius(200)
            .id(UUID.randomUUID())
            .enabled(false)
            .build();
    GeoAlarm.save(context, a1);
    GeoAlarm.save(context, a2);
    Collection<GeoAlarm> alarms = GeoAlarm.getGeoAlarms(context);
    assertEquals(2, alarms.size());
  }

  @Test
  public void getGeoAlarms_malformedJson_skipsInvalid() {
    Context context = ApplicationProvider.getApplicationContext();
    GeoAlarm alarm = testAlarm.withEnabled(false);
    GeoAlarm.save(context, alarm);
    // Write malformed JSON directly
    SharedPreferences prefs = context.getSharedPreferences("alarms", Context.MODE_PRIVATE);
    prefs.edit().putString("bad-key", "not valid json {{{").apply();
    Collection<GeoAlarm> alarms = GeoAlarm.getGeoAlarms(context);
    assertEquals(1, alarms.size());
  }

  // ---- getGeoAlarm ----

  @Test
  public void getGeoAlarm_nonexistentId_returnsNull() {
    Context context = ApplicationProvider.getApplicationContext();
    assertNull(GeoAlarm.getGeoAlarm(context, UUID.randomUUID()));
  }

  @Test
  public void getGeoAlarm_existingAlarm_returnsAlarm() {
    Context context = ApplicationProvider.getApplicationContext();
    GeoAlarm alarm = testAlarm.withEnabled(false);
    GeoAlarm.save(context, alarm);
    GeoAlarm loaded = GeoAlarm.getGeoAlarm(context, alarm.id);
    assertNotNull(loaded);
    assertEquals(alarm.id, loaded.id);
  }

  // ---- getGeoAlarmForGeofenceFn ----

  @Test
  public void getGeoAlarmForGeofenceFn_validId_returnsAlarm() {
    Context context = ApplicationProvider.getApplicationContext();
    GeoAlarm alarm = testAlarm.withEnabled(false);
    GeoAlarm.save(context, alarm);
    Geofence geofence = mock(Geofence.class);
    when(geofence.getRequestId()).thenReturn(alarm.id.toString());
    Function<Geofence, GeoAlarm> fn = GeoAlarm.getGeoAlarmForGeofenceFn(context);
    GeoAlarm result = fn.apply(geofence);
    assertNotNull(result);
    assertEquals(alarm.id, result.id);
  }

  @Test
  public void getGeoAlarmForGeofenceFn_unknownId_returnsNull() {
    Context context = ApplicationProvider.getApplicationContext();
    Geofence geofence = mock(Geofence.class);
    when(geofence.getRequestId()).thenReturn(UUID.randomUUID().toString());
    Function<Geofence, GeoAlarm> fn = GeoAlarm.getGeoAlarmForGeofenceFn(context);
    assertNull(fn.apply(geofence));
  }

  // ---- toString ----

  @Test
  public void toString_withPlace_returnsPlace() {
    assertEquals("Home", testAlarm.withPlace("Home").toString());
  }

  @Test
  public void toString_withoutPlace_returnsCoordinates() {
    GeoAlarm alarm =
        GeoAlarm.builder()
            .location(new LatLng(37.4220, -122.0841))
            .radius(100)
            .id(UUID.randomUUID())
            .build();
    assertEquals("37.4220,-122.0841", alarm.toString());
  }

  // ---- getMarkerOptions ----

  @Test
  public void getMarkerOptions_withPlace_titleIsPlace() {
    assertEquals("Work", testAlarm.withPlace("Work").getMarkerOptions().getTitle());
  }

  @Test
  public void getMarkerOptions_nullPlace_titleIsEmpty() {
    assertEquals("", testAlarm.getMarkerOptions().getTitle());
  }

  @Test
  public void getMarkerOptions_positionMatchesLocation() {
    GeoAlarm alarm =
        GeoAlarm.builder()
            .location(new LatLng(37.4220, -122.0841))
            .radius(100)
            .id(UUID.randomUUID())
            .build();
    assertEquals(new LatLng(37.4220, -122.0841), alarm.getMarkerOptions().getPosition());
  }

  // ---- save ----

  @Test
  public void save_enabledAlarm_setsTime() {
    Context context = ApplicationProvider.getApplicationContext();
    GeoAlarm alarm =
        GeoAlarm.builder()
            .location(new LatLng(0, 0))
            .radius(100)
            .id(UUID.randomUUID())
            .enabled(true)
            .hour(8)
            .minute(0)
            .days(ImmutableSet.copyOf(DayOfWeek.values()))
            .build();
    assertNull(alarm.time);
    GeoAlarm.save(context, alarm);
    GeoAlarm loaded = GeoAlarm.getGeoAlarm(context, alarm.id);
    assertNotNull(loaded);
    assertNotNull("Time should be set after save for enabled alarm", loaded.time);
  }

  @Test
  public void save_disabledAlarm_preservesNullTime() {
    Context context = ApplicationProvider.getApplicationContext();
    GeoAlarm alarm =
        GeoAlarm.builder()
            .location(new LatLng(0, 0))
            .radius(100)
            .id(UUID.randomUUID())
            .enabled(false)
            .hour(8)
            .minute(0)
            .build();
    GeoAlarm.save(context, alarm);
    GeoAlarm loaded = GeoAlarm.getGeoAlarm(context, alarm.id);
    assertNotNull(loaded);
    assertNull("Time should remain null for disabled alarm", loaded.time);
  }

  @Test
  public void save_enabledAlarmNoHour_timeRemainsNull() {
    Context context = ApplicationProvider.getApplicationContext();
    GeoAlarm alarm =
        GeoAlarm.builder()
            .location(new LatLng(0, 0))
            .radius(100)
            .id(UUID.randomUUID())
            .enabled(true)
            .build();
    GeoAlarm.save(context, alarm);
    GeoAlarm loaded = GeoAlarm.getGeoAlarm(context, alarm.id);
    assertNotNull(loaded);
    assertNull("Time should remain null when hour/minute not set", loaded.time);
  }

  // ---- remove ----

  @Test
  public void remove_existingAlarm_noLongerRetrievable() {
    Context context = ApplicationProvider.getApplicationContext();
    GeoAlarm alarm = testAlarm.withEnabled(false);
    GeoAlarm.save(context, alarm);
    assertNotNull(GeoAlarm.getGeoAlarm(context, alarm.id));
    GeoAlarm.remove(context, alarm);
    assertNull(GeoAlarm.getGeoAlarm(context, alarm.id));
  }

  // ---- isNonRepeating ----

  @Test
  public void isNonRepeating_nullDays_returnsTrue() {
    assertTrue(testAlarm.isNonRepeating());
  }

  @Test
  public void isNonRepeating_emptyDays_returnsTrue() {
    assertTrue(testAlarm.withDays(ImmutableSet.of()).isNonRepeating());
  }

  @Test
  public void isNonRepeating_withDays_returnsFalse() {
    assertFalse(testAlarm.withDays(ImmutableSet.of(DayOfWeek.MONDAY)).isNonRepeating());
  }

  // ---- toJson round-trip ----

  @Test
  public void toJson_roundTrip_preservesAllFields() {
    Context context = ApplicationProvider.getApplicationContext();
    GeoAlarm alarm =
        GeoAlarm.builder()
            .location(new LatLng(37.4, -122.0))
            .radius(500)
            .id(UUID.randomUUID())
            .enabled(false)
            .hour(8)
            .minute(30)
            .place("Home")
            .days(ImmutableSet.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
            .ringtoneUri("content://media/42")
            .build();
    GeoAlarm.save(context, alarm);
    GeoAlarm loaded = GeoAlarm.getGeoAlarm(context, alarm.id);
    assertNotNull(loaded);
    assertEquals(alarm.place, loaded.place);
    assertEquals(alarm.radius, loaded.radius);
    assertEquals(alarm.hour, loaded.hour);
    assertEquals(alarm.minute, loaded.minute);
    assertEquals(alarm.ringtoneUri, loaded.ringtoneUri);
    assertEquals(alarm.days, loaded.days);
  }

  // ---- equals ----

  @Test
  public void equals_sameAlarm_returnsTrue() {
    GeoAlarm a =
        GeoAlarm.builder()
            .id(testAlarm.id)
            .location(testAlarm.location)
            .radius(testAlarm.radius)
            .build();
    GeoAlarm b =
        GeoAlarm.builder()
            .id(testAlarm.id)
            .location(testAlarm.location)
            .radius(testAlarm.radius)
            .build();
    assertEquals(a, b);
  }

  @Test
  public void equals_differentId_notEqual() {
    GeoAlarm a =
        GeoAlarm.builder().id(UUID.randomUUID()).location(new LatLng(0, 0)).radius(100).build();
    GeoAlarm b =
        GeoAlarm.builder().id(UUID.randomUUID()).location(new LatLng(0, 0)).radius(100).build();
    assertNotEquals(a, b);
  }

  @Test
  public void equals_withAllFieldsSet() {
    UUID id = UUID.randomUUID();
    GeoAlarm a =
        GeoAlarm.builder()
            .id(id)
            .location(new LatLng(37.4, -122.0))
            .radius(500)
            .enabled(true)
            .hour(8)
            .minute(30)
            .place("Home")
            .days(ImmutableSet.of(DayOfWeek.MONDAY))
            .ringtoneUri("content://media/42")
            .time(123456L)
            .build();
    GeoAlarm b =
        GeoAlarm.builder()
            .id(id)
            .location(new LatLng(37.4, -122.0))
            .radius(500)
            .enabled(true)
            .hour(8)
            .minute(30)
            .place("Home")
            .days(ImmutableSet.of(DayOfWeek.MONDAY))
            .ringtoneUri("content://media/42")
            .time(123456L)
            .build();
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  public void equals_null_notEqual() {
    assertFalse(testAlarm.equals(null));
  }

  @Test
  public void equals_differentType_notEqual() {
    assertFalse(testAlarm.equals("not an alarm"));
  }

  @Test
  public void equals_differentPlace_notEqual() {
    GeoAlarm a = testAlarm.withPlace("Home");
    GeoAlarm b = testAlarm.withPlace("Work");
    assertNotEquals(a, b);
  }

  @Test
  public void equals_nullVsNonNullPlace_notEqual() {
    GeoAlarm a = testAlarm.withPlace(null);
    GeoAlarm b = testAlarm.withPlace("Home");
    assertNotEquals(a, b);
  }

  @Test
  public void equals_nullVsNonNullHour_notEqual() {
    GeoAlarm a = testAlarm.withHour(null);
    GeoAlarm b = testAlarm.withHour(8);
    assertNotEquals(a, b);
  }

  @Test
  public void equals_nullVsNonNullMinute_notEqual() {
    GeoAlarm a = testAlarm.withMinute(null);
    GeoAlarm b = testAlarm.withMinute(30);
    assertNotEquals(a, b);
  }

  @Test
  public void equals_nullVsNonNullDays_notEqual() {
    GeoAlarm a = testAlarm.withDays(null);
    GeoAlarm b = testAlarm.withDays(ImmutableSet.of(DayOfWeek.MONDAY));
    assertNotEquals(a, b);
  }

  @Test
  public void equals_nullVsNonNullRingtoneUri_notEqual() {
    GeoAlarm a = testAlarm.withRingtoneUri(null);
    GeoAlarm b = testAlarm.withRingtoneUri("content://media/42");
    assertNotEquals(a, b);
  }

  @Test
  public void equals_nullVsNonNullTime_notEqual() {
    GeoAlarm a = testAlarm.withTime(null);
    GeoAlarm b = testAlarm.withTime(123456L);
    assertNotEquals(a, b);
  }

  @Test
  public void equals_differentEnabled_notEqual() {
    GeoAlarm a = testAlarm.withEnabled(true);
    GeoAlarm b = testAlarm.withEnabled(false);
    assertNotEquals(a, b);
  }

  @Test
  public void equals_differentRadius_notEqual() {
    GeoAlarm a = testAlarm.withRadius(100);
    GeoAlarm b = testAlarm.withRadius(200);
    assertNotEquals(a, b);
  }

  @Test
  public void equals_differentLocation_notEqual() {
    GeoAlarm a = testAlarm.withLocation(new LatLng(0, 0));
    GeoAlarm b = testAlarm.withLocation(new LatLng(1, 1));
    assertNotEquals(a, b);
  }

  // ---- hashCode ----

  @Test
  public void hashCode_equalObjects_sameHash() {
    UUID id = UUID.randomUUID();
    GeoAlarm a =
        GeoAlarm.builder()
            .id(id)
            .location(new LatLng(0, 0))
            .radius(100)
            .place("Home")
            .hour(8)
            .minute(30)
            .days(ImmutableSet.of(DayOfWeek.MONDAY))
            .ringtoneUri("uri")
            .time(12345L)
            .build();
    GeoAlarm b =
        GeoAlarm.builder()
            .id(id)
            .location(new LatLng(0, 0))
            .radius(100)
            .place("Home")
            .hour(8)
            .minute(30)
            .days(ImmutableSet.of(DayOfWeek.MONDAY))
            .ringtoneUri("uri")
            .time(12345L)
            .build();
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  public void hashCode_withNullFields_doesNotThrow() {
    // Builder with only required fields; all nullable fields are null
    GeoAlarm alarm =
        GeoAlarm.builder().id(UUID.randomUUID()).location(new LatLng(0, 0)).radius(100).build();
    alarm.hashCode(); // should not throw
  }

  // ---- toJson ----

  @Test
  public void toJson_returnsNonEmptyString() {
    String json = testAlarm.toJson();
    assertNotNull(json);
    assertTrue("toJson should return non-empty", json.length() > 0);
    assertTrue("toJson should contain id", json.contains(testAlarm.id.toString()));
  }

  // ---- with* methods that were at 0% ----

  @Test
  public void withId_changesId() {
    UUID newId = UUID.randomUUID();
    GeoAlarm changed = testAlarm.withId(newId);
    assertEquals(newId, changed.id);
    assertNotEquals(testAlarm.id, changed.id);
  }

  @Test
  public void withLocation_changesLocation() {
    LatLng newLoc = new LatLng(40.7, -74.0);
    GeoAlarm changed = testAlarm.withLocation(newLoc);
    assertEquals(newLoc, changed.location);
  }

  @Test
  public void withRadius_changesRadius() {
    GeoAlarm changed = testAlarm.withRadius(999);
    assertEquals(999, changed.radius);
  }

  // ---- Helpers ----

  private void assertAlarmTime(
      int hour, int minute, LocalDateTime now, LocalDateTime expected, String message) {
    assertAlarmTime(hour, minute, now, expected, new DayOfWeek[] {}, message);
  }

  private void assertAlarmTime(
      int hour,
      int minute,
      LocalDateTime now,
      LocalDateTime expectedLocal,
      DayOfWeek[] days,
      String message) {
    GeoAlarm alarm =
        testAlarm.withDays(ImmutableSet.copyOf(days)).withHour(hour).withMinute(minute);
    ZonedDateTime expected = expectedLocal.atZone(ZoneId.systemDefault());
    ZonedDateTime actual = alarm.calculateAlarmTime(now);
    assertEquals(message, expected, actual);
  }
}
