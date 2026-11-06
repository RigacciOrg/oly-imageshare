# design/

Source and working material that is **not part of the app build** — nothing here
is packaged into the APK. See `docs/DEVELOPMENT.md` for how the shipped icons are
generated from these sources.

## Contents

- `icon/`
  - `icon-master-512.png` — the master artwork (512×512, transparent background).
    **Regenerate the whole launcher icon set from this file** (adaptive foreground,
    legacy square/round, monochrome, and the Play Store icon). Keep any vector or
    layered source (e.g. `.svg`, `.xcf`) here too.
  - `exports/` — throwaway intermediate bitmaps. Git-ignored (see `.gitignore`);
    the folder is kept via `.gitkeep`.
- `play-store/` — assets for the Google Play Console listing (uploaded by hand or
  via fastlane; not shipped in the app):
  - `icon-512.png` — hi-res app icon, 512×512, flattened on white (the Play Store
    applies its own rounded mask, so no transparency here).
  - `feature-graphic-1024x500.png` — add when ready (1024×500, required by Play).
  - `screenshots/` — phone screenshots for the listing.

## Regenerating the icons

The exact recipe (sizes, safe zone, monochrome extraction) is in
`docs/DEVELOPMENT.md › Regenerating the icons`. The short version: run the
PIL/Python steps on `icon/icon-master-512.png` to produce the `mipmap-*` layers
and this `play-store/icon-512.png`.
