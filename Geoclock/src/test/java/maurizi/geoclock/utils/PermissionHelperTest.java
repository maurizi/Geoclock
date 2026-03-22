package maurizi.geoclock.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowApplication;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class PermissionHelperTest {

  private Context context;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
  }

  // ---- needsBackgroundLocation ----

  @Test
  @Config(sdk = 28)
  public void needsBackgroundLocation_belowQ_returnsFalse() {
    assertFalse(PermissionHelper.needsBackgroundLocation(context));
  }

  @Test
  @Config(sdk = 29)
  public void needsBackgroundLocation_api29_returnsTrue() {
    // Background location not granted by default in Robolectric
    assertTrue(PermissionHelper.needsBackgroundLocation(context));
  }

  // ---- needsNotificationPermission ----

  @Test
  @Config(sdk = 32)
  public void needsNotificationPermission_belowTiramisu_returnsFalse() {
    assertFalse(PermissionHelper.needsNotificationPermission(context));
  }

  @Test
  @Config(sdk = 33)
  public void needsNotificationPermission_api33_returnsTrue() {
    assertTrue(PermissionHelper.needsNotificationPermission(context));
  }

  // ---- needsExactAlarmPermission ----

  @Test
  @Config(sdk = 28)
  public void needsExactAlarmPermission_belowS_returnsFalse() {
    assertFalse(PermissionHelper.needsExactAlarmPermission(context));
  }

  @Test
  @Config(sdk = 31)
  public void needsExactAlarmPermission_api31_granted_returnsFalse() {
    ShadowAlarmManager.setCanScheduleExactAlarms(true);
    assertFalse(PermissionHelper.needsExactAlarmPermission(context));
  }

  @Test
  @Config(sdk = 31)
  public void needsExactAlarmPermission_api31_revoked_returnsTrue() {
    ShadowAlarmManager.setCanScheduleExactAlarms(false);
    assertTrue(PermissionHelper.needsExactAlarmPermission(context));
  }

  // ---- hasAllAlarmPermissions ----

  @Test
  public void hasAllAlarmPermissions_nullContext_returnsFalse() {
    assertFalse(PermissionHelper.hasAllAlarmPermissions(null));
  }

  @Test
  @Config(sdk = 28)
  public void hasAllAlarmPermissions_oldApi_allGranted_returnsTrue() {
    // On API 28, no background location, no notification permission, no exact alarm needed
    assertTrue(PermissionHelper.hasAllAlarmPermissions(context));
  }

  // ---- needsFullScreenIntentPermission ----

  @Test
  @Config(sdk = 33)
  public void needsFullScreenIntentPermission_belowUpsideDownCake_returnsFalse() {
    assertFalse(PermissionHelper.needsFullScreenIntentPermission(context));
  }

  // ---- hasAllAlarmPermissions edge cases ----

  @Test
  @Config(sdk = 33)
  public void hasAllAlarmPermissions_api33_missingNotification_returnsFalse() {
    // On API 33, POST_NOTIFICATIONS is not granted by default
    assertFalse(PermissionHelper.hasAllAlarmPermissions(context));
  }

  @Test
  @Config(sdk = 29)
  public void hasAllAlarmPermissions_api29_missingBackgroundLocation_returnsFalse() {
    assertFalse(PermissionHelper.hasAllAlarmPermissions(context));
  }

  // ---- needsFullScreenIntentPermission on API 34 ----

  @Test
  @Config(sdk = 34)
  public void needsFullScreenIntentPermission_api34_returnsTrue() {
    // On API 34 (UPSIDE_DOWN_CAKE), full-screen intent permission is required
    // Default Robolectric does not grant it
    assertTrue(PermissionHelper.needsFullScreenIntentPermission(context));
  }

  @Test
  @Config(sdk = 34)
  public void hasAllAlarmPermissions_api34_returnsFalse() {
    // On API 34, multiple permissions are required and not granted by default
    assertFalse(PermissionHelper.hasAllAlarmPermissions(context));
  }

  // ---- needsExactAlarmPermission additional cases ----

  @Test
  @Config(sdk = 33)
  public void needsExactAlarmPermission_api33_granted_returnsFalse() {
    ShadowAlarmManager.setCanScheduleExactAlarms(true);
    assertFalse(PermissionHelper.needsExactAlarmPermission(context));
  }

  @Test
  @Config(sdk = 33)
  public void needsExactAlarmPermission_api33_revoked_returnsTrue() {
    ShadowAlarmManager.setCanScheduleExactAlarms(false);
    assertTrue(PermissionHelper.needsExactAlarmPermission(context));
  }

  // ---- requestAlarmPermissions chain ----

  @Test
  @Config(sdk = 28)
  public void requestAlarmPermissions_api28_allGranted_callsOnComplete() {
    FragmentActivity activity =
        Robolectric.buildActivity(FragmentActivity.class).create().start().resume().get();
    boolean[] completed = {false};
    PermissionHelper.requestAlarmPermissions(activity, () -> completed[0] = true);
    assertTrue("onComplete should be called when all permissions already granted", completed[0]);
  }

  @Test
  @Config(sdk = 29)
  public void requestAlarmPermissions_api29_showsDialogForBackgroundLocation() {
    FragmentActivity activity =
        Robolectric.buildActivity(FragmentActivity.class).create().start().resume().get();
    boolean[] completed = {false};
    PermissionHelper.requestAlarmPermissions(activity, () -> completed[0] = true);
    // A dialog should be shown because background location is needed on API 29
    // The dialog prevents immediate completion
    assertFalse("onComplete should not be called before dialog dismissed on API 29", completed[0]);
  }

  @Test
  @Config(sdk = 33)
  public void requestAlarmPermissions_api33_showsDialog() {
    ShadowAlarmManager.setCanScheduleExactAlarms(true);
    FragmentActivity activity =
        Robolectric.buildActivity(FragmentActivity.class).create().start().resume().get();
    boolean[] completed = {false};
    PermissionHelper.requestAlarmPermissions(activity, () -> completed[0] = true);
    assertFalse("onComplete should not be called before dialogs dismissed on API 33", completed[0]);
  }

  // ---- Chain reaches deeper methods when earlier permissions are granted ----

  @Test
  @Config(sdk = 33)
  public void requestAlarmPermissions_api33_bgLocationGranted_blocksOnNotification() {
    // Grant background location so the chain skips requestBackgroundLocation
    // and reaches requestNotificationPermission (which needs permission on API 33)
    ShadowApplication shadowApp =
        Shadows.shadowOf((Application) ApplicationProvider.getApplicationContext());
    shadowApp.grantPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    ShadowAlarmManager.setCanScheduleExactAlarms(true);

    FragmentActivity activity =
        Robolectric.buildActivity(FragmentActivity.class).create().start().resume().get();
    boolean[] completed = {false};
    PermissionHelper.requestAlarmPermissions(activity, () -> completed[0] = true);
    // Chain should block on notification permission dialog (not complete immediately)
    assertFalse(
        "Should block on notification permission dialog, not complete immediately", completed[0]);
  }

  @Test
  @Config(sdk = 31)
  public void
      requestAlarmPermissions_api31_bgLocationGranted_exactAlarmRevoked_blocksOnExactAlarm() {
    // Grant background location, revoke exact alarm
    ShadowApplication shadowApp =
        Shadows.shadowOf((Application) ApplicationProvider.getApplicationContext());
    shadowApp.grantPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    ShadowAlarmManager.setCanScheduleExactAlarms(false);

    FragmentActivity activity =
        Robolectric.buildActivity(FragmentActivity.class).create().start().resume().get();
    boolean[] completed = {false};
    PermissionHelper.requestAlarmPermissions(activity, () -> completed[0] = true);
    // On API 31, notification permission not needed. Chain should block on exact alarm dialog.
    assertFalse("Should block on exact alarm dialog", completed[0]);
  }

  @Test
  @Config(sdk = 33)
  public void requestAlarmPermissions_api33_allExceptNotification_blocksOnNotification() {
    // Grant background location + exact alarm granted — only notification left
    ShadowApplication shadowApp =
        Shadows.shadowOf((Application) ApplicationProvider.getApplicationContext());
    shadowApp.grantPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    ShadowAlarmManager.setCanScheduleExactAlarms(true);

    FragmentActivity activity =
        Robolectric.buildActivity(FragmentActivity.class).create().start().resume().get();
    boolean[] completed = {false};
    PermissionHelper.requestAlarmPermissions(activity, () -> completed[0] = true);
    assertFalse("Should block on notification permission", completed[0]);
  }

  // ---- hasAllAlarmPermissions: specific missing combos ----

  @Test
  @Config(sdk = 31)
  public void hasAllAlarmPermissions_api31_bgLocationGranted_exactAlarmRevoked_returnsFalse() {
    ShadowApplication shadowApp =
        Shadows.shadowOf((Application) ApplicationProvider.getApplicationContext());
    shadowApp.grantPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    ShadowAlarmManager.setCanScheduleExactAlarms(false);
    assertFalse(PermissionHelper.hasAllAlarmPermissions(context));
  }

  @Test
  @Config(sdk = 31)
  public void hasAllAlarmPermissions_api31_allGranted_returnsTrue() {
    ShadowApplication shadowApp =
        Shadows.shadowOf((Application) ApplicationProvider.getApplicationContext());
    shadowApp.grantPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    ShadowAlarmManager.setCanScheduleExactAlarms(true);
    assertTrue(PermissionHelper.hasAllAlarmPermissions(context));
  }

  // ---- needsBackgroundLocation: permission granted ----

  @Test
  @Config(sdk = 33)
  public void needsBackgroundLocation_api33_returnsTrue() {
    assertTrue(PermissionHelper.needsBackgroundLocation(context));
  }

  @Test
  @Config(sdk = 33)
  public void needsBackgroundLocation_api33_granted_returnsFalse() {
    ShadowApplication shadowApp =
        Shadows.shadowOf((Application) ApplicationProvider.getApplicationContext());
    shadowApp.grantPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    assertFalse(PermissionHelper.needsBackgroundLocation(context));
  }
}
