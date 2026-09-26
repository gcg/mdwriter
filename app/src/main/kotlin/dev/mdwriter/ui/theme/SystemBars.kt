package dev.mdwriter.ui.theme

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Transparent bars from enableEdgeToEdge(); icon contrast follows the IN-APP theme (it may differ from the system). */
@Composable
fun SystemBarsAppearance(darkIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = LocalActivity.current?.window ?: return
    SideEffect {
        window.isNavigationBarContrastEnforced = false // no translucent scrim on 3-button nav
        WindowCompat.getInsetsController(window, view).run {
            isAppearanceLightStatusBars = darkIcons // light theme -> dark icons
            isAppearanceLightNavigationBars = darkIcons
        }
    }
}
