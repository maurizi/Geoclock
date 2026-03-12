# Privacy Policy

**Geoclock — Location-Based Alarm Clock**

*Last updated: March 10, 2026*

## Overview

Geoclock is a location-based alarm clock for Android. Your privacy is important — the app is designed to work entirely on your device with no data collection or tracking.

## Data Collection

**Geoclock does not collect, store, or transmit any personal data to external servers.** The app has no analytics, crash reporting, or tracking SDKs.

## Permissions

Geoclock requests the following permissions, all used exclusively for core functionality:

| Permission | Purpose |
|---|---|
| **Location (fine, coarse, background)** | Detect when you enter or leave a geographic area to trigger alarms. Background location is needed so alarms work when the app is not in the foreground. |
| **Exact alarms** | Schedule alarms at precise times. |
| **Notifications** | Display alarm notifications when triggered. |
| **Full-screen intent** | Show the alarm screen over the lock screen. |
| **Vibrate** | Vibrate the device when an alarm fires. |
| **Wake lock / Disable keyguard** | Wake the screen and dismiss the lock screen for active alarms. |
| **Internet / Network state** | Load Google Maps tiles and reverse-geocode addresses. No user data is sent. |
| **Receive boot completed** | Re-register alarms and geofences after the device restarts. |

## Data Storage

All alarm data (times, locations, settings) is stored locally on your device using Android SharedPreferences. Nothing is uploaded or synced to any server.

## Third-Party Services

Geoclock uses **Google Play Services** for:
- **Google Maps SDK** — displaying maps and selecting locations
- **Fused Location Provider** — obtaining device location
- **Geofencing API** — monitoring geographic boundaries

These services are provided by Google and governed by [Google's Privacy Policy](https://policies.google.com/privacy). Geoclock does not send any additional data to Google beyond what these standard Android APIs require to function.

## Children's Privacy

Geoclock does not knowingly collect any information from children under 13.

## Changes

If this policy changes, the updated version will be posted in this repository with a new "Last updated" date.

## Contact

For questions about this privacy policy, open an issue on the [Geoclock GitHub repository](https://github.com/maurizi/Geoclock).
