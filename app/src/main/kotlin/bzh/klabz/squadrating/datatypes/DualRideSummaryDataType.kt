package bzh.klabz.squadrating.datatypes

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.core.content.ContextCompat
import androidx.glance.GlanceModifier
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import bzh.klabz.squadrating.R
import bzh.klabz.squadrating.streamDataFlow
import bzh.klabz.squadrating.datastores.exploredSquadratsDataStore
import bzh.klabz.squadrating.SquadratingExtension.Companion.TAG
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.ShowCustomStreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import java.text.DecimalFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// TODO: move to separate extension
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class DualRideSummaryDataType(
    private val karooSystem: KarooSystemService,
    private val applicationContext: Context
) : DataTypeImpl("squadrating", "dual_ride_summary") {
    protected val glance = GlanceRemoteViews()

    private fun collectDouble(stream:StreamState):Double {
        return when (stream) {
            StreamState.Idle, StreamState.Searching, StreamState.NotAvailable -> 0.toDouble()
            else -> (stream as? StreamState.Streaming)?.dataPoint?.singleValue?.toDouble() ?: 0.toDouble()
        }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        val scope = CoroutineScope(Dispatchers.IO + Job())

        val configJob = scope.launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
            emitter.onNext(ShowCustomStreamState(message = "", color = null))
            awaitCancellation()
        }

        val viewjob = scope.launch {
            val distanceFlow = karooSystem.streamDataFlow(DataType.Type.DISTANCE)
            val elevationFlow = karooSystem.streamDataFlow(DataType.Type.ELEVATION_GAIN)

            combine(distanceFlow, elevationFlow) { (distance, elevation) -> Pair(distance, elevation) }
            .distinctUntilChanged()
            .collect { (distanceStream, elevationStream) ->
                val distance = collectDouble(distanceStream)
                val elevation = collectDouble(elevationStream)
                // Log.d(TAG, "Collected ${distance}, ${elevation}")

                var view = glance.compose(context, DpSize.Unspecified) {
                    Box(modifier = GlanceModifier.fillMaxSize()) {
                        DoubleTypesVerticalScreen(
                            "${DecimalFormat("0.0").format(distance)}",
                            "${elevation.roundToInt()}",
                            R.drawable.ic_distance,
                            R.drawable.ic_ascent,
                            Color(ContextCompat.getColor(applicationContext, R.color.icongreen))
                        )
                    }
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
