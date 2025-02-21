package de.klabz.karootilehunting.datatypes

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.compose.ui.unit.DpSize
import de.klabz.karootilehunting.R
import de.klabz.karootilehunting.datastores.exploredTilesDataStore
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.ViewConfig
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class DualRecentDataType(
    private val karooSystem: KarooSystemService,
    private val applicationContext: Context
) : DataTypeImpl("karoo-tilehunting", "dual_trip") {
    protected val glance = GlanceRemoteViews()
    private var viewjob: Job? = null

    override fun startStream(emitter: Emitter<StreamState>) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                while (true) {
                    emitter.onNext(StreamState.Streaming(
                        DataPoint(
                            dataTypeId,
                            mapOf(DataType.Field.SINGLE to 1.0),
                            extension
                        )
                    ))
                   delay(800L)
                }
            } catch (e: CancellationException) {
                Log.d(extension, "DualRecent Stream cancelled")
            } catch (e: Exception) {
                Log.e(extension, "DualRecent Stream error", e)
                emitter.onError(e)
            }
        }
        emitter.setCancellable {
            job.cancel()
        }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        val scope = CoroutineScope(Dispatchers.IO + Job())

        viewjob = scope.launch {
            try {
            applicationContext.exploredTilesDataStore.data.collect { exploredTiles ->
                val recentTiles = exploredTiles.recentlyExploredTilesCount
                val newTiles = exploredTiles.recentlyExploredNewTilesCount
                var view = glance.compose(context, DpSize.Unspecified) {
                    DoubleScreenSelector(
                        true,
                        recentTiles.toDouble(),
                        newTiles.toDouble(),
                        R.drawable.trip,
                        R.drawable.location_plus,
                        "Recent",
                        "New",
                        // FieldSize.MEDIUM,
                        false, // TODO try true
                        // ViewConfig.Alignment.RIGHT,
                        "Recent tiles",
                        false
                    )
                }.remoteViews
                emitter.updateView(view)
            }

            } catch (e: CancellationException) {
                Log.d(extension, "DualRecent ViewJob cancelled")
            } catch (e: Exception) {
                Log.e(extension, "DualRecent ViewJob error", e)
                if (!scope.isActive) return@launch

                viewjob?.let {
                    if (it.isActive) {
                        it.cancel()
                        Log.d(extension, "DualRecent ViewJob cancelled")
                    }
                }
                viewjob = null

                delay(1000)
                startView(context, config, emitter)
            }
        }

        emitter.setCancellable {
            viewjob!!.cancel()
        }
    }
}
