package de.dimskiy.waypoints.platform.ui.screens

import de.dimskiy.waypoints.R
import de.dimskiy.waypoints.platform.offline.PoiTypes

/** Catalogue order gives multi-category POIs a stable icon across refreshes. */
internal fun poiCategoryIcon(categories: Set<String>): Int {
    val type = PoiTypes.labels.keys.firstOrNull { it in categories }
    if (type == "food.cafe") return R.drawable.ic_poi_cafe
    return when (type?.substringBefore('.')) {
        "supplies" -> R.drawable.ic_poi_store
        "toilets" -> R.drawable.ic_poi_toilets
        "water" -> R.drawable.ic_poi_water
        "bicycle" -> R.drawable.ic_poi_bicycle
        "food" -> R.drawable.ic_poi_food
        "rest" -> R.drawable.ic_poi_rest
        "sights" -> R.drawable.ic_poi_viewpoint
        "nature" -> R.drawable.ic_poi_nature
        "stay" -> R.drawable.ic_poi_lodging
        "health" -> R.drawable.ic_poi_health
        "charging" -> R.drawable.ic_poi_charging
        "transport" -> R.drawable.ic_poi_transport
        else -> R.drawable.ic_poi_pin
    }
}
