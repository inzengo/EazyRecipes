package nl.eazyrecipes.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import nl.eazyrecipes.ui.theme.Eazy

/*
 * De app is altijd licht, dus standaard donkere statusbalk-iconen (zie MainActivity). Alleen boven een foto
 * zijn witte iconen nodig; daar zorgt een donker verloop voor contrast.
 */

/** Zet de statusbalk-iconen op wit ([light] = true) of donker zolang dit scherm zichtbaar is. */
@Composable
fun StatusBarIcons(light: Boolean) {
    val view = LocalView.current
    val window = remember(view) { view.context.findActivity()?.window } ?: return
    DisposableEffect(light) {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !light // "light status bars" = donkere iconen
        onDispose { controller.isAppearanceLightStatusBars = true }
    }
}

/** Donker verloop bovenaan een foto, zodat de witte statusbalk-iconen en de knoppen leesbaar blijven. */
@Composable
fun PhotoTopScrim(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(140.dp)
            .background(Brush.verticalGradient(listOf(Eazy.Ink.copy(alpha = 0.45f), Color.Transparent)))
    )
}

/**
 * Voor schermen met een foto bovenaan en een witte kaart die eroverheen scrolt: boven de foto witte iconen,
 * daarna een witte balk met donkere iconen. Plaats dit als laatste kind van een `Box`, zodat het bovenop ligt.
 */
@Composable
fun PhotoStatusBar(scroll: ScrollState, photoHeight: Dp, cardOverlap: Dp = 36.dp) {
    val density = LocalDensity.current
    val statusBar = WindowInsets.statusBars.getTop(density)
    val overPhoto by remember(density, statusBar) {
        derivedStateOf { scroll.value < with(density) { (photoHeight - cardOverlap).roundToPx() } - statusBar }
    }
    StatusBarIcons(light = overPhoto)
    if (!overPhoto) Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Eazy.Paper))
}

/** Effen balk achter de statusbalk, zodat er geen inhoud onder de klok door scrolt. */
@Composable
fun StatusBarBackdrop(color: Color) {
    Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(color))
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
