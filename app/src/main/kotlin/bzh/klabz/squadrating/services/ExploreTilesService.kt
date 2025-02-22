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
import bzh.klabz.squadrating.Squadrat
import bzh.klabz.squadrating.coordsToSquadrat
import bzh.klabz.squadrating.datastores.exploredSquadratsDataStore
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
    fun startJob(context: Context): Job {
        val mediaPlayer = MediaPlayer.create(context, R.raw.alert6)

        return CoroutineScope(Dispatchers.IO).launch {
            val exploredSquadratsFlow = context.exploredSquadratsDataStore.data
                .map {
                    val exploredSquadrats = it.exploredSquadratsList.map { tile -> Squadrat(tile.x, tile.y) }.toSet()
                    val recentlyExploredSquadrats = it.recentlyExploredSquadratsList.map { tile -> Squadrat(tile.x, tile.y) }.toSet()
                    val recentlyExploredNewSquadrats = it.recentlyExploredNewSquadratsList.map { tile -> Squadrat(tile.x, tile.y) }.toSet()
                    val ubersquadrat = if(it.biggestUbersquadratX != 0 && it.biggestUbersquadratY != 0 && it.biggestUbersquadratSize != 0) Ubersquadrat(it.biggestUbersquadratX, it.biggestUbersquadratY, it.biggestUbersquadratSize) else null

                    ExploredSquadratsData(exploredSquadrats, recentlyExploredSquadrats, recentlyExploredNewSquadrats, ubersquadrat)
                }

            val locationFlow = karooSystem.stream<OnLocationChanged>()
            val rideStateFlow = karooSystem.stream<RideState>()

            data class StreamData(val exploredSquadrats: ExploredSquadratsData, val location: OnLocationChanged, val rideState: RideState)

            combine(exploredSquadratsFlow, locationFlow, rideStateFlow) { exploredSquadrats, location, rideState -> StreamData(exploredSquadrats, location, rideState) }
                .filter { (_, _, rideState) -> rideState is RideState.Recording }
                .filter { (_, location, _) ->
                    val tile = coordsToSquadrat(location.lat, location.lng)

                    val tileCorners = listOf(
                        CurrentCorner.TOP_LEFT.getCoords(tile),
                        CurrentCorner.TOP_RIGHT.getCoords(tile),
                        CurrentCorner.BOTTOM_RIGHT.getCoords(tile),
                        CurrentCorner.BOTTOM_LEFT.getCoords(tile)
                    )

                    val point = Point.fromLngLat(location.lng, location.lat)

                    // Convert margin from meters to degrees (approximate)
                    val margin = TurfConversion.convertLength(
                        20.0,
                        TurfConstants.UNIT_METERS,
                        TurfConstants.UNIT_DEGREES
                    )

                    // Check if point is inside the tile boundaries with margin
                    point.longitude() > tileCorners[0].longitude() + margin &&
                            point.longitude() < tileCorners[1].longitude() - margin &&
                            point.latitude() < tileCorners[0].latitude() - margin &&
                            point.latitude() > tileCorners[3].latitude() + margin
                }.filter { (exploredSquadrats, location) ->
                    val tile = coordsToSquadrat(location.lat, location.lng)

                    // New tile
                    !exploredSquadrats.exploredSquadrats.contains(tile) && !exploredSquadrats.recentlyExploredNewSquadrats.contains(tile)
                    // New recent tile
                    || !exploredSquadrats.recentlyExploredSquadrats.contains(tile)
                }.collect { (exploredSquadrats, location) ->
                    val currentSquadrat = coordsToSquadrat(location.lat, location.lng)
                    val isNew = !exploredSquadrats.exploredSquadrats.contains(currentSquadrat) && !exploredSquadrats.recentlyExploredNewSquadrats.contains(currentSquadrat)

                    if (isNew) Log.i(TAG, "New tile explored: ${location.lat}, ${location.lng}")
                    else Log.i(TAG, "Squadrat explored: ${location.lat}, ${location.lng}")

                    if (isNew) {
                        val msg = when (exploredSquadrats.recentlyExploredNewSquadrats.size) {
                            0, 1 -> "New tile explored"
                            2 -> "2nd new tile!"
                            3 -> "3rd new tile!"
                            else -> "${exploredSquadrats.recentlyExploredNewSquadrats.size}th new tile!"
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
                        val exploredSquadrats = if (isNew) exploredSquadratsSet + currentSquadrat else exploredSquadratsSet
                        val recentlyExploredSquadrats = data.recentlyExploredSquadratsList.map { Squadrat(it.x, it.y) }.toSet() + currentSquadrat
                        val recentlyExploredNewSquadratsSet = data.recentlyExploredNewSquadratsList.map { Squadrat(it.x, it.y) }.toSet()
                        val recentlyExploredNewSquadrats = if (isNew) recentlyExploredNewSquadratsSet + currentSquadrat else recentlyExploredNewSquadratsSet
                        val updatedUbersquadrat = Ubersquadrat.getBiggestUbersquadrat(exploredSquadrats)

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
                            .addAllRecentlyExploredSquadrats(recentlyExploredSquadrats.map { tile -> bzh.klabz.squadrating.data.Squadrat.newBuilder().setX(tile.x).setY(tile.y).build() })
                            .clearRecentlyExploredNewSquadrats()
                            .addAllRecentlyExploredNewSquadrats(recentlyExploredNewSquadrats.map { tile -> bzh.klabz.squadrating.data.Squadrat.newBuilder().setX(tile.x).setY(tile.y).build() })
                            .clearExploredSquadrats()
                            .addAllExploredSquadrats(exploredSquadrats.map { tile -> bzh.klabz.squadrating.data.Squadrat.newBuilder().setX(tile.x).setY(tile.y).build() })
                            .setBiggestUbersquadratX(updatedUbersquadrat?.x ?: 0)
                            .setBiggestUbersquadratY(updatedUbersquadrat?.y ?: 0)
                            .setBiggestUbersquadratSize(updatedUbersquadrat?.size ?: 0)
                            .build()
                    }
                }
        }
    }
}
