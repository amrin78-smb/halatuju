# Build instructions

## Requirements
* JDK 17+ (verified with OpenJDK 21.0.12)
* Android SDK with `platforms;android-36` and `build-tools;36.0.0`
* `local.properties` with `sdk.dir=/path/to/Android/Sdk` (Android Studio creates it automatically)
* Network access on the first build (Gradle 9.3.1 distribution and Maven dependencies)

## Commands
```bash
./gradlew test                      # JVM unit tests (core + app + diagnostic)
./gradlew assembleDebug             # both debug APKs
./gradlew connectedDebugAndroidTest # instrumented tests (device/emulator attached)
```

Outputs:
* `app/build/outputs/apk/debug/app-debug.apk`: GPS Walk Simulator
* `diagnostic/build/outputs/apk/debug/diagnostic-debug.apk`: GPS Location Inspector

## Install
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r diagnostic/build/outputs/apk/debug/diagnostic-debug.apk
```

## Troubleshooting
* **Gradle download times out / HTTP 504:** `services.gradle.org` redirects to GitHub releases, which was intermittently returning 504 during development. Download `gradle-9.3.1-bin.zip` from any mirror, verify SHA-256 `b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06`, and place it in `~/.gradle/wrapper/dists/gradle-9.3.1-bin/<hash>/`. The wrapper re-checks the checksum (`distributionSha256Sum`).
* **Android Studio:** open the root folder. It is a standard Gradle Kotlin DSL project with a version catalog in `gradle/libs.versions.toml`.
