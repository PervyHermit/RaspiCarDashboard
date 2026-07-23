# Update the existing repository to V5

1. Copy the V5 source files into the existing repository.
2. Verify that no `.jks`, credentials or Base64 signing files are included.
3. Commit and push the update.
4. Open the GitHub Actions build.
5. Download `RaspiCarDashboard-v5-debug`.
6. Extract and install `app-debug.apk`.

V5 uses version code 6 and keeps the existing application ID. When the same persistent signing secrets are used, it installs directly over the signed V4 build.
