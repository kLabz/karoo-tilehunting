package bzh.klabz.squadrating.services

import android.content.Context
import android.util.Log
import bzh.klabz.squadrating.Squadrat
import bzh.klabz.squadrating.SquadratingExtension.Companion.TAG
import bzh.klabz.squadrating.Ubersquadrat
import bzh.klabz.squadrating.data.Activity
import bzh.klabz.squadrating.datastores.activityLinesDataStore
import bzh.klabz.squadrating.datastores.exploredSquadratsDataStore
import bzh.klabz.squadrating.datastores.userPreferencesDataStore
import com.mapbox.geojson.LineString
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
                                val alreadyExploredSquadrats =
                                    exploredSquadrats.exploredSquadratsList.map { Squadrat(it.x, it.y) }.toSet()
                                val newSquadrats = activities.flatMap {
                                    it.tiles.map { Squadrat(it.x, it.y) }
                                }.toSet()
                                activityCount += activities.size
                                val updatedExploredSquadrats = alreadyExploredSquadrats + newSquadrats
                                val updatedExploredSquadratsProto = updatedExploredSquadrats.map {
                                    bzh.klabz.squadrating.data.Squadrat.newBuilder().setX(it.x).setY(it.y).build()
                                }
                                Log.d(TAG, "New explored squadrats count: ${updatedExploredSquadrats.size}, $activityCount activities")
                                val updatedUbersquadrat = Ubersquadrat.getBiggestUbersquadrat(updatedExploredSquadrats)
                                Log.d(TAG, "New ubersquadrat: $updatedUbersquadrat")

                                exploredSquadrats.toBuilder()
                                    .setDownloadedActivities(activityCount)
                                    .clearExploredSquadrats()
                                    .addAllExploredSquadrats(updatedExploredSquadratsProto)
                                    .setBiggestUbersquadratX(updatedUbersquadrat?.x ?: 0)
                                    .setBiggestUbersquadratY(updatedUbersquadrat?.y ?: 0)
                                    .setBiggestUbersquadratSize(updatedUbersquadrat?.size ?: 0)
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
