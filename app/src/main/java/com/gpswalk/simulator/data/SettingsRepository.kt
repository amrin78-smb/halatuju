package com.gpswalk.simulator.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gpswalk.core.model.MovementProfile
import com.gpswalk.core.model.SimulationState
import com.gpswalk.core.model.UpdateInterval
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal val Context.settingsStore by preferencesDataStore(name = "settings")

data class UserSettings(
    val speedKmh: Double = MovementProfile.NORMAL.speedKmh,
    val interval: UpdateInterval = UpdateInterval.DEFAULT,
    /** Also inject through Play services FLP mock mode (in addition to framework test providers). */
    val useFusedChannel: Boolean = true,
    val lastRouteName: String? = null,
) {
    val profile: MovementProfile
        get() = MovementProfile.PRESETS.firstOrNull { it.speedKmh == speedKmh } ?: MovementProfile.custom(speedKmh)
}

/**
 * Summary of the last simulation. Only aggregate statistics are stored — never a history of
 * positions.
 */
data class SimulationRecord(
    val state: SimulationState,
    val routeName: String,
    val distanceMeters: Double,
    val totalMeters: Double,
    val movingElapsedMillis: Long,
    val publishedCount: Long,
    val updatedAtMillis: Long,
    val message: String?,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val speed = doublePreferencesKey("speed_kmh")
        val interval = longPreferencesKey("interval_ms")
        val fused = booleanPreferencesKey("use_fused")
        val lastRoute = stringPreferencesKey("last_route")
        val recState = stringPreferencesKey("rec_state")
        val recRoute = stringPreferencesKey("rec_route")
        val recDistance = doublePreferencesKey("rec_distance")
        val recTotal = doublePreferencesKey("rec_total")
        val recElapsed = longPreferencesKey("rec_elapsed")
        val recCount = longPreferencesKey("rec_count")
        val recUpdated = longPreferencesKey("rec_updated")
        val recMessage = stringPreferencesKey("rec_message")
        val recordKeys = listOf(recState, recRoute, recDistance, recTotal, recElapsed, recCount, recUpdated, recMessage)
    }

    val settings: Flow<UserSettings> = context.settingsStore.data.map { p ->
        UserSettings(
            speedKmh = (p[Keys.speed] ?: MovementProfile.NORMAL.speedKmh)
                .coerceIn(MovementProfile.MIN_KMH, MovementProfile.MAX_KMH),
            interval = UpdateInterval.fromMillis(p[Keys.interval] ?: UpdateInterval.DEFAULT.millis),
            useFusedChannel = p[Keys.fused] ?: true,
            lastRouteName = p[Keys.lastRoute],
        )
    }

    val lastRecord: Flow<SimulationRecord?> = context.settingsStore.data.map(::readRecord)

    suspend fun current(): UserSettings = settings.first()
    suspend fun currentRecord(): SimulationRecord? = lastRecord.first()

    suspend fun setSpeed(kmh: Double) = context.settingsStore.edit { it[Keys.speed] = kmh }
    suspend fun setInterval(interval: UpdateInterval) = context.settingsStore.edit { it[Keys.interval] = interval.millis }
    suspend fun setUseFused(enabled: Boolean) = context.settingsStore.edit { it[Keys.fused] = enabled }
    suspend fun setLastRouteName(name: String) = context.settingsStore.edit { it[Keys.lastRoute] = name }

    suspend fun saveRecord(r: SimulationRecord) = context.settingsStore.edit { p ->
        p[Keys.recState] = r.state.name
        p[Keys.recRoute] = r.routeName
        p[Keys.recDistance] = r.distanceMeters
        p[Keys.recTotal] = r.totalMeters
        p[Keys.recElapsed] = r.movingElapsedMillis
        p[Keys.recCount] = r.publishedCount
        p[Keys.recUpdated] = r.updatedAtMillis
        if (r.message != null) p[Keys.recMessage] = r.message else p.remove(Keys.recMessage)
    }

    suspend fun clearRecords() = context.settingsStore.edit { p ->
        Keys.recordKeys.forEach { p.remove(it) }
        p.remove(Keys.lastRoute)
    }

    private fun readRecord(p: Preferences): SimulationRecord? {
        val state = p[Keys.recState]?.let { runCatching { SimulationState.valueOf(it) }.getOrNull() } ?: return null
        return SimulationRecord(
            state = state,
            routeName = p[Keys.recRoute] ?: "",
            distanceMeters = p[Keys.recDistance] ?: 0.0,
            totalMeters = p[Keys.recTotal] ?: 0.0,
            movingElapsedMillis = p[Keys.recElapsed] ?: 0,
            publishedCount = p[Keys.recCount] ?: 0,
            updatedAtMillis = p[Keys.recUpdated] ?: 0,
            message = p[Keys.recMessage],
        )
    }
}
