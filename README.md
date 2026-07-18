# RaspiCar Dashboard V3

Custom landscape launcher/dashboard for a Raspberry Pi 5 running LineageOS, designed around a 1280×800 HDMI touchscreen but now adaptable to smaller and larger Android windows.

![RaspiCar Dashboard V3 preview](docs/v3-dashboard.png)

> The large content card switches between **Spotify mode** and **Camera mode**. The preview above shows the V3 visual direction; the live camera replaces the Spotify controls when Camera is selected.

## V3 dashboard

- RaspiCar dashboard on the left.
- Waze requested on the right in Android split-screen.
- Time, date, current weather and GPS speed.
- Spotify-focused mini player with album art, title, artist, progress and previous/play/next controls.
- Embedded Android Camera2 preview for a built-in or USB camera exposed by LineageOS.
- Fixed buttons for GPS Connector, Camera, Spotify and RaspiCar settings.
- Five user-configurable shortcut slots plus a permanent **Apps** button.
- Full app drawer containing installed launchable apps.
- Compact, Medium, Large and automatic layout profiles.
- Dark Blue, Graphite, Orange, Green, Red, Purple and custom accent themes.
- Manual or automatic sun-based dim overlay.
- First-run setup wizard.

## Waze behaviour

Waze can open once when the dashboard starts. It is intentionally **not** forced back every time RaspiCar resumes.

If the user closes Waze, it remains closed. The visible **Open Waze** button restores it manually.

Android/LineageOS remains responsible for the final split position and ratio. RaspiCar requests Waze on the right with roughly 65% of the display.

## Spotify mini player

RaspiCar intentionally follows only Spotify's active Android MediaSession. Other media apps do not replace the dashboard controls.

Notification-listener access is required for:

- title and artist;
- album art;
- progress;
- play/pause;
- previous/next.

The Spotify button opens the full Spotify app when the user needs to choose music. A draggable return overlay can bring RaspiCar back to the left pane.

## Embedded USB camera

Camera mode uses Android Camera2 directly. If LineageOS exposes the USB camera to the normal Android Camera app, it should also appear in RaspiCar's camera selector.

Camera settings include:

- camera selection with live preview;
- mirror image on/off;
- rotation 0°, 90°, 180° or 270°.

The camera is opened only while Camera mode is active. Returning to Spotify releases it.

## Automatic dimming

Dimming has three modes:

- **Off**
- **Manual** — one fixed percentage
- **Automatic** — day and night percentages based on sunrise and sunset at the last known GPS location

The sun calculation works offline after a location has been received. A configurable offset can move the sunrise/sunset switch earlier or later.

Because the display is connected through HDMI, this remains a touch-through software overlay. It darkens the rendered image but does not reduce the physical backlight power.

## Adaptive layouts

The dashboard can use:

- **Automatic** — chosen from the current Android app-window size
- **Compact** — phones and short landscape windows
- **Medium** — the normal Raspberry Pi split pane
- **Large** — tablets or larger dashboard panes

This is based on the current app window, not only the physical screen resolution, so split-screen sizing is taken into account.

## First-run setup

V3 opens a setup sequence for:

1. Waze, GPS Connector and Spotify checks.
2. Location, camera, media and overlay permissions.
3. USB-camera selection and preview.
4. Layout and colour theme.
5. Waze split-screen test.
6. Default Home-app selection.
7. Final status summary.

The setup can be run again from RaspiCar settings.

## Permissions

RaspiCar may request:

- precise location for speed, weather and sun times;
- camera for the embedded preview;
- notification-listener access for Spotify;
- display-over-other-apps for the dim layer and return button.

On sideloaded APKs, Android may block special access until **Allow restricted settings** is enabled from the app-info menu.

## Building with GitHub Actions

The workflow builds:

`RaspiCarDashboard-v3-debug`

The APK inside the artifact is:

`app-debug.apk`

See [BUILD_WITH_GITHUB.md](BUILD_WITH_GITHUB.md).

## Persistent signing

GitHub-hosted runners normally create a new temporary debug certificate. APKs signed by different certificates cannot update each other.

V3 supports optional persistent signing through these repository secrets:

- `RASPI_KEYSTORE_BASE64`
- `RASPI_KEYSTORE_PASSWORD`
- `RASPI_KEY_ALIAS`
- `RASPI_KEY_PASSWORD`

See [SIGNING_WITH_GITHUB.md](SIGNING_WITH_GITHUB.md).

The V2 test APK was signed with a different temporary debug key. V2 will normally need to be uninstalled once before installing the persistently signed V3 build. Back up or note your shortcut choices first.
