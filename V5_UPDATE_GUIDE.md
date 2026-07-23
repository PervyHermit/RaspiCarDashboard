# Update the existing repository to V5.1

1. Copy the V5.1 source files into the existing repository.
2. Verify that no `.jks`, credentials or Base64 signing files are included.
3. Commit and push the update.
4. Open the GitHub Actions build.
5. Download `RaspiCarDashboard-v5.1-debug`.
6. Extract and install `app-debug.apk`.

V5.1 uses version code 7 and keeps the existing application ID. When the same persistent signing secrets are used, it installs directly over the signed V5 build.
