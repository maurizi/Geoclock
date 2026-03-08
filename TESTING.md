# Testing Guide

## Automated Tests

### Unit tests (Robolectric)

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew test
```

### Coverage report (JaCoCo)

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew jacocoTestReport
open Geoclock/build/reports/jacoco/jacocoTestReport/html/index.html
```

### Instrumentation tests (requires emulator or device)

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew connectedAndroidTest
open Geoclock/build/reports/androidTests/connected/index.html
```

Requires a `google_apis` emulator image for geofencing tests (Play Services).

---

## Manual-Only Test Checklist

The following behaviors cannot be reliably automated in CI:

| Behavior | Reason |
|---|---|
| Vibration motor fires during alarm | No emulator hardware feedback channel |
| Exact visual lock-screen appearance | Device-specific keyguard rendering varies |
| Alarm delivery during Doze mode | Doze is hard to reliably trigger programmatically in CI |
| Alarm audio volume respects system settings | AudioManager reports differ per device/emulator config |

### Manual test steps

1. **Vibration**: Set an alarm, enter the geofence. Verify the device vibrates when the alarm rings.
2. **Lock screen**: With screen locked, verify `AlarmRingingActivity` appears on top of the keyguard.
3. **Doze mode**: Enable Doze via `adb shell dumpsys deviceidle force-idle`. Set an alarm. Verify it fires.
4. **Audio volume**: Set device volume to 0, trigger alarm. Verify alarm overrides to audible level (if `streamType` is `STREAM_ALARM`).

---

## Geofence Tests (Large Tests)

`GeofenceIntegrationTest` tests are annotated `@LargeTest` and may take up to 30 seconds per test due to Play Services geofence evaluation latency.

To exclude them from a test run:
```bash
./gradlew connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.notAnnotation=androidx.test.filters.LargeTest
```
