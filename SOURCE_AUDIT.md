# Source audit — V2

- Java files: 6
- XML files: 25
- XML well-formedness: checked successfully
- Java source: basic `javac` syntax scan performed; Android symbols unavailable locally
- Layout ID references: checked against layout resources
- Android SDK compilation: delegated to the included GitHub Actions workflow
- Real-device behavior: must be tested on LineageOS 23.2 / Raspberry Pi 5
- Important device-dependent behavior: Waze split placement and VLC Picture-in-Picture transition
