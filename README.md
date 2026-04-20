# CubicAuto 🎵

> A Spotify-powered Android Auto media app with a full Winamp/Cubic Player aesthetic.

---

## What This Is

CubicAuto is a complete Android app that:

- **Connects to Spotify** via the official App Remote SDK (no audio re-streaming — Spotify handles playback)
- **Registers as an Android Auto media app** so your car's head unit shows track info and media controls
- **Runs a full Cubic/Winamp-style Compose UI** on your phone: animated spectrum analyzer, VT323 LCD fonts, draggable knobs, glowing LED indicators, scrolling marquee, CRT phosphor color palette

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│                   Android Phone                     │
│                                                     │
│  ┌─────────────────┐    ┌────────────────────────┐  │
│  │  MainActivity   │    │  CubicMediaService     │  │
│  │  (Compose UI)   │◄──►│  (MediaBrowserService) │  │
│  │                 │    │                        │  │
│  │  CubicPlayer    │    │  MediaSession ──────── │──┼──► Android Auto
│  │  Screen         │    │  SessionToken          │  │     (car display)
│  └────────┬────────┘    └──────────┬─────────────┘  │
│           │                        │                 │
│           └────────────┬───────────┘                 │
│                        ▼                             │
│              ┌──────────────────┐                   │
│              │ SpotifyRepository│                   │
│              │ (App Remote SDK) │                   │
│              └────────┬─────────┘                   │
└───────────────────────┼─────────────────────────────┘
                        │  IPC / Binder
                        ▼
              ┌──────────────────┐
              │  Spotify App     │
              │  (audio output)  │
              └──────────────────┘
```

**Key design decision:** CubicAuto does NOT stream audio itself. It remote-controls the Spotify app via the App Remote SDK. This means:
- No Spotify Premium API audio streaming quota issues
- Spotify handles DRM, caching, crossfade, normalization
- CubicAuto just sends commands and reads state

---

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Android Studio | Ladybug (2024.2) or newer | |
| Android SDK | API 35 | compile target |
| Min device | API 26 (Android 8.0) | Android Auto minimum |
| Spotify app | Latest | Must be installed on the same phone |
| Spotify account | Free or Premium | App Remote works with both |

---

## Setup: Step by Step

### 1. Clone / open project

```bash
git clone https://github.com/you/CubicAuto.git
cd CubicAuto
```

Open in Android Studio. Let Gradle sync complete.

---

### 2. Get the Spotify App Remote SDK

The Spotify SDK is distributed as a binary `.aar` — it is **not** on Maven Central.

1. Go to [developer.spotify.com/documentation/android](https://developer.spotify.com/documentation/android)
2. Download **spotify-app-remote-release-0.8.0.aar** (or latest)
3. Place it at:
   ```
   app/libs/spotify-app-remote-release-0.8.0.aar
   ```
4. In `app/build.gradle.kts` the `fileTree` dependency already picks it up:
   ```kotlin
   implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))
   ```

---

### 3. Register your app on Spotify Developer Dashboard

1. Go to [developer.spotify.com/dashboard](https://developer.spotify.com/dashboard)
2. Click **Create app**
3. Fill in:
   - **App name:** CubicAuto
   - **Redirect URI:** `cubicauto://callback`
   - **APIs:** check *Android* and *Web API*
4. Copy your **Client ID**
5. Go to **Settings → Android Fingerprints** and add your debug SHA-1:
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore \
           -alias androiddebugkey -storepass android -keypass android \
           | grep SHA1
   ```
6. Open `app/build.gradle.kts` and replace:
   ```kotlin
   buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"YOUR_SPOTIFY_CLIENT_ID\"")
   ```
   with your actual Client ID.

---

### 4. Uncomment the Spotify SDK calls

With the SDK `.aar` in place, open `SpotifyRepository.kt` and uncomment:

- The `import` statements at the top
- The `SpotifyAppRemote.connect()` block inside `connect()`
- The `subscribeToPlaybackState()` call and its body
- The individual `playerApi.*` calls in each control function

Remove the stub error line:
```kotlin
// DELETE THIS when SDK is present:
_connectionState.value = SpotifyConnectionState.Error("Spotify SDK .aar not yet added…")
```

---

### 5. Enable Android Auto Developer Mode (for testing without a car)

**On your phone:**
1. Settings → Apps → Android Auto → triple-dot menu → Developer settings
2. Enable **Unknown sources**

**To test on a phone screen (no car required):**
1. Connect phone via USB
2. In Android Auto Developer settings, enable **Start head unit server**
3. Run in terminal:
   ```bash
   adb forward tcp:5277 tcp:5277
   ```
4. Download **Desktop Head Unit (DHU)** from the Android SDK Manager:
   ```
   SDK Manager → SDK Tools → Android Auto Desktop Head Unit Emulator
   ```
5. Launch DHU:
   ```bash
   $ANDROID_HOME/extras/google/auto/desktop-head-unit
   ```

Your CubicAuto app will appear in the DHU media section.

---

### 6. Build and run

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or just hit ▶ Run in Android Studio.

---

## Project Structure

```
CubicAuto/
├── app/
│   ├── libs/                          ← Drop Spotify .aar here
│   └── src/main/
│       ├── AndroidManifest.xml        ← AA declarations, service, permissions
│       ├── java/com/cubicauto/
│       │   ├── CubicAutoApplication.kt  ← Notification channels
│       │   ├── MainActivity.kt          ← Compose host, MediaBrowser client
│       │   ├── model/
│       │   │   └── Models.kt            ← Track, PlaybackState, enums
│       │   ├── service/
│       │   │   ├── CubicMediaService.kt ← MediaBrowserService (AA entry point)
│       │   │   └── NotificationHelper.kt← Media notification builder
│       │   ├── spotify/
│       │   │   └── SpotifyRepository.kt ← SDK wrapper, StateFlow state
│       │   ├── ui/
│       │   │   ├── PlayerViewModel.kt   ← State holder, coroutine animations
│       │   │   └── CubicPlayerScreen.kt ← Full Compose UI (Cubic aesthetic)
│       │   └── util/
│       │       └── BootReceiver.kt      ← Auto-start on device reboot
│       └── res/
│           ├── xml/automotive_app_desc.xml  ← REQUIRED for Android Auto
│           ├── drawable/ic_music_note.xml
│           └── values/{strings,colors,themes}.xml
├── gradle/
│   ├── libs.versions.toml             ← Version catalog
│   └── wrapper/gradle-wrapper.properties
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## How Android Auto Integration Works

### The MediaBrowserService contract

Android Auto looks for apps that:

1. Declare `<uses name="media" />` in `res/xml/automotive_app_desc.xml`
2. Reference that file via `com.google.android.gms.car.application` metadata in the manifest
3. Expose a `MediaBrowserService` intent filter

When you plug into Android Auto (or connect via DHU), it:
1. Binds to `CubicMediaService` via `onGetRoot()`
2. Calls `onLoadChildren()` to populate the browse tree
3. Subscribes to `MediaSession` for playback state and metadata
4. Renders its **own** car-safe UI using that data — you cannot change this UI

### What shows on the car dash

| MediaSession field | Displayed as |
|--------------------|--------------|
| `METADATA_KEY_TITLE` | Track name |
| `METADATA_KEY_ARTIST` | Artist name |
| `METADATA_KEY_ALBUM_ART` | Album art thumbnail |
| `METADATA_KEY_DURATION` | Progress bar total |
| `PlaybackStateCompat.STATE_PLAYING` | Play/pause button state |
| `ACTION_SKIP_TO_NEXT/PREVIOUS` | Skip buttons |

### What runs on the phone

Everything in `CubicPlayerScreen.kt` — the full retro UI. This is where the Cubic aesthetic lives.

---

## Customization

### Change the color palette
All colors are defined in `CubicPlayerScreen.kt` in the `Cubic` object:
```kotlin
private object Cubic {
    val accent  = Color(0xFF00E5FF)  // change this to any color
    val accent2 = Color(0xFF00FF9D)
    // ...
}
```

### Change the demo playlist
Edit `DEMO_QUEUE` in `CubicPlayerScreen.kt`. When the Spotify SDK is active, this is replaced by the actual Spotify queue from `PlayerViewModel`.

### Add Spotify Web API browse
For a real browse tree (Recently Played, Your Library, etc.), add Retrofit calls to `SpotifyRepository` using the [Spotify Web API](https://developer.spotify.com/documentation/web-api). The OAuth token from the App Remote SDK handshake can be reused.

### Voice search
`MediaSessionCallback.onPlayFromSearch()` in `CubicMediaService` receives Google Assistant voice queries. Map these to Spotify URI searches via the Web API.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| App not appearing in Android Auto | Missing `automotive_app_desc.xml` or manifest meta-data | Check manifest, verify XML path |
| Spotify connection error | SHA-1 not registered in dashboard | Add debug fingerprint to dashboard |
| "SDK .aar not found" build error | `.aar` not in `app/libs/` | Download from Spotify developer portal |
| DHU shows app but no controls | `MediaSession` not active | Ensure `isActive = true` in service |
| Notification not showing | Missing `FOREGROUND_SERVICE` permission | Already in manifest; check Android 13+ POST_NOTIFICATIONS |
| App crashes on Android 14 | `foregroundServiceType` mismatch | Verify `mediaPlayback` in manifest `<service>` |

---

## Legal Notes

- This app uses the **Spotify App Remote SDK** which is subject to [Spotify's Developer Terms of Service](https://developer.spotify.com/terms)
- Do not redistribute the Spotify `.aar` binary
- Android Auto integration is subject to [Google's Android Auto developer policies](https://developer.android.com/training/cars/media)
- For public Play Store release, submit for [Android Auto quality review](https://developer.android.com/training/cars/media/quality)

---

## License

MIT — do whatever you want with the CubicAuto code. The Spotify SDK and Android Auto frameworks have their own licenses.
