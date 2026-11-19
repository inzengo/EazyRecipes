package nl.eazyrecipes.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import nl.eazyrecipes.R
import nl.eazyrecipes.data.MainIngredient

// --- kleuren (design v0.2, zie STATUS.md) ---

object Eazy {
    val Ink = Color(0xFF121212)
    val Paper = Color(0xFFFFFFFF)
    val Grey = Color(0xFFF2F2F0)
    val Line = Color(0xFFEFEFEC)
    val Dash = Color(0xFFD4D4CF)
    val Muted = Color(0xFF5E6066)
    val Subtle = Color(0xFF8A8C91)
    val Brand = Color(0xFF9B8CFF)
    val Snel = ContextColor("Snel", Color(0xFFFFC738), Color(0xFF8F5800), R.drawable.ph_lightning)
    val Alles = ContextColor("Alles", Ink, Ink, R.drawable.ph_fork_knife)
}

/**
 * Contextkleur: een fel vlak ([vivid]) met altijd zwarte tekst erop (contrast ≥ 6:1),
 * en een donkere variant ([deep]) voor iconen/cijfers op wit of grijs.
 */
data class ContextColor(val label: String, val vivid: Color, val deep: Color, @DrawableRes val icon: Int)

val MainIngredient?.context: ContextColor
    get() = when (this) {
        MainIngredient.VLEES -> ContextColor("Vlees", Color(0xFFFF5A4A), Color(0xFFC8261A), R.drawable.ph_cow)
        MainIngredient.VIS -> ContextColor("Vis", Color(0xFF4D9DFF), Color(0xFF1F5FCC), R.drawable.ph_fish)
        MainIngredient.VEGA -> ContextColor("Vega", Color(0xFF3DD66F), Color(0xFF167A3B), R.drawable.ph_leaf)
        MainIngredient.VEGAN -> ContextColor("Vegan", Color(0xFF47E3BC), Color(0xFF0B7560), R.drawable.ph_plant)
        MainIngredient.ZOET -> ContextColor("Zoet", Color(0xFFFF8AD4), Color(0xFFB0237A), R.drawable.ph_cake)
        null -> Eazy.Alles
    }

/** Recepten van 30 minuten of korter krijgen het gele "Snel"-label. */
fun isQuick(totalMinutes: Int?): Boolean = totalMinutes != null && totalMinutes <= 30

// --- typografie: Bricolage Grotesque (koppen, cijfers, knoppen) + Figtree (tekst) ---

@OptIn(ExperimentalTextApi::class)
private fun bricolage(opticalSize: Float) = FontFamily(
    listOf(500, 600, 700, 800).map { w ->
        Font(
            R.font.bricolage_grotesque,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w), FontVariation.Setting("opsz", opticalSize)),
        )
    }
)

/** Grote koppen (optische maat groot: strakker). */
val BricolageDisplay = bricolage(48f)

/** Kaarttitels, knoppen, cijfers. */
val Bricolage = bricolage(16f)

@OptIn(ExperimentalTextApi::class)
val Figtree = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(R.font.figtree, weight = FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)

private val typography = Typography(
    displaySmall = TextStyle(fontFamily = BricolageDisplay, fontWeight = FontWeight.ExtraBold, fontSize = 38.sp, lineHeight = 39.sp, letterSpacing = (-0.035).em),
    headlineMedium = TextStyle(fontFamily = BricolageDisplay, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 34.sp, letterSpacing = (-0.03).em),
    headlineSmall = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.02).em),
    titleLarge = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, lineHeight = 23.sp, letterSpacing = (-0.02).em),
    titleMedium = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 19.sp, letterSpacing = (-0.01).em),
    bodyLarge = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, lineHeight = 15.sp),
)

private val colors = lightColorScheme(
    primary = Eazy.Ink, onPrimary = Eazy.Paper,
    secondary = Eazy.Brand, onSecondary = Eazy.Ink,
    background = Eazy.Grey, onBackground = Eazy.Ink,
    surface = Eazy.Paper, onSurface = Eazy.Ink,
    surfaceVariant = Eazy.Grey, onSurfaceVariant = Eazy.Muted,
    outline = Eazy.Dash, outlineVariant = Eazy.Line,
    error = Color(0xFFC8261A), onError = Eazy.Paper,
)

private val shapes = Shapes(
    small = RoundedCornerShape(18.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

@Composable
fun EazyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
}
