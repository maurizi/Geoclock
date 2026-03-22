package maurizi.geoclock.shadows;

import static org.mockito.Mockito.mock;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.LatLngBounds;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/** Shadows CameraUpdateFactory to return mock CameraUpdate objects in unit tests. */
@Implements(CameraUpdateFactory.class)
public class ShadowCameraUpdateFactory {

  @Implementation
  public static CameraUpdate newLatLngBounds(LatLngBounds bounds, int padding) {
    return mock(CameraUpdate.class);
  }

  @Implementation
  public static CameraUpdate newLatLngZoom(
      com.google.android.gms.maps.model.LatLng latLng, float zoom) {
    return mock(CameraUpdate.class);
  }
}
