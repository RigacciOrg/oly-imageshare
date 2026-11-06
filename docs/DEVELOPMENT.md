# Development notes

Developer-facing companion to the README. Covers the project layout, the main
classes, and how to regenerate the app icons.

## Project layout

```
app/
├── build.gradle.kts            module build: deps, SDK levels, version tasks
├── version.properties          major / minor / code (see README › Versioning)
└── src/main/
    ├── AndroidManifest.xml      activities, permissions, launcher icon
    ├── java/org/rigacci/olyshare/
    │   ├── MainActivity.kt          menu; Quit prunes the thumbnail cache
    │   ├── CameraGalleryActivity.kt grid, paging, selection, download, delete
    │   ├── ThumbAdapter.kt          RecyclerView adapter (one page of thumbs)
    │   ├── GalleryImage.kt          model: type, protected, timestamp, cacheKey
    │   ├── OlympusCamera.kt         the HTTP API client (see docs/CAMERA_API.md)
    │   ├── GalleryLoader.kt         orchestrates a full scan + thumbnail fetch
    │   ├── ThumbCache.kt            private disk cache + in-memory LruCache
    │   ├── DownloadTarget.kt        MediaStore / SAF / legacy download backends
    │   ├── ConnectionActivity.kt    get_caminfo check + result rendering
    │   ├── SettingsActivity.kt      hosts the settings fragment
    │   ├── SettingsFragment.kt      PreferenceFragmentCompat + SAF folder picker
    │   ├── Prefs.kt                 typed access to all settings + defaults
    │   └── AboutActivity.kt         static info; version read from PackageInfo
    └── res/
        ├── layout/        activity + item + dialog layouts
        ├── values/        strings_*, dimens, styles, themes, arrays, ic_launcher_background
        ├── xml/           preferences, network_security_config
        ├── drawable/      ic_* UI icons (vector) + overlay flags
        └── mipmap-*/      launcher icons (adaptive + legacy + monochrome)
```

## Architecture

- **One `Activity` per screen.** No navigation framework; screens are launched
  with explicit `Intent`s from `MainActivity`.
- **Networking is isolated** in `OlympusCamera`; everything blocks and runs on a
  background `Executor`, with results posted back to the UI thread.
- **`GalleryLoader`** performs capability discovery, the recursive directory walk,
  share-list queries, and parallel thumbnail caching, reporting progress phases.
- **`DownloadTarget`** abstracts the destination: `MediaStoreTarget` (Android 10+,
  `DCIM/OlyShare`), `SafTarget` (a picked folder tree), and `LegacyDcimTarget`
  (API < 29 fallback). Each supports an existence check so already-present files
  are skipped.
- **Rotation** is handled in-place (`android:configChanges`) so the gallery is not
  reloaded from the camera; the current page is recomputed to keep the same images
  visible.

## Dependencies

See the README for the list. All are pure-JVM AndroidX libraries (no native
`.so`), which is what keeps the app small and automatically 16 KB-page-size
compliant. There is no NDK dependency and no ABI filtering.

## Versioning

`app/version.properties` is the single source of truth; the build reads it. Bump
with `./gradlew bumpBuild | bumpVersion | bumpMajor` (details in the README).

## Regenerating the icons

### UI icons (buttons, overlay flags)

Source glyphs come from Google's Material Symbols (`@material-design-icons/svg`,
Apache-2.0) converted to Android vector drawables:

```bash
npm install -g svg2vectordrawable
s2v -i glyph.svg -o app/src/main/res/drawable/ic_glyph.xml
```

- Action icons (`ic_arrow_back`, `ic_delete`, `ic_check_box`, …): a single path,
  dark fill, recolored at use via `android:tint`.
- Overlay flags (`ic_ov_*`): drawn twice — a thick black outline underneath and
  the colored glyph on top — so they stay visible over any thumbnail (the "halo").
  Yellow = selection tick (top-right); green = protected / share / video / RAW
  (bottom-left).

### Launcher icon (from a 512×512 source PNG)

Generated with Python/PIL into an adaptive icon plus legacy and monochrome layers:

- **Adaptive foreground** (`mipmap-*/ic_launcher_foreground.png`): the artwork
  scaled to ~67 % of the 108 dp canvas and centered, so it stays inside the mask
  safe zone — nothing is clipped by circle / squircle / rounded masks.
- **Background** (`values/ic_launcher_background.xml`): a solid color (white).
- **Legacy** (`mipmap-*/ic_launcher.png`, `ic_launcher_round.png`): the art on a
  white square (~86 %) and inside a white circle (~64 %), five densities.
- **Monochrome** (`mipmap-*/ic_launcher_monochrome.png`): a single-color line-art
  silhouette (dark outlines + lens + Wi-Fi arcs) for Android 13+ themed icons,
  referenced from `<monochrome>` in the adaptive XML.
- **Play Store icon**: 512×512 flattened on white, uploaded in the Play Console
  (not part of the APK).

Keep a 512×512 transparent-background master so the whole set can be regenerated.
