package bzh.klabz.squadrating.datatypes

// Simplified from https://github.com/lockevod/Karoo-KDoubleType/blob/Development/app/src/main/kotlin/com/enderthor/kCustomField/datatype/CustomDoubleTypeView.kt
// TODO: proper attribution, check licensing, etc.

import android.util.Log
import android.content.Context
import androidx.glance.action.clickable
import androidx.glance.action.Action
import androidx.glance.action.action
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.appwidget.background
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.action.actionParametersOf
import androidx.glance.action.ActionParameters
import androidx.glance.color.ColorProvider
import androidx.glance.layout.*
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.text.*
import kotlinx.coroutines.flow.MutableStateFlow
import bzh.klabz.squadrating.SquadratingExtension.Companion.TAG

val quadSquadratsPageFlow:MutableStateFlow<Int> = MutableStateFlow(0)
val quadYardUberPageFlow:MutableStateFlow<Int> = MutableStateFlow(0)
val quadYardPageFlow:MutableStateFlow<Int> = MutableStateFlow(0)
val quadUberPageFlow:MutableStateFlow<Int> = MutableStateFlow(0)

class CyclePageAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // TODO: only toggle the widget which asked for it...
        Log.d(TAG, "Cycling page (glanceId = ${glanceId}, parameters = ${parameters})")

        quadSquadratsPageFlow.value = when (quadSquadratsPageFlow.value) {
            0 -> 1
            else -> 0
        }
        quadYardPageFlow.value = when (quadYardPageFlow.value) {
            0 -> 1
            else -> 0
        }
        quadYardUberPageFlow.value = when (quadYardUberPageFlow.value) {
            0 -> 1
            else -> 0
        }
        quadUberPageFlow.value = when (quadUberPageFlow.value) {
            0 -> 1
            else -> 0
        }
    }
}

@Composable
fun HorizontalScreenContent(number: String, icon: Int, iconColor:Color? = null) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.End
    ) {
        Text(
            text = number,
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = if (number.length > 5) 28.sp else 34.sp,
                fontFamily = FontFamily.Monospace,
                color = ColorProvider(Color.Black, Color.White),
                textAlign = TextAlign.End
            ),
            modifier = GlanceModifier.defaultWeight().padding(0.dp, if (number.length > 5) 5.dp else 2.dp),
        )
        Spacer(modifier = GlanceModifier.width(5.dp).fillMaxHeight())
        Image(
            provider = ImageProvider(icon),
            contentDescription = "Icon",
            modifier = GlanceModifier.size(20.dp),
            colorFilter = if (iconColor == null) null else ColorFilter.tint(ColorProvider(
                Color.Black,
                iconColor
            ))
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 200, heightDp = 150)
@Composable
fun DoubleTypesVerticalScreen(
    leftNumber: String,
    rightNumber: String,
    leftIcon: Int,
    rightIcon: Int,
    iconColor:Color? = null
) {
    Column(modifier = GlanceModifier.fillMaxSize().padding(vertical = 5.dp)) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            HorizontalScreenContent(leftNumber, leftIcon, iconColor)
        }
        Column(modifier = GlanceModifier.defaultWeight()) {
            HorizontalScreenContent(rightNumber, rightIcon, iconColor)
        }
    }
}

