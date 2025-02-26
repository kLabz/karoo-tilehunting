package bzh.klabz.squadrating.datatypes

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.core.content.ContextCompat
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.GlanceId
import bzh.klabz.squadrating.R
import bzh.klabz.squadrating.SquadratingExtension.Companion.TAG
import bzh.klabz.squadrating.data.ExploredSquadrats
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class QuadSquadratsDataType(
    private val applicationContext: Context
) : DataTypeImpl("squadrating", "quad_squadrats") {
    protected val glance = GlanceRemoteViews()

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        val scope = CoroutineScope(Dispatchers.IO)

        val configJob = scope.launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
            emitter.onNext(ShowCustomStreamState(message = "", color = null))
            awaitCancellation()
        }

        val viewjob = scope.launch {
            val exploredSquadratsFlow = applicationContext.exploredSquadratsDataStore.data

            data class StreamData(
                val exploredSquadrats:ExploredSquadrats,
                val page:Int
            )

            combine(exploredSquadratsFlow, quadSquadratsPageFlow) {
                exploredSquadrats, pageFlow -> StreamData(exploredSquadrats, pageFlow)
            }
            .collect { (exploredSquadrats, pageFlow) ->
                // Log.d(TAG, "collect dual yard/uber")
                var view = when (pageFlow) {
                    0 -> {
                        val squadrats = exploredSquadrats.exploredSquadratsCount
                        val squadratinhos = exploredSquadrats.exploredSquadratinhosCount
                        val glance = GlanceRemoteViews()
                        glance.compose(applicationContext, DpSize.Unspecified) {
                            val modifier = if (!config.preview) GlanceModifier.fillMaxSize().clickable(onClick = actionRunCallback<CyclePageAction>())
                                else GlanceModifier.fillMaxSize()

                            Box(modifier = modifier) {
                                DoubleTypesVerticalScreen(
                                    squadrats.toString(),
                                    squadratinhos.toString(),
                                    R.drawable.squadrat,
                                    R.drawable.squadratinho
                                )
                            }
                        }
                    }
                    else -> {
                        val squadrats = exploredSquadrats.recentlyExploredNewSquadratsCount
                        val squadratinhos = exploredSquadrats.recentlyExploredNewSquadratinhosCount
                        glance.compose(applicationContext, DpSize.Unspecified) {
                            val modifier = if (!config.preview) GlanceModifier.fillMaxSize().clickable(onClick = actionRunCallback<CyclePageAction>())
                                else GlanceModifier.fillMaxSize()

                            Box(modifier = modifier) {
                                DoubleTypesVerticalScreen(
                                    "+${squadrats}",
                                    "+${squadratinhos}",
                                    R.drawable.new_squadrat,
                                    R.drawable.new_squadratinho
                                )
                            }
                        }
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
