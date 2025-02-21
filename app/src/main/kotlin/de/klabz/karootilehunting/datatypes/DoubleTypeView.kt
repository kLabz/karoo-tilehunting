package de.klabz.karootilehunting.datatypes
// From https://github.com/lockevod/Karoo-KDoubleType/blob/Development/app/src/main/kotlin/com/enderthor/kCustomField/datatype/CustomDoubleTypeView.kt

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.background
import androidx.glance.appwidget.cornerRadius
import androidx.glance.color.ColorProvider
import androidx.glance.layout.*
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import kotlin.math.roundToInt


fun formatTimeFromSeconds(seconds: Double): String {
    val totalMinutes = (seconds / 60).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return "${hours}:${minutes.toString().padStart(2, '0')}"
}


fun formatNumber(number: Double, isInt: Boolean, isTime: Boolean = false): String = buildString {
    if (isTime) {
        append(formatTimeFromSeconds(number))
    } else {
        if (isInt) append(number.roundToInt().toString().take(5))
        else append(((number * 10.0).roundToInt() / 10.0).toString().take(5))
    }
}


@Composable
fun VerticalDivider(isTopField: Boolean, isdivider: Boolean) {
    val height = when {
        isTopField -> 10.dp
        else -> 14.dp
    }
    Box(modifier = GlanceModifier.fillMaxWidth().height(height)) {
        Row(modifier = GlanceModifier.fillMaxSize()) {
            Column(modifier = GlanceModifier.defaultWeight()) {}
            if (isdivider) Spacer(modifier = GlanceModifier.fillMaxHeight().width(1.dp).background(ColorProvider(Color.Black, Color.White)))
            Column(modifier = GlanceModifier.defaultWeight()) {}
        }
    }
}

@Composable
fun IconRow(
    icon: Int,
    modifier: GlanceModifier = GlanceModifier.fillMaxWidth()
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.End
            // FieldPosition.CENTER -> Alignment.CenterHorizontally
            // FieldPosition.RIGHT -> Alignment.End
            // FieldPosition.LEFT -> Alignment.Start
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = null,
            modifier = GlanceModifier.size(20.dp)
        )
    }
}


@Composable
fun NumberRow(number: String) {
    val padding = 2.dp
    val fontSize = 42.sp
    // when {
    //     onlyOne -> 42.sp
    //     number.length > 3 -> 32.sp
    //     else -> 38.sp
    // }

    Row(
        modifier = GlanceModifier
            .fillMaxHeight()
            .fillMaxWidth()
            .padding(bottom = padding),
        verticalAlignment = Alignment.Bottom,
        horizontalAlignment = Alignment.End
        // when (layout) {
        //     FieldPosition.CENTER -> Alignment.CenterHorizontally
        //     FieldPosition.RIGHT -> Alignment.End
        //     FieldPosition.LEFT -> Alignment.Start
        // }
    ) {
        Text(
            text = number,
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = fontSize,
                fontFamily = FontFamily.Monospace,
                color = ColorProvider(Color.Black, Color.White)
            )
        )
        Spacer(
            modifier = GlanceModifier
                .fillMaxHeight()
                .width(2.dp)
        )
    }
}



@Composable
fun OneIconRow(icon: Int, text: String) {
    val isSmall = false
    val rowHeight = if (isSmall) 31.dp else 37.dp
    val iconSize = if (isSmall) 16.dp else 20.dp
    val fontSize = if (isSmall) 15.sp else 18.sp
    val topPadding = if (isSmall) (-2).dp else (-1).dp

    Row(
        modifier = GlanceModifier.fillMaxWidth().height(rowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = GlanceModifier
                .height(if (isSmall) 20.dp else 24.dp)
                .width(24.dp)
        ) {
            Image(
                provider = ImageProvider(icon),
                contentDescription = null,
                modifier = GlanceModifier.size(iconSize).padding(top = topPadding)
            )
        }

        Column(
            modifier = GlanceModifier
                .height(if (isSmall) 32.dp else 36.dp)
                .fillMaxWidth()
                .padding(end = 3.dp),
            horizontalAlignment = Alignment.End,
            verticalAlignment = Alignment.CenterVertically
        ) {

            val displayText = text.takeIf { it.length <= 10 } ?: text.split(" ", limit = 2)
                .let { parts -> if (parts.size > 1) "${parts[0]}\n${parts[1]}" else text }

            val adjustedFontSize = if ((displayText.count { it == '\n' } + 1) == 2) (fontSize.value * 0.85).sp else fontSize

            Text(
                text = displayText,
                maxLines = 2,
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = adjustedFontSize,
                    fontFamily = FontFamily.Monospace,
                    color = ColorProvider(Color.Black, Color.White),
                    textAlign = TextAlign.End
                )
            )
        }
    }
}

@Composable
fun OneNumberRow(
    number: String,
    textSize: Int,
    secondValue: String
) {
    val padding = 2.dp
    val displayNumber = number

    Row(
        modifier = GlanceModifier
            .fillMaxHeight()
            .fillMaxWidth()
            .padding(bottom = padding, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.End
        // when (layout) {
        //     FieldPosition.CENTER -> Alignment.CenterHorizontally
        //     FieldPosition.RIGHT -> Alignment.End
        //     FieldPosition.LEFT -> Alignment.Start
        // }
    ) {
        Text(
            text = displayNumber,
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = textSize.sp,
                fontFamily = FontFamily.Monospace,
                color = ColorProvider(Color.Black, Color.White)
            ),
            modifier = GlanceModifier.padding(top = -padding)
        )
    }
}


@Composable
fun HorizontalScreenContent(number: String, icon: Int) {
   // val colorIcon= ColorFilter.tint(colorFilter)
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.End
        // when (layout.name) {
        //     "CENTER" -> Alignment.CenterHorizontally
        //     "RIGHT" -> Alignment.End
        //     else -> Alignment.Start
        // }
    ) {
        // if (layout.name == "LEFT") {
        //     Image(
        //         provider = ImageProvider(icon),
        //         contentDescription = "Icon",
        //         modifier = GlanceModifier.size(20.dp),
        //         colorFilter = colorIcon
        //     )
        //     Spacer(modifier = GlanceModifier.width(6.dp))
        // }
        Text(
            text = number,
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 38.sp,
                fontFamily = FontFamily.Monospace,
                color = ColorProvider(Color.Black, Color.White),
                textAlign = TextAlign.End
                // when (layout.name) {
                //     "CENTER" -> TextAlign.Center
                //     "RIGHT" -> TextAlign.End
                //     else -> TextAlign.Start
                // }
            ),
            modifier = GlanceModifier.defaultWeight()
        )
        // if (layout.name != "LEFT") {
            Spacer(modifier = GlanceModifier.width(5.dp).fillMaxHeight())
            Image(
                provider = ImageProvider(icon),
                contentDescription = "Icon",
                modifier = GlanceModifier.size(20.dp),
                // colorFilter = colorIcon
            )
        // }
        Spacer(modifier = GlanceModifier.height(1.dp).background(ColorProvider(Color.Black, Color.White)))
    }
}


@Composable
fun SingleHorizontalField(icon: Int, number: String) {
    val height = 9.dp
    Spacer(modifier = GlanceModifier.height(height))
    IconRow(icon)
    Spacer(modifier = GlanceModifier.height(5.dp))
    NumberRow(number.take(4))

}


@Composable
fun NotSupported(overlayText: String, fontSize: Int)
{
    Box(
        modifier = GlanceModifier.fillMaxSize().padding(5.dp),
        contentAlignment = Alignment(
            vertical = Alignment.Vertical.CenterVertically,
            horizontal = Alignment.Horizontal.CenterHorizontally,
        ),
    ) {
        Text(
            overlayText,
            maxLines = 2,
            style = TextStyle(
                ColorProvider(Color.Black, Color.White),
                fontSize = (0.8 * fontSize).sp,
                fontFamily = FontFamily.Monospace
            ),
            modifier = GlanceModifier.background(ColorProvider(Color.White, Color.Black)
            ).padding(1.dp)
        )
    }

}


@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 200, heightDp = 150)
@Composable
fun RollingFieldScreen(
    dNumber: Double, isInt: Boolean,
    icon: Int, label: String,
    iskaroo3: Boolean, textSize:Int,ispreview:Boolean, secondValue:Double, isinit: Boolean=false
) {
    if (!isinit)
    {

        // val icon = action.icon
        // val label = action.label

            val number = formatNumber(dNumber, isInt)
            val numberSecond = formatNumber(secondValue, isInt)

            Box(modifier = GlanceModifier.fillMaxSize()) {
                Row(
                    modifier = if (iskaroo3) GlanceModifier.fillMaxSize()
                        .cornerRadius(6.dp) else GlanceModifier.fillMaxSize()
                )
                {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Spacer(modifier = GlanceModifier.height(4.dp))
                            OneIconRow(icon, label.uppercase())
                            //Spacer(modifier = GlanceModifier.height(1.dp))
                            OneNumberRow(
                                number.take(6),
                                (textSize * (if (ispreview) 0.8 else 1.0)).roundToInt(),
                                numberSecond.take(3)
                            )
                    }
                }
            }
    } else {
        NotSupported("Searching...", textSize)
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 200, heightDp = 150)
@Composable
fun DoubleScreenSelector(
    showH: Boolean,
    leftNumber: Double,
    rightNumber: Double,
    leftIcon:Int,
    rightIcon:Int,
    leftLabel:String,
    rightLabel:String,
    isKaroo3: Boolean,
    text: String,
    isdivider:Boolean,
    isinit:Boolean = false
) {


    // define values from primary and secondary fields

    if (!isinit) {

        val newLeft = if (!showH) formatNumber(leftNumber, true) else "0.0"
        val newRight = if (!showH) formatNumber(rightNumber, true) else "0.0"

        if (!showH) {
            DoubleTypesVerticalScreenBig(
                newLeft,
                newRight,
                leftIcon,
                rightIcon,
                isKaroo3,
                isdivider
            )
        } else {
            DoubleTypesScreenHorizontal(
                newLeft,
                newRight,
                leftIcon,
                rightIcon,
                isKaroo3,
                text,
                isdivider
            )
        }
    }
    else {
        NotSupported("Searching...", 21)
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 200, heightDp = 150)
@Composable
fun DoubleTypesScreenHorizontal(
    leftNumber: String, rightNumber: String, leftIcon: Int, rightIcon: Int,
    isKaroo3: Boolean, text: String, isdivider:Boolean
) {

    VerticalDivider(true,isdivider)
    Box(modifier = GlanceModifier.fillMaxSize().padding(start = 1.dp, end = 1.dp)) {

        Row(modifier = GlanceModifier.fillMaxSize().let { if (isKaroo3) it.cornerRadius(8.dp) else it }) {
            Column(modifier = GlanceModifier.defaultWeight().background(ColorProvider(Color.White, Color.Black))) {
                SingleHorizontalField(leftIcon, leftNumber)
            }
            if (isdivider) Spacer(modifier = GlanceModifier.fillMaxHeight().width(1.dp).background(ColorProvider(Color.Black, Color.White)))
            Column(modifier = GlanceModifier.defaultWeight().background(ColorProvider(Color.White, Color.Black))) {
                SingleHorizontalField(rightIcon, rightNumber)
            }
        }
    }
    VerticalDivider(false, isdivider)
}


@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 200, heightDp = 150)
@Composable
fun DoubleTypesVerticalScreenSmall(
    leftNumber: String, rightNumber: String, leftIcon: Int, rightIcon: Int,
    isKaroo3: Boolean, isdivider: Boolean
) {
    Box(modifier = GlanceModifier.fillMaxSize().padding(start = 1.dp, end = 1.dp)) {
        Column(modifier = if (isKaroo3) GlanceModifier.fillMaxSize().cornerRadius(8.dp) else GlanceModifier.fillMaxSize()) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Spacer(modifier = GlanceModifier.fillMaxWidth().height(1.dp))
                HorizontalScreenContent(leftNumber, leftIcon)
            }
            if (isdivider) Spacer(modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(ColorProvider(Color.Black, Color.White)))
            Spacer(modifier = GlanceModifier.fillMaxWidth().height(3.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                HorizontalScreenContent(rightNumber, rightIcon)
            }
        }
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 200, heightDp = 150)
@Composable
fun DoubleTypesVerticalScreenBig(
    leftNumber: String, rightNumber: String, leftIcon: Int, rightIcon: Int,
    isKaroo3: Boolean, isdivider: Boolean
) {
    Box(modifier = GlanceModifier.fillMaxSize().padding(start = 1.dp, end = 1.dp)) {
        Column(modifier = if (isKaroo3) GlanceModifier.fillMaxSize().cornerRadius(8.dp) else GlanceModifier.fillMaxSize()) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Spacer(modifier = GlanceModifier.fillMaxWidth().height(2.dp))
                HorizontalScreenContent(leftNumber, leftIcon)
            }
            if (isdivider) Spacer(modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(ColorProvider(Color.Black, Color.White)))
            Spacer(modifier = GlanceModifier.fillMaxWidth().height(7.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                HorizontalScreenContent(rightNumber, rightIcon)
            }
        }
    }
}
