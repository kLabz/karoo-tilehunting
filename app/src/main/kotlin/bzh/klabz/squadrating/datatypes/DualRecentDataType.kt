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
import bzh.klabz.squadrating.datastores.exploredSquadratsDataStore
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.ShowCustomStreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import io.hammerhead.karooext.models.DataType

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class DualRecentDataType(
    private val applicationContext: Context
) : DataTypeImpl("squadrating", "dual_trip") {
    protected val glance = GlanceRemoteViews()

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        val scope = CoroutineScope(Dispatchers.IO + Job())

        val configJob = scope.launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
            emitter.onNext(ShowCustomStreamState(message = "", color = null))
            awaitCancellation()
        }

        // DataType.Type.TIME_TO_DESTINATION
        // DataType.Type.TIME_OF_ARRIVAL

        // DataType.Type.DISTANCE
        // DataType.Type.ELEVATION_GAIN

        val viewjob = scope.launch {
            applicationContext.exploredSquadratsDataStore.data.collect { exploredSquadrats ->
                val recentSquadrats = exploredSquadrats.recentlyExploredSquadratsCount
                val newSquadrats = exploredSquadrats.recentlyExploredNewSquadratsCount
                var view = glance.compose(context, DpSize.Unspecified) {
                    Box(modifier = GlanceModifier.fillMaxSize()) {
                        DoubleTypesVerticalScreen(
                            recentSquadrats.toString(),
                            newSquadrats.toString(),
                            R.drawable.trip,
                            R.drawable.location_plus,
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
