# Known limitations (Milestone 1)

1. **Mock locations are always identifiable as mock.** This is by design. Apps that reject mock locations, including many games (e.g. Pokémon GO) and apps using Play Integrity, will refuse or ignore them. This project does not and will not attempt to conceal the mock flag or bypass such checks.
2. **Requires Developer options and manual mock-app selection.** The app cannot select itself; Android reserves that for the user. If another app is selected mid-run, injection fails with `SecurityException` and the simulation enters ERROR.
3. **FLP mock mode and test providers are global.** While a run is active, *every* app on the device receives only simulated positions. Both are released on stop, completion, error and service teardown. **If the process is killed outright (SIGKILL), Android leaves the test providers registered**: other apps keep seeing the last mock fix and real GPS stays blocked. **Reopening GPS Walk Simulator clears this** (verified on the emulator). Alternatively, de-select the mock location app in Developer options, or reboot.
4. **The network test provider is best-effort.** Some OEM builds may refuse to replace `network`. The simulation continues on `gps` (and FLP) and records the failure in the injection result.
5. **Background survival is not guaranteed on Samsung.** One UI's battery management ("Sleeping apps", "Deep sleeping apps", adaptive battery, "Put unused apps to sleep") can still stop the app. Recommended: Settings › Apps › GPS Walk Simulator › Battery › **Unrestricted**. If the process is killed, the app reports it on the next launch and does not restart.
6. **Without notification permission** the FGS runs but its notification and its Pause/Stop actions are hidden. Use the app UI to stop.
7. **No map.** Milestone 1 uses coordinate entry and presets only, so no Maps API key is needed.
8. **Spherical earth model.** About 0.3 % distance error versus WGS84 ellipsoid geodesics, which is negligible at walking scale.
9. **Fixed accuracy (5 m) and no GPS noise.** Movement is perfectly smooth. Jitter is a candidate for a later milestone.
10. **Interval changes** apply on the next START. Speed changes apply immediately.
11. **Route editing** is locked while a simulation is active.
12. **Instrumented test caveat:** Android throttles location delivery to background apps, so on-device tests that listen for locations keep an Activity in the foreground. Tests that need the mock-app selection are *skipped* (via `assume`) when it is not set. Run the suite once with and once without the selection to cover both paths. `connectedDebugAndroidTest` uninstalls the apps afterwards, which also clears the selection.
13. **Release builds** are not configured for signing. Only debug APKs are produced.
