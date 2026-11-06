# <img src="prototype/assets/icon.svg" width="40" height="40" align="center" alt="GeoReminder Icon" /> GeoReminder

[![Android CI & Release](https://github.com/prashith/GeoReminder/actions/workflows/android.yml/badge.svg)](https://github.com/prashith/GeoReminder/actions)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Platform: Android 8.0+](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-green.svg)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![Maps: osmdroid](https://img.shields.io/badge/Maps-OpenStreetMap%20osmdroid-orange.svg)](https://github.com/osmdroid/osmdroid)

An open-source, privacy-first geofenced reminder app for Android built with **Jetpack Compose** and **OpenStreetMap** (`osmdroid`). Operates entirely offline without proprietary Google Play Services, location tracking, or telemetry.

---

## 🌟 Key Features

1. **100% Open-Source Maps & Offline Tile Storage**
   - Renders OpenStreetMap vector/raster tiles using `osmdroid` — zero Google Maps API keys or account dependencies.
   - Built-in **Offline Tile Downloader**: download map bounding boxes for offline use with live tile count, download progress, and storage quota controls.
   - Automatic tile cache management and cache clearing.

2. **Precision Geofencing with Hysteresis**
   - Interactive full-screen map with bottom sheet controls for quick reminders.
   - Configurable trigger radius (50m to 5,000m) with live visual circle overlays.
   - **Smart Hysteresis Buffer** (`radius * 1.25 + 30m`) prevents repetitive alarm triggers caused by GPS jitter when standing near a geofence boundary.

3. **Choice of Trigger Alert: Notification vs Full Alarm**
   - **Notification Alert**: High-priority heads-up notification with custom sound, vibration, and one-tap completion.
   - **Full-Screen Alarm**: Rings out loud on the alarm audio stream, bypasses Do Not Disturb/silent switches, wakes the device screen even when locked via `FLAG_KEEP_SCREEN_ON` and `setShowWhenLocked(true)`, featuring snooze and dismiss actions.

4. **Modern Android Architecture**
   - **UI**: Modern Jetpack Compose Material 3 with adaptive layouts, custom icon, and Dark/Light mode theme support.
   - **Background Location**: Low-power Foreground Location Service with sticky foreground notification (`FOREGROUND_SERVICE_TYPE_LOCATION`).
   - **Storage**: Sequential coroutine-safe persistent storage with JSON serialization.

---

## 📱 Permissions Breakdown

GeoReminder only requests permissions strictly necessary for offline geofencing:

| Permission | Reason |
|---|---|
| `ACCESS_FINE_LOCATION` & `ACCESS_COARSE_LOCATION` | Required to calculate real-time distance to active geofence targets. |
| `ACCESS_BACKGROUND_LOCATION` | Allows geofence checking when the screen is turned off or when navigating in other apps. |
| `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_LOCATION` | Keeps the geofence engine alive reliably on Android 8.0+ and Android 14+. |
| `POST_NOTIFICATIONS` | Displays heads-up alerts and persistent service status on Android 13+. |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | Ensures alarms fire at the exact millisecond when geofences trigger or snooze timers elapse. |
| `WAKE_LOCK` | Wakes the CPU briefly to play alarm audio and render the full-screen alarm screen when locked. |
| `VIBRATE` | Provides haptic feedback during geofence alerts. |
| `INTERNET` | Only used to fetch OpenStreetMap tiles when downloading offline regions. |

---

## 🚀 Building & Installation

### Prerequisites
- JDK 17 (Java 17)
- Android SDK Platform 35+ / Build Tools 35.0.0+

### Build from Source
```bash
# Clone the repository
git clone https://github.com/prashith/GeoReminder.git
cd GeoReminder

# Run Unit Tests
./gradlew testDebugUnitTest

# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew assembleRelease
```

Generated APKs will be located at:
- **Debug**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release**: `app/build/outputs/apk/release/app-release.apk`

### Sideloading onto Device

#### Via ADB:
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

#### Via KDE Connect / Wireless Transfer:
1. Transfer `app-release.apk` or `app-debug.apk` to your phone.
2. Tap the received APK notification and approve "Install from Unknown Sources".

### Release Signing
To sign with your custom release keystore, provide the following environment variables:
```bash
export KEYSTORE_FILE="/path/to/keystore.jks"
export KEYSTORE_PASSWORD="your-keystore-password"
export KEY_ALIAS="your-key-alias"
export KEY_PASSWORD="your-key-password"

./gradlew assembleRelease
```
*Note: If no keystore is provided, `./gradlew assembleRelease` automatically signs with the debug key so the APK can be immediately sideloaded without manual signing.*

---

## 🧪 Testing

Run automated unit tests:
```bash
./gradlew testDebugUnitTest
```

Run Android Lint checks:
```bash
./gradlew lintDebug
```

---

## 📄 License & Attribution

- Released under the [Apache License 2.0](LICENSE).
- Map data & tiles: © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors.
- Map rendering engine: [osmdroid](https://github.com/osmdroid/osmdroid).
