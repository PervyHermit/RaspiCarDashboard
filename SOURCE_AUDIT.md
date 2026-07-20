# V4 source audit

Checks performed in the generation environment:

- All Android XML resource files and the manifest parse successfully.
- Java source files pass a syntax-only `javac` parse; Android symbols cannot be resolved without an installed Android SDK.
- Java `R.id`, `R.layout`, `R.drawable`, `R.string` and `R.color` references were compared with project resources; no project resource references are missing.
- Manifest entries were added for the local-media activity and media-playback foreground service.
- Version code/name and GitHub artifact name were updated to V4.
- Private signing material is not included in the source or update archives.

The Android SDK was unavailable in this execution environment, so the final Android/Gradle compilation must run through the included GitHub Actions workflow. Camera, split-screen, overlay and audio behaviour still require testing on the target Raspberry Pi/phone.
