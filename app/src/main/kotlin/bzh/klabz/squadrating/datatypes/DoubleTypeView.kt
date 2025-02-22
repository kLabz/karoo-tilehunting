package bzh.klabz.squadrating.datatypes

// Simplified from https://github.com/lockevod/Karoo-KDoubleType/blob/Development/app/src/main/kotlin/com/enderthor/kCustomField/datatype/CustomDoubleTypeView.kt
// TODO: proper attribution, check licensing, etc.

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.appwidget.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.*
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.text.*

@Composable
fun HorizontalScreenContent(number: String, icon: Int, iconColor: Color) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().fillMaxHeight(),
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
        Spacer(modifier = GlanceModifier.width(3.dp).fillMaxHeight())
        Image(
            provider = ImageProvider(icon),
            contentDescription = "Icon",
            modifier = GlanceModifier.size(20.dp),
            colorFilter = ColorFilter.tint(ColorProvider(
                Color.Black,
                iconColor
            ))
        )
        Spacer(modifier = GlanceModifier.height(1.dp).background(ColorProvider(Color.Black, Color.White)))
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 200, heightDp = 150)
@Composable
fun DoubleTypesVerticalScreen(leftNumber: String, rightNumber: String, leftIcon: Int, rightIcon: Int, iconColor: Color) {
    Box(modifier = GlanceModifier.fillMaxSize().padding(start = 2.dp, end = 2.dp)) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                HorizontalScreenContent(leftNumber, leftIcon, iconColor)
            }
            Spacer(modifier = GlanceModifier.fillMaxWidth().height(4.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                HorizontalScreenContent(rightNumber, rightIcon, iconColor)
            }
        }
    }
}

