package bzh.klabz.squadrating.services

import android.content.Context
import android.util.Log
import bzh.klabz.squadrating.Squadrat
import bzh.klabz.squadrating.Squadratinho
import bzh.klabz.squadrating.SquadratingExtension.Companion.TAG
import bzh.klabz.squadrating.Ubersquadrat
import bzh.klabz.squadrating.Ubersquadratinho
import bzh.klabz.squadrating.coordsToSquadrat
import bzh.klabz.squadrating.coordsToSquadratinho
import bzh.klabz.squadrating.calcYard
import bzh.klabz.squadrating.calcYardinho
import bzh.klabz.squadrating.data.Activity
import bzh.klabz.squadrating.datastores.activityLinesDataStore
import bzh.klabz.squadrating.datastores.exploredSquadratsDataStore
import bzh.klabz.squadrating.datastores.userPreferencesDataStore
import bzh.klabz.squadrating.squadratCenter
import bzh.klabz.squadrating.squadratinhoCenter
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.geojson.utils.PolylineUtils
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfConversion
import com.mapbox.turf.TurfMeasurement
import com.mapbox.turf.TurfMisc
import com.mapbox.turf.TurfTransformation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class StatshuntersDownloadService(private val applicationContext: Context, val statshuntersTilesProvider: StatshuntersTilesProvider) {
    fun fillSummaryPolyline(points:MutableList<Point>, lineSquadrats:MutableList<Squadrat>) {
        fun foo(o:Int):Int {
            val start = coordsToSquadrat(points[o - 1].latitude(), points[o - 1].longitude())
            val end = coordsToSquadrat(points[o].latitude(), points[o].longitude())
            if (start.equals(end) || start.isNeighbourish(end)) return 0

            var i = 1
            val s = points[o - 1]
            val e = points[o]
            val u = Point.fromLngLat(
                e.longitude() + (s.longitude() - e.longitude()) / 2,
                e.latitude() + (s.latitude() - e.latitude()) / 2
            )
            val d = coordsToSquadrat(u.latitude(), u.longitude())
            points.add(o, u)
            lineSquadrats.add(o, d)
            i += foo(o + 1)
            i += foo(o)
            return i
        }
        var n = 1
        val count = points.size
        while (n < count) {
            n += foo(n)
            n++
        }
    }

    fun fillSummaryPolyline1(points:MutableList<Point>, lineSquadratinhos:MutableList<Squadratinho>) {
        fun foo(o:Int):Int {
            val start = coordsToSquadratinho(points[o - 1].latitude(), points[o - 1].longitude())
            val end = coordsToSquadratinho(points[o].latitude(), points[o].longitude())
            if (start.equals(end) || start.isNeighbourish(end)) return 0

            var i = 1
            val s = points[o - 1]
            val e = points[o]
            val u = Point.fromLngLat(
                e.longitude() + (s.longitude() - e.longitude()) / 2,
                e.latitude() + (s.latitude() - e.latitude()) / 2
            )
            val d = coordsToSquadratinho(u.latitude(), u.longitude())
            points.add(o, u)
            lineSquadratinhos.add(o, d)
            i += foo(o + 1)
            i += foo(o)
            return i
        }
        var n = 1
        val count = points.size
        while (n < count) {
            n += foo(n)
            n++
        }
    }

    fun cornerPasses(lineSquadrats:List<Squadrat>):List<Pair<Int, Int>> {
        val ret:MutableList<Pair<Int, Int>> = mutableListOf()
        if (lineSquadrats.size < 2) return ret

        for (r in 1..(lineSquadrats.size - 1)) {
            val p1 = lineSquadrats[r-1];
            val p2 = lineSquadrats[r]
            if (p1.hasCommonCorners(p2)) ret.add(Pair(r - 1, r))
        }

        return ret
    }

    fun cornerPasses1(lineSquadratinhos:List<Squadratinho>):List<Pair<Int, Int>> {
        val ret:MutableList<Pair<Int, Int>> = mutableListOf()
        if (lineSquadratinhos.size < 2) return ret

        for (r in 1..(lineSquadratinhos.size - 1)) {
            val p1 = lineSquadratinhos[r-1];
            val p2 = lineSquadratinhos[r]
            if (p1.hasCommonCorners(p2)) ret.add(Pair(r - 1, r))
        }

        return ret
    }

    fun det(p1:Pair<Double, Double>, p2:Pair<Double, Double>, p3:Pair<Double, Double>):Double {
        return (p2.first - p1.first) * (p3.second - p1.second) - (p2.second - p1.second) * (p3.first - p1.first)
    }

    fun intersect(p1:Pair<Double, Double>, p2:Pair<Double, Double>, p3:Pair<Double, Double>, p4:Pair<Double, Double>):Pair<Double, Double>? {
        val (t, n) = p1
        val (r, a) = p2
        val (o, s) = p3
        val (e, i) = p4

        if (t == r && n == a || o == e && s == i) return null
        val denominator = (i - s) * (r - t) - (e - o) * (a - n)
        if (0 == denominator.toInt()) return null
        val u = ((e - o) * (n - s) - (i - s) * (t - o)) / denominator
        val d = ((r - t) * (n - s) - (a - n) * (t - o)) / denominator
        if (u < 0 || u > 1 || d < 0 || d > 1) return null
        return Pair(t + u * (r - t), n + u * (a - n))
    }

    fun findMissingSquadrat(s1:Squadrat, s2:Squadrat, p1:Point, p2:Point):Squadrat? {
        var i:Squadrat? = null
        var u = false
        var d = false
        var h = false
        val c = if (s1.y > s2.y) s2 else s1
        val f = if (s1.y > s2.y) s1 else s2
        val q = if (s1.y > s2.y) p1 else p2
        val l = if (s1.y > s2.y) p2 else p1

        var y = squadratCenter(c)
        var S = squadratCenter(f)

        // TODO: squadratinho only part

        val x = intersect(y, S, Pair(l.latitude(), l.longitude()), Pair(q.latitude(), q.longitude()))
        if (x != null) {
            val t = coordsToSquadrat(x.first, x.second)
            if (t.equals(Squadrat(c.x, c.y))) u = true
            else if (t.equals(Squadrat(f.x, f.y))) d = true
        }

        val p = det(squadratCenter(c), squadratCenter(f), Pair(l.latitude(), l.longitude()))
        val C = det(squadratCenter(c), squadratCenter(f), Pair(q.latitude(), q.longitude()))

        if (!u && !d || (p > 0 && C > 0 || p < 0 && C < 0)) {
            if (c.x < f.x) {
                if (p > 0 || C > 0) {
                    i = Squadrat(f.x, c.y)
                } else {
                    if (p < 0 || C < 0) {
                        i = Squadrat(c.x, f.y)
                    } else {
                        h = true
                    }
                }
            } else {
                if (p > 0 || C > 0) {
                    i = Squadrat(c.x, f.y)
                } else {
                    if (p < 0 || C < 0) {
                        i = Squadrat(f.x, c.y)
                    } else {
                        h = true
                    }
                }
            }
        } else {
            if (u) {
                if (c.x < f.x) {
                    if (p > 0 || C < 0) {
                        i = Squadrat(c.x, f.y)
                    } else {
                        if (p < 0 || C > 0) {
                            i = Squadrat(f.x, c.y)
                        } else {
                            h = true
                        }
                    }
                } else {
                    if (p > 0 || C < 0) {
                        i = Squadrat(f.x, c.y)
                    } else {
                        if (p < 0 || C > 0) {
                            i = Squadrat(c.x, f.y)
                        } else {
                            h = true
                        }
                    }
                }
            } else {
                if (d && (c.x < f.x)) {
                    if (p > 0 || C < 0) {
                        i = Squadrat(f.x, c.y)
                    } else {
                        if (p < 0 || C > 0) {
                            i = Squadrat(c.x, f.y)
                        } else {
                            h = true
                        }
                    }
                } else {
                    if (p > 0 || C < 0) {
                        i = Squadrat(c.x, f.y)
                    } else {
                        if (p < 0 || C > 0) {
                            i = Squadrat(f.x, c.y)
                        } else {
                            h = true
                        }
                    }
                }
            }
        }

        if (h) return Squadrat(c.x, f.y)
        return i
    }

    fun findMissingSquadratinho(s1:Squadratinho, s2:Squadratinho, p1:Point, p2:Point):Squadratinho? {
        var i:Squadratinho? = null
        var u = false
        var d = false
        var h = false
        val c = if (s1.y > s2.y) s2 else s1
        val f = if (s1.y > s2.y) s1 else s2
        val q = if (s1.y > s2.y) p1 else p2
        val l = if (s1.y > s2.y) p2 else p1

        var y = squadratinhoCenter(c)
        var S = squadratinhoCenter(f)

        // Note: squadratinho only part
        val t = coordsToSquadrat(y.first, y.second)
        val n = coordsToSquadrat(S.first, S.second)
        if (t.x != n.x && t.y != n.y) {
            y = squadratCenter(t)
            S = squadratCenter(n)
        }

        val x = intersect(y, S, Pair(l.latitude(), l.longitude()), Pair(q.latitude(), q.longitude()))
        if (x != null) {
            val t = coordsToSquadratinho(x.first, x.second)
            if (t.equals(Squadratinho(c.x, c.y))) u = true
            else if (t.equals(Squadratinho(f.x, f.y))) d = true
        }

        val p = det(squadratinhoCenter(c), squadratinhoCenter(f), Pair(l.latitude(), l.longitude()))
        val C = det(squadratinhoCenter(c), squadratinhoCenter(f), Pair(q.latitude(), q.longitude()))

        if (!u && !d || (p > 0 && C > 0 || p < 0 && C < 0)) {
            if (c.x < f.x) {
                if (p > 0 || C > 0) {
                    i = Squadratinho(f.x, c.y)
                } else {
                    if (p < 0 || C < 0) {
                        i = Squadratinho(c.x, f.y)
                    } else {
                        h = true
                    }
                }
            } else {
                if (p > 0 || C > 0) {
                    i = Squadratinho(c.x, f.y)
                } else {
                    if (p < 0 || C < 0) {
                        i = Squadratinho(f.x, c.y)
                    } else {
                        h = true
                    }
                }
            }
        } else {
            if (u) {
                if (c.x < f.x) {
                    if (p > 0 || C < 0) {
                        i = Squadratinho(c.x, f.y)
                    } else {
                        if (p < 0 || C > 0) {
                            i = Squadratinho(f.x, c.y)
                        } else {
                            h = true
                        }
                    }
                } else {
                    if (p > 0 || C < 0) {
                        i = Squadratinho(f.x, c.y)
                    } else {
                        if (p < 0 || C > 0) {
                            i = Squadratinho(c.x, f.y)
                        } else {
                            h = true
                        }
                    }
                }
            } else {
                if (d && (c.x < f.x)) {
                    if (p > 0 || C < 0) {
                        i = Squadratinho(f.x, c.y)
                    } else {
                        if (p < 0 || C > 0) {
                            i = Squadratinho(c.x, f.y)
                        } else {
                            h = true
                        }
                    }
                } else {
                    if (p > 0 || C < 0) {
                        i = Squadratinho(c.x, f.y)
                    } else {
                        if (p < 0 || C > 0) {
                            i = Squadratinho(f.x, c.y)
                        } else {
                            h = true
                        }
                    }
                }
            }
        }

        if (h) return Squadratinho(c.x, f.y)
        return i
    }

    fun findMissingSquadrats(points:List<Point>, lineSquadrats:List<Squadrat>):Set<Squadrat> {
        val ret:MutableSet<Squadrat> = mutableSetOf()
        val cornerPasses = cornerPasses(lineSquadrats)
        for ((s1, s2) in cornerPasses) {
            val o = lineSquadrats[s1]
            val e = lineSquadrats[s2]
            val i = points[s1]
            val u = points[s2]
            val missingSquadrat = findMissingSquadrat(o, e, i, u)
            if (missingSquadrat != null) ret.add(missingSquadrat)
        }
        return ret
    }

    fun findMissingSquadratinhos(points:List<Point>, lineSquadratinhos:List<Squadratinho>):Set<Squadratinho> {
        val ret:MutableSet<Squadratinho> = mutableSetOf()
        val cornerPasses = cornerPasses1(lineSquadratinhos)
        for ((s1, s2) in cornerPasses) {
            val o = lineSquadratinhos[s1]
            val e = lineSquadratinhos[s2]
            val i = points[s1]
            val u = points[s2]
            val missingSquadratinho = findMissingSquadratinho(o, e, i, u)
            if (missingSquadratinho != null) ret.add(missingSquadratinho)
        }
        return ret
    }

    fun processLine(line:String, newSquadrats:MutableSet<Squadrat>, newSquadratinhos:MutableSet<Squadratinho>, precision:Int) {
        var points = PolylineUtils.decode(line, precision).toMutableList()
        val lineSquadrats = points.map { coordsToSquadrat(it.latitude(), it.longitude()) }.toMutableList()
        fillSummaryPolyline(points, lineSquadrats)
        for (squadrat in lineSquadrats) if (!newSquadrats.contains(squadrat)) newSquadrats.add(squadrat)

        val missingSquadrats = findMissingSquadrats(points, lineSquadrats)
        for (squadrat in missingSquadrats) if (!newSquadrats.contains(squadrat)) newSquadrats.add(squadrat)

        points = PolylineUtils.decode(line, precision).toMutableList()
        val lineSquadratinhos = points.map { coordsToSquadratinho(it.latitude(), it.longitude()) }.toMutableList()
        fillSummaryPolyline1(points, lineSquadratinhos)
        for (squadratinho in lineSquadratinhos) if (!newSquadratinhos.contains(squadratinho)) newSquadratinhos.add(squadratinho)
        val missingSquadratinhos = findMissingSquadratinhos(points, lineSquadratinhos)
        for (squadratinho in missingSquadratinhos) if (!newSquadratinhos.contains(squadratinho)) newSquadratinhos.add(squadratinho)
    }

    fun startJob(): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            applicationContext.exploredSquadratsDataStore.updateData {
                val updated = it.toBuilder().setIsDownloading(false).build()

                if (!it.lastDownloadError.isNullOrBlank()){
                    updated.toBuilder().setLastDownloadedAt(0).build()
                } else {
                    updated
                }
            }

            data class StreamData(val sharecode: String, val lastUpdatedAt: Long)

            val settingsCodeFlow = applicationContext.userPreferencesDataStore.data.map { it.statshuntersSharecode }.filter { !it.isNullOrBlank() }
            val exploredSquadratsFlow = applicationContext.exploredSquadratsDataStore.data

            combine(settingsCodeFlow, exploredSquadratsFlow) { sharecode, exploredSquadrats -> sharecode to exploredSquadrats }
                .map { (sharecode, exploredSquadrats) -> StreamData(sharecode, exploredSquadrats.lastDownloadedAt) }
                .distinctUntilChanged()
                .filter { (_, lastDownloadedAt) -> lastDownloadedAt < System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 7 }
                .collect {
                    Log.d(TAG, "Starting tile download job")

                    applicationContext.exploredSquadratsDataStore.updateData {
                        it.toBuilder()
                            .setIsDownloading(true)
                            .setLastDownloadError("")
                            .build()
                    }

                    try {
                        var activityCount = 0
                        val sharecode = applicationContext.userPreferencesDataStore.data.first().statshuntersSharecode
                        statshuntersTilesProvider.requestTiles(sharecode.trim()).collect { (activities, lines) ->
                            Log.d(TAG, "Received ${activities.size} activities with ${lines?.size} lines")
                            val hasLines = !lines.isNullOrEmpty() && lines.size == activities.size

                            applicationContext.exploredSquadratsDataStore.updateData { exploredSquadrats ->
                                val alreadyExploredSquadrats = exploredSquadrats.exploredSquadratsList.map { Squadrat(it.x, it.y) }.toSet()
                                val alreadyExploredSquadratinhos = exploredSquadrats.exploredSquadratinhosList.map { Squadratinho(it.x, it.y) }.toSet()

                                val newSquadrats:MutableSet<Squadrat> = mutableSetOf()
                                val newSquadratinhos:MutableSet<Squadratinho> = mutableSetOf()
                                if (!hasLines) {
                                    // Fallback to statshunters tiles, but then there will be no squadratinho support
                                    activities.forEach {
                                        it.tiles.forEach {
                                            val squadrat = Squadrat(it.x, it.y)
                                            if (!newSquadrats.contains(squadrat)) newSquadrats.add(squadrat)
                                        }
                                    }
                                } else {
                                    lines.forEach { processLine(it.data, newSquadrats, newSquadratinhos, 5 /* precision */) }
                                }

                                activityCount += activities.size
                                val updatedExploredSquadrats = alreadyExploredSquadrats + newSquadrats
                                val updatedExploredSquadratsProto = updatedExploredSquadrats.map {
                                    bzh.klabz.squadrating.data.Squadrat.newBuilder().setX(it.x).setY(it.y).build()
                                }
                                Log.d(TAG, "New explored squadrats count: ${updatedExploredSquadrats.size}, $activityCount activities")
                                val updatedUbersquadrat = Ubersquadrat.getBiggestUbersquadrat(updatedExploredSquadrats)
                                Log.d(TAG, "New ubersquadrat: $updatedUbersquadrat")

                                val updatedExploredSquadratinhos = alreadyExploredSquadratinhos + newSquadratinhos
                                val updatedExploredSquadratinhosProto = updatedExploredSquadratinhos.map {
                                    bzh.klabz.squadrating.data.Squadratinho.newBuilder().setX(it.x).setY(it.y).build()
                                }
                                Log.d(TAG, "New explored squadratinhos count: ${updatedExploredSquadratinhos.size}, $activityCount activities")
                                val updatedUbersquadratinho = Ubersquadratinho.getBiggestUbersquadratinho(updatedExploredSquadratinhos)
                                Log.d(TAG, "New ubersquadratinho: $updatedUbersquadratinho")

                                val updatedYard = calcYard(updatedExploredSquadrats)
                                Log.d(TAG, "New yard: $updatedYard")
                                val updatedYardinho = calcYardinho(updatedExploredSquadratinhos)
                                Log.d(TAG, "New yardinho: $updatedYardinho")

                                exploredSquadrats.toBuilder()
                                    .setDownloadedActivities(activityCount)
                                    .clearExploredSquadrats()
                                    .clearExploredSquadratinhos()
                                    .addAllExploredSquadrats(updatedExploredSquadratsProto)
                                    .addAllExploredSquadratinhos(updatedExploredSquadratinhosProto)
                                    .setBiggestUbersquadratX(updatedUbersquadrat?.x ?: 0)
                                    .setBiggestUbersquadratY(updatedUbersquadrat?.y ?: 0)
                                    .setBiggestUbersquadratSize(updatedUbersquadrat?.size ?: 0)
                                    .setBiggestUbersquadratinhoX(updatedUbersquadratinho?.x ?: 0)
                                    .setBiggestUbersquadratinhoY(updatedUbersquadratinho?.y ?: 0)
                                    .setBiggestUbersquadratinhoSize(updatedUbersquadratinho?.size ?: 0)
                                    .setYard(updatedYard)
                                    .setYardinho(updatedYardinho)
                                    .build()
                            }

                            if (hasLines) {
                                print("Adding ${lines?.size} lines to datastore")

                                val activityList = lines?.mapIndexed { index, line ->
                                    val activity = activities[index]

                                    Activity.newBuilder()
                                        .addAllTiles(activity.tiles.map { tile -> bzh.klabz.squadrating.data.Squadrat.newBuilder().setX(tile.x).setY(tile.y).build() })
                                        .setId(activity.id)
                                        .setDate(activity.date)
                                        .setName(activity.name)
                                        .setAverageCadence(activity.averageCadence)
                                        .setAverageHeartrate(activity.averageHeartrate)
                                        .setAvg(activity.avg)
                                        .setCommute(activity.commute)
                                        .setDistance(activity.distance)
                                        .setElapsedTime(activity.elapsedTime)
                                        .setForeignId(activity.foreignId)
                                        .setGearForeignId(activity.gearForeignId ?: "")
                                        .setGearId(activity.gearId ?: 0)
                                        .setKilojoules(activity.kilojoules)
                                        .setLat(activity.lat)
                                        .setLng(activity.lng)
                                        .setMaxHeartrate(activity.maxHeartrate)
                                        .setMaxSpeed(activity.maxSpeed)
                                        .setMovingTime(activity.movingTime)
                                        .setTotalElevationGain(activity.totalElevationGain)
                                        .setTrainer(activity.trainer)
                                        .setUserId(activity.userId)
                                        .setWorkoutType(activity.workoutType)
                                        .setEncodedPolyline(line.data)
                                        .build()
                                } ?: emptyList()

                                val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

                                val sortedActivityList = activityList.sortedByDescending { activity ->
                                    try {
                                        java.time.LocalDateTime.parse(activity.date, formatter)
                                    } catch (e: Throwable) {
                                        Log.w(TAG, "Failed to parse date", e)
                                        null
                                    }
                                }

                                applicationContext.activityLinesDataStore.updateData { activityLines ->
                                    val existingActivities = activityLines.activitiesList.map { it.id }.toSet()
                                    val newActivities = sortedActivityList.filter { it.id !in existingActivities }

                                    activityLines.toBuilder().addAllActivities(newActivities).build()
                                }
                            }
                        }

                        applicationContext.exploredSquadratsDataStore.updateData {
                            it.toBuilder().setIsDownloading(false).setLastDownloadedAt(System.currentTimeMillis()).build()
                        }
                    } catch(e: CancellationException){
                        Log.d(TAG, "Download job cancelled")
                    } catch(e: Throwable){
                        Log.e(TAG, "Failed to download tiles", e)

                        applicationContext.exploredSquadratsDataStore.updateData {
                            var errorMessage = e.message ?: "Unknown error"
                            if (e is StatshuntersTilesProvider.HttpDownloadError){
                                when (e.httpError) {
                                    401, 403 -> errorMessage = "Access denied"
                                    0 -> errorMessage = "No internet connection"
                                    404 -> errorMessage = "Not found"
                                }
                            }

                            it.toBuilder().setLastDownloadError(errorMessage).setIsDownloading(false).setLastDownloadedAt(System.currentTimeMillis()).build()
                        }
                    }
                }
        }
    }
}
