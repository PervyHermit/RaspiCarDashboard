# V3 source audit

Completed checks:

- All Android XML resources parse successfully.
- Manifest activities and services have matching Java classes.
- Java resource references were checked against generated resource names.
- Java sources compile against the Android 36 public `android.jar` with Java 17.
- Android resources compile and link with AAPT2 36.
- Compiled classes convert to `classes.dex` with D8 36.
- A signed test APK was created and verified with APK Signature Scheme v3.
- APK badging confirms package `nl.roy.raspicardashboard`, version code 3, min SDK 29 and target SDK 36.

Remaining device tests:

- USB-camera enumeration and preview on the KonstaKANG Raspberry Pi build.
- Camera rotation and mirror behaviour with the actual vehicle camera.
- Compact layout on the Android 17 phone.
- Automatic day/night transition with GPS location.
- Spotify metadata and transport controls on the target devices.
- Waze right-side split restoration after setup.
