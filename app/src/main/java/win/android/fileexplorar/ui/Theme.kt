package win.android.fileexplorar.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WinLightColors = lightColorScheme(
    primary = Color(0xFF005FB8),
    onPrimary = Color.White,
    secondary = Color(0xFF005FB8),
    background = Color(0xFFF3F3F3),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFF9F9F9),
    outline = Color(0xFFE5E5E5),
    outlineVariant = Color(0xFFD1D1D1)
)

@Composable
fun WinExplorerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WinLightColors,
        content = content
    )
}
