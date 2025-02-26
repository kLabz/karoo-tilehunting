package bzh.klabz.squadrating.services

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfConversion
import bzh.klabz.squadrating.CurrentCorner
import bzh.klabz.squadrating.SquadratingExtension.Companion.TAG
import bzh.klabz.squadrating.SquadratingExtension.ExploredSquadratsData
import bzh.klabz.squadrating.R
import bzh.klabz.squadrating.Ubersquadrat
import bzh.klabz.squadrating.Ubersquadratinho
import bzh.klabz.squadrating.Squadrat
import bzh.klabz.squadrating.Squadratinho
import bzh.klabz.squadrating.coordsToSquadrat
import bzh.klabz.squadrating.coordsToSquadratinho
import bzh.klabz.squadrating.datastores.exploredSquadratsDataStore
import bzh.klabz.squadrating.calcYard
import bzh.klabz.squadrating.calcYardinho
import io.hammerhead.karooext.models.InRideAlert
import io.hammerhead.karooext.models.OnLocationChanged
import io.hammerhead.karooext.models.PlayBeepPattern
import io.hammerhead.karooext.models.RideState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ExploreSquadratsService(private val karooSystem: KarooSystemServiceProvider) {
    companion object {
        val margin = TurfConversion.convertLength(
            5.0,
            TurfConstants.UNIT_METERS,
            TurfConstants.UNIT_DEGREES
        )
    }

    fun startJob(context: Context): Job {
        val mediaPlayer = MediaPlayer.create(context, R.raw.alert6)

        return CoroutineScope(Dispatchers.IO).launch {
            val exploredSquadratsFlow = context.exploredSquadratsDataStore.data
                .map {
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

            val locationFlow = karooSystem.stream<OnLocationChanged>()
            val rideStateFlow = karooSystem.stream<RideState>()

            data class StreamData(val exploredSquadrats: ExploredSquadratsData, val location: OnLocationChanged, val rideState: RideState)

            combine(exploredSquadratsFlow, locationFlow, rideStateFlow) { exploredSquadrats, location, rideState -> StreamData(exploredSquadrats, location, rideState) }
                .filter { (_, _, rideState) -> rideState is RideState.Recording }
                .filter { (_, location, _) ->
                    val squadrat = coordsToSquadrat(location.lat, location.lng)

                    val tileCorners = listOf(
                        CurrentCorner.TOP_LEFT.getCoords(squadrat),
                        CurrentCorner.TOP_RIGHT.getCoords(squadrat),
                        CurrentCorner.BOTTOM_RIGHT.getCoords(squadrat),
                        CurrentCorner.BOTTOM_LEFT.getCoords(squadrat)
                    )

                    val point = Point.fromLngLat(location.lng, location.lat)

                    // Check if point is inside the squadrat boundaries with margin
                    val newSquadrat = point.longitude() > tileCorners[0].longitude() + margin &&
                        point.longitude() < tileCorners[1].longitude() - margin &&
                        point.latitude() < tileCorners[0].latitude() - margin &&
                        point.latitude() > tileCorners[3].latitude() + margin

                    if (newSquadrat) {
                        true
                    } else {
                        val squadratinho = coordsToSquadratinho(location.lat, location.lng)

                        val tileCorners = listOf(
                            CurrentCorner.TOP_LEFT.getCoords(squadratinho),
                            CurrentCorner.TOP_RIGHT.getCoords(squadratinho),
                            CurrentCorner.BOTTOM_RIGHT.getCoords(squadratinho),
                            CurrentCorner.BOTTOM_LEFT.getCoords(squadratinho)
                        )

                        // Check if point is inside the squadratinho boundaries with margin
                        point.longitude() > tileCorners[0].longitude() + margin &&
                            point.longitude() < tileCorners[1].longitude() - margin &&
                            point.latitude() < tileCorners[0].latitude() - margin &&
                            point.latitude() > tileCorners[3].latitude() + margin
                    }
                }.filter { (exploredSquadrats, location) ->
                    val squadrat = coordsToSquadrat(location.lat, location.lng)
                    val squadratinho = coordsToSquadratinho(location.lat, location.lng)

                    // New squadrat
                    (!exploredSquadrats.exploredSquadrats.contains(squadrat) && !exploredSquadrats.recentlyExploredNewSquadrats.contains(squadrat))
                    // New recent squadrat
                    || !exploredSquadrats.recentlyExploredSquadrats.contains(squadrat)
                    // New squadratinho
                    || (!exploredSquadrats.exploredSquadratinhos.contains(squadratinho) && !exploredSquadrats.recentlyExploredNewSquadratinhos.contains(squadratinho))
                }.collect { (exploredSquadrats, location) ->
                    val currentSquadrat = coordsToSquadrat(location.lat, location.lng)
                    val currentSquadratinho = coordsToSquadratinho(location.lat, location.lng)

                    val isNewSquadrat = !exploredSquadrats.exploredSquadrats.contains(currentSquadrat) && !exploredSquadrats.recentlyExploredNewSquadrats.contains(currentSquadrat)
                    val isRecentSquadrat = !exploredSquadrats.recentlyExploredSquadrats.contains(currentSquadrat)
                    val isNewSquadratinho = !exploredSquadrats.exploredSquadratinhos.contains(currentSquadratinho) && !exploredSquadrats.recentlyExploredNewSquadratinhos.contains(currentSquadratinho)

                    if (isNewSquadrat) Log.i(TAG, "New squadrat explored: ${location.lat}, ${location.lng}")
                    else if (isRecentSquadrat) Log.i(TAG, "Squadrat explored: ${location.lat}, ${location.lng}")
                    if (isNewSquadratinho) Log.i(TAG, "New squadratinho explored: ${location.lat}, ${location.lng}")

                    if (isNewSquadrat) {
                        val msg = when (exploredSquadrats.recentlyExploredNewSquadrats.size) {
                            0, 1 -> "New squadrat explored"
                            2 -> "2nd new squadrat!"
                            3 -> "3rd new squadrat!"
                            else -> "${exploredSquadrats.recentlyExploredNewSquadrats.size}th new squadrat!"
                        }

                        karooSystem.karooSystemService.dispatch(
                            InRideAlert(id = "newtile-${System.currentTimeMillis()}",
                                icon = R.drawable.crosshair,
                                title = "Squadrating",
                                detail = msg,
                                autoDismissMs = 5_000L,
                                backgroundColor = R.color.lime,
                                textColor = R.color.black
                            )
                        )

                        karooSystem.karooSystemService.dispatch(
                            PlayBeepPattern(listOf(
                                PlayBeepPattern.Tone(4_000, 500),
                                PlayBeepPattern.Tone(4_500, 500),
                                PlayBeepPattern.Tone(4_000, 500)
                            ))
                        )

                        mediaPlayer?.start()
                    }

                    context.exploredSquadratsDataStore.updateData { data ->
                        val exploredSquadratsSet = data.exploredSquadratsList.map { Squadrat(it.x, it.y) }.toSet()
                        val exploredSquadrats = if (isNewSquadrat) exploredSquadratsSet + currentSquadrat else exploredSquadratsSet

                        val exploredSquadratinhosSet = data.exploredSquadratinhosList.map { Squadratinho(it.x, it.y) }.toSet()
                        val exploredSquadratinhos = if (isNewSquadratinho) exploredSquadratinhosSet + currentSquadratinho else exploredSquadratinhosSet

                        val recentlyExploredSquadratsSet = data.recentlyExploredSquadratsList.map { Squadrat(it.x, it.y) }.toSet()
                        val recentlyExploredSquadrats = if (isRecentSquadrat) recentlyExploredSquadratsSet + currentSquadrat else recentlyExploredSquadratsSet

                        val recentlyExploredNewSquadratsSet = data.recentlyExploredNewSquadratsList.map { Squadrat(it.x, it.y) }.toSet()
                        val recentlyExploredNewSquadrats = if (isNewSquadrat) recentlyExploredNewSquadratsSet + currentSquadrat else recentlyExploredNewSquadratsSet

                        val recentlyExploredNewSquadratinhosSet = data.recentlyExploredNewSquadratinhosList.map { Squadratinho(it.x, it.y) }.toSet()
                        val recentlyExploredNewSquadratinhos = if (isNewSquadratinho) recentlyExploredNewSquadratinhosSet + currentSquadratinho else recentlyExploredNewSquadratinhosSet

                        val updatedUbersquadrat = Ubersquadrat.getBiggestUbersquadrat(exploredSquadrats)
                        val updatedUbersquadratinho = Ubersquadratinho.getBiggestUbersquadratinho(exploredSquadratinhos)
                        val updatedYard = calcYard(exploredSquadrats)
                        val updatedYardinho = calcYardinho(exploredSquadratinhos)

                        if (updatedUbersquadrat != null && updatedUbersquadrat!!.size > data.biggestUbersquadratSize) {
                            karooSystem.karooSystemService.dispatch(
                                InRideAlert(id = "incrubersquadrat-${System.currentTimeMillis()}",
                                    icon = R.drawable.crosshair,
                                    title = "Squadrating",
                                    detail = "Ubersquadrat increased to ${updatedUbersquadrat!!.size}x${updatedUbersquadrat!!.size}!",
                                    autoDismissMs = 15_000L,
                                    backgroundColor = R.color.lime,
                                    textColor = R.color.black
                                )
                            )
                        }

                        data.toBuilder()
                            .clearRecentlyExploredSquadrats()
                            .addAllRecentlyExploredSquadrats(recentlyExploredSquadrats.map { squadrat -> bzh.klabz.squadrating.data.Squadrat.newBuilder().setX(squadrat.x).setY(squadrat.y).build() })
                            .clearRecentlyExploredNewSquadrats()
                            .addAllRecentlyExploredNewSquadrats(recentlyExploredNewSquadrats.map { squadrat -> bzh.klabz.squadrating.data.Squadrat.newBuilder().setX(squadrat.x).setY(squadrat.y).build() })
                            .clearRecentlyExploredNewSquadratinhos()
                            .addAllRecentlyExploredNewSquadratinhos(recentlyExploredNewSquadratinhos.map { squadratinho -> bzh.klabz.squadrating.data.Squadratinho.newBuilder().setX(squadratinho.x).setY(squadratinho.y).build() })
                            .clearExploredSquadrats()
                            .addAllExploredSquadrats(exploredSquadrats.map { squadrat -> bzh.klabz.squadrating.data.Squadrat.newBuilder().setX(squadrat.x).setY(squadrat.y).build() })
                            .clearExploredSquadratinhos()
                            .addAllExploredSquadratinhos(exploredSquadratinhos.map { squadratinho -> bzh.klabz.squadrating.data.Squadratinho.newBuilder().setX(squadratinho.x).setY(squadratinho.y).build() })
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
                }
        }
    }
}
