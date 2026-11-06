# GeoReminder

A simple, offline-first location reminder app for Android that doesn't use Google Play Services or track you. Built with Jetpack Compose and OpenStreetMap ([osmdroid](https://github.com/osmdroid/osmdroid)).

I built this because most geofence reminder apps either depend on Google Play Services, require an account, upload your coordinates to a server, or just show a quiet notification that you miss while commuting. GeoReminder works completely offline, can trigger a full-screen alarm like a wake-up clock, and lets you download map tiles so you don't need mobile data.

## Features

- **Offline maps**: Uses OpenStreetMap tiles. You can select an area on the map and pre-download tiles up to zoom level 16 for offline trips.
- **Notification or loud alarm**: Choose between a normal heads-up notification or a full-screen alarm (uses the alarm audio stream, wakes the screen, and bypasses silent mode).
- **Custom trigger radius**: 50m to 5km slider with real-time visual circle on the map.
- **Jitter protection**: Includes a hysteresis buffer (~25% + 30m) so walking along the edge of a zone won't re-trigger the alarm repeatedly.
- **Battery-friendly foreground service**: Runs a persistent foreground service with low-frequency GPS polling while moving and stops waking when reminders are disarmed.
- **No accounts, no telemetry**: Reminders and downloaded tiles are stored locally in app storage.

## Download

Get the latest signed APK from the [Releases](https://github.com/Prashithshetty/GeoReminder/releases/latest) page:

- `GeoReminder-v1.0.0-Release.apk` (signed with debug fallback, ready to sideload)
- Or install via ADB:
  ```bash
  adb install -r app/build/outputs/apk/release/app-release.apk
  ```

## Permissions & Battery

Android is strict about background location. For GeoReminder to reliably ring when you arrive somewhere:

1. **Location ("Allow all the time")**: Needed so the foreground service can check your distance to saved pins when the screen is off or another app is open.
2. **Alarms & Reminders**: Needed on Android 12+ (`SCHEDULE_EXACT_ALARM`) so snooze timers and alarm triggers fire at the exact second.
3. **Battery Optimization**: Open the in-app "Engine" tab and disable battery optimization for GeoReminder. Aggressive OEM task killers (MIUI, OneUI, ColorOS) will otherwise kill the service after 15 minutes of screen-off time.

## Build

Requires JDK 17 and Android SDK 35+.

```bash
git clone https://github.com/Prashithshetty/GeoReminder.git
cd GeoReminder

# Run tests
./gradlew testDebugUnitTest

# Build APKs
./gradlew assembleRelease
```

The APK will be generated at `app/build/outputs/apk/release/app-release.apk`.

To sign with your own keystore:
```bash
export KEYSTORE_FILE="/path/to/keystore.jks"
export KEYSTORE_PASSWORD="password"
export KEY_ALIAS="alias"
export KEY_PASSWORD="password"
./gradlew assembleRelease
```
If no keystore variables are provided, the release build automatically falls back to debug signing so the APK can be sideloaded immediately.

## Tech Stack

- **UI**: Jetpack Compose, Material 3
- **Map engine**: osmdroid 6.1.18
- **Background**: Android Foreground Service + AlarmManager + WakeLock
- **Architecture**: Single-activity, unidirectional state flow, coroutines

## License

[Apache 2.0](LICENSE). Map data © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors.
