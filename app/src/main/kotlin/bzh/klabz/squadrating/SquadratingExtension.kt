package bzh.klabz.squadrating

import android.content.Context
import android.util.Log
import bzh.klabz.squadrating.calcYard
import bzh.klabz.squadrating.calcYardinho
import bzh.klabz.squadrating.datastores.exploredSquadratsDataStore
import bzh.klabz.squadrating.datatypes.DualAllTimeDataType
import bzh.klabz.squadrating.datatypes.DualRecentDataType
import bzh.klabz.squadrating.datatypes.ExploredSquadratsDataType
import bzh.klabz.squadrating.datatypes.RecentlyExploredNewSquadratsDataType
import bzh.klabz.squadrating.datatypes.RecentlyExploredSquadratsDataType
import bzh.klabz.squadrating.datatypes.UbersquadratSizeDataType
import bzh.klabz.squadrating.services.ClusterDrawService
import bzh.klabz.squadrating.services.ExploreSquadratsService
import bzh.klabz.squadrating.services.KarooSystemServiceProvider
import bzh.klabz.squadrating.services.StatshuntersDownloadService
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

class SquadratingExtension : KarooExtension("squadrating", "1.0-beta6") {
    companion object {
        const val TAG = "squadrating"
    }

    private val karooSystem: KarooSystemServiceProvider by inject()
    private val statshuntersDownloadService: StatshuntersDownloadService by inject()
    private val squadratDrawer: ClusterDrawService by inject()
    private val exploreSquadratsService: ExploreSquadratsService by inject()
    private val context: Context by inject()

    private var updateLastKnownGpsPositionJob: Job? = null
    private var serviceJob: Job? = null
    private var statshuntersDownloadJob: Job? = null
    private var addExploredSquadratsJob: Job? = null

    override val types by lazy {
        listOf(
            DualAllTimeDataType(applicationContext),
            DualRecentDataType(applicationContext),
            ExploredSquadratsDataType(karooSystem.karooSystemService, applicationContext),
            RecentlyExploredSquadratsDataType(karooSystem.karooSystemService, applicationContext),
            RecentlyExploredNewSquadratsDataType(karooSystem.karooSystemService, applicationContext),
            UbersquadratSizeDataType(karooSystem.karooSystemService, applicationContext)
        )
    }

    data class ExploredSquadratsData(
        // Squadrats
        val exploredSquadrats: Set<Squadrat>,
        val recentlyExploredSquadrats: Set<Squadrat>,
        val recentlyExploredNewSquadrats: Set<Squadrat>,
        val ubersquadrat: Ubersquadrat?,
        val yard: Int, // TODO: with area?

        // Squadratinhos
        val exploredSquadratinhos: Set<Squadratinho>,
        // TODO: val recentlyExploredSquadratinhos: Set<Squadratinho>, (?)
        val recentlyExploredNewSquadratinhos: Set<Squadratinho>,
        val ubersquadratinho: Ubersquadratinho?,
        val yardinho: Int, // TODO: with area?
    )

    override fun startMap(emitter: Emitter<MapEffect>) {
        Log.d(TAG, "Starting map effect")

        squadratDrawer.startJob(emitter)
    }

    private val newSquadratsField by lazy {
        DeveloperField(
            fieldDefinitionNumber = 0,
            fitBaseTypeId = 132, // FitBaseType.UInt16
            fieldName = "New squadrats",
            units = "squadrats",
        )
    }

    private val newSquadratinhosField by lazy {
        DeveloperField(
            fieldDefinitionNumber = 1,
            fitBaseTypeId = 132, // FitBaseType.UInt16
            fieldName = "New squadratinhos",
            units = "squadratinhos",
        )
    }

    private val exploredSquadratsField by lazy {
        DeveloperField(
            fieldDefinitionNumber = 2,
            fitBaseTypeId = 132, // FitBaseType.UInt16
            fieldName = "Explored squadrats",
            units = "squadrats",
        )
    }

    private val ubersquadratSizeField by lazy {
        DeveloperField(
            fieldDefinitionNumber = 3,
            fitBaseTypeId = 131, // FitBaseType.UInt8
            fieldName = "Ubersquadrat size",
            units = "squadrats",
        )
    }

    private val ubersquadratinhoSizeField by lazy {
        DeveloperField(
            fieldDefinitionNumber = 4,
            fitBaseTypeId = 131, // FitBaseType.UInt8
            fieldName = "Ubersquadratinho size",
            units = "squadratinhos",
        )
    }

    override fun startFit(emitter: Emitter<FitEffect>) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            val rideStateFlow = karooSystem.stream<RideState>()
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

            var lastSquadratsCount:UShort = 0u;
            var lastNewSquadratsCount:UShort = 0u;
            var lastNewSquadratinhosCount:UShort = 0u;
            var lastUbersquadratSize:UByte = 0u;
            var lastUbersquadratinhoSize:UByte = 0u;

            combine(exploredSquadratsFlow, rideStateFlow) { exploredSquadrats, rideState -> Pair(exploredSquadrats, rideState) }
                .filter { (exploredSquadrats, rideState) ->
                    !(rideState is RideState.Idle) && (
                        (lastSquadratsCount != exploredSquadrats.recentlyExploredSquadrats.size.toUShort())
                        || (lastNewSquadratsCount != exploredSquadrats.recentlyExploredNewSquadrats.size.toUShort())
                        || (lastNewSquadratinhosCount != exploredSquadrats.recentlyExploredNewSquadratinhos.size.toUShort())
                        || (lastUbersquadratSize != exploredSquadrats.ubersquadrat?.size?.toUByte() ?: 0u)
                        || (lastUbersquadratinhoSize != exploredSquadrats.ubersquadratinho?.size?.toUByte() ?: 0u)
                    )
                }
                .collect { (exploredSquadrats, rideState) ->
                    var lastSquadratsCount_ = exploredSquadrats.recentlyExploredSquadrats.size.toUShort()
                    if (lastSquadratsCount != lastSquadratsCount_) {
                        Log.i(TAG, "Writing explored squadrats count: ${lastSquadratsCount_}")
                        lastSquadratsCount = lastSquadratsCount_
                        emitter.onNext(WriteToRecordMesg(FieldValue(exploredSquadratsField, lastSquadratsCount.toDouble())))
                    }
                    if (rideState is RideState.Paused) emitter.onNext(WriteToSessionMesg(FieldValue(exploredSquadratsField, lastSquadratsCount.toDouble())))

                    var lastNewSquadratsCount_ = exploredSquadrats.recentlyExploredNewSquadrats.size.toUShort()
                    if (lastNewSquadratsCount != lastNewSquadratsCount_) {
                        Log.i(TAG, "Writing new squadrats count: ${lastNewSquadratsCount_}")
                        lastNewSquadratsCount = lastNewSquadratsCount_
                        emitter.onNext(WriteToRecordMesg(FieldValue(newSquadratsField, lastNewSquadratsCount.toDouble())))
                    }
                    if (rideState is RideState.Paused) emitter.onNext(WriteToSessionMesg(FieldValue(exploredSquadratsField, lastSquadratsCount.toDouble())))

                    var lastNewSquadratinhosCount_ = exploredSquadrats.recentlyExploredNewSquadratinhos.size.toUShort()
                    if (lastNewSquadratinhosCount != lastNewSquadratinhosCount_) {
                        Log.i(TAG, "Writing new squadratinhos count: ${lastNewSquadratinhosCount_}")
                        lastNewSquadratinhosCount = lastNewSquadratinhosCount_
                        emitter.onNext(WriteToRecordMesg(FieldValue(newSquadratinhosField, lastNewSquadratinhosCount.toDouble())))
                    }
                    if (rideState is RideState.Paused) emitter.onNext(WriteToSessionMesg(FieldValue(exploredSquadratsField, lastSquadratsCount.toDouble())))

                    var lastUbersquadratSize_ = exploredSquadrats.ubersquadrat?.size?.toUByte() ?: 0u
                    if (lastUbersquadratSize != lastUbersquadratSize_) {
                        Log.i(TAG, "Writing new ubersquadrat size: ${lastUbersquadratSize_}")
                        lastUbersquadratSize = lastUbersquadratSize_
                        emitter.onNext(WriteToRecordMesg(FieldValue(ubersquadratSizeField, lastUbersquadratSize.toDouble())))
                    }
                    if (rideState is RideState.Paused) emitter.onNext(WriteToSessionMesg(FieldValue(ubersquadratSizeField, lastUbersquadratSize.toDouble())))

                    var lastUbersquadratinhoSize_ = exploredSquadrats.ubersquadratinho?.size?.toUByte() ?: 0u
                    if (lastUbersquadratinhoSize != lastUbersquadratinhoSize_) {
                        Log.i(TAG, "Writing new ubersquadratinho size: ${lastUbersquadratinhoSize_}")
                        lastUbersquadratinhoSize = lastUbersquadratinhoSize_
                        emitter.onNext(WriteToRecordMesg(FieldValue(ubersquadratSizeField, lastUbersquadratinhoSize.toDouble())))
                    }
                    if (rideState is RideState.Paused) emitter.onNext(WriteToSessionMesg(FieldValue(ubersquadratinhoSizeField, lastUbersquadratinhoSize.toDouble())))
                }
        }

        emitter.setCancellable {
            job.cancel()
        }
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "Starting squadrating extension")

        updateLastKnownGpsPositionJob = CoroutineScope(Dispatchers.IO).launch {
            karooSystem.stream<OnLocationChanged>().throttle(60_000L).collect { event ->
                applicationContext.lastKnownGpsCoordsDataStore.updateData { location ->
                    location.toBuilder().setLatitude(event.lat).setLongitude(event.lng).build()
                }

                Log.d(TAG, "Updated last known GPS position: ${event.lat}, ${event.lng}")
            }
        }

        addExploredSquadratsJob = exploreSquadratsService.startJob(this)
        statshuntersDownloadJob = statshuntersDownloadService.startJob()
    }

    override fun onDestroy() {
        serviceJob?.cancel()
        serviceJob = null

        statshuntersDownloadJob?.cancel()
        statshuntersDownloadJob = null

        updateLastKnownGpsPositionJob?.cancel()
        updateLastKnownGpsPositionJob = null

        addExploredSquadratsJob?.cancel()
        addExploredSquadratsJob = null

        super.onDestroy()
    }
}
