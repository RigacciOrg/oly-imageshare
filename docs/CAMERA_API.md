# Olympus / OM System Wi-Fi HTTP API

Reference for the camera protocol Oly ImageShare speaks. All requests are plain
HTTP GET to the camera's address (default `192.168.0.10`, configurable in
Settings). Connect the phone to the camera's Wi-Fi hotspot first
(camera: Playback menu → *Connection to Smartphone*).

Base URL in this document: `http://<CAMERA_IP>`

## Capability discovery

    GET /get_commandlist.cgi

Returns an XML list of the `cgi` commands the camera supports. Presence of a
command is detected by its name, e.g.:

- `exec_erase`      → the camera supports **deletion**.
- `get_rsvimglist`  → the camera supports the **share / reserved** list.

The app only issues those requests when the corresponding command is advertised.

## Camera mode

    GET /switch_cammode.cgi?mode=play    # required before listing / reading images
    GET /switch_cammode.cgi?mode=rec     # restore shooting mode (done on leaving the gallery)

## Camera info

    GET /get_caminfo.cgi

```xml
<caminfo>
  <model>E-M10MarkIV</model>
  <SerialNumberList><Serial1>BJGA99719</Serial1></SerialNumberList>
  <GetRawImageSupport>available</GetRawImageSupport>
  ...
</caminfo>
```

`<model>` is always present; `SerialNumberList` and the capability fields are
optional (e.g. an E-M10 Mark II returns only `<model>`).

## Listing images

    GET /get_imglist.cgi?DIR=/DCIM
    GET /get_imglist.cgi?DIR=/DCIM/100OLYMP

The first line is a version header (`VER_100`); each following line is CSV:

    <directory>,<filename>,<size>,<attribute>,<date>,<time>

Example:

    VER_100
    /DCIM,100OLYMP,0,16,23902,40743
    /DCIM/100OLYMP,PA300001.JPG,10709581,0,23902,40743

Directories are walked recursively from the DCIM root (the "Camera DCIM folder"
setting). The gallery flattens every file into one list regardless of
sub-directory.

### `attribute` (bit field)

| Bit | Mask | Meaning       |
|-----|------|---------------|
| 0   | 0x01 | Protected     |
| 4   | 0x10 | Sub-directory |

(Confirmed from sample data: protected JPGs carry attribute `1`; the `100OLYMP`
directory entry carries `16`.)

### `date` (FAT-style bit field)

| Bits  | Field | Encoding              |
|-------|-------|-----------------------|
| 15–9  | Year  | value + 1980          |
| 8–5   | Month | 1–12                  |
| 4–0   | Day   | 1–31                  |

### `time` (FAT-style bit field)

| Bits   | Field  | Encoding      |
|--------|--------|---------------|
| 15–11  | Hour   | 0–23          |
| 10–5   | Minute | 0–59          |
| 4–0    | Second | value × 2     |

Example: date `23902`, time `40743` → 2026-10-30 19:57.

### File types (by extension)

- Photos: `.JPG` / `.JPEG`
- RAW: `.ORF`
- Movies: `.MOV`, `.MP4`, `.AVI`

## Share / reserved list

    GET /get_rsvimglist.cgi?DIR=/DCIM/100OLYMP

Same line format as `get_imglist`. The returned paths are the images the camera
has flagged for sharing (shown with the green "share" marker).

## Thumbnails

    GET /get_thumbnail.cgi?DIR=/DCIM/100OLYMP/PA300001.JPG

Returns a ~160×120 JPEG. Small enough to store as-is; the app caches each one in
private storage keyed by a SHA-1 of the listing fields.

## Full-resolution download

    GET /DCIM/100OLYMP/PA300001.JPG      # the file path itself, no cgi

The response is the original file; `Content-Length` drives the per-file progress bar.

## Deletion

    GET /exec_erase.cgi?DIR=/DCIM/100OLYMP/PA300001.JPG

Only available when `get_commandlist` advertises `exec_erase`.

## Typical workflow

1. `get_commandlist.cgi` → note erase / share-list support.
2. `switch_cammode.cgi?mode=play`.
3. Walk `get_imglist.cgi` from `/DCIM`; for each directory with files, optionally
   `get_rsvimglist.cgi`.
4. `get_thumbnail.cgi` for any image not already cached.
5. On demand: full-file GET (download) or `exec_erase.cgi` (delete).
6. On exit: `switch_cammode.cgi?mode=rec`.
