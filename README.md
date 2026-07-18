# RaspiCar Dashboard V2

Custom landscape launcher/dashboard for a Raspberry Pi 5 running LineageOS on a 1280×800 HDMI touchscreen.

## V2 layout

- RaspiCar dashboard on the left.
- Waze requested on the right in Android split-screen.
- Spotify-focused mini player with album art, title, artist, progress and previous/play/next controls.
- Current speed, time, date and weather.
- Fixed buttons for Waze/GPS, VLC, Spotify and RaspiCar settings.
- Six user-configurable shortcut slots.
- Larger icon windows to avoid the clipping seen in V1.

## Waze behavior

Waze is opened once at the initial dashboard start when that preference is enabled. It is intentionally **not** reopened every time RaspiCar resumes. If the user closes Waze, the visible **Open Waze** button restores it manually.

Android/LineageOS remains responsible for the exact split position and ratio. The app requests Waze on the right with roughly 65% of the screen.

## Spotify mini player

The dashboard intentionally selects only Spotify's active Android MediaSession. VLC or another media app will not replace the Spotify controls.

Notification-listener access is required so RaspiCar can read Spotify metadata and send media commands. The Spotify icon or album art can open the full Spotify app. A draggable **Dashboard** overlay button is shown so the user can return without hunting for system navigation.

## VLC floating/Picture-in-Picture

Android does not let RaspiCar directly force another app's activity into PiP. VLC must enter its own Picture-in-Picture mode.

Set VLC to use PiP:

1. Open VLC.
2. Open VLC settings.
3. Under Video / Background-PiP mode, choose Picture-in-Picture playback.
4. From RaspiCar, tap VLC and start a video.
5. Tap the floating **VLC zwevend** button.
6. RaspiCar returns and VLC should remain as a movable PiP window.

This requires Android's **Display over other apps** permission for RaspiCar. The same permission is already used by the HDMI dim overlay.

## Settings

RaspiCar settings include:

- Open Waze once at dashboard startup.
- Start GPS Connector before Waze.
- Weather enable/disable.
- Floating return button enable/disable.
- Dim overlay and dim percentage.
- Overlay, media and location permissions.
- Default Home app selection.
- Direct button to the complete Android settings.
- Manual **Waze now open right** action.

## Build

The GitHub Actions workflow builds a debug APK and uploads it as:

`RaspiCarDashboard-v2-debug`

The APK inside the artifact is:

`app-debug.apk`
