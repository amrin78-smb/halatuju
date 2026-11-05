package com.gpswalk.simulator.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gpswalk.core.model.Coordinate
import com.gpswalk.core.model.Route
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.routeStore by preferencesDataStore(name = "routes")

/**
 * User-saved routes, stored as a small JSON document in DataStore (a handful of routes does
 * not justify Room). Built-in presets are not stored.
 */
class RouteRepository(private val context: Context) {
    private val key = stringPreferencesKey("routes_json")

    val savedRoutes: Flow<List<Route>> = context.routeStore.data.map { p ->
        p[key]?.let { runCatching { RouteJson.decode(it) }.getOrDefault(emptyList()) } ?: emptyList()
    }

    /** Saves or replaces (by name) a route. */
    suspend fun save(route: Route) = context.routeStore.edit { p ->
        val existing = p[key]?.let { runCatching { RouteJson.decode(it) }.getOrNull() } ?: emptyList()
        p[key] = RouteJson.encode(existing.filterNot { it.name == route.name } + route)
    }

    suspend fun delete(name: String) = context.routeStore.edit { p ->
        val existing = p[key]?.let { runCatching { RouteJson.decode(it) }.getOrNull() } ?: emptyList()
        p[key] = RouteJson.encode(existing.filterNot { it.name == name })
    }

    suspend fun deleteAll() = context.routeStore.edit { it.remove(key) }
}

object RouteJson {
    fun encode(routes: List<Route>): String = JSONArray().apply {
        routes.forEach { r ->
            put(JSONObject().apply {
                put("name", r.name)
                put("waypoints", JSONArray().apply {
                    r.waypoints.forEach { c -> put(JSONArray().put(c.latitude).put(c.longitude).put(c.altitudeMeters)) }
                })
            })
        }
    }.toString()

    fun decode(json: String): List<Route> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val wps = o.getJSONArray("waypoints")
            Route(
                name = o.getString("name"),
                waypoints = (0 until wps.length()).map { j ->
                    val w = wps.getJSONArray(j)
                    Coordinate(w.getDouble(0), w.getDouble(1), w.optDouble(2, 0.0))
                },
            )
        }
    }
}
