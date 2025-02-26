package bzh.klabz.squadrating.datatypes

import android.content.Context
import android.util.Log
import androidx.glance.action.Action
import androidx.glance.action.action
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.core.content.ContextCompat
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
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
class QuadYardUberDataType(
    private val applicationContext: Context
) : DataTypeImpl("squadrating", "quad_yard_uber") {
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

            combine(exploredSquadratsFlow, quadYardUberPageFlow) {
                exploredSquadrats, pageFlow -> StreamData(exploredSquadrats, pageFlow)
            }
            .collect { (exploredSquadrats, pageFlow) ->
                // Log.d(TAG, "collect dual yard/uber")
                var view = when (pageFlow) {
                    0 -> {
                        val yard = exploredSquadrats.yard
                        val ubersquadrat = exploredSquadrats.biggestUbersquadratSize
                        glance.compose(applicationContext, DpSize.Unspecified) {
                            val modifier = if (!config.preview) GlanceModifier.fillMaxSize().clickable(onClick = actionRunCallback<CyclePageAction>())
                                else GlanceModifier.fillMaxSize()

                            Box(modifier = modifier) {
                                DoubleTypesVerticalScreen(
                                    yard.toString(),
                                    ubersquadrat.toString(),
                                    R.drawable.yard,
                                    R.drawable.ubersquadrat
                                )
                            }
                        }
                    }
                    else -> {
                        val yardinho = exploredSquadrats.yardinho
                        val ubersquadratinho = exploredSquadrats.biggestUbersquadratinhoSize
                        glance.compose(applicationContext, DpSize.Unspecified) {
                            val modifier = if (!config.preview) GlanceModifier.fillMaxSize().clickable(onClick = actionRunCallback<CyclePageAction>())
                                else GlanceModifier.fillMaxSize()

                            Box(modifier = modifier) {
                                DoubleTypesVerticalScreen(
                                    yardinho.toString(),
                                    ubersquadratinho.toString(),
                                    R.drawable.yardinho,
                                    R.drawable.ubersquadratinho
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
