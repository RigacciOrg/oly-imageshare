# Oly ImageShare

Download photos from Olympus / OM System cameras over their built-in Wi-Fi,
straight to your Android phone.

## Why this app

Oly ImageShare is **free software** (GPL-3.0-or-later), and that choice shapes
everything about it:

- **No advertising.** There are no ads, no "premium" upsell, no banners.
- **No user tracking.** The app contains no analytics, no telemetry, no crash
  reporting services, no third-party SDKs that phone home. It talks to exactly one
  thing: your camera.
- **Maximum privacy.** Your photos go from the camera to your phone and nowhere
  else. Nothing is uploaded to any cloud, and the app asks for no account.
- **Lightweight.** It is written against the plain Android platform with a handful
  of small, well-known AndroidX libraries and **no native code of its own**, so the
  package is small, starts quickly, and is automatically compatible with 16 KB
  memory page devices (a current Google Play requirement).
- **Open and inspectable.** The whole source is available; you can read exactly
  what it does and build it yourself.

The app connects to the camera's Wi-Fi hotspot and uses its HTTP API to list
images, show thumbnails, download full-resolution files, and (on cameras that
support it) delete them.

## Features

From the main menu:

- **Camera gallery** — an adaptive thumbnail grid of every photo and movie on the
  card, newest first. Flags mark protected images (key), share-reserved images,
  videos and RAW (.ORF) files. Select images (by page, whole gallery, protected,
  or shared) and download or delete them, with progress and the ability to abort.
- **Check camera connection** — verifies the camera is reachable and shows its
  model and serial number, or a helpful message if the connection fails.
- **Settings** — download destination (shared storage or a folder you pick),
  thumbnail / button / label sizes, thumbnail-cache retention, and the camera's
  IP address and DCIM folder.
- **About** and **Quit** (which also prunes the old thumbnail cache).

## Requirements

- An Olympus / OM System camera with the Wi-Fi "Connection to Smartphone" feature.
- Android 7.0 (API 24) or newer.

---

## Technical notes

### Architecture

- **100 % Kotlin**, one `Activity` per screen (`MainActivity`,
  `CameraGalleryActivity`, `ConnectionActivity`, `SettingsActivity`,
  `AboutActivity`). No cross-screen framework, no single-Activity navigation graph
  — each function is self-contained.
- The camera protocol lives in `OlympusCamera.kt` (plain `HttpURLConnection`,
  `xml`/CSV parsing), kept separate from the UI so it is easy to read and reuse.
- Thumbnails are cached in the app's **private internal storage**
  (`filesDir/thumbs`), one JPEG per image named by a SHA-1 of the camera's listing
  fields; stale entries are pruned after the configurable retention period.
- Settings are stored in the app's private `SharedPreferences` via the AndroidX
  Preference library, read everywhere through a single `Prefs` helper.

### Dependencies

Deliberately minimal. All are pure-JVM AndroidX libraries with **no native `.so`
files**, which keeps the app small and 16 KB-page-size compliant:

```kotlin
dependencies {
    implementation("androidx.preference:preference-ktx:1.2.1") // Settings screen
    implementation("androidx.recyclerview:recyclerview:1.3.2")  // thumbnail grid
    implementation("androidx.documentfile:documentfile:1.0.1")  // SAF downloads
}
```

(`preference-ktx` transitively brings in `appcompat` and `fragment`.)

### Build configuration

- `compileSdk = 36`, `minSdk = 24`, `targetSdk = 36`.
- No NDK and no `android.archs` filtering are needed, because the app ships no
  native libraries — it runs on every 64-bit (and 32-bit) device as-is.

---

## Toolchain setup (Linux, no Android Studio)

You only need a JDK and the Android command-line SDK; Gradle installs itself via
the project's wrapper.

```bash
# JDK 17 or newer (Debian/Ubuntu example)
sudo apt install openjdk-17-jdk

# Android command-line tools: download "Command line tools only" from
# https://developer.android.com/studio#command-line-tools-only then:
mkdir -p ~/Android/sdk/cmdline-tools
unzip commandlinetools-linux-*.zip -d ~/Android/sdk/cmdline-tools
mv ~/Android/sdk/cmdline-tools/cmdline-tools ~/Android/sdk/cmdline-tools/latest

# Point the environment at the SDK (add these to ~/.bashrc)
export ANDROID_HOME=$HOME/Android/sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

# Install the needed packages and accept the licenses
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
sdkmanager --licenses
```

Create `local.properties` in the project root pointing at the SDK (or export
`ANDROID_HOME` as above):

```bash
echo "sdk.dir=$HOME/Android/sdk" > local.properties
```

Any editor works (VSCodium, neovim, …); the optional "Kotlin by JetBrains" LSP
extension gives completion and go-to-definition without an IDE.

## Build & deploy

```bash
# Build the debug APK
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk

# Or build + install on a connected phone in one step
./gradlew installDebug

# Launch it and watch its log output
adb shell am start -n org.rigacci.olyshare/.MainActivity
adb logcat --pid=$(adb shell pidof org.rigacci.olyshare)
```

Enable **Developer options -> USB debugging** on the phone first. Note that
`adb` over USB keeps working while the phone is joined to the camera's
(internet-less) Wi-Fi, so you can deploy and read logs during real testing.

## Versioning

The current version lives in `app/version.properties` (`major`, `minor`, `code`),
which the build reads as-is. `versionName` is `major.minor` (shown to users);
`versionCode` is the integer `code` that Google Play uses and that must always
increase for each upload. Advance it on purpose with one of three tasks, each run
as its **own** command, before building:

```bash
./gradlew bumpBuild     # code +1 only          (versionName unchanged)
./gradlew bumpVersion   # minor +1 and code +1  (e.g. 0.1 -> 0.2)
./gradlew bumpMajor     # major +1, minor 0, code +1  (e.g. 0.7 -> 1.0)
```

Do not chain a bump with a build in one invocation (Gradle reads the version
during configuration, before the bump task runs). You can also edit
`version.properties` by hand at any time.

## Release build

Create a signing keystore once, wire a `signingConfigs` block into
`app/build.gradle.kts`, then produce a signed App Bundle for Google Play:

```bash
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 \
        -validity 10000 -alias oly
./gradlew bundleRelease   # -> app/build/outputs/bundle/release/app-release.aab
```

Because the app has no native libraries, the bundle is 16 KB-page-size compliant
with nothing extra to do.

## License

Copyright (C) 2026 Niccolo Rigacci <niccolo@rigacci.org>

This program is free software: you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later version
(**GPL-3.0-or-later**). See the `LICENSE` file for details.

Project page: https://github.com/RigacciOrg/oly-imageshare
