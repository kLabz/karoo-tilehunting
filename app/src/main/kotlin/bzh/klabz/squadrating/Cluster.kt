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

@Serializable
data class Squadrat(val x: Int, val y: Int) {
    fun isNeighbour(squadrat: Squadrat): Boolean {
        return (this.x == squadrat.x && (this.y == squadrat.y + 1 || this.y == squadrat.y - 1)) ||
                (this.y == squadrat.y && (this.x == squadrat.x + 1 || this.x == squadrat.x - 1))
    }

    fun isSurrounded(squadrats: Set<Squadrat>): Boolean {
        return squadrats.contains(Squadrat(x + 1, y)) &&
                squadrats.contains(Squadrat(x - 1, y)) &&
                squadrats.contains(Squadrat(x, y + 1)) &&
                squadrats.contains(Squadrat(x, y - 1))
    }

    fun getLon(): Double {
        val zoom: Int = 14
        val n = 2.0.pow(zoom)
        return x / n * 360.0 - 180.0
    }

    fun getLat(): Double {
        val zoom: Int = 14
        val n = 2.0.pow(zoom)
        val latRad = atan(sinh(Math.PI * (1 - 2 * y / n)))
        return Math.toDegrees(latRad)
    }
}

@Serializable
data class Squadratinho(val x: Int, val y: Int) {
    fun isNeighbour(squadrat: Squadratinho): Boolean {
        return (this.x == squadrat.x && (this.y == squadrat.y + 1 || this.y == squadrat.y - 1)) ||
                (this.y == squadrat.y && (this.x == squadrat.x + 1 || this.x == squadrat.x - 1))
    }

    fun isSurrounded(squadratinhos: Set<Squadratinho>): Boolean {
        return squadratinhos.contains(Squadratinho(x + 1, y)) &&
                squadratinhos.contains(Squadratinho(x - 1, y)) &&
                squadratinhos.contains(Squadratinho(x, y + 1)) &&
                squadratinhos.contains(Squadratinho(x, y - 1))
    }

    fun getLon(): Double {
        val zoom: Int = 17
        val n = 2.0.pow(zoom)
        return x / n * 360.0 - 180.0
    }

    fun getLat(): Double {
        val zoom: Int = 17
        val n = 2.0.pow(zoom)
        val latRad = atan(sinh(Math.PI * (1 - 2 * y / n)))
        return Math.toDegrees(latRad)
    }
}

enum class CurrentCorner {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

    // Returns the actual (unmodified) coordinates for the given squadrat corner.
    fun getCoords(squadrat: Squadrat): Point {
        return when (this) {
            TOP_LEFT -> Point.fromLngLat(squadrat.getLon(), squadrat.getLat())
            TOP_RIGHT -> Point.fromLngLat(Squadrat(squadrat.x + 1, squadrat.y).getLon(), squadrat.getLat())
            BOTTOM_LEFT -> Point.fromLngLat(squadrat.getLon(), Squadrat(squadrat.x, squadrat.y + 1).getLat())
            BOTTOM_RIGHT -> Point.fromLngLat(Squadrat(squadrat.x + 1, squadrat.y + 1).getLon(), Squadrat(squadrat.x, squadrat.y + 1).getLat())
        }
    }

    // Returns the actual (unmodified) coordinates for the given squadratinho corner.
    fun getCoords(squadratinho: Squadratinho): Point {
        return when (this) {
            TOP_LEFT -> Point.fromLngLat(squadratinho.getLon(), squadratinho.getLat())
            TOP_RIGHT -> Point.fromLngLat(Squadratinho(squadratinho.x + 1, squadratinho.y).getLon(), squadratinho.getLat())
            BOTTOM_LEFT -> Point.fromLngLat(squadratinho.getLon(), Squadratinho(squadratinho.x, squadratinho.y + 1).getLat())
            BOTTOM_RIGHT -> Point.fromLngLat(Squadratinho(squadratinho.x + 1, squadratinho.y + 1).getLon(), Squadratinho(squadratinho.x, squadratinho.y + 1).getLat())
        }
    }
}

/**
 * The Cluster class represents a collection of contiguous squadrats.
 * The outlines are computed by collecting the boundary edges that do not have neighbouring squadrats.
 * Then each boundary segment is inset toward the squadrat interior by a given offset.
 * Finally, the inset segments are chained into closed polylines.
 */
open class Cluster {
    val squadrats = mutableSetOf<Squadrat>()

    /**
     * Computes the cluster outlines. Each outline is a closed polyline represented as a LineString.
     *
     * The insetOffset is provided in meters and will be converted to degrees.
     */
    fun getPolyline(insetOffset: Double = 50.0): List<LineString> {
        if (squadrats.isEmpty()) return emptyList()

        val insetDegrees = TurfConversion.lengthToDegrees(insetOffset, TurfConstants.UNIT_METERS)
        val segments = mutableListOf<Pair<Point, Point>>()

        for (squadrat in squadrats) {
            val topLeft = CurrentCorner.TOP_LEFT.getCoords(squadrat)
            val topRight = CurrentCorner.TOP_RIGHT.getCoords(squadrat)
            val bottomLeft = CurrentCorner.BOTTOM_LEFT.getCoords(squadrat)
            val bottomRight = CurrentCorner.BOTTOM_RIGHT.getCoords(squadrat)

            // Calculate corner points with appropriate insets
            val noNorth = !hasNorthNeighbor(squadrat)
            val noEast = !hasEastNeighbor(squadrat)
            val noSouth = !hasSouthNeighbor(squadrat)
            val noWest = !hasWestNeighbor(squadrat)

            // Calculate inset points considering both dimensions
            val inTopLeft = Point.fromLngLat(
                topLeft.longitude() + (if (noWest) insetDegrees else 0.0),
                topLeft.latitude() - (if (noNorth) insetDegrees else 0.0)
            )
            val inTopRight = Point.fromLngLat(
                topRight.longitude() - (if (noEast) insetDegrees else 0.0),
                topRight.latitude() - (if (noNorth) insetDegrees else 0.0)
            )
            val inBottomLeft = Point.fromLngLat(
                bottomLeft.longitude() + (if (noWest) insetDegrees else 0.0),
                bottomLeft.latitude() + (if (noSouth) insetDegrees else 0.0)
            )
            val inBottomRight = Point.fromLngLat(
                bottomRight.longitude() - (if (noEast) insetDegrees else 0.0),
                bottomRight.latitude() + (if (noSouth) insetDegrees else 0.0)
            )

            // Add segments for exposed edges
            if (noNorth) segments.add(inTopLeft to inTopRight)
            if (noEast) segments.add(inTopRight to inBottomRight)
            if (noSouth) segments.add(inBottomLeft to inBottomRight)
            if (noWest) segments.add(inTopLeft to inBottomLeft)
        }

        val polylines = chainSegments(segments)
        return polylines.map { pts -> LineString.fromLngLats(pts) }
    }

    // Check for neighbouring squadrats in each direction using the squadrat grid.
    private fun hasNorthNeighbor(squadrat: Squadrat) = squadrats.any { it.x == squadrat.x && it.y == squadrat.y - 1 }
    private fun hasEastNeighbor(squadrat: Squadrat) = squadrats.any { it.x == squadrat.x + 1 && it.y == squadrat.y }
    private fun hasSouthNeighbor(squadrat: Squadrat) = squadrats.any { it.x == squadrat.x && it.y == squadrat.y + 1 }
    private fun hasWestNeighbor(squadrat: Squadrat) = squadrats.any { it.x == squadrat.x - 1 && it.y == squadrat.y }

    private fun chainSegments(segments: List<Pair<Point, Point>>): List<List<Point>> {
        if (segments.isEmpty()) return emptyList()

        val remaining = segments.toMutableList()
        val polylines = mutableListOf<MutableList<Point>>()

        while (remaining.isNotEmpty()) {
            val currentPolyline = mutableListOf<Point>()
            val firstSeg = remaining.removeAt(0)
            currentPolyline.add(firstSeg.first)
            currentPolyline.add(firstSeg.second)

            var isClosed = false
            var changed = true
            while (changed && !isClosed) {
                changed = false
                for (i in remaining.indices.reversed()) {
                    val seg = remaining[i]
                    val start = currentPolyline.first()
                    val end = currentPolyline.last()

                    when {
                        pointsEqual(seg.first, end) -> {
                            currentPolyline.add(seg.second)
                            remaining.removeAt(i)
                            changed = true
                        }
                        pointsEqual(seg.second, end) -> {
                            currentPolyline.add(seg.first)
                            remaining.removeAt(i)
                            changed = true
                        }
                        pointsEqual(seg.first, start) -> {
                            currentPolyline.add(0, seg.second)
                            remaining.removeAt(i)
                            changed = true
                        }
                        pointsEqual(seg.second, start) -> {
                            currentPolyline.add(0, seg.first)
                            remaining.removeAt(i)
                            changed = true
                        }
                    }
                    if (pointsEqual(currentPolyline.first(), currentPolyline.last())) {
                        isClosed = true
                        break
                    }
                }
            }
            //if (!pointsEqual(currentPolyline.first(), currentPolyline.last())) {
            //    currentPolyline.add(currentPolyline.first())
            //}
            polylines.add(currentPolyline)
        }
        return polylines
    }

    // Checks if two points are effectively equal using a small epsilon.
    private fun pointsEqual(p1: Point, p2: Point, epsilon: Double = 1e-6): Boolean {
        return abs(p1.longitude() - p2.longitude()) < epsilon &&
                abs(p1.latitude() - p2.latitude()) < epsilon
    }

    /**
     * Returns a list of LineString objects representing the grid lines between adjacent squadrats in the cluster.
     * This method creates one line per shared edge and merges collinear adjacent segments.
     */
    fun getGridPolylines(): List<LineString> {
        // Temporary data class to hold segment info for merging.
        data class Segment(val fixed: Double, var start: Double, var end: Double)

        val verticalSegments = mutableListOf<Segment>()
        val horizontalSegments = mutableListOf<Segment>()

        // For each squadrat, check right and bottom neighbours to draw an internal edge.
        for (squadrat in squadrats) {
            // Vertical shared edge.
            if (squadrats.contains(Squadrat(squadrat.x + 1, squadrat.y))) {
                val top = CurrentCorner.TOP_RIGHT.getCoords(squadrat).latitude()
                val bottom = CurrentCorner.BOTTOM_RIGHT.getCoords(squadrat).latitude()
                val lng = CurrentCorner.TOP_RIGHT.getCoords(squadrat).longitude()
                verticalSegments.add(Segment(lng, min(top, bottom), max(top, bottom)))
            }
            // Horizontal shared edge.
            if (squadrats.contains(Squadrat(squadrat.x, squadrat.y + 1))) {
                val left = CurrentCorner.BOTTOM_LEFT.getCoords(squadrat).longitude()
                val right = CurrentCorner.BOTTOM_RIGHT.getCoords(squadrat).longitude()
                val lat = CurrentCorner.BOTTOM_LEFT.getCoords(squadrat).latitude()
                horizontalSegments.add(Segment(lat, min(left, right), max(left, right)))
            }
        }

        // Merge contiguous vertical segments.
        val mergedVertical = verticalSegments.groupBy { it.fixed }.flatMap { (_, segments) ->
            segments.sortedBy { it.start }.fold(mutableListOf<Segment>()) { acc, seg ->
                if (acc.isEmpty()) {
                    acc.add(seg)
                } else {
                    val last = acc.last()
                    if (abs(seg.start - last.end) < 1e-6 || seg.start <= last.end) {
                        last.end = max(last.end, seg.end)
                    } else {
                        acc.add(seg)
                    }
                }
                acc
            }
        }

        // Merge contiguous horizontal segments.
        val mergedHorizontal = horizontalSegments.groupBy { it.fixed }.flatMap { (_, segments) ->
            segments.sortedBy { it.start }.fold(mutableListOf<Segment>()) { acc, seg ->
                if (acc.isEmpty()) {
                    acc.add(seg)
                } else {
                    val last = acc.last()
                    if (abs(seg.start - last.end) < 1e-6 || seg.start <= last.end) {
                        last.end = max(last.end, seg.end)
                    } else {
                        acc.add(seg)
                    }
                }
                acc
            }
        }

        val gridLines = mutableListOf<LineString>()

        // Vertical segments: fixed value is longitude, start/end represent latitude.
        for (seg in mergedVertical) {
            val p1 = Point.fromLngLat(seg.fixed, seg.start)
            val p2 = Point.fromLngLat(seg.fixed, seg.end)
            gridLines.add(LineString.fromLngLats(listOf(p1, p2)))
        }

        // Horizontal segments: fixed value is latitude, start/end represent longitude.
        for (seg in mergedHorizontal) {
            val p1 = Point.fromLngLat(seg.start, seg.fixed)
            val p2 = Point.fromLngLat(seg.end, seg.fixed)
            gridLines.add(LineString.fromLngLats(listOf(p1, p2)))
        }
        return gridLines
    }
}
open class ClusterSquadratinho {
    val squadratinhos = mutableSetOf<Squadratinho>()

    /**
     * Computes the cluster outlines. Each outline is a closed polyline represented as a LineString.
     *
     * The insetOffset is provided in meters and will be converted to degrees.
     */
    fun getPolyline(insetOffset: Double = 50.0): List<LineString> {
        if (squadratinhos.isEmpty()) return emptyList()

        val insetDegrees = TurfConversion.lengthToDegrees(insetOffset, TurfConstants.UNIT_METERS)
        val segments = mutableListOf<Pair<Point, Point>>()

        for (squadratinho in squadratinhos) {
            val topLeft = CurrentCorner.TOP_LEFT.getCoords(squadratinho)
            val topRight = CurrentCorner.TOP_RIGHT.getCoords(squadratinho)
            val bottomLeft = CurrentCorner.BOTTOM_LEFT.getCoords(squadratinho)
            val bottomRight = CurrentCorner.BOTTOM_RIGHT.getCoords(squadratinho)

            // Calculate corner points with appropriate insets
            val noNorth = !hasNorthNeighbor(squadratinho)
            val noEast = !hasEastNeighbor(squadratinho)
            val noSouth = !hasSouthNeighbor(squadratinho)
            val noWest = !hasWestNeighbor(squadratinho)

            // Calculate inset points considering both dimensions
            val inTopLeft = Point.fromLngLat(
                topLeft.longitude() + (if (noWest) insetDegrees else 0.0),
                topLeft.latitude() - (if (noNorth) insetDegrees else 0.0)
            )
            val inTopRight = Point.fromLngLat(
                topRight.longitude() - (if (noEast) insetDegrees else 0.0),
                topRight.latitude() - (if (noNorth) insetDegrees else 0.0)
            )
            val inBottomLeft = Point.fromLngLat(
                bottomLeft.longitude() + (if (noWest) insetDegrees else 0.0),
                bottomLeft.latitude() + (if (noSouth) insetDegrees else 0.0)
            )
            val inBottomRight = Point.fromLngLat(
                bottomRight.longitude() - (if (noEast) insetDegrees else 0.0),
                bottomRight.latitude() + (if (noSouth) insetDegrees else 0.0)
            )

            // Add segments for exposed edges
            if (noNorth) segments.add(inTopLeft to inTopRight)
            if (noEast) segments.add(inTopRight to inBottomRight)
            if (noSouth) segments.add(inBottomLeft to inBottomRight)
            if (noWest) segments.add(inTopLeft to inBottomLeft)
        }

        val polylines = chainSegments(segments)
        return polylines.map { pts -> LineString.fromLngLats(pts) }
    }

    // Check for neighbouring squadrats in each direction using the squadrat grid.
    private fun hasNorthNeighbor(squadratinho: Squadratinho) = squadratinhos.any { it.x == squadratinho.x && it.y == squadratinho.y - 1 }
    private fun hasEastNeighbor(squadratinho: Squadratinho) = squadratinhos.any { it.x == squadratinho.x + 1 && it.y == squadratinho.y }
    private fun hasSouthNeighbor(squadratinho: Squadratinho) = squadratinhos.any { it.x == squadratinho.x && it.y == squadratinho.y + 1 }
    private fun hasWestNeighbor(squadratinho: Squadratinho) = squadratinhos.any { it.x == squadratinho.x - 1 && it.y == squadratinho.y }

    private fun chainSegments(segments: List<Pair<Point, Point>>): List<List<Point>> {
        if (segments.isEmpty()) return emptyList()

        val remaining = segments.toMutableList()
        val polylines = mutableListOf<MutableList<Point>>()

        while (remaining.isNotEmpty()) {
            val currentPolyline = mutableListOf<Point>()
            val firstSeg = remaining.removeAt(0)
            currentPolyline.add(firstSeg.first)
            currentPolyline.add(firstSeg.second)

            var isClosed = false
            var changed = true
            while (changed && !isClosed) {
                changed = false
                for (i in remaining.indices.reversed()) {
                    val seg = remaining[i]
                    val start = currentPolyline.first()
                    val end = currentPolyline.last()

                    when {
                        pointsEqual(seg.first, end) -> {
                            currentPolyline.add(seg.second)
                            remaining.removeAt(i)
                            changed = true
                        }
                        pointsEqual(seg.second, end) -> {
                            currentPolyline.add(seg.first)
                            remaining.removeAt(i)
                            changed = true
                        }
                        pointsEqual(seg.first, start) -> {
                            currentPolyline.add(0, seg.second)
                            remaining.removeAt(i)
                            changed = true
                        }
                        pointsEqual(seg.second, start) -> {
                            currentPolyline.add(0, seg.first)
                            remaining.removeAt(i)
                            changed = true
                        }
                    }
                    if (pointsEqual(currentPolyline.first(), currentPolyline.last())) {
                        isClosed = true
                        break
                    }
                }
            }
            //if (!pointsEqual(currentPolyline.first(), currentPolyline.last())) {
            //    currentPolyline.add(currentPolyline.first())
            //}
            polylines.add(currentPolyline)
        }
        return polylines
    }

    // Checks if two points are effectively equal using a small epsilon.
    private fun pointsEqual(p1: Point, p2: Point, epsilon: Double = 1e-6): Boolean {
        return abs(p1.longitude() - p2.longitude()) < epsilon &&
                abs(p1.latitude() - p2.latitude()) < epsilon
    }

    /**
     * Returns a list of LineString objects representing the grid lines between adjacent squadrats in the cluster.
     * This method creates one line per shared edge and merges collinear adjacent segments.
     */
    fun getGridPolylines(): List<LineString> {
        // Temporary data class to hold segment info for merging.
        data class Segment(val fixed: Double, var start: Double, var end: Double)

        val verticalSegments = mutableListOf<Segment>()
        val horizontalSegments = mutableListOf<Segment>()

        // For each squadrat, check right and bottom neighbours to draw an internal edge.
        for (squadratinho in squadratinhos) {
            // Vertical shared edge.
            if (squadratinhos.contains(Squadratinho(squadratinho.x + 1, squadratinho.y))) {
                val top = CurrentCorner.TOP_RIGHT.getCoords(squadratinho).latitude()
                val bottom = CurrentCorner.BOTTOM_RIGHT.getCoords(squadratinho).latitude()
                val lng = CurrentCorner.TOP_RIGHT.getCoords(squadratinho).longitude()
                verticalSegments.add(Segment(lng, min(top, bottom), max(top, bottom)))
            }
            // Horizontal shared edge.
            if (squadratinhos.contains(Squadratinho(squadratinho.x, squadratinho.y + 1))) {
                val left = CurrentCorner.BOTTOM_LEFT.getCoords(squadratinho).longitude()
                val right = CurrentCorner.BOTTOM_RIGHT.getCoords(squadratinho).longitude()
                val lat = CurrentCorner.BOTTOM_LEFT.getCoords(squadratinho).latitude()
                horizontalSegments.add(Segment(lat, min(left, right), max(left, right)))
            }
        }

        // Merge contiguous vertical segments.
        val mergedVertical = verticalSegments.groupBy { it.fixed }.flatMap { (_, segments) ->
            segments.sortedBy { it.start }.fold(mutableListOf<Segment>()) { acc, seg ->
                if (acc.isEmpty()) {
                    acc.add(seg)
                } else {
                    val last = acc.last()
                    if (abs(seg.start - last.end) < 1e-6 || seg.start <= last.end) {
                        last.end = max(last.end, seg.end)
                    } else {
                        acc.add(seg)
                    }
                }
                acc
            }
        }

        // Merge contiguous horizontal segments.
        val mergedHorizontal = horizontalSegments.groupBy { it.fixed }.flatMap { (_, segments) ->
            segments.sortedBy { it.start }.fold(mutableListOf<Segment>()) { acc, seg ->
                if (acc.isEmpty()) {
                    acc.add(seg)
                } else {
                    val last = acc.last()
                    if (abs(seg.start - last.end) < 1e-6 || seg.start <= last.end) {
                        last.end = max(last.end, seg.end)
                    } else {
                        acc.add(seg)
                    }
                }
                acc
            }
        }

        val gridLines = mutableListOf<LineString>()

        // Vertical segments: fixed value is longitude, start/end represent latitude.
        for (seg in mergedVertical) {
            val p1 = Point.fromLngLat(seg.fixed, seg.start)
            val p2 = Point.fromLngLat(seg.fixed, seg.end)
            gridLines.add(LineString.fromLngLats(listOf(p1, p2)))
        }

        // Horizontal segments: fixed value is latitude, start/end represent longitude.
        for (seg in mergedHorizontal) {
            val p1 = Point.fromLngLat(seg.start, seg.fixed)
            val p2 = Point.fromLngLat(seg.end, seg.fixed)
            gridLines.add(LineString.fromLngLats(listOf(p1, p2)))
        }
        return gridLines
    }
}


// Groups a set of squadrats into clusters of contiguous squadrats.
fun clusterSquadrats(squadrats: Set<Squadrat>): List<Cluster> {
    val clusters = mutableListOf<Cluster>()
    val remainingSquadrats = squadrats.toMutableSet()

    while (remainingSquadrats.isNotEmpty()) {
        val seedSquadrat = remainingSquadrats.first()
        val newCluster = Cluster()
        val squadratsToAdd = mutableSetOf(seedSquadrat)

        remainingSquadrats.remove(seedSquadrat)

        var squadratsAdded = true
        while (squadratsAdded) {
            squadratsAdded = false
            val newlyAddedSquadrats = mutableSetOf<Squadrat>()

            for (squadrat in squadratsToAdd) {
                if (newCluster.squadrats.add(squadrat)) {
                    newlyAddedSquadrats.add(squadrat)
                }
            }

            for (squadrat in newlyAddedSquadrats) {
                val neighbours = remainingSquadrats.filter { it.isNeighbour(squadrat) }
                squadratsToAdd.addAll(neighbours)
                remainingSquadrats.removeAll(neighbours)
                if (neighbours.isNotEmpty()) {
                    squadratsAdded = true
                }
            }
        }
        clusters.add(newCluster)
    }
    return clusters
}

// Groups a set of squadratinhos into clusters of contiguous squadratinhos.
fun clusterSquadratinhos(squadratinhos: Set<Squadratinho>): List<ClusterSquadratinho> {
    val clusters = mutableListOf<ClusterSquadratinho>()
    val remainingSquadratinhos = squadratinhos.toMutableSet()

    while (remainingSquadratinhos.isNotEmpty()) {
        val seedSquadratinho = remainingSquadratinhos.first()
        val newCluster = ClusterSquadratinho()
        val squadratinhosToAdd = mutableSetOf(seedSquadratinho)

        remainingSquadratinhos.remove(seedSquadratinho)

        var squadratinhosAdded = true
        while (squadratinhosAdded) {
            squadratinhosAdded = false
            val newlyAddedSquadratinhos = mutableSetOf<Squadratinho>()

            for (squadratinho in squadratinhosToAdd) {
                if (newCluster.squadratinhos.add(squadratinho)) {
                    newlyAddedSquadratinhos.add(squadratinho)
                }
            }

            for (squadratinho in newlyAddedSquadratinhos) {
                val neighbours = remainingSquadratinhos.filter { it.isNeighbour(squadratinho) }
                squadratinhosToAdd.addAll(neighbours)
                remainingSquadratinhos.removeAll(neighbours)
                if (neighbours.isNotEmpty()) {
                    squadratinhosAdded = true
                }
            }
        }
        clusters.add(newCluster)
    }
    return clusters
}
