package de.klabz.karootilehunting.datatypes

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import de.klabz.karootilehunting.R
import de.klabz.karootilehunting.datastores.exploredTilesDataStore
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

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class DualAllTimeDataType(
    private val applicationContext: Context
) : DataTypeImpl("karoo-tilehunting", "dual_alltime") {
    protected val glance = GlanceRemoteViews()

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        val scope = CoroutineScope(Dispatchers.IO + Job())

        val configJob = scope.launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
            emitter.onNext(ShowCustomStreamState(message = "", color = null))
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
