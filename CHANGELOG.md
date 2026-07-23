# Changelog

## V5.1 — 5.1.0

- Kept Waze on the right while user-launched apps replace the HUD on the left.
- Kept the draggable return-to-HUD overlay above external apps, the Apps drawer and RaspiCar settings.
- Moved Apps into the six-icon fixed row: Camera, Spotify, Volume, Waze, Apps and Settings.
- Expanded the personal shortcut row to six user slots.
- Moved volume below the media/camera panel and made the fixed Volume button show or hide the slider.
- Made the setup preview persist setup completion, while retaining the explicit rerun option in Settings.
- Removed the built-in local-media activity, library and playback service.
- Removed synchronous camera-thread waiting and moved Camera2 opening/closing work off the UI thread to prevent repeated ANR dialogs.

## V5 — 5.0.0

- Rebuilt the dashboard as a compact vertical stack: time/weather/speed, volume, camera/media and one combined app card.
- Moved the speed meter into the top status card.
- Combined fixed apps, shortcuts and the Apps drawer without section headings or icon labels.
- Replaced the GPS dashboard tile with a volume mute/restore tile; optional GPS Connector startup remains available.
- Moved Waze beside RaspiCar settings in the fixed app row.
- Placed previous, play/pause and next controls over the album artwork.
- Kept the volume slider visible while either media or camera is active.
- Added adjustable top-status and app-card heights alongside the existing camera-width setting.
- Requested split-screen launch bounds for external apps opened from fixed buttons, shortcuts and the Apps drawer.
- Kept the draggable return-to-dashboard HUD available for apps launched from the Apps drawer.
- Hardened USB-camera discovery so stale or malformed camera IDs are skipped individually.
- Removed the LineageOS/Raspberry Pi footer and redundant dashboard source labels.

## V4.0.2 — camera stability fix

- Fixed a crash when opening **Camera kiezen** on some Camera2/USB-camera implementations.
- Coalesced duplicate preview starts and protected against stale Camera2 callbacks.
- Hardened camera enumeration when a device reports incomplete camera characteristics.
- Hidden the media-volume card while the embedded camera is active, giving the camera panel more vertical space.
- Restored the volume card automatically when returning to Spotify/local media.

## V4 — 4.0.0

- Added a universal draggable return-to-dashboard overlay for external apps opened from RaspiCar.
- Added edge snapping and saved position for the return overlay.
- Made GPS Connector optional and added built-in Android GPS mode.
- Added Spotify, Local and Automatic media-source modes.
- Added a built-in local audio player with background playback and media notification.
- Added local-folder selection through Android Storage Access Framework.
- Added a local music library browser for internal, SD-card and USB storage providers.
- Added a device media-volume slider with mute/restore.
- Fixed stretched camera previews by preserving the camera aspect ratio.
- Added camera Fit and Fill/Crop display modes.
- Added automatic, 4:3 and 16:9 camera aspect preferences.
- Added adjustable camera preview width from 45% to 100%.
- Updated the setup wizard for optional GPS Connector and media-source selection.
- Updated fixed app behaviour to reflect the selected GPS and media source.
- Kept the established Waze behaviour unchanged.

## V3 — 3.0.0

- Removed VLC-specific dashboard integration.
- Made Spotify the dedicated media source for the home screen.
- Added embedded Camera2 preview for cameras exposed by Android, including supported USB cameras.
- Added camera selection, live preview, mirror and rotation settings.
- Replaced the fixed VLC button with Camera.
- Fixed app row is now GPS Connector, Camera, Spotify and Settings.
- Changed shortcuts from six custom slots to five custom slots plus a permanent Apps drawer.
- Added an app drawer containing installed launchable apps.
- Added Compact, Medium, Large and Automatic layout profiles.
- Added six predefined colour themes and a custom accent colour.
- Added automatic sunrise/sunset dimming with separate day/night levels and time offset.
- Added a seven-step first-run setup flow.
- Added status checks for apps and permissions.
- Kept Waze on-demand behaviour: closing Waze does not cause RaspiCar to force it open again.
- Added optional persistent GitHub signing through repository secrets.
- Added V3 README artwork.

## V2 — 2.0.0

- Spotify-focused mini player.
- Manual Open Waze control.
- Larger fixed-app and shortcut tiles.
- Floating return overlay for external apps.
- Waze no longer reopened on every resume.

## V1 — 1.0.0

- First dashboard prototype.
