package de.klabz.karootilehunting.datatypes

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.Color
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
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class DualAllTimeDataType(
    private val karooSystem: KarooSystemService,
    private val applicationContext: Context
) : DataTypeImpl("karoo-tilehunting", "dual_alltime") {
    protected val glance = GlanceRemoteViews()

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
                Log.d(extension, "DualAllTime Stream cancelled")
            } catch (e: Exception) {
                Log.e(extension, "DualAllTime Stream error", e)
                emitter.onError(e)
            }
        }
        emitter.setCancellable {
            job.cancel()
        }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        val scope = CoroutineScope(Dispatchers.IO + Job())

        val configJob = scope.launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
            awaitCancellation()
        }

        val viewjob = scope.launch {
            applicationContext.exploredTilesDataStore.data.collect { exploredTiles ->
                val tiles = exploredTiles.exploredTilesCount
                val squareSize = exploredTiles.biggestSquareSize
                var view = glance.compose(context, DpSize.Unspecified) {
                    DoubleTypesVerticalScreen(
                        tiles.toString(),
                        "${squareSize}x${squareSize}",
                        R.drawable.been_here,
                        R.drawable.area,
                        Color(ContextCompat.getColor(applicationContext, R.color.icongreen))
                    )
                }.remoteViews
                emitter.updateView(view)
            }
        }

        emitter.setCancellable {
            configJob.cancel()
            viewjob.cancel()
        }
    }
}
