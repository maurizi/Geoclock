package maurizi.geoclock.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.PendingIntent;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.Tasks;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import maurizi.geoclock.GeoAlarm;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class LocationServiceGoogleTest {

  private GeofencingClient mockClient;
  private LocationServiceGoogle service;

  @Before
  public void setUp() {
    mockClient = mock(GeofencingClient.class);
    when(mockClient.addGeofences(any(GeofencingRequest.class), any(PendingIntent.class)))
        .thenReturn(Tasks.forResult(null));
    when(mockClient.removeGeofences(any(List.class))).thenReturn(Tasks.forResult(null));
    service = new LocationServiceGoogle(ApplicationProvider.getApplicationContext(), mockClient);
  }

  @Test
  public void addGeofences_emptyList_doesNotCallClientAndReturnsTask() {
    // Before fix: GeofencingRequest.Builder.build() threw "No geofence has been added"
    assertNotNull(service.addGeofences(Collections.emptyList()));
    verify(mockClient, never()).addGeofences(any(), any());
  }

  @Test
  public void addGeofences_zeroRadius_clampsAndRegistersSuccessfully() {
    // Before fix: setCircularRegion threw IllegalArgumentException for radius <= 0
    GeoAlarm alarm = alarm(0);
    service.addGeofences(Collections.singletonList(alarm));
    verify(mockClient).addGeofences(any(GeofencingRequest.class), any(PendingIntent.class));
  }

  @Test
  public void addGeofences_positiveRadius_registersGeofence() {
    GeoAlarm alarm = alarm(100);
    service.addGeofences(Collections.singletonList(alarm));
    verify(mockClient).addGeofences(any(GeofencingRequest.class), any(PendingIntent.class));
  }

  @Test
  public void addGeofences_multipleAlarms_registersAll() {
    List<GeoAlarm> alarms = Arrays.asList(alarm(50), alarm(200));
    service.addGeofences(alarms);

    ArgumentCaptor<GeofencingRequest> captor = ArgumentCaptor.forClass(GeofencingRequest.class);
    verify(mockClient).addGeofences(captor.capture(), any(PendingIntent.class));
    // Both geofences should be in the single request
    assertNotNull(captor.getValue().getGeofences());
    assertEquals(2, captor.getValue().getGeofences().size());
  }

  @Test
  public void addGeofence_singleAlarm_delegatesToAddGeofences() {
    GeoAlarm alarm = alarm(100);
    service.addGeofence(alarm);
    verify(mockClient).addGeofences(any(GeofencingRequest.class), any(PendingIntent.class));
  }

  @Test
  public void removeGeofence_callsClientWithAlarmId() {
    GeoAlarm alarm = alarm(100);
    service.removeGeofence(alarm);
    verify(mockClient).removeGeofences(Collections.singletonList(alarm.id.toString()));
  }

  private static GeoAlarm alarm(int radius) {
    return GeoAlarm.builder()
        .id(UUID.randomUUID())
        .location(new LatLng(37.4, -122.0))
        .radius(radius)
        .enabled(true)
        .build();
  }
}
