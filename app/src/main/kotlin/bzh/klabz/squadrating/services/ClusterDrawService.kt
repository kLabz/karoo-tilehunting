package bzh.klabz.squadrating.services

import android.content.Context
import android.util.Log
import androidx.annotation.ColorRes
import bzh.klabz.squadrating.Cluster
import bzh.klabz.squadrating.R
import bzh.klabz.squadrating.Squadrat
import bzh.klabz.squadrating.SquadratingExtension.Companion.TAG
import bzh.klabz.squadrating.SquadratingExtension.ExploredSquadratsData
import bzh.klabz.squadrating.Ubersquadrat
import bzh.klabz.squadrating.clusterSquadrats
import bzh.klabz.squadrating.coordsToSquadrat
import bzh.klabz.squadrating.data.GpsCoords
import bzh.klabz.squadrating.data.PastActivities
import bzh.klabz.squadrating.data.UserPreferences
import bzh.klabz.squadrating.datastores.activityLinesDataStore
import bzh.klabz.squadrating.datastores.exploredSquadratsDataStore
import bzh.klabz.squadrating.datastores.userPreferencesDataStore
import bzh.klabz.squadrating.lastKnownGpsCoordsDataStore
import bzh.klabz.squadrating.throttle
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.HidePolyline
import io.hammerhead.karooext.models.MapEffect
import io.hammerhead.karooext.models.OnLocationChanged
import io.hammerhead.karooext.models.OnMapZoomLevel
import io.hammerhead.karooext.models.ShowPolyline
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ClusterDrawService(private val karooSystem: KarooSystemServiceProvider,
                         private val applicationContext: Context) {

    private val gpsFlow = flow<GpsCoords> {
        val initialPosition = applicationContext.lastKnownGpsCoordsDataStore.data.firstOrNull()
        if (initialPosition != null && initialPosition.latitude != 0.0 && initialPosition.longitude != 0.0){
            Log.d(TAG, "Using last known GPS position: ${initialPosition.latitude}, ${initialPosition.longitude}")
            emit(initialPosition)
        }

        karooSystem.stream<OnLocationChanged>().collect {
            emit(GpsCoords.newBuilder().setLatitude(it.lat).setLongitude(it.lng).build())
        }
    }

    private var lastDrawnPolylines = setOf<ShowPolyline>()

    fun startJob(emitter: Emitter<MapEffect>): Job {
        val squadratClusterJob = CoroutineScope(Dispatchers.IO).launch {
            // First, redrawa everything that should already be drawn
            lastDrawnPolylines.forEach { emitter.onNext(it) }

            val mapZoomFlow = karooSystem.stream<OnMapZoomLevel>().map { (it.zoomLevel / 2).roundToInt() * 2 }

            val gpsSquadratFlow = gpsFlow.map { coordsToSquadrat(it.latitude, it.longitude) }.throttle(10_000L)

            val exploredSquadratsFlow = applicationContext.exploredSquadratsDataStore.data.map {
                val exploredSquadrats = it.exploredSquadratsList.map { squadrat -> Squadrat(squadrat.x, squadrat.y) }.toSet()
                val recentlyExploredSquadrats = it.recentlyExploredSquadratsList.map { squadrat -> Squadrat(squadrat.x, squadrat.y) }.toSet()
                val recentlyExploredNewSquadrats = it.recentlyExploredNewSquadratsList.map { squadrat -> Squadrat(squadrat.x, squadrat.y) }.toSet()
                val ubersquadrat = if(it.biggestUbersquadratX != 0 && it.biggestUbersquadratY != 0 && it.biggestUbersquadratSize != 0) Ubersquadrat(it.biggestUbersquadratX, it.biggestUbersquadratY, it.biggestUbersquadratSize) else null

                ExploredSquadratsData(exploredSquadrats, recentlyExploredSquadrats, recentlyExploredNewSquadrats, ubersquadrat)
            }

            val settingsFlow = applicationContext.userPreferencesDataStore.data

            val linesFlow = channelFlow {
                send(null)

                settingsFlow.collectLatest { settings ->
                    if (settings.showActivityLines){
                        applicationContext.activityLinesDataStore.data.collect {
                            send(it)
                        }
                    }
                }
            }

            data class StreamData(val exploredSquadrats: ExploredSquadratsData,
                                  val lines: PastActivities? = null,
                                  val settings: UserPreferences,
                                  val centerSquadrat: Squadrat,
                                  val mapZoom: Int)

            combine(exploredSquadratsFlow, linesFlow, settingsFlow, gpsSquadratFlow, mapZoomFlow) { exploredSquadrats, lines, settings, centerSquadrat, mapZoom ->
                StreamData(exploredSquadrats, lines, settings, centerSquadrat, mapZoom)
            }.distinctUntilChanged().collect { (exploredSquadratsData, pastActivities, settings, centerSquadrat, mapZoom) ->
                    if (!settings.isDisabled){
                        val startTime = System.currentTimeMillis()

                        Log.d(TAG, "Start updating squadrats")

                        val squadratLoadRadius = settings.squadratDrawRange.let { if(it > 0) it else 3 }.coerceIn(2..5)
                        val showGridLines = !settings.hideGridLines
                        val viewUbersquadrat = Ubersquadrat(centerSquadrat.x - squadratLoadRadius, centerSquadrat.y - squadratLoadRadius, squadratLoadRadius * 2 + 1)

                        val linesInViewUbersquadrat = mutableMapOf<Int, LineString>()
                        activityLoop@ for (activity in pastActivities?.activitiesList ?: emptyList()){
                            val decoded = LineString.fromPolyline(activity.encodedPolyline, 5)

                            val segments = mutableListOf<List<Point>>()
                            var currentSegment: MutableList<Point> = mutableListOf()

                            for(coords in decoded.coordinates()){
                                val isInside = viewUbersquadrat.isInside(coords.latitude(), coords.longitude())
                                if (isInside){
                                    currentSegment.add(coords)
                                } else {
                                    if (currentSegment.isNotEmpty()){
                                        segments.add(currentSegment)
                                        currentSegment = mutableListOf()
                                    }
                                }
                            }

                            if (currentSegment.isNotEmpty()){
                                segments.add(currentSegment.toList())
                                currentSegment.clear()
                            }

                            segments.forEach { linesInViewUbersquadrat[activity.id] = (LineString.fromLngLats(it)) }

                            if (linesInViewUbersquadrat.size > 100){ // Render at most 100 activities
                                break@activityLoop
                            }
                        }

                        Log.d(TAG, "Lines in view ubersquadrat: ${linesInViewUbersquadrat.size}")

                        val squadratLoadRangeX = centerSquadrat.x - squadratLoadRadius..centerSquadrat.x + squadratLoadRadius
                        val squadratLoadRangeY = centerSquadrat.y - squadratLoadRadius..centerSquadrat.y + squadratLoadRadius

                        val insetOffset = when (mapZoom) {
                            in 0..10 -> 175.0
                            11 -> 125.0
                            12 -> 75.0
                            13 -> 37.5
                            14 -> 25.0
                            15 -> 15.0
                            16 -> 10.0
                            else -> 5.0
                        }

                        val recentlyExploredNewSquadrats = exploredSquadratsData.recentlyExploredNewSquadrats
                            .filter { it.x in squadratLoadRangeX && it.y in squadratLoadRangeY }
                            .map { Squadrat(it.x, it.y) }.toSet()

                        val allExploredSquadratsInRange = exploredSquadratsData.exploredSquadrats
                            .filter { it.x in squadratLoadRangeX && it.y in squadratLoadRangeY }
                            .map { Squadrat(it.x, it.y) }.toSet()
                        val exploredSquadratsInRange = allExploredSquadratsInRange - recentlyExploredNewSquadrats

                        Log.i(TAG, "Explored squadrats: ${exploredSquadratsInRange.size} - Center Squadrat: $centerSquadrat - Map Zoom: $mapZoom")

                        val ubersquadrat = exploredSquadratsData.ubersquadrat
                        Log.i(TAG, "Largest ubersquadrat: $ubersquadrat")

                        val ubersquadratSquadrats = exploredSquadratsInRange.intersect((ubersquadrat?.getAllSquadrats() ?: emptySet()).toSet())
                        val exploredSquadratsWithNeighbours = (exploredSquadratsInRange - ubersquadratSquadrats).filter { it.isSurrounded(exploredSquadratsData.exploredSquadrats) }.toSet()
                        val otherExploredSquadrats = (exploredSquadratsInRange - ubersquadratSquadrats - recentlyExploredNewSquadrats - exploredSquadratsWithNeighbours).toSet()
                        val unexploredSquadrats = viewUbersquadrat.getAllSquadrats() - exploredSquadratsInRange - recentlyExploredNewSquadrats
                        Log.i(TAG, "Unexplored squadrats: ${unexploredSquadrats.size}")

                        val ubersquadratCluster = clusterSquadrats(ubersquadratSquadrats).singleOrNull()
                        val clusteredExploredSquadratsWithNeighbours = clusterSquadrats(exploredSquadratsWithNeighbours)
                        val clusteredExploredSquadrats = clusterSquadrats(otherExploredSquadrats)
                        val clusteredUnexploredSquadrats = clusterSquadrats(unexploredSquadrats)
                        val clusteredRecentlyExploredNewSquadrats = clusterSquadrats(recentlyExploredNewSquadrats)

                        val ubersquadratClusterGridLines = ubersquadratCluster?.getGridPolylines() ?: emptyList()
                        val clusteredExploredGridLines = clusteredExploredSquadrats.flatMap { it.getGridPolylines() }
                        val clusteredUnexploredGridLines = clusteredUnexploredSquadrats.flatMap { it.getGridPolylines() }
                        val clusteredRecentlyExploredGridLines = clusteredRecentlyExploredNewSquadrats.flatMap { it.getGridPolylines() }
                        val clusteredExploredSquadratsWithNeighboursGridLines = clusteredExploredSquadratsWithNeighbours.flatMap { it.getGridPolylines() }

                        fun getPolylineCommands(cluster: Cluster?, identifier: String, @ColorRes color: Int, width: Int = 10): List<ShowPolyline> {
                            return cluster?.getPolyline(insetOffset)?.map { polyline ->
                                val str = polyline.toPolyline(5)
                                ShowPolyline(
                                    id = "${identifier}-${str.hashCode()}",
                                    encodedPolyline = str,
                                    color = applicationContext.getColor(color),
                                    width = width
                                )
                            } ?: emptyList()
                        }

                        val ubersquadratClusterPolyline = getPolylineCommands(ubersquadratCluster, "ubersquadrat-cluster",
                            R.color.blue
                        ).toSet()

                        val activityPolylines = linesInViewUbersquadrat.map { (id, line) ->
                            val str = line.toPolyline(5)
                            ShowPolyline(
                                id = "activity-${id}-${viewUbersquadrat.size}",
                                encodedPolyline = str,
                                color = applicationContext.getColor(R.color.gray),
                                width = 4
                            )
                        }

                        val clusteredExploredPolylines = clusteredExploredSquadrats.map {
                            getPolylineCommands(it, "clustered-explored", R.color.red)
                        }.flatten().toSet()

                        val clusteredUnexploredPolylines = clusteredUnexploredSquadrats.map {
                            getPolylineCommands(it, "clustered-unexplored", R.color.gray)
                        }.flatten().toSet()

                        val clusteredRecentlyExploredPolylines = clusteredRecentlyExploredNewSquadrats.map {
                            getPolylineCommands(it, "clustered-recent", R.color.lime)
                        }.flatten().toSet()

                        val clusteredExploredSquadratsWithNeighboursPolylines = clusteredExploredSquadratsWithNeighbours.map {
                            getPolylineCommands(it, "clustered-explored-neighbours", R.color.green)
                        }.flatten().toSet()

                        val ubersquadratClusterGridPolylines = ubersquadratClusterGridLines.map { ShowPolyline(id = "ubersquadrat-cluster-grid-${it.hashCode()}",
                            encodedPolyline = it.toPolyline(5),
                            color = applicationContext.getColor(R.color.blue),
                            width = 5)
                        }.toSet()

                        val clusteredExploredGridPolylines = clusteredExploredGridLines.map { ShowPolyline(id = "clustered-explored-grid-${it.hashCode()}",
                            encodedPolyline = it.toPolyline(5),
                            color = applicationContext.getColor(R.color.red),
                            width = 5)
                        }.toSet()

                        val clusteredUnexploredGridPolylines = clusteredUnexploredGridLines.map {
                            ShowPolyline(id = "clustered-unexplored-grid-${it.hashCode()}",
                                encodedPolyline = it.toPolyline(5),
                                color = applicationContext.getColor(R.color.gray),
                                width = 5)
                        }.toSet()

                        val clusteredRecentlyExploredGridPolylines = clusteredRecentlyExploredGridLines.map {
                            ShowPolyline(id = "clustered-recent-grid-${it.hashCode()}",
                                encodedPolyline = it.toPolyline(5),
                                color = applicationContext.getColor(R.color.lime),
                                width = 5)
                        }.toSet()

                        val clusteredExploredSquadratsWithNeighboursGridPolylines = clusteredExploredSquadratsWithNeighboursGridLines.map {
                            ShowPolyline(id = "clustered-explored-neighbours-grid-${it.hashCode()}",
                                encodedPolyline = it.toPolyline(5),
                                color = applicationContext.getColor(R.color.green),
                                width = 5)
                        }.toSet()

                        val gridLines = if (showGridLines){
                            clusteredExploredGridPolylines + clusteredUnexploredGridPolylines +
                                    ubersquadratClusterGridPolylines + clusteredRecentlyExploredGridPolylines + clusteredExploredSquadratsWithNeighboursGridPolylines
                        } else {
                            emptySet()
                        }

                        val polylines = gridLines + clusteredExploredPolylines + ubersquadratClusterPolyline +
                                clusteredUnexploredPolylines + clusteredRecentlyExploredPolylines + clusteredExploredSquadratsWithNeighboursPolylines +
                                activityPolylines

                        val newPolylines = polylines - lastDrawnPolylines
                        val droppedPolylines = lastDrawnPolylines - polylines

                        Log.i(TAG, "Map update took ${System.currentTimeMillis() - startTime}ms - added ${newPolylines.size} polylines - removed ${droppedPolylines.size} polylines - ${polylines.size} total")

                        newPolylines.forEach { emitter.onNext(it) }
                        droppedPolylines.forEach { emitter.onNext(HidePolyline(it.id)) }

                        lastDrawnPolylines = polylines
                    } else {
                        Log.d(TAG, "Map is disabled - ${lastDrawnPolylines.size} previously drawn")

                        lastDrawnPolylines.forEach { emitter.onNext(HidePolyline(it.id)) }
                        lastDrawnPolylines = emptySet()
                    }
                }
        }

        emitter.setCancellable {
            Log.d(TAG, "Stopping map effect")

            squadratClusterJob.cancel()
        }

        return squadratClusterJob
    }
}
