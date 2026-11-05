# GPS Walk Simulator (Milestone 1)

Native Android (Kotlin, Jetpack Compose, Material 3) location-testing tool. It simulates walking along a route by publishing locations through Android's **documented mock-location APIs**. A separate app, **GPS Location Inspector**, independently checks what Android actually delivers, including Android's own *mock* flag.

> Locations from this app are always flagged as mock by Android. The project does not hide mock status, bypass Play Integrity or anti-cheat, or target any game. Apps that reject mock locations (e.g. Pokémon GO) will keep rejecting them.

| | Simulator | Inspector |
|---|---|---|
| Package | `com.gpswalk.simulator` | `com.gpswalk.diagnostic` |
| APK | `app/build/outputs/apk/debug/app-debug.apk` | `diagnostic/build/outputs/apk/debug/diagnostic-debug.apk` |

## Quick start
```bash
./gradlew test assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r diagnostic/build/outputs/apk/debug/diagnostic-debug.apk
```
On the phone: Developer options › **Select mock location app** › *GPS Walk Simulator*. Then press START in the simulator and open the Inspector.

## Docs
* [ARCHITECTURE.md](docs/ARCHITECTURE.md): modules, API choices, engine, failure policy
* [BUILD_INSTRUCTIONS.md](docs/BUILD_INSTRUCTIONS.md)
* [DEVICE_TESTING.md](docs/DEVICE_TESTING.md): Galaxy S25 Ultra test procedure
* [LIMITATIONS.md](docs/LIMITATIONS.md)
* [CHANGELOG.md](docs/CHANGELOG.md)

## Project layout
```
core/        pure-Kotlin engine (geodesy, interpolation, state machine, runner) + JVM tests
app/         simulator: ui/, service/, location/, data/, permissions/, util/
diagnostic/  GPS Location Inspector (no dependency on app/core)
docs/
```
Logcat tags: `GPSWalkSimulator` (simulator), `GPSLocationInspector` (each received fix).
