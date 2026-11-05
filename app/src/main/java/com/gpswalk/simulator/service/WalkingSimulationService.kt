package com.gpswalk.simulator.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.gpswalk.core.model.SimulationState
import com.gpswalk.simulator.gpsWalkApp
import com.gpswalk.simulator.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground service (type "location") that keeps the process alive while a simulation is
 * active and shows the ongoing notification. Simulation logic lives in [SimulationController].
 *
 * Android allows only one instance of a Service; repeated ACTION_START while active is ignored,
 * so duplicate simulations cannot be created. START_NOT_STICKY ensures the system never
 * restarts a simulation on its own after the process is killed.
 */
class WalkingSimulationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notifications: SimulationNotificationManager
    private var inForeground = false
    private var observing = false

    private val controller get() = gpsWalkApp.controller

    override fun onCreate() {
        super.onCreate()
        notifications = SimulationNotificationManager(this).also { it.ensureChannel() }
        AppLog.i("Service created")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_PAUSE -> controller.pause()
            ACTION_RESUME -> controller.resume()
            ACTION_STOP -> {
                controller.stop()
                shutdown()
            }
            else -> {
                // Null intent = system restart attempt; we never auto-restart a simulation.
                AppLog.w("Service started without a recognised action (${intent?.action}); stopping")
                if (!controller.state.isActive) shutdown()
            }
        }
        return START_NOT_STICKY
    }

    private fun handleStart() {
        if (controller.state.isActive) {
            AppLog.w("Start ignored: simulation already active")
            return
        }
        if (controller.state != SimulationState.READY) {
            AppLog.w("Start ignored: no route loaded (state=${controller.state})")
            shutdown()
            return
        }
        // startForeground must be called promptly after startForegroundService().
        try {
            ServiceCompat.startForeground(
                this,
                SimulationNotificationManager.NOTIFICATION_ID,
                notifications.build(controller.snapshot.value),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
            inForeground = true
        } catch (e: Exception) {
            val reason = when {
                e is SecurityException ->
                    "Foreground service could not start: location permission is required for a location-type foreground service."
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException ->
                    "Foreground service start not allowed (app was not in the foreground)."
                else -> "Foreground service failed to start: ${e.message}"
            }
            AppLog.e(reason, e)
            controller.reportFailure(reason)
            shutdown()
            return
        }
        if (!controller.startRunner()) {
            controller.reportFailure("Simulation could not be started (state=${controller.state})")
            shutdown()
            return
        }
        observe()
    }

    private fun observe() {
        if (observing) return
        observing = true
        scope.launch {
            controller.snapshot
                .map { Triple(it.state, (it.distanceTravelledMeters).toLong(), it.speedKmh) }
                .distinctUntilChanged()
                .collect {
                    val s = controller.snapshot.value
                    if (s.state.isActive) {
                        notifications.notify(s)
                    } else if (s.state.isTerminal) {
                        AppLog.i("Simulation ended with state ${s.state}; stopping service")
                        shutdown()
                    }
                }
        }
    }

    private fun shutdown() {
        if (inForeground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            inForeground = false
        }
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        // Destroyed while still active => the system tore us down (the user path stops first).
        if (controller.state.isActive) {
            controller.reportFailure("The simulation service was terminated by the system. Simulation stopped.")
        }
        AppLog.i("Service destroyed")
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.gpswalk.simulator.action.START"
        const val ACTION_PAUSE = "com.gpswalk.simulator.action.PAUSE"
        const val ACTION_RESUME = "com.gpswalk.simulator.action.RESUME"
        const val ACTION_STOP = "com.gpswalk.simulator.action.STOP"

        /** Must be called from a user action while the app is in the foreground. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, WalkingSimulationService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, WalkingSimulationService::class.java).setAction(ACTION_STOP))
        }
    }
}
