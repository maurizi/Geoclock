package maurizi.geoclock.utils;

import android.content.Context;
import android.icu.util.LocaleData;
import android.icu.util.ULocale;
import maurizi.geoclock.R;

public class DistanceUtils {

  private DistanceUtils() {}

  public static boolean useImperial(Context ctx) {
    LocaleData.MeasurementSystem ms =
        LocaleData.getMeasurementSystem(
            ULocale.forLocale(ctx.getResources().getConfiguration().getLocales().get(0)));
    return ms != LocaleData.MeasurementSystem.SI;
  }

  public static String formatDiameter(Context ctx, float radiusMeters) {
    float diameter = radiusMeters * 2;
    if (useImperial(ctx)) {
      float feet = diameter * 3.28084f;
      if (feet < 5280) {
        int rounded = Math.round(feet / 5f) * 5;
        return ctx.getString(R.string.distance_wide_feet, rounded);
      } else {
        return ctx.getString(R.string.distance_wide_miles, feet / 5280f);
      }
    }
    if (diameter < 1000) {
      int rounded = Math.round(diameter / 5f) * 5;
      return ctx.getString(R.string.distance_wide_meters, rounded);
    } else {
      return ctx.getString(R.string.distance_wide_km, diameter / 1000f);
    }
  }

  public static String formatDistance(Context ctx, float meters) {
    if (useImperial(ctx)) {
      float feet = meters * 3.28084f;
      if (feet < 5280) {
        return ctx.getString(R.string.distance_edge_feet, (int) feet);
      } else {
        return ctx.getString(R.string.distance_edge_miles, feet / 5280f);
      }
    }
    if (meters < 1000) {
      return ctx.getString(R.string.distance_edge_meters, (int) meters);
    } else {
      return ctx.getString(R.string.distance_edge_km, meters / 1000f);
    }
  }
}
