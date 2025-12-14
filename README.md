# extshare

Quick Settings tile to mirror the main screen to a foldable’s external/cover display. Built for quick toggling on devices like the Motorola Razr 50 Ultra.

## ✨ Features
- Quick Settings tile: tap to start/stop mirroring instantly.
- MediaProjection-based capture for full-screen mirroring.
- External display routing via `Presentation` + `VirtualDisplay`.
- In-app controls to request permission, start, and stop.
- Foreground service with low-priority notification.

## Getting Started

### Requirements
- Android device with external/cover display support (tested target: Razr 50 Ultra).
- Android SDK installed locally (`/home/r/android-sdk` configured in `local.properties`).
- Java 17+.

### Build the APK
```bash
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

### Install & Use
1) Install the APK on your device.  
2) Add the “Mirror screen” tile to Quick Settings.  
3) First tap asks for screen-capture permission (required once).  
4) Toggle the tile (or use the app buttons) to mirror to the external display.  
5) Toggle off to stop the foreground service.

### Notes
- If you see a warning about `compileSdk 35`, it is expected with AGP 8.5.0; you can suppress it via `android.suppressUnsupportedCompileSdk=35` in `gradle.properties` if desired.
- Mirroring stops automatically if the external display disappears.

## Project Layout
- `app/src/main/java/org/arnor/extshare` — activities, tile service, media-projection service, presentation.
- `app/src/main/res` — layouts, themes, icons, strings.
- `app/build.gradle.kts` — Android app config and dependencies.

## License
MIT (feel free to adapt for your device or ROM tools).
