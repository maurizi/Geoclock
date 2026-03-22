package maurizi.geoclock.shadows;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/** Shadows SupportMapFragment to avoid Google Play Services initialization in Robolectric tests. */
@Implements(SupportMapFragment.class)
public class ShadowSupportMapFragment {

  @Implementation
  protected View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
    return new FrameLayout(inflater.getContext());
  }

  @Implementation
  public void getMapAsync(OnMapReadyCallback callback) {
    // no-op: map will never be ready in unit tests
  }
}
