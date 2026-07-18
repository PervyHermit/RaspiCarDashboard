# Update the existing repository to V3

Use `RaspiCarDashboard-V3-update-only.zip`.

1. Extract the update zip.
2. Copy all included files and folders into the local `RaspiCarDashboard` repository.
3. Choose **Replace files in destination**.
4. Open GitHub Desktop.
5. Review the changed and added files.
6. Commit with a message such as `RaspiCar Dashboard V3`.
7. Push to GitHub.
8. Wait for **Build debug APK** to become green.
9. Download `RaspiCarDashboard-v3-debug` from the workflow artifacts.

Before the first V3 build, configure persistent signing secrets if future APKs should install as updates.

Because V2 used a different temporary debug certificate, uninstall V2 before installing the first persistently signed V3 build.
