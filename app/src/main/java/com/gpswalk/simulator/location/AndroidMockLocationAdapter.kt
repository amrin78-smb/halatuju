package com.gpswalk.simulator.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import com.gpswalk.core.engine.LocationPublisher
import com.gpswalk.core.engine.PublishResult
import com.gpswalk.core.model.SimulatedFix
import com.gpswalk.simulator.util.AppLog

/**
 * Injects mock locations through the framework LocationManager test-provider API:
 * addTestProvider / setTestProviderEnabled / setTestProviderLocation / removeTestProvider.
 *
 * The GPS provider is required; the network provider is best-effort, so apps requesting
 * NETWORK_PROVIDER updates also see the simulated position. Locations delivered by a test
 * provider are always flagged as mock by the platform (Location.isMock / isFromMockProvider).
 *
 * Requires this app to be selected in Developer options > Select mock location app;
 * otherwise every call throws SecurityException.
 */
class AndroidMockLocationAdapter(context: Context) : LocationPublisher {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(LocationManager::class.java)
    private val activeProviders = mutableListOf<String>()

    override val name: String = "LocationManager test providers"

    override fun isAvailable(): Boolean = MockLocationStatus.isSelectedMockLocationApp(appContext)

    override suspend fun initialize(): PublishResult {
        activeProviders.clear()
        val failures = mutableListOf<String>()
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                // A stale test provider from a previous crash would make addTestProvider fail.
                removeQuietly(provider)
                addTestProvider(provider)
                locationManager.setTestProviderEnabled(provider, true)
                activeProviders += provider
                AppLog.i("Test provider '$provider' added and enabled")
            } catch (e: SecurityException) {
                AppLog.e("SecurityException adding test provider '$provider'", e)
                return PublishResult.Failure(NOT_SELECTED_MESSAGE, fatal = true, cause = e)
            } catch (e: IllegalArgumentException) {
                AppLog.w("Could not add test provider '$provider'", e)
                failures += "$provider: ${e.message}"
            }
        }
        if (LocationManager.GPS_PROVIDER !in activeProviders) {
            stop()
            return PublishResult.Failure(
                "GPS test provider unavailable: ${failures.joinToString("; ")}", fatal = true,
            )
        }
        return PublishResult.Success(activeProviders.joinToString("+"))
    }

    override suspend fun publish(fix: SimulatedFix): PublishResult {
        if (activeProviders.isEmpty()) return PublishResult.Failure("Test providers not initialized", fatal = true)
        val failed = mutableListOf<String>()
        for (provider in activeProviders) {
            try {
                locationManager.setTestProviderLocation(provider, LocationFactory.create(provider, fix))
            } catch (e: SecurityException) {
                // Typically: the user selected a different mock location app mid-simulation.
                AppLog.e("SecurityException injecting into '$provider'", e)
                return PublishResult.Failure(NOT_SELECTED_MESSAGE, fatal = true, cause = e)
            } catch (e: IllegalArgumentException) {
                // Provider was removed externally or the location was rejected as incomplete.
                AppLog.e("IllegalArgumentException injecting into '$provider'", e)
                if (provider == LocationManager.GPS_PROVIDER) {
                    return PublishResult.Failure("GPS test provider rejected location: ${e.message}", fatal = true, cause = e)
                }
                failed += provider
            }
        }
        val ok = activeProviders - failed.toSet()
        return PublishResult.Success(ok.joinToString("+") + if (failed.isEmpty()) "" else " (${failed.joinToString()} failed)")
    }

    override suspend fun stop() {
        for (provider in activeProviders.toList()) removeQuietly(provider)
        if (activeProviders.isNotEmpty()) AppLog.i("Removed test providers: $activeProviders")
        activeProviders.clear()
    }

    @SuppressLint("InlinedApi") // See comment in the API 29–30 branch.
    private fun addTestProvider(provider: String) {
        val isGps = provider == LocationManager.GPS_PROVIDER
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val builder = ProviderProperties.Builder()
                .setHasNetworkRequirement(!isGps)
                .setHasSatelliteRequirement(isGps)
                .setHasAltitudeSupport(true)
                .setHasSpeedSupport(true)
                .setHasBearingSupport(true)
            if (isGps) {
                builder.setPowerUsage(ProviderProperties.POWER_USAGE_HIGH).setAccuracy(ProviderProperties.ACCURACY_FINE)
            } else {
                builder.setPowerUsage(ProviderProperties.POWER_USAGE_LOW).setAccuracy(ProviderProperties.ACCURACY_COARSE)
            }
            val properties = builder.build()
            locationManager.addTestProvider(provider, properties)
        } else {
            // API 29–30: the only available overload (deprecated in API 31). ProviderProperties
            // constants are compile-time ints equal to the Criteria values (inlined; safe on 29).
            @Suppress("DEPRECATION")
            locationManager.addTestProvider(
                provider,
                /* requiresNetwork = */ !isGps,
                /* requiresSatellite = */ isGps,
                /* requiresCell = */ false,
                /* hasMonetaryCost = */ false,
                /* supportsAltitude = */ true,
                /* supportsSpeed = */ true,
                /* supportsBearing = */ true,
                /* powerRequirement = */ if (isGps) ProviderProperties.POWER_USAGE_HIGH else ProviderProperties.POWER_USAGE_LOW,
                /* accuracy = */ if (isGps) ProviderProperties.ACCURACY_FINE else ProviderProperties.ACCURACY_COARSE,
            )
        }
    }

    private fun removeQuietly(provider: String) {
        try {
            locationManager.removeTestProvider(provider)
        } catch (e: IllegalArgumentException) {
            // Not a test provider — nothing to remove.
        } catch (e: SecurityException) {
            AppLog.w("SecurityException removing test provider '$provider'", e)
        }
    }

    /**
     * Removes test providers left behind when this app's process was killed mid-simulation.
     * The platform keeps them registered, which freezes consumers on a stale mock fix and blocks
     * real GPS. Must only be called when no simulation is active.
     *
     * Note: on recent Android versions removeTestProvider() is a silent no-op when no test
     * provider exists, and there is no public API to ask whether one does, so this cannot
     * report whether anything was actually stale.
     */
    fun clearLeftoverTestProviders() {
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                locationManager.removeTestProvider(provider)
            } catch (e: IllegalArgumentException) {
                // Older releases throw when there is no such test provider: nothing to clear.
            } catch (e: SecurityException) {
                // Not the selected mock app: this app cannot own test providers, nothing to clear.
                return
            }
        }
        AppLog.i("Cleared any leftover test providers owned by this app (startup cleanup)")
    }

    companion object {
        const val NOT_SELECTED_MESSAGE =
            "GPS Walk Simulator is not selected as the mock location app. Open Developer options > " +
                "Select mock location app and choose GPS Walk Simulator."
    }
}
