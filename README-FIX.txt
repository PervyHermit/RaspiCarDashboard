RaspiCar Dashboard V4.0.2 camera fix

1. Copy everything inside this folder into the root of your existing RaspiCarDashboard repository.
2. Replace existing files when prompted.
3. Commit and push through GitHub Desktop.
4. Wait for the GitHub Actions APK build.
5. Install the new APK over V4/V4.0.1.

Changes:
- Prevents duplicate Camera2 open requests in Camera kiezen.
- Ignores stale camera callbacks after switching/leaving the screen.
- Handles incomplete or unusual USB camera metadata safely.
- Hides the media volume card while camera mode is active.
- Restores the volume card when returning to media.
- Includes the earlier MediaMetadataRetriever compile fix in DashboardActivity.
