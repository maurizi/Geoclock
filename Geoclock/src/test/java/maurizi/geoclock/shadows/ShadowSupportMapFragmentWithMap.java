package maurizi.geoclock.shadows;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.Marker;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * Shadow that invokes getMapAsync callback with a mock GoogleMap, enabling tests to exercise
 * map-dependent code paths (setupMap, marker drag, etc.).
 */
@Implements(SupportMapFragment.class)
public class ShadowSupportMapFragmentWithMap {

  private static GoogleMap lastMockMap;
  private static GoogleMap.OnMarkerDragListener lastMarkerDragListener;
  private static GoogleMap.OnMapClickListener lastMapClickListener;

  public static GoogleMap getLastMockMap() {
    return lastMockMap;
  }

  public static GoogleMap.OnMarkerDragListener getLastMarkerDragListener() {
    return lastMarkerDragListener;
  }

  public static GoogleMap.OnMapClickListener getLastMapClickListener() {
    return lastMapClickListener;
  }

  public static void reset() {
    lastMockMap = null;
    lastMarkerDragListener = null;
    lastMapClickListener = null;
  }

  @Implementation
  protected View onCreateView(
      LayoutInflater inflater, android.view.ViewGroup container, Bundle savedState) {
    return new FrameLayout(inflater.getContext());
  }

  @Implementation
  public void getMapAsync(OnMapReadyCallback callback) {
    GoogleMap mockMap = mock(GoogleMap.class);
    UiSettings mockUiSettings = mock(UiSettings.class);
    when(mockMap.getUiSettings()).thenReturn(mockUiSettings);

    Marker mockMarker = mock(Marker.class);
    when(mockMap.addMarker(any())).thenReturn(mockMarker);

    Circle mockCircle = mock(Circle.class);
    when(mockMap.addCircle(any())).thenReturn(mockCircle);

    // Capture listeners
    org.mockito.stubbing.Answer<Void> captureDragListener =
        invocation -> {
          lastMarkerDragListener = invocation.getArgument(0);
          return null;
        };
    org.mockito.stubbing.Answer<Void> captureClickListener =
        invocation -> {
          lastMapClickListener = invocation.getArgument(0);
          return null;
        };
    org.mockito.Mockito.doAnswer(captureDragListener).when(mockMap).setOnMarkerDragListener(any());
    org.mockito.Mockito.doAnswer(captureClickListener).when(mockMap).setOnMapClickListener(any());

    lastMockMap = mockMap;
    callback.onMapReady(mockMap);
  }
}
