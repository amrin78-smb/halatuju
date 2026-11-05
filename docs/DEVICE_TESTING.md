# Device testing — Samsung Galaxy S25 Ultra (One UI, no root)

## 1. One-time setup on the phone
1. **Developer options:** Settings › About phone › Software information › tap **Build number** 7 times.
2. **USB debugging** (only for ADB-based checks): Settings › Developer options › USB debugging.
3. Install both APKs (see BUILD_INSTRUCTIONS.md).
4. **Select the mock location app:** Settings › Developer options › **Select mock location app** › *GPS Walk Simulator*.
   (The simulator's Setup tab has a button that opens Developer options, and shows whether the selection is active.)
5. Keep **Location** on.
6. Recommended for long runs: Settings › Apps › GPS Walk Simulator › Battery › **Unrestricted**.

## 2. Core validation (AC11–AC13)
1. Open **GPS Location Inspector** and allow location *While using the app*. With no simulation running, the status should become **REAL LOCATION RECEIVED** (if the phone has a fix) or **NO LOCATION RECEIVED**.
2. Open **GPS Walk Simulator** › Route tab › *Use* on Route A › Simulator tab › **START**. Allow location and notifications.
3. Status should show **RUNNING**, *Last injection: OK – gps+network+fused*, and *Locations published* should increase.
4. Switch to the Inspector. Expected: **MOCK LOCATION RECEIVED**, *Android mock flag: TRUE (mock)*, and latitude increasing about 0.00001° per second at 4 km/h. Both *Fused* and *LocationManager GPS* should count up in "Per source".
5. Optional ADB evidence:
   ```bash
   adb logcat -s GPSLocationInspector GPSWalkSimulator
   adb shell dumpsys location | grep -A3 "provider \[mock\]"
   ```

## 3. Controls (AC06–AC10)
* PAUSE (in the app, or the notification's *Pause*): the Inspector shows a constant position with speed 0.
* RESUME: movement continues from the same point, with no jump.
* STOP: the notification disappears at once. Within a few seconds the Inspector returns to REAL or NO LOCATION.
* Let Route A finish: 111.2 m at 4 km/h ≈ 1 min 40 s. Expect state COMPLETED, distance 111.2 m, elapsed 0:01:40, and the final position 13.757300, 100.501800.

## 4. Background (AC14)
Start Route B (1.08 km), press Home, open the Inspector, and lock and unlock the screen. The Inspector should keep receiving mock fixes, and the notification's progress should increase. For a long test, leave it running with the screen off for 10+ minutes and record whether One UI stopped it.

## 5. Negative tests
* De-select the mock app (Developer options › Select mock location app › *Nothing*), then press START. Expect **ERROR** with "not selected as the mock location app". No green indicators.
* Deny the location permission, then press START. Expect a message explaining it is required for the location foreground service.
* Switch the mock app to another app during a run. Expect ERROR within one interval.

## Emulator equivalence (used during development)
On an emulator, `adb shell appops set com.gpswalk.simulator android:mock_location allow` sets the same app-op as the Developer-options picker. **Do not** use that on the S25. Use the Settings UI so the device configuration stays under your control.
