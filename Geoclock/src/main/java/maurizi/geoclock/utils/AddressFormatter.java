package maurizi.geoclock.utils;

import android.location.Address;
import androidx.annotation.Nullable;

public final class AddressFormatter {

  private AddressFormatter() {}

  /**
   * Returns a short human-readable address: street number + name, or locality, or full address as
   * fallback.
   */
  @Nullable
  public static String shortAddress(Address addr) {
    String thoroughfare = addr.getThoroughfare();
    if (thoroughfare != null) {
      String sub = addr.getSubThoroughfare();
      return sub != null ? sub + " " + thoroughfare : thoroughfare;
    }
    if (addr.getLocality() != null) return addr.getLocality();
    return addr.getMaxAddressLineIndex() >= 0 ? addr.getAddressLine(0) : null;
  }
}
