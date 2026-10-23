package top.wkbin.taixu.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Terminal canvas presets stay dark and authentic regardless of system light/dark mode. */
@Composable
fun terminalSurfaceColor(scheme: String): Color = when (scheme) {
    "matrix" -> Color(0xFF070D0A)
    "amber" -> Color(0xFF110D08)
    "aurora" -> Color(0xFF09101D)
    else -> Color(0xFF0B0E14)
}

@Composable
fun terminalContentColor(scheme: String): Color = when (scheme) {
    "matrix" -> Color(0xFF22C55E)
    "amber" -> Color(0xFFF59E0B)
    "aurora" -> Color(0xFF38BDF8)
    else -> Color(0xFFE6EDF3)
}
