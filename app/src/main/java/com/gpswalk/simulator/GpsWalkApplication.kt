package com.gpswalk.simulator

import android.app.Application
import com.gpswalk.simulator.data.RouteRepository
import com.gpswalk.simulator.data.SettingsRepository
import com.gpswalk.simulator.service.SimulationController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container; the object graph is too small to justify a DI framework. */
class GpsWalkApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settingsRepository by lazy { SettingsRepository(this) }
    val routeRepository by lazy { RouteRepository(this) }
    val controller by lazy { SimulationController(this, settingsRepository, appScope) }
}

val android.content.Context.gpsWalkApp: GpsWalkApplication get() = applicationContext as GpsWalkApplication
