#!/usr/bin/env bash
#
# Local test harness matching CI's instrumentation test flow.
# Usage:
#   ./scripts/run-instrumentation-tests.sh [API_LEVEL...]
#   ./scripts/run-instrumentation-tests.sh          # runs all: 26 31 33 34
#   ./scripts/run-instrumentation-tests.sh 33       # runs only API 33
#   ./scripts/run-instrumentation-tests.sh 26 33    # runs API 26 and 33
#
set -euo pipefail

ANDROID_SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$ANDROID_SDK/platform-tools/adb"
EMULATOR="$ANDROID_SDK/emulator/emulator"
AVDMANAGER="$ANDROID_SDK/cmdline-tools/latest/bin/avdmanager"
SDKMANAGER="$ANDROID_SDK/cmdline-tools/latest/bin/sdkmanager"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
APP_PACKAGE="maurizi.geoclock.debug"
TEST_PACKAGE="maurizi.geoclock.debug.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
TEST_JAVA_PACKAGE="maurizi.geoclock"

# Detect architecture: arm64 on Apple Silicon, x86_64 on Intel/Linux
if [ "$(uname -m)" = "arm64" ] || [ "$(uname -m)" = "aarch64" ]; then
    ARCH="arm64-v8a"
else
    ARCH="x86_64"
fi

API_LEVELS=("${@:-26 31 33 34}")
RESULTS=()

avd_name() { echo "test_api_$1"; }

ensure_system_image() {
    local api=$1
    local img_dir="$ANDROID_SDK/system-images/android-$api/google_apis/$ARCH"
    if [ ! -d "$img_dir" ] || [ ! -f "$img_dir/system.img" ]; then
        echo ">>> Installing system image for API $api ($ARCH)..."
        yes | "$SDKMANAGER" "system-images;android-$api;google_apis;$ARCH"
    fi
}

ensure_avd() {
    local api=$1
    local name
    name=$(avd_name "$api")
    if ! "$EMULATOR" -list-avds 2>/dev/null | grep -qx "$name"; then
        echo ">>> Creating AVD $name..."
        echo "no" | "$AVDMANAGER" create avd \
            -n "$name" \
            -k "system-images;android-$api;google_apis;$ARCH" \
            -d "pixel" \
            --force
    fi
}

boot_emulator() {
    local api=$1
    local name
    name=$(avd_name "$api")

    # Kill all running emulators
    local serials
    serials=$("$ADB" devices 2>/dev/null | grep "emulator-" | awk '{print $1}' || true)
    if [ -n "$serials" ]; then
        echo ">>> Shutting down running emulators..."
        for s in $serials; do
            "$ADB" -s "$s" emu kill 2>/dev/null || true
        done
        sleep 5
    fi

    echo ">>> Booting emulator $name..."
    "$EMULATOR" -avd "$name" -no-audio -no-window -gpu swiftshader_indirect &
    EMULATOR_PID=$!

    # Wait for boot
    echo ">>> Waiting for emulator to boot..."
    "$ADB" wait-for-device
    local deadline=$((SECONDS + 180))
    while [ $SECONDS -lt $deadline ]; do
        local boot_complete
        boot_complete=$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
        if [ "$boot_complete" = "1" ]; then
            echo ">>> Emulator booted (API $("$ADB" shell getprop ro.build.version.sdk | tr -d '\r'))"
            return 0
        fi
        sleep 3
    done
    echo "ERROR: Emulator did not boot within 180s"
    return 1
}

run_tests_on_api() {
    local api=$1
    echo ""
    echo "========================================"
    echo "  Running instrumentation tests: API $api"
    echo "========================================"

    ensure_system_image "$api"
    ensure_avd "$api"
    boot_emulator "$api"

    # Enable location
    "$ADB" shell settings put global location_mode 3 2>/dev/null || true
    "$ADB" shell settings put secure location_mode 3 2>/dev/null || true

    # Install APKs (skip build — already done)
    echo ">>> Installing APKs..."
    (cd "$PROJECT_DIR" && ./gradlew installDebug installDebugAndroidTest --stacktrace)

    # Grant permissions
    "$ADB" shell appops set "$APP_PACKAGE" android:mock_location allow
    "$ADB" shell appops set "$APP_PACKAGE" SCHEDULE_EXACT_ALARM allow 2>/dev/null || true
    "$ADB" shell pm grant "$APP_PACKAGE" android.permission.ACCESS_FINE_LOCATION 2>/dev/null || true
    "$ADB" shell pm grant "$APP_PACKAGE" android.permission.ACCESS_BACKGROUND_LOCATION 2>/dev/null || true

    # Run tests
    echo ">>> Running tests..."
    local output_file="$PROJECT_DIR/test-output-api${api}.txt"
    "$ADB" shell am instrument -w \
        -e package "$TEST_JAVA_PACKAGE" \
        "$TEST_PACKAGE/$RUNNER" 2>&1 | tee "$output_file"

    # Check results
    if grep -q "^OK " "$output_file"; then
        echo ">>> API $api: PASSED"
        RESULTS+=("API $api: PASSED")
    else
        echo ">>> API $api: FAILED"
        RESULTS+=("API $api: FAILED")
    fi
}

# Build once first
echo ">>> Building APKs (arch: $ARCH)..."
(cd "$PROJECT_DIR" && ./gradlew assembleDebug assembleDebugAndroidTest --stacktrace)

for api in "${API_LEVELS[@]}"; do
    run_tests_on_api "$api" || RESULTS+=("API $api: ERROR")
done

# Kill emulator
"$ADB" emu kill 2>/dev/null || true

echo ""
echo "========================================"
echo "  Results Summary"
echo "========================================"
for r in "${RESULTS[@]}"; do
    echo "  $r"
done

# Exit with failure if any test failed
for r in "${RESULTS[@]}"; do
    if [[ "$r" == *FAILED* ]] || [[ "$r" == *ERROR* ]]; then
        exit 1
    fi
done
