# Build V4 with GitHub Actions

1. Copy the complete V4 update into the local `RaspiCarDashboard` repository.
2. Confirm that `.github`, `app`, `docs`, `build.gradle` and `settings.gradle` are in the repository root.
3. Do not place the private `.jks` file or signing credentials in the repository.
4. Commit and push the changes to `main`.
5. Open **Actions → Build debug APK** on GitHub.
6. Wait for the workflow to finish with a green checkmark.
7. Download the artifact **RaspiCarDashboard-v4-debug**.
8. Extract `app-debug.apk` and install it on the Android device.

## Updating V3

When V3 was built with the same four persistent-signing secrets, V4 can install over V3 without uninstalling it:

- `RASPI_KEYSTORE_BASE64`
- `RASPI_KEYSTORE_PASSWORD`
- `RASPI_KEY_ALIAS`
- `RASPI_KEY_PASSWORD`

If Android reports an incompatible signature, the installed APK was signed with a different key. Do not delete the persistent key; it is required for all future updates.
