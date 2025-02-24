package bzh.klabz.squadrating.services

import android.content.Context
import android.util.Log
import androidx.annotation.ColorRes
import bzh.klabz.squadrating.Cluster
import bzh.klabz.squadrating.ClusterSquadratinho
import bzh.klabz.squadrating.R
import bzh.klabz.squadrating.Squadrat
import bzh.klabz.squadrating.Squadratinho
import bzh.klabz.squadrating.SquadratingExtension.Companion.TAG
import bzh.klabz.squadrating.SquadratingExtension.ExploredSquadratsData
import bzh.klabz.squadrating.Ubersquadrat
import bzh.klabz.squadrating.Ubersquadratinho
import bzh.klabz.squadrating.clusterSquadrats
import bzh.klabz.squadrating.clusterSquadratinhos
import bzh.klabz.squadrating.coordsToSquadrat
import bzh.klabz.squadrating.coordsToSquadratinho
import bzh.klabz.squadrating.calcYard
import bzh.klabz.squadrating.calcYardinho
import bzh.klabz.squadrating.data.GpsCoords
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
import kotlin.math.exp
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

            // val mapZoomFlow = karooSystem.stream<OnMapZoomLevel>().map { (it.zoomLevel / 2).roundToInt() * 2 }
            val mapZoomFlow = karooSystem.stream<OnMapZoomLevel>().map { it.zoomLevel.roundToInt() }

            val gpsSquadratFlow = gpsFlow.map { Pair(coordsToSquadrat(it.latitude, it.longitude), coordsToSquadratinho(it.latitude, it.longitude)) }.throttle(10_000L)

            val exploredSquadratsFlow = applicationContext.exploredSquadratsDataStore.data.map {
                // Squadrats
                val exploredSquadrats = it.exploredSquadratsList.map { squadrat -> Squadrat(squadrat.x, squadrat.y) }.toSet()
                val recentlyExploredSquadrats = it.recentlyExploredSquadratsList.map { squadrat -> Squadrat(squadrat.x, squadrat.y) }.toSet()
                val recentlyExploredNewSquadrats = it.recentlyExploredNewSquadratsList.map { squadrat -> Squadrat(squadrat.x, squadrat.y) }.toSet()
                val ubersquadrat = if(it.biggestUbersquadratX != 0 && it.biggestUbersquadratY != 0 && it.biggestUbersquadratSize != 0) Ubersquadrat(it.biggestUbersquadratX, it.biggestUbersquadratY, it.biggestUbersquadratSize) else null
                val yard = calcYard(exploredSquadrats)

                // Squadratinhos
                val exploredSquadratinhos = it.exploredSquadratinhosList.map { squadratinho -> Squadratinho(squadratinho.x, squadratinho.y) }.toSet()
                val recentlyExploredNewSquadratinhos = it.recentlyExploredNewSquadratinhosList.map { squadratinho -> Squadratinho(squadratinho.x, squadratinho.y) }.toSet()
                val ubersquadratinho = if(it.biggestUbersquadratinhoX != 0 && it.biggestUbersquadratinhoY != 0 && it.biggestUbersquadratinhoSize != 0) Ubersquadratinho(it.biggestUbersquadratinhoX, it.biggestUbersquadratinhoY, it.biggestUbersquadratinhoSize) else null
                val yardinho = calcYardinho(exploredSquadratinhos)

                ExploredSquadratsData(
                    // Squadrats
                    exploredSquadrats,
                    recentlyExploredSquadrats,
                    recentlyExploredNewSquadrats,
                    ubersquadrat,
                    yard,

                    // Squadratinhos
                    exploredSquadratinhos,
                    recentlyExploredNewSquadratinhos,
                    ubersquadratinho,
                    yardinho
                )
            }

            val settingsFlow = applicationContext.userPreferencesDataStore.data

            // val linesFlow = channelFlow {
            //     send(null)

            //     settingsFlow.collectLatest { settings ->
            //         if (settings.showActivityLines){
            //             applicationContext.activityLinesDataStore.data.collect {
            //                 send(it)
            //             }
            //         }
            //     }
            // }

            data class StreamData(val exploredSquadrats: ExploredSquadratsData,
                                  val settings: UserPreferences,
                                  val centerSquadrats: Pair<Squadrat, Squadratinho>,
                                  val mapZoom: Int)

            combine(exploredSquadratsFlow, settingsFlow, gpsSquadratFlow, mapZoomFlow) { exploredSquadrats, settings, centerSquadrats, mapZoom ->
                StreamData(exploredSquadrats, settings, centerSquadrats, mapZoom)
            }.distinctUntilChanged().collect { (exploredSquadratsData, settings, centerSquadrats, mapZoom) ->
                    // TODO: only compute for squadrats/squadratinhos accordingly
                    if (!settings.areSquadratsDisabled || !settings.areSquadratinhosDisabled){
                        val startTime = System.currentTimeMillis()
                        val (centerSquadrat, centerSquadratinho) = centerSquadrats

                        Log.d(TAG, "Start updating squadrats")

                        val squadratLoadRadius = settings.squadratDrawRange.let { if(it > 0) it else 3 }.coerceIn(2..5)
                        val showSquadratGridLines = !settings.areSquadratsDisabled && !settings.hideSquadratGridLines
                        val viewUbersquadrat = Ubersquadrat(centerSquadrat.x - squadratLoadRadius, centerSquadrat.y - squadratLoadRadius, squadratLoadRadius * 2 + 1)

                        val squadratinhoLoadRadius = settings.squadratinhoDrawRange.let { if(it > 0) it else 3 }.coerceIn(2..5)
                        val showSquadratinhoGridLines = !settings.areSquadratinhosDisabled && !settings.hideSquadratinhoGridLines
                        val viewUbersquadratinho = Ubersquadratinho(centerSquadratinho.x - squadratinhoLoadRadius, centerSquadratinho.y - squadratinhoLoadRadius, squadratinhoLoadRadius * 2 + 1)

                        val squadratLoadRangeX = centerSquadrat.x - squadratLoadRadius..centerSquadrat.x + squadratLoadRadius
                        val squadratLoadRangeY = centerSquadrat.y - squadratLoadRadius..centerSquadrat.y + squadratLoadRadius

                        val squadratinhoLoadRangeX = centerSquadratinho.x - squadratinhoLoadRadius..centerSquadratinho.x + squadratinhoLoadRadius
                        val squadratinhoLoadRangeY = centerSquadratinho.y - squadratinhoLoadRadius..centerSquadratinho.y + squadratinhoLoadRadius

                        // val insetOffset = when (mapZoom) {
                        //     in 0..10 -> 175.0
                        //     11 -> 125.0
                        //     12 -> 75.0
                        //     13 -> 37.5
                        //     14 -> 25.0
                        //     15 -> 15.0
                        //     16 -> 10.0
                        //     else -> 5.0
                        // }
                        val insetOffset = if (mapZoom <= 10) {
                            175.0
                        } else {
                            val calculated = 206.2 * exp(-0.55 * (mapZoom - 10))
                            calculated.coerceAtLeast(2.0)
                        }

                        val recentlyExploredNewSquadrats = exploredSquadratsData.recentlyExploredNewSquadrats
                            .filter { it.x in squadratLoadRangeX && it.y in squadratLoadRangeY }
                            .map { Squadrat(it.x, it.y) }.toSet()

                        val recentlyExploredNewSquadratinhos = exploredSquadratsData.recentlyExploredNewSquadratinhos
                        .filter { it.x in squadratinhoLoadRangeX && it.y in squadratinhoLoadRangeY }
                        .map { Squadratinho(it.x, it.y) }.toSet()

                        val allExploredSquadratsInRange = exploredSquadratsData.exploredSquadrats
                            .filter { it.x in squadratLoadRangeX && it.y in squadratLoadRangeY }
                            .map { Squadrat(it.x, it.y) }.toSet()
                        val exploredSquadratsInRange = allExploredSquadratsInRange - recentlyExploredNewSquadrats

                        val allExploredSquadratinhosInRange = exploredSquadratsData.exploredSquadratinhos
                            .filter { it.x in squadratinhoLoadRangeX && it.y in squadratinhoLoadRangeY }
                            .map { Squadratinho(it.x, it.y) }.toSet()
                        val exploredSquadratinhosInRange = allExploredSquadratinhosInRange - recentlyExploredNewSquadratinhos

                        Log.i(TAG, "Explored squadrats: ${exploredSquadratsInRange.size} - Center Squadrat: $centerSquadrat - Map Zoom: $mapZoom")
                        Log.i(TAG, "Explored squadratinhos: ${exploredSquadratinhosInRange.size} - Center Squadratinho: $centerSquadratinho - Map Zoom: $mapZoom")

                        val ubersquadrat = exploredSquadratsData.ubersquadrat
                        Log.i(TAG, "Largest ubersquadrat: $ubersquadrat")

                        val ubersquadratinho = exploredSquadratsData.ubersquadratinho
                        Log.i(TAG, "Largest ubersquadratinho: $ubersquadratinho")

                        val ubersquadratSquadrats = exploredSquadratsInRange.intersect((ubersquadrat?.getAllSquadrats() ?: emptySet()).toSet())
                        val exploredSquadratsWithNeighbours = (exploredSquadratsInRange - ubersquadratSquadrats).filter { it.isSurrounded(exploredSquadratsData.exploredSquadrats) }.toSet()
                        val otherExploredSquadrats = (exploredSquadratsInRange - ubersquadratSquadrats - recentlyExploredNewSquadrats - exploredSquadratsWithNeighbours).toSet()
                        val unexploredSquadrats = viewUbersquadrat.getAllSquadrats() - exploredSquadratsInRange - recentlyExploredNewSquadrats
                        Log.i(TAG, "Unexplored squadrats: ${unexploredSquadrats.size}")

                        val ubersquadratinhoSquadratinhos = exploredSquadratinhosInRange.intersect((ubersquadratinho?.getAllSquadratinhos() ?: emptySet()).toSet())
                        val exploredSquadratinhosWithNeighbours = (exploredSquadratinhosInRange - ubersquadratinhoSquadratinhos).filter { it.isSurrounded(exploredSquadratsData.exploredSquadratinhos) }.toSet()
                        val otherExploredSquadratinhos = (exploredSquadratinhosInRange - ubersquadratinhoSquadratinhos - recentlyExploredNewSquadratinhos - exploredSquadratinhosWithNeighbours).toSet()
                        val unexploredSquadratinhos = viewUbersquadratinho.getAllSquadratinhos() - exploredSquadratinhosInRange - recentlyExploredNewSquadratinhos
                        Log.i(TAG, "Unexplored squadratinhos: ${unexploredSquadratinhos.size}")

                        val ubersquadratCluster = clusterSquadrats(ubersquadratSquadrats).singleOrNull()
                        val clusteredExploredSquadratsWithNeighbours = clusterSquadrats(exploredSquadratsWithNeighbours)
                        val clusteredExploredSquadrats = clusterSquadrats(otherExploredSquadrats)
                        val clusteredUnexploredSquadrats = clusterSquadrats(unexploredSquadrats)
                        val clusteredRecentlyExploredNewSquadrats = clusterSquadrats(recentlyExploredNewSquadrats)

                        val ubersquadratinhoCluster = clusterSquadratinhos(ubersquadratinhoSquadratinhos).singleOrNull()
                        val clusteredExploredSquadratinhosWithNeighbours = clusterSquadratinhos(exploredSquadratinhosWithNeighbours)
                        val clusteredExploredSquadratinhos = clusterSquadratinhos(otherExploredSquadratinhos)
                        val clusteredUnexploredSquadratinhos = clusterSquadratinhos(unexploredSquadratinhos)
                        val clusteredRecentlyExploredNewSquadratinhos = clusterSquadratinhos(recentlyExploredNewSquadratinhos)

                        val ubersquadratClusterGridLines = ubersquadratCluster?.getGridPolylines() ?: emptyList()
                        val clusteredExploredGridLines = clusteredExploredSquadrats.flatMap { it.getGridPolylines() }
                        val clusteredUnexploredGridLines = clusteredUnexploredSquadrats.flatMap { it.getGridPolylines() }
                        val clusteredRecentlyExploredGridLines = clusteredRecentlyExploredNewSquadrats.flatMap { it.getGridPolylines() }
                        val clusteredExploredSquadratsWithNeighboursGridLines = clusteredExploredSquadratsWithNeighbours.flatMap { it.getGridPolylines() }

                        val ubersquadratinhoClusterGridLines = ubersquadratinhoCluster?.getGridPolylines() ?: emptyList()
                        val clusteredExploredGridLines1 = clusteredExploredSquadratinhos.flatMap { it.getGridPolylines() }
                        val clusteredUnexploredGridLines1 = clusteredUnexploredSquadratinhos.flatMap { it.getGridPolylines() }
                        val clusteredRecentlyExploredGridLines1 = clusteredRecentlyExploredNewSquadratinhos.flatMap { it.getGridPolylines() }
                        val clusteredExploredSquadratinhosWithNeighboursGridLines = clusteredExploredSquadratinhosWithNeighbours.flatMap { it.getGridPolylines() }

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
                        fun getPolylineCommands1(cluster: ClusterSquadratinho?, identifier: String, @ColorRes color: Int, width: Int = 10): List<ShowPolyline> {
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

                        val ubersquadratinhoClusterPolyline = getPolylineCommands1(ubersquadratinhoCluster, "ubersquadratinho-cluster",
                            R.color.blue
                        ).toSet()

                        val clusteredExploredPolylines = clusteredExploredSquadrats.map {
                            getPolylineCommands(it, "clustered-explored", R.color.red)
                        }.flatten().toSet()

                        val clusteredExploredPolylines1 = clusteredExploredSquadratinhos.map {
                            getPolylineCommands1(it, "clustered-explored-inho", R.color.red)
                        }.flatten().toSet()

                        val clusteredUnexploredPolylines = clusteredUnexploredSquadrats.map {
                            getPolylineCommands(it, "clustered-unexplored", R.color.gray)
                        }.flatten().toSet()

                        val clusteredUnexploredPolylines1 = clusteredUnexploredSquadratinhos.map {
                            getPolylineCommands1(it, "clustered-unexplored-inho", R.color.gray)
                        }.flatten().toSet()

                        val clusteredRecentlyExploredPolylines = clusteredRecentlyExploredNewSquadrats.map {
                            getPolylineCommands(it, "clustered-recent", R.color.lime)
                        }.flatten().toSet()

                        val clusteredRecentlyExploredPolylines1 = clusteredRecentlyExploredNewSquadratinhos.map {
                            getPolylineCommands1(it, "clustered-recent-inho", R.color.lime)
                        }.flatten().toSet()

                        val clusteredExploredSquadratsWithNeighboursPolylines = clusteredExploredSquadratsWithNeighbours.map {
                            getPolylineCommands(it, "clustered-explored-neighbours", R.color.green)
                        }.flatten().toSet()

                        val clusteredExploredSquadratinhosWithNeighboursPolylines = clusteredExploredSquadratinhosWithNeighbours.map {
                            getPolylineCommands1(it, "clustered-explored-neighbours-inho", R.color.green)
                        }.flatten().toSet()

                        val ubersquadratClusterGridPolylines = ubersquadratClusterGridLines.map { ShowPolyline(id = "ubersquadrat-cluster-grid-${it.hashCode()}",
                            encodedPolyline = it.toPolyline(5),
                            color = applicationContext.getColor(R.color.blue),
                            width = 5)
                        }.toSet()

                        val ubersquadratinhoClusterGridPolylines = ubersquadratinhoClusterGridLines.map { ShowPolyline(id = "ubersquadratinho-cluster-grid-${it.hashCode()}",
                            encodedPolyline = it.toPolyline(5),
                            color = applicationContext.getColor(R.color.blue),
                            width = 5)
                        }.toSet()

                        val clusteredExploredGridPolylines = clusteredExploredGridLines.map { ShowPolyline(id = "clustered-explored-grid-${it.hashCode()}",
                            encodedPolyline = it.toPolyline(5),
                            color = applicationContext.getColor(R.color.red),
                            width = 5)
                        }.toSet()

                        val clusteredExploredGridPolylines1 = clusteredExploredGridLines1.map { ShowPolyline(id = "clustered-explored-inho-grid-${it.hashCode()}",
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

                        val clusteredUnexploredGridPolylines1 = clusteredUnexploredGridLines1.map {
                            ShowPolyline(id = "clustered-unexplored-inho-grid-${it.hashCode()}",
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

                        val clusteredRecentlyExploredGridPolylines1 = clusteredRecentlyExploredGridLines1.map {
                            ShowPolyline(id = "clustered-recent-inho-grid-${it.hashCode()}",
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

                        val clusteredExploredSquadratinhosWithNeighboursGridPolylines = clusteredExploredSquadratinhosWithNeighboursGridLines.map {
                            ShowPolyline(id = "clustered-explored-neighbours-inho-grid-${it.hashCode()}",
                                encodedPolyline = it.toPolyline(5),
                                color = applicationContext.getColor(R.color.green),
                                width = 5)
                        }.toSet()

                        val gridLines = if (showSquadratGridLines){
                            clusteredExploredGridPolylines + clusteredUnexploredGridPolylines +
                                ubersquadratClusterGridPolylines + clusteredRecentlyExploredGridPolylines + clusteredExploredSquadratsWithNeighboursGridPolylines
                        } else {
                            emptySet()
                        }

                        val gridLines1 = if (showSquadratinhoGridLines){
                            clusteredExploredGridPolylines1 + clusteredUnexploredGridPolylines1 +
                                ubersquadratinhoClusterGridPolylines + clusteredRecentlyExploredGridPolylines1 + clusteredExploredSquadratinhosWithNeighboursGridPolylines
                        } else {
                            emptySet()
                        }

                        var polylines:Set<ShowPolyline> = emptySet()
                        if (!settings.areSquadratsDisabled) polylines += gridLines + clusteredExploredPolylines + ubersquadratClusterPolyline +
                            clusteredUnexploredPolylines + clusteredRecentlyExploredPolylines + clusteredExploredSquadratsWithNeighboursPolylines

                        if (!settings.areSquadratinhosDisabled) polylines += gridLines1 + clusteredExploredPolylines1 + ubersquadratinhoClusterPolyline +
                            clusteredUnexploredPolylines1 + clusteredRecentlyExploredPolylines1 + clusteredExploredSquadratinhosWithNeighboursPolylines

                        // if (!settings.areSquadratinhosDisabled) polylines += gridLines1 + clusteredUnexploredPolylines1

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
