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
import java.text.DateFormat
import java.util.Calendar
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
class DualRideTimeDataType(
    private val karooSystem: KarooSystemService,
    private val applicationContext: Context
) : DataTypeImpl("squadrating", "dual_ride_time") {
    protected val glance = GlanceRemoteViews()

    private fun collectDouble(stream:StreamState):Double {
        return when (stream) {
            StreamState.Idle, StreamState.Searching, StreamState.NotAvailable -> 0.toDouble()
            else -> (stream as? StreamState.Streaming)?.dataPoint?.singleValue?.toDouble() ?: 0.toDouble()
        }
    }

    private fun formatTimeFromMinutes(totalMinutes: Int): String {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return "${hours}:${minutes.toString().padStart(2, '0')}"
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        val scope = CoroutineScope(Dispatchers.IO + Job())

        val configJob = scope.launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
            emitter.onNext(ShowCustomStreamState(message = "", color = null))
            awaitCancellation()
        }

        val viewjob = scope.launch {
            val totalFlow = karooSystem.streamDataFlow(DataType.Type.ELAPSED_TIME)
            val pausedFlow = karooSystem.streamDataFlow(DataType.Type.PAUSED_TIME)

            combine(totalFlow, pausedFlow) { (total, paused) ->
                Pair((collectDouble(total) / 60000).roundToInt(), (collectDouble(paused) / 60000).roundToInt())
            }
            .distinctUntilChanged()
            .collect { (total, paused) ->
                // Log.d(TAG, "Collected ${total}, ${paused}")

                var view = glance.compose(context, DpSize.Unspecified) {
                    Box(modifier = GlanceModifier.fillMaxSize()) {
                        DoubleTypesVerticalScreen(
                            "${formatTimeFromMinutes(total)}",
                            "${formatTimeFromMinutes(paused)}",
                            R.drawable.time,
                            R.drawable.pause_circle,
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
