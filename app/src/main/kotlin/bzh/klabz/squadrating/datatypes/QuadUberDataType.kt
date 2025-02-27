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
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class QuadUberDataType(
    private val applicationContext: Context
) : DataTypeImpl("squadrating", "quad_uber") {
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
                val page:Int,
                val ubersquadrat:Int,
                val ubersquadratinho:Int,
                val previousUbersquadrat:Int,
                val previousUbersquadratinho:Int
            )

            combine(exploredSquadratsFlow, quadUberPageFlow) {
                exploredSquadrats, pageFlow -> StreamData(
                    pageFlow,
                    exploredSquadrats.biggestUbersquadratSize,
                    exploredSquadrats.biggestUbersquadratinhoSize,
                    exploredSquadrats.previousUbersquadratSize,
                    exploredSquadrats.previousUbersquadratinhoSize
                )
            }
            .distinctUntilChanged()
            .collect { (page, ubersquadrat, ubersquadratinho, previousUbersquadrat, previousUbersquadratinho) ->
                // Log.d(TAG, "collect dual yard/uber")
                var view = glance.compose(applicationContext, DpSize.Unspecified) {
                    val modifier = if (!config.preview) GlanceModifier.fillMaxSize().clickable(onClick = actionRunCallback<CyclePageAction>())
                        else GlanceModifier.fillMaxSize()

                    Box(modifier = modifier) {
                        when (page) {
                            0 -> {
                                DoubleTypesVerticalScreen(
                                    "${ubersquadrat}x${ubersquadrat}",
                                    "${ubersquadratinho}x${ubersquadratinho}",
                                    R.drawable.ubersquadrat,
                                    R.drawable.ubersquadratinho
                                )
                            }
                            else -> {
                                DoubleTypesVerticalScreen(
                                    "+${ubersquadrat - previousUbersquadrat}",
                                    "+${ubersquadratinho - previousUbersquadratinho}",
                                    R.drawable.incr_ubersquadrat,
                                    R.drawable.incr_ubersquadratinho
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
