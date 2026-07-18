# Build V3 with GitHub Actions

1. Copy the complete V3 update into the local `RaspiCarDashboard` repository.
2. Commit and push the changed files with GitHub Desktop.
3. Open the repository on GitHub.
4. Open **Actions**.
5. Select **Build debug APK**.
6. Open the newest green workflow run.
7. Download the artifact **RaspiCarDashboard-v3-debug**.
8. Extract the artifact zip.
9. Install `app-debug.apk` on the Android device.

## Signing

Without signing secrets, GitHub builds a normal temporary debug APK. A later workflow run may use a different certificate and Android may refuse it as an update.

For repeatable updates, configure the four signing secrets described in `SIGNING_WITH_GITHUB.md` before building V3.

V2 used a different temporary debug certificate. Uninstall V2 before installing the persistently signed V3 APK.
