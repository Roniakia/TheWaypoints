package de.dimskiy.waypoints.platform.offline

import com.google.gson.JsonArray
import java.util.Locale
import kotlin.math.*

fun CatalogueRegion.matches(query: String): Boolean {
    val haystack = PoiSearchText.normalize("$searchName $name $country $continent")
    return PoiSearchText.normalize(query).split(' ').filter(String::isNotEmpty).all { it in haystack } &&
        PoiSearchText.matchesNative("$name $country $continent", PoiSearchText.nativeWords(query))
}

fun recommendedRegionIds(regions: List<CatalogueRegion>, position: PoiPosition?, countryCode: String): List<String> {
    if (position == null) {
        val country = Locale("", countryCode).getDisplayCountry(Locale.ENGLISH)
        return regions.filter { it.country.equals(country, ignoreCase = true) }.map { it.descriptor.id }
    }
    return regions.sortedWith(compareBy<CatalogueRegion> { boundaryDistance(it, position) }.thenBy { it.descriptor.id })
        .take(3).map { it.descriptor.id }
}

fun CatalogueRegion.contains(position: PoiPosition): Boolean {
    val coordinates = geometry.getAsJsonArray("coordinates")
    val polygons = if (geometry.get("type").asString == "Polygon") listOf(coordinates)
        else coordinates.map { it.asJsonArray }
    return polygons.any { polygon -> polygon.size() > 0 &&
        inside(polygon[0].asJsonArray, position) && polygon.drop(1).none { inside(it.asJsonArray, position) } }
}

fun OfflineUiState.needsRegionDownload(): Boolean {
    if (!ready || page != OfflinePage.POIS) return false
    if (installed.isEmpty()) return true
    val point = position ?: return false
    val containing = catalogue?.regions.orEmpty().filter { it.contains(point) }
    return containing.isNotEmpty() && containing.none { region -> installed.any { it.id == region.descriptor.id } }
}

private fun boundaryDistance(region: CatalogueRegion, position: PoiPosition): Double {
    val coordinates = region.geometry.getAsJsonArray("coordinates")
    val polygons = if (region.geometry.get("type").asString == "Polygon") listOf(coordinates)
        else coordinates.map { it.asJsonArray }
    var best = Double.POSITIVE_INFINITY
    for (polygon in polygons) {
        if (polygon.size() == 0) continue
        if (inside(polygon[0].asJsonArray, position) && polygon.drop(1).none { inside(it.asJsonArray, position) }) return 0.0
        for (ring in polygon) {
            val points = ring.asJsonArray
            for (i in 1 until points.size()) {
                fun xy(point: com.google.gson.JsonElement): Pair<Double, Double> {
                    val p = point.asJsonArray
                    val lon = ((p[0].asDouble - position.longitude + 540) % 360) - 180
                    return lon * cos(Math.toRadians(position.latitude)) to p[1].asDouble - position.latitude
                }
                val (ax, ay) = xy(points[i - 1]); val (bx, by) = xy(points[i])
                val dx = bx - ax; val dy = by - ay
                val length = dx * dx + dy * dy
                val t = if (length == 0.0) 0.0 else (-(ax * dx + ay * dy) / length).coerceIn(0.0, 1.0)
                best = min(best, hypot(ax + t * dx, ay + t * dy))
            }
        }
    }
    return best // Local equirectangular boundary distance, used only for recommendations.
}

private fun inside(ring: JsonArray, p: PoiPosition): Boolean {
    var result = false
    var previous = ring.size() - 1
    for (i in 0 until ring.size()) {
        val a = ring[i].asJsonArray; val b = ring[previous].asJsonArray
        val ax = a[0].asDouble; val ay = a[1].asDouble
        val bx = b[0].asDouble; val by = b[1].asDouble
        if ((ay > p.latitude) != (by > p.latitude) &&
            p.longitude < (bx - ax) * (p.latitude - ay) / (by - ay) + ax) result = !result
        previous = i
    }
    return result
}

object PoiTypes {
    val labels = linkedMapOf(
        "water.drinking_water" to "Drinking water", "toilets.toilets" to "Toilets", "toilets.showers" to "Showers",
        "bicycle.repair" to "Bicycle repair", "bicycle.pump" to "Bicycle pump", "bicycle.shop" to "Bicycle shop",
        "bicycle.tubes" to "Spare tubes", "bicycle.wash" to "Bicycle wash", "bicycle.parking" to "Bicycle parking",
        "food.cafe" to "Cafe", "food.restaurant" to "Restaurant", "food.fast_food" to "Fast food",
        "food.bakery" to "Bakery", "food.ice_cream" to "Ice cream", "food.pub" to "Pub",
        "supplies.supermarket" to "Supermarket", "supplies.convenience" to "Convenience store", "supplies.vending" to "Food/drink vending",
        "rest.shelter" to "Shelter", "rest.picnic" to "Picnic area", "rest.bench" to "Bench",
        "sights.viewpoint" to "Viewpoint", "sights.attraction" to "Attraction", "nature.peak" to "Peak", "nature.swimming" to "Swimming",
        "stay.camp" to "Camping", "stay.lodging" to "Lodging", "health.pharmacy" to "Pharmacy",
        "health.medical" to "Medical help", "health.aed" to "Defibrillator", "charging.ebike" to "E-bike charging",
        "charging.device" to "Device charging", "transport.station" to "Station", "transport.rental" to "Bicycle rental")
    val groups = mapOf("water" to "Water", "toilets" to "Toilets & showers", "bicycle" to "Bicycle services",
        "food" to "Food & drink", "supplies" to "Supplies", "rest" to "Rest stops", "sights" to "Sights",
        "nature" to "Nature", "stay" to "Places to stay", "health" to "Health", "charging" to "Charging", "transport" to "Transport")
    val defaults = labels.keys - setOf("rest.bench", "bicycle.parking")
    fun label(id: String) = labels[id] ?: id.substringAfter('.').replace('_', ' ')
    fun aliases(id: String) = label(id) + " " + when (id) {
        "food.cafe" -> "coffee espresso"
        "toilets.toilets" -> "wc restroom bathroom"
        "bicycle.repair" -> "bike repair tools workshop"
        "bicycle.pump" -> "bike pump air tyre tire"
        "water.drinking_water" -> "water tap refill"
        else -> id.replace('.', ' ').replace('_', ' ')
    }
}
