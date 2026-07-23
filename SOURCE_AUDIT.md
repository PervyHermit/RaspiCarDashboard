# V5.1 source audit

Checks performed in the generation environment:

- All Android XML resource files and the manifest parse successfully.
- Java `R.id`, `R.layout`, `R.drawable`, `R.string` and `R.color` references were compared with project resources; no project resource references are missing.
- The built-in local-media activity and foreground playback service were removed from source and manifest.
- Version code/name and GitHub artifact name were updated to V5.1.
- Private signing material is not included in the source or update archives.

Java 17 and the Android SDK were unavailable in this execution environment, so the final Android/Gradle compilation must run through the included GitHub Actions workflow. Camera, split-screen, overlay and audio behaviour still require testing on the target Raspberry Pi/phone.
