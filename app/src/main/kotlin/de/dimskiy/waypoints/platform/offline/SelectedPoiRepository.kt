package de.dimskiy.waypoints.platform.offline

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import de.dimskiy.waypoints.platform.di.BaseModule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

data class SelectedPoi(val token: String, val name: String, val position: PoiPosition, val categories: Set<String>)

/** Independent of packs and Favorites: removing a region must not discard a selected destination. */
@Singleton
class SelectedPoiRepository @Inject constructor(
    private val preferences: DataStore<Preferences>,
    @BaseModule.DispatcherIO private val io: CoroutineDispatcher,
) {
    private val key = stringPreferencesKey("selectedRidePoi")
    val selected = preferences.data.map { decode(it[key]) }.distinctUntilChanged()

    suspend fun select(name: String, position: PoiPosition, categories: Set<String>) = withContext(io) {
        val target = SelectedPoi(UUID.randomUUID().toString(), name, position, categories)
        val value = JsonObject().apply {
            addProperty("token", target.token)
            addProperty("name", target.name)
            addProperty("latitude", position.latitude)
            addProperty("longitude", position.longitude)
            add("categories", JsonArray().apply { categories.sorted().forEach { add(it) } })
        }.toString()
        preferences.edit { it[key] = value }
    }

    /** Atomic comparison also protects a reselection of the same POI from an old arrival check. */
    suspend fun clearIfSelected(token: String) = withContext(io) {
        preferences.edit { if (decode(it[key])?.token == token) it.remove(key) }
        Unit
    }

    private fun decode(value: String?): SelectedPoi? = try {
        value?.let {
            val data = JsonParser.parseString(it).asJsonObject
            SelectedPoi(data.get("token").asString.also { require(it.isNotBlank()) },
                data.get("name").asString,
                PoiPosition(data.get("latitude").asDouble, data.get("longitude").asDouble),
                data.getAsJsonArray("categories").map { it.asString }.toSet())
        }
    } catch (_: Exception) { null }
}

/** Two distinct, fresh fixes inside 50m confirm arrival. A missing/outside fix resets confirmation. */
internal class PoiArrivalTracker {
    private var token: String? = null
    private var insideFix: Long? = null
    fun arrived(targetToken: String, distanceMetres: Double?, fixTime: Long?): Boolean {
        if (token != targetToken) { token = targetToken; insideFix = null }
        if (distanceMetres == null || !distanceMetres.isFinite() || distanceMetres < 0 ||
            distanceMetres > 50 || fixTime == null) {
            insideFix = null
            return false
        }
        val previous = insideFix
        if (previous == null || fixTime < previous) { insideFix = fixTime; return false }
        return fixTime > previous
    }
}

internal fun poiDistanceMetres(a: PoiPosition, b: PoiPosition): Double {
    val lat = Math.toRadians(b.latitude - a.latitude)
    val lon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a.latitude)) *
        cos(Math.toRadians(b.latitude)) * sin(lon / 2).pow(2)
    return 6_371_000 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
}
