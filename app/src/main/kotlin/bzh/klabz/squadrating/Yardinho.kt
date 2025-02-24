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
open class YardinhoCluster {
    val squadratinhos = mutableSetOf<Squadratinho>()

    fun getSize(): Int {
        return squadratinhos.size
    }
}

fun calcYardinho(squadratinhos: Set<Squadratinho>): Int {
    val clusters = mutableListOf<YardinhoCluster>()
    val remainingSquadratinhos = squadratinhos.toMutableSet()

    while (remainingSquadratinhos.isNotEmpty()) {
        val seedSquadratinho = remainingSquadratinhos.first()
        remainingSquadratinhos.remove(seedSquadratinho)
        if (!seedSquadratinho.isSurrounded(squadratinhos)) continue

        val newYardinhoCluster = YardinhoCluster()
        val squadratinhosToAdd = mutableSetOf(seedSquadratinho)


        var squadratinhosAdded = true
        while (squadratinhosAdded) {
            squadratinhosAdded = false
            val newlyAddedSquadratinhos = mutableSetOf<Squadratinho>()

            for (squadratinho in squadratinhosToAdd) {
                if (newYardinhoCluster.squadratinhos.add(squadratinho)) {
                    newlyAddedSquadratinhos.add(squadratinho)
                }
            }

            for (squadratinho in newlyAddedSquadratinhos) {
                val neighbours = remainingSquadratinhos.filter { it.isNeighbour(squadratinho) && it.isSurrounded(squadratinhos) }
                squadratinhosToAdd.addAll(neighbours)
                remainingSquadratinhos.removeAll(neighbours)
                if (neighbours.isNotEmpty()) {
                    squadratinhosAdded = true
                }
            }
        }
        clusters.add(newYardinhoCluster)
    }

    var max = 0
    for (cluster in clusters) {
        val size = cluster.getSize()
        if (size > max) max = size
    }

    return max
}
