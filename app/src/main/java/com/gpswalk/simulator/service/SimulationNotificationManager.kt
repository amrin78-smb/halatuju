package com.gpswalk.simulator.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.gpswalk.core.engine.SimulationSnapshot
import com.gpswalk.core.model.SimulationState
import com.gpswalk.simulator.MainActivity
import com.gpswalk.simulator.R
import com.gpswalk.simulator.util.Format

class SimulationNotificationManager(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Walking simulation", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while GPS Walk Simulator is injecting mock locations"
                setShowBadge(false)
            },
        )
    }

    fun build(s: SimulationSnapshot): Notification {
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = when (s.state) {
            SimulationState.RUNNING -> "Simulating walk – ${Format.speed(s.speedKmh)}"
            SimulationState.PAUSED -> "Simulation paused"
            else -> "GPS Walk Simulator – ${s.state.name}"
        }
        val text = "${s.routeName}: ${Format.distance(s.distanceTravelledMeters)} / " +
            "${Format.distance(s.totalDistanceMeters)} • ${Format.duration(s.movingElapsedMillis)}"
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_walk)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText("Mock location active")
            .setOngoing(s.state.isActive)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setProgress(1000, (s.progressFraction * 1000).toInt(), false)
        when (s.state) {
            SimulationState.RUNNING -> builder.addAction(0, "Pause", serviceIntent(WalkingSimulationService.ACTION_PAUSE))
            SimulationState.PAUSED -> builder.addAction(0, "Resume", serviceIntent(WalkingSimulationService.ACTION_RESUME))
            else -> Unit
        }
        if (s.state.isActive) builder.addAction(0, "Stop", serviceIntent(WalkingSimulationService.ACTION_STOP))
        return builder.build()
    }

    fun notify(s: SimulationSnapshot) {
        // POST_NOTIFICATIONS may be denied; the FGS still runs, the notification is just hidden.
        try {
            manager.notify(NOTIFICATION_ID, build(s))
        } catch (e: SecurityException) {
            com.gpswalk.simulator.util.AppLog.w("Notification update not permitted", e)
        }
    }

    private fun serviceIntent(action: String): PendingIntent = PendingIntent.getService(
        context, action.hashCode(),
        Intent(context, WalkingSimulationService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val CHANNEL_ID = "simulation"
        const val NOTIFICATION_ID = 1001
    }
}
