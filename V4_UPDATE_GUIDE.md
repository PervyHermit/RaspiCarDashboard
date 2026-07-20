# Update the existing repository to V4

Use `RaspiCarDashboard-V4-update-only.zip` for the smallest update.

1. Extract the zip outside the repository.
2. Copy all extracted files into the local `RaspiCarDashboard` repository.
3. Choose **Replace files in destination**.
4. Open GitHub Desktop.
5. Verify that no `.jks`, credentials or Base64 signing files are listed.
6. Commit with a message such as `RaspiCar Dashboard V4`.
7. Push to `main`.
8. Open the green GitHub Actions run.
9. Download `RaspiCarDashboard-v4-debug`.
10. Extract and install `app-debug.apk`.

V4 uses version code 4 and the same application ID as V3. With the same persistent signing secrets it should update the persistently signed V3 installation directly.
