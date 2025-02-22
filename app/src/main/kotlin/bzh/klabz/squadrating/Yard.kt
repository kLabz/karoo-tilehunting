package bzh.klabz.squadrating

import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfConversion
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sinh
import bzh.klabz.squadrating.Cluster

/**
 * The Cluster class represents a collection of contiguous squadrats.
 * The outlines are computed by collecting the boundary edges that do not have neighbouring squadrats.
 * Then each boundary segment is inset toward the squadrat interior by a given offset.
 * Finally, the inset segments are chained into closed polylines.
 */
open class YardCluster {
    val squadrats = mutableSetOf<Squadrat>()

    fun getSize(): Int {
        return squadrats.size
    }
}

fun calcYard(squadrats: Set<Squadrat>): Int {
    val clusters = mutableListOf<YardCluster>()
    val remainingSquadrats = squadrats.toMutableSet()

    while (remainingSquadrats.isNotEmpty()) {
        val seedSquadrat = remainingSquadrats.first()
        remainingSquadrats.remove(seedSquadrat)
        if (!seedSquadrat.isSurrounded(squadrats)) continue

        val newYardCluster = YardCluster()
        val squadratsToAdd = mutableSetOf(seedSquadrat)


        var squadratsAdded = true
        while (squadratsAdded) {
            squadratsAdded = false
            val newlyAddedSquadrats = mutableSetOf<Squadrat>()

            for (squadrat in squadratsToAdd) {
                if (newYardCluster.squadrats.add(squadrat)) {
                    newlyAddedSquadrats.add(squadrat)
                }
            }

            for (squadrat in newlyAddedSquadrats) {
                val neighbours = remainingSquadrats.filter { it.isNeighbour(squadrat) && it.isSurrounded(squadrats) }
                squadratsToAdd.addAll(neighbours)
                remainingSquadrats.removeAll(neighbours)
                if (neighbours.isNotEmpty()) {
                    squadratsAdded = true
                }
            }
        }
        clusters.add(newYardCluster)
    }

    var max = 0
    for (cluster in clusters) {
        val size = cluster.getSize()
        if (size > max) max = size
    }

    return max
}
