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

// TODO: move to separate extension
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class DualTimeDataType(
    private val karooSystem: KarooSystemService,
    private val applicationContext: Context
) : DataTypeImpl("squadrating", "dual_time") {
    protected val glance = GlanceRemoteViews()

    private fun collectDouble(stream:StreamState):Double {
        return when (stream) {
            StreamState.Idle, StreamState.Searching, StreamState.NotAvailable -> 0.toDouble()
            else -> (stream as? StreamState.Streaming)?.dataPoint?.singleValue?.toDouble() ?: 0.toDouble()
        }
    }

    private fun formatTimeFromSeconds(seconds: Double): String {
        val totalMinutes = (seconds / 60).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return "${hours}:${minutes.toString().padStart(2, '0')}"
    }

    private fun epochToTimeOfDay(epochMillis: Double): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = epochMillis.toLong()
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(calendar.time)
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        val scope = CoroutineScope(Dispatchers.IO + Job())

        val viewjob = scope.launch {
            // TODO: handle "No route"
            val ttdFlow = karooSystem.streamDataFlow(DataType.Type.TIME_TO_DESTINATION)
            val toaFlow = karooSystem.streamDataFlow(DataType.Type.TIME_OF_ARRIVAL)

            combine(ttdFlow, toaFlow) { (ttd, toa) -> Pair(ttd, toa) }
            .distinctUntilChanged()
            .collect { (ttdStream, toaStream) ->
                val ttd = collectDouble(ttdStream)
                val toa = collectDouble(toaStream)
                // Log.d(TAG, "Collected ${ttd}, ${toa}")

                if (ttd == 0.toDouble() && toa == 0.toDouble()) {
                    // Log.d(TAG, "No route")
                    emitter.onNext(UpdateGraphicConfig(showHeader = true))
                    emitter.onNext(ShowCustomStreamState(message = "No route", color = ContextCompat.getColor(applicationContext, R.color.white)))
                } else {
                    emitter.onNext(UpdateGraphicConfig(showHeader = false))
                    emitter.onNext(ShowCustomStreamState(message = "", color = null))

                    var view = glance.compose(context, DpSize.Unspecified) {
                        Box(modifier = GlanceModifier.fillMaxSize()) {
                            DoubleTypesVerticalScreen(
                                "${formatTimeFromSeconds(ttd/1000)}",
                                "${epochToTimeOfDay(toa)}",
                                R.drawable.timer,
                                R.drawable.time,
                                Color(ContextCompat.getColor(applicationContext, R.color.icongreen))
                            )
                        }
                    }.remoteViews
                    emitter.updateView(view)
                }
            }
        }

        emitter.setCancellable {
            viewjob.cancel()
        }
    }
}
