# Architecture — GPS Walk Simulator (Milestone 1)

## Modules

| Module | Type | Purpose |
|---|---|---|
| `:core` | Pure Kotlin/JVM library | Models, geodesy, route interpolation, walking engine, state machine, simulation runner. No Android dependency, so it is unit-tested on the JVM with virtual time. |
| `:app` | Android application `com.gpswalk.simulator` | Compose UI, foreground service, Android mock-location adapters, DataStore persistence. |
| `:diagnostic` | Android application `com.gpswalk.diagnostic` | GPS Location Inspector, an independent receiver. It has no dependency on `:app` or `:core` and no IPC with the simulator. |

```
 UI (Compose, MainViewModel) ─┐
                              ├──► SimulationController (process singleton, main thread)
 WalkingSimulationService ────┘          │  owns
   (FGS type=location,                   ▼
    notification + actions)        SimulationRunner (core) ── WalkingEngine (core)
                                         │                       └─ RouteInterpolator / DistanceCalculator
                                         ▼ LocationPublisher (core interface)
                              CompositeLocationPublisher (app)
                                ├─ AndroidMockLocationAdapter  (primary, required)
                                └─ FusedMockLocationAdapter    (secondary, optional)
```

### Separation of concerns
* **UI:** `ui/`, `MainActivity`. Renders `SimulationSnapshot`; never computes positions.
* **Simulation logic:** `core/engine`. `WalkingEngine` is deterministic and synchronous. `SimulationRunner` adds the coroutine loop and the failure policy.
* **Android location integration:** `app/location`. This is the only code that touches `LocationManager` or Play services.
* **Background execution:** `app/service`. The service only keeps the process in the foreground and mirrors state into the notification. Simulation state lives in `SimulationController`, so it survives Activity recreation and configuration changes.
* **Persistence:** `app/data`, using DataStore Preferences. Room is not used: a few routes and one record don't justify it.
* **Diagnostics:** the separate `:diagnostic` app.

The `LocationPublisher` interface in `:core` is the provider abstraction requested in the brief. Unit tests use a `FakePublisher`, which is the deterministic test mode. The interface takes a platform-neutral `SimulatedFix`, not `android.location.Location`, so `:core` stays JVM-only. `LocationFactory` does the conversion.

## Mock-location API decision

Investigated options:

| API | Status | Decision |
|---|---|---|
| `LocationManager.addTestProvider` / `setTestProviderEnabled` / `setTestProviderLocation` / `removeTestProvider` | Public, documented. On API 31+ uses `addTestProvider(String, ProviderProperties)`; the 10-argument overload is deprecated in 31 and used only on API 29–30. Requires the caller to be the **selected mock location app** (the `android:mock_location` app-op), otherwise `SecurityException`. | **Primary channel.** Injects into the `gps` provider (required) and the `network` provider (best effort). |
| Play services `FusedLocationProviderClient.setMockMode(true)` / `setMockLocation()` | Public, documented. Requires the mock-location app selection plus a location permission. While enabled, FLP serves only injected locations to all FLP clients, flagged as mock. | **Secondary channel, on by default** (toggle in Setup). Most apps read FLP, so this makes delivery to them deterministic. |
| Developer options › *Select mock location app* | User-controlled system setting. Declaring `ACCESS_MOCK_LOCATION` in the manifest is what lists the app there. | Required, and documented for the user. The app **never** changes it. It only reads it through `AppOpsManager.checkOpNoThrow` (API 36) or `unsafeCheckOpNoThrow` (API 29–35) to show diagnostics. |

The following were rejected because the brief forbids them: hidden APIs or reflection, `WRITE_SECURE_SETTINGS`, shell or `appops` from the app, root, and any attempt to clear the mock flag.

**The mock flag:** the platform marks every location from a test provider as mock (`Location.isMock`, or `isFromMockProvider` before API 31). Play services marks locations injected with `setMockLocation` the same way. `LocationFactory` also calls `setMock(true)` on API 31+. That only makes the intent explicit; the app never clears the flag.

**Location completeness:** `setTestProviderLocation` rejects locations without `time` and `elapsedRealtimeNanos`. Both are always set, from `System.currentTimeMillis()` and `SystemClock.elapsedRealtimeNanos()`.

## Walking engine
* Distance uses Haversine on a sphere of radius 6,371,008.8 m. Bearing is the initial great-circle bearing. Interpolation uses spherical linear interpolation (slerp) along each segment's great circle, so long segments follow the true path rather than a lat/lon straight line. Altitude is interpolated linearly.
* Position is a function of **accumulated moving time × speed**, measured with a monotonic clock (`elapsedRealtimeNanos`). It is never derived from counting timer ticks. Late callbacks, pauses and speed changes cannot cause drift or jumps. A speed change banks the distance covered at the old speed first.
* On completion the moving time is clamped to the exact arrival time, and the final fix is the destination with speed 0.
* State machine: `IDLE → READY → RUNNING ⇄ PAUSED → {COMPLETED | STOPPED | ERROR} → READY`. Illegal transitions throw. A terminal state cannot go directly back to RUNNING: the user must press START, which reloads the route.
* While PAUSED, the held position keeps being published with speed 0, so consumers do not see a stale or timed-out provider.

## Failure policy
* Publisher initialization failure → `ERROR` immediately. Example: app not selected as the mock location app.
* `SecurityException` during injection (e.g. the user switched the mock app mid-run) → fatal → `ERROR`. Providers are released.
* Non-fatal injection failures → `ERROR` after 5 consecutive failures. There is no indefinite retry.
* A secondary channel (FLP) failing to initialize is dropped and reported in the injection-result text. The primary channel is still required.
* `startForeground` failure (missing location permission, `ForegroundServiceStartNotAllowedException`) → `ERROR` with a user-facing reason.
* The service destroyed while active → `ERROR` ("terminated by the system").
* **Leftover test providers after a process kill.** Found during emulator testing: when the process is SIGKILLed, Android keeps its gps/network test providers registered. Consumers then freeze on the last mock fix and real GPS is blocked, even across an APK reinstall. On every fresh start with no active simulation, `SimulationController` therefore calls `removeTestProvider` for gps and network (documented API, own providers only). If the last run was killed, it also turns off FLP mock mode. On API 36, `removeTestProvider` is a silent no-op when nothing is registered, and no public API reports whether a test provider exists, so the log says "cleared any leftover" rather than claiming something was stale.
* Process killed while active: the persisted record still says RUNNING or PAUSED. On next launch the app shows a notice that the simulation stopped unexpectedly, and marks the record as `ERROR`. It does **not** restart. The service returns `START_NOT_STICKY`.

## Background execution
* The foreground service has `android:foregroundServiceType="location"` and declares `FOREGROUND_SERVICE_LOCATION`. Android 14+ requires a location runtime permission to be granted before `startForeground` with this type, so START requests `ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION`. **No `ACCESS_BACKGROUND_LOCATION`** is requested: the FGS is started from a visible Activity, which is the documented while-in-use path.
* `POST_NOTIFICATIONS` is requested on Android 13+. If it is denied, the service still runs, but the notification (and its Pause/Stop actions) is hidden.
* The service is only ever started by an explicit START tap.

## Persistence (DataStore)
* `settings`: speed, interval, FLP toggle, last route name, and the last simulation record (state, route name, distance, moving time, publish count, timestamp, error). **No position history** is stored.
* `routes`: user-saved routes as a small JSON document.
* Setup → *Delete saved routes and records* wipes both.

## Diagnostic app
`LocationReceiver` subscribes at the same time to:
* FusedLocationProviderClient, `PRIORITY_HIGH_ACCURACY`, 1 s (when Play services is available), and
* `LocationManager.GPS_PROVIDER`, 500 ms (the fallback and cross-check).

It reports the mock flag exactly as Android supplies it. Status is computed by the pure function `InspectorStatus.resolve`. Each received fix is logged to Logcat under the tag `GPSLocationInspector`, so delivery can be verified over ADB.

## Toolchain
AGP 9.1.1 (built-in Kotlin), Gradle 9.3.1, Kotlin 2.2.10, JDK 21 (bytecode target 17), compileSdk/targetSdk 36 (Android 16), minSdk 29. Compose BOM 2025.10.01 with Material 3, DataStore 1.1.7, play-services-location 21.3.0. API 37 platforms exist, but this AGP version is validated against 36, and 36 matches current One UI 8 firmware.
