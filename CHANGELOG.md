# Changelog

## V2 — Spotify/PiP workflow and layout update

- Media widget now deliberately targets Spotify only, so VLC playback cannot take over the dashboard controls.
- Added a visible **Open Waze** button inside the dashboard.
- Waze opens once when the dashboard starts, but is no longer continuously reopened from `onResume()` after the user closes it.
- Added a draggable overlay return button when Spotify or VLC is opened.
- VLC workflow now assists Picture-in-Picture: start a video in VLC and tap **VLC zwevend** to return to the dashboard. VLC enters PiP when VLC's own PiP setting is enabled.
- Increased the height of both icon panels and enlarged fixed/custom app icons to prevent clipping on the 1280×800 split-screen layout.
- Retained six configurable app slots and drag-to-trash removal.
- Retained RaspiCar settings, including direct access to full Android settings.
- Delayed initial Waze launch until the first location permission dialog has finished.
- Version bumped to `2.0.0-v2` / version code 2.

## V1

- Initial custom launcher/dashboard.
- Waze split-screen launch.
- Weather, speed, universal media session controls, custom app slots and HDMI dim overlay.
