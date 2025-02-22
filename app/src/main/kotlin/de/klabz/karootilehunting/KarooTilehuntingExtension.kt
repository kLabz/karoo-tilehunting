package de.klabz.karootilehunting

import android.content.Context
import android.util.Log
import de.klabz.karootilehunting.datastores.exploredTilesDataStore
import de.klabz.karootilehunting.datatypes.DualAllTimeDataType
import de.klabz.karootilehunting.datatypes.DualRecentDataType
import de.klabz.karootilehunting.datatypes.ExploredTilesDataType
import de.klabz.karootilehunting.datatypes.RecentlyExploredTilesDataType
import de.klabz.karootilehunting.datatypes.RecentlyExploredNewTilesDataType
import de.klabz.karootilehunting.datatypes.SquareSizeDataType
import de.klabz.karootilehunting.services.ClusterDrawService
import de.klabz.karootilehunting.services.ExploreTilesService
import de.klabz.karootilehunting.services.KarooSystemServiceProvider
import de.klabz.karootilehunting.services.TileDownloadService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DeveloperField
import io.hammerhead.karooext.models.FieldValue
import io.hammerhead.karooext.models.FitEffect
import io.hammerhead.karooext.models.MapEffect
import io.hammerhead.karooext.models.OnLocationChanged
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.WriteToRecordMesg
import io.hammerhead.karooext.models.WriteToSessionMesg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class KarooTilehuntingExtension : KarooExtension("karoo-tilehunting", "1.0-beta6") {
    companion object {
        const val TAG = "karoo-tilehunting"
    }

    private val karooSystem: KarooSystemServiceProvider by inject()
    private val tileDownloadService: TileDownloadService by inject()
    private val tileDrawer: ClusterDrawService by inject()
    private val exploreTilesService: ExploreTilesService by inject()
    private val context: Context by inject()

    private var updateLastKnownGpsPositionJob: Job? = null
    private var serviceJob: Job? = null
    private var tileDownloadJob: Job? = null
    private var addExploredTilesJob: Job? = null

    override val types by lazy {
        listOf(
            DualAllTimeDataType(applicationContext),
            DualRecentDataType(applicationContext),
            ExploredTilesDataType(karooSystem.karooSystemService, applicationContext),
            RecentlyExploredTilesDataType(karooSystem.karooSystemService, applicationContext),
            RecentlyExploredNewTilesDataType(karooSystem.karooSystemService, applicationContext),
            SquareSizeDataType(karooSystem.karooSystemService, applicationContext)
        )
    }

    data class ExploredTilesData(val exploredTiles: Set<Tile>, val recentlyExploredTiles: Set<Tile>, val recentlyExploredNewTiles: Set<Tile>, val square: Square?)

    override fun startMap(emitter: Emitter<MapEffect>) {
        Log.d(TAG, "Starting map effect")

        tileDrawer.startJob(emitter)
    }

    private val newTilesField by lazy {
        DeveloperField(
            fieldDefinitionNumber = 0,
            fitBaseTypeId = 132, // FitBaseType.UInt16
            fieldName = "New tiles",
            units = "tiles",
        )
    }

    private val exploredTilesField by lazy {
        DeveloperField(
            fieldDefinitionNumber = 1,
            fitBaseTypeId = 132, // FitBaseType.UInt16
            fieldName = "Explored tiles",
            units = "tiles",
        )
    }

    private val squareSizeField by lazy {
        DeveloperField(
            fieldDefinitionNumber = 2,
            fitBaseTypeId = 131, // FitBaseType.UInt8
            fieldName = "Square size",
            units = "squares",
        )
    }

    override fun startFit(emitter: Emitter<FitEffect>) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            val rideStateFlow = karooSystem.stream<RideState>()
            val exploredTilesFlow = context.exploredTilesDataStore.data
                .map {
                    val exploredTiles = it.exploredTilesList.map { tile -> Tile(tile.x, tile.y) }.toSet()
                    val recentlyExploredTiles = it.recentlyExploredTilesList.map { tile -> Tile(tile.x, tile.y) }.toSet()
                    val recentlyExploredNewTiles = it.recentlyExploredNewTilesList.map { tile -> Tile(tile.x, tile.y) }.toSet()
                    val square = if(it.biggestSquareX != 0 && it.biggestSquareY != 0 && it.biggestSquareSize != 0) Square(it.biggestSquareX, it.biggestSquareY, it.biggestSquareSize) else null

                    ExploredTilesData(exploredTiles, recentlyExploredTiles, recentlyExploredNewTiles, square)
                }

            var lastTilesCount:UShort = 0u;
            var lastNewTilesCount:UShort = 0u;
            var lastSquareSize:UByte = 0u;

            combine(exploredTilesFlow, rideStateFlow) { exploredTiles, rideState -> Pair(exploredTiles, rideState) }
                .filter { (exploredTiles, rideState) ->
                    !(rideState is RideState.Idle) && (
                        (lastTilesCount != exploredTiles.recentlyExploredTiles.size.toUShort())
                        || (lastNewTilesCount != exploredTiles.recentlyExploredNewTiles.size.toUShort())
                        || (lastSquareSize != exploredTiles.square?.size?.toUByte() ?: 0u)
                    )
                }
                .collect { (exploredTiles, rideState) ->
                    var lastTilesCount_ = exploredTiles.recentlyExploredTiles.size.toUShort()
                    if (lastTilesCount != lastTilesCount_) {
                        Log.i(TAG, "Writing explored tiles count: ${lastTilesCount_}")
                        lastTilesCount = lastTilesCount_
                        emitter.onNext(WriteToRecordMesg(FieldValue(exploredTilesField, lastTilesCount.toDouble())))
                    }
                    if (rideState is RideState.Paused) emitter.onNext(WriteToSessionMesg(FieldValue(exploredTilesField, lastTilesCount.toDouble())))

                    var lastNewTilesCount_ = exploredTiles.recentlyExploredNewTiles.size.toUShort()
                    if (lastNewTilesCount != lastNewTilesCount_) {
                        Log.i(TAG, "Writing new tiles count: ${lastNewTilesCount_}")
                        lastNewTilesCount = lastNewTilesCount_
                        emitter.onNext(WriteToRecordMesg(FieldValue(newTilesField, lastNewTilesCount.toDouble())))
                    }
                    if (rideState is RideState.Paused) emitter.onNext(WriteToSessionMesg(FieldValue(exploredTilesField, lastTilesCount.toDouble())))

                    var lastSquareSize_ = exploredTiles.square?.size?.toUByte() ?: 0u
                    if (lastSquareSize != lastSquareSize_) {
                        Log.i(TAG, "Writing new square size: ${lastSquareSize_}")
                        lastSquareSize = lastSquareSize_
                        emitter.onNext(WriteToRecordMesg(FieldValue(squareSizeField, lastSquareSize.toDouble())))
                    }
                    if (rideState is RideState.Paused) emitter.onNext(WriteToSessionMesg(FieldValue(squareSizeField, lastSquareSize.toDouble())))
                }
        }

        emitter.setCancellable {
            job.cancel()
        }
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "Starting karoo tilehunting extension")

        updateLastKnownGpsPositionJob = CoroutineScope(Dispatchers.IO).launch {
            karooSystem.stream<OnLocationChanged>().throttle(60_000L).collect { event ->
                applicationContext.lastKnownGpsCoordsDataStore.updateData { location ->
                    location.toBuilder().setLatitude(event.lat).setLongitude(event.lng).build()
                }

                Log.d(TAG, "Updated last known GPS position: ${event.lat}, ${event.lng}")
            }
        }

        addExploredTilesJob = exploreTilesService.startJob(this)

        tileDownloadJob = tileDownloadService.startJob()
    }

    override fun onDestroy() {
        serviceJob?.cancel()
        serviceJob = null

        tileDownloadJob?.cancel()
        tileDownloadJob = null

        updateLastKnownGpsPositionJob?.cancel()
        updateLastKnownGpsPositionJob = null

        addExploredTilesJob?.cancel()
        addExploredTilesJob = null

        super.onDestroy()
    }
}
