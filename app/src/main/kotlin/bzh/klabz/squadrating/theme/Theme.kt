package bzh.klabz.squadrating.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.LocalContext
import bzh.klabz.squadrating.R

@Composable
fun AppTheme(
    content: @Composable () -> Unit,
) {
    val scheme = lightColorScheme(
        primary = Color(0xFF663399),
        secondary = Color(0xFF27916a),
        tertiary = Color(0xFF221133),
    )

    MaterialTheme(
        content = content,
        colorScheme = scheme
    )
}
