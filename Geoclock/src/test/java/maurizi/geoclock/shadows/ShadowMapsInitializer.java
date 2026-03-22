package maurizi.geoclock.shadows;

import android.content.Context;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.MapsInitializer.Renderer;
import com.google.android.gms.maps.OnMapsSdkInitializedCallback;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@Implements(MapsInitializer.class)
public class ShadowMapsInitializer {

  @Implementation
  public static synchronized int initialize(Context context) {
    return 0; // ConnectionResult.SUCCESS
  }

  @Implementation
  public static synchronized int initialize(
      Context context, Renderer preferredRenderer, OnMapsSdkInitializedCallback callback) {
    if (callback != null) {
      callback.onMapsSdkInitialized(Renderer.LATEST);
    }
    return 0;
  }
}
