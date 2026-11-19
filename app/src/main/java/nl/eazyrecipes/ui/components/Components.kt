package nl.eazyrecipes.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import nl.eazyrecipes.R
import nl.eazyrecipes.ui.theme.ContextColor
import nl.eazyrecipes.ui.theme.Eazy
import java.io.File

@Composable
fun Ico(@DrawableRes id: Int, size: Dp = 20.dp, tint: Color = Color.Unspecified, contentDescription: String? = null) {
    Icon(
        painterResource(id),
        contentDescription = contentDescription,
        modifier = Modifier.size(size),
        tint = if (tint == Color.Unspecified) androidx.compose.material3.LocalContentColor.current else tint,
    )
}

/** Ronde knop met icoon (48dp: ruim genoeg om te raken). */
@Composable
fun RoundButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Eazy.Paper.copy(alpha = 0.9f),
    contentColor: Color = Eazy.Ink,
    size: Dp = 48.dp,
) {
    Surface(onClick = onClick, shape = CircleShape, color = background, contentColor = contentColor, modifier = modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) { Ico(icon, 20.dp, contentDescription = contentDescription) }
    }
}

/** Pil met icoon + label, bv. de categorie-sticker of bron-label. */
@Composable
fun Chip(
    label: String,
    @DrawableRes icon: Int?,
    background: Color,
    modifier: Modifier = Modifier,
    contentColor: Color = Eazy.Ink,
    onClick: (() -> Unit)? = null,
    /** Klein icoon achteraan, bv. een pijltje dat laat zien dat er een keuzemenu achter zit. */
    @DrawableRes trailing: Int? = null,
) {
    val content: @Composable () -> Unit = {
        Row(
            Modifier.height(36.dp).padding(start = if (icon != null) 9.dp else 14.dp, end = if (trailing != null) 10.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) Ico(icon, 18.dp)
            Text(label, style = MaterialTheme.typography.labelMedium)
            if (trailing != null) Ico(trailing, 14.dp)
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, shape = CircleShape, color = background, contentColor = contentColor, modifier = modifier) { content() }
    } else {
        Surface(shape = CircleShape, color = background, contentColor = contentColor, modifier = modifier) { content() }
    }
}

/** Tijd-label op een kaart: geel "Snel"-vlak bij ≤ 30 min, anders wit. */
@Composable
fun TimeBadge(text: String, quick: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(if (quick) Eazy.Snel.vivid else Eazy.Paper.copy(alpha = 0.92f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Ico(if (quick) R.drawable.ph_lightning else R.drawable.ph_clock, 13.dp, tint = Eazy.Ink)
        Text(text, style = MaterialTheme.typography.labelSmall, color = Eazy.Ink)
    }
}

/** Statistiek-tegel op het detail-/voorbeeldscherm. [value] mag een invoerveld zijn. */
@Composable
fun StatTile(@DrawableRes icon: Int, label: String, ctx: ContextColor, modifier: Modifier = Modifier, value: @Composable () -> Unit) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(Eazy.Grey).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Ico(icon, 20.dp, tint = ctx.deep)
        value()
        Text(label, style = MaterialTheme.typography.bodySmall, color = Eazy.Muted)
    }
}

/** Foto van het recept: lokale kopie als die er is, anders de bron-URL; zonder foto een vlak in de contextkleur. */
@Composable
fun RecipePhoto(imagePath: String?, imageUrl: String?, ctx: ContextColor, modifier: Modifier = Modifier, iconSize: Dp = 72.dp) {
    Box(modifier.background(ctx.vivid), contentAlignment = Alignment.Center) {
        Ico(ctx.icon, iconSize, tint = Eazy.Ink.copy(alpha = 0.18f))
        val model: Any? = imagePath?.let(::File) ?: imageUrl
        if (model != null) {
            AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}

/** De grote zwarte pil-knop onderaan (zoals "Start kookmodus"), met een rondje in de contextkleur. */
@Composable
fun BigPillButton(
    text: String,
    @DrawableRes icon: Int,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) Eazy.Ink else Eazy.Subtle,
        contentColor = Eazy.Paper,
        modifier = modifier.fillMaxWidth().height(68.dp),
    ) {
        Row(Modifier.padding(start = 26.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Box(Modifier.size(52.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                Ico(icon, 22.dp, tint = Eazy.Ink)
            }
        }
    }
}

/**
 * Foutmelding met een eigen vorm (niet alleen kleur): wit vlak, dikke zwarte rand, rond kruis-icoon.
 */
@Composable
fun ErrorCard(title: String, message: String, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Eazy.Paper)
            .border(3.dp, Eazy.Ink, MaterialTheme.shapes.large)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Eazy.Ink), contentAlignment = Alignment.Center) {
            Ico(R.drawable.ic_alert, 22.dp, tint = Eazy.Paper)
        }
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Eazy.Muted)
        actions()
    }
}

/** Gestippelde knop "+ … toevoegen" (zoals "Nieuwe collectie" in de mockup). */
@Composable
fun DashedAddButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth().dashedBorder(Eazy.Dash, 22.dp),
    ) {
        Row(Modifier.padding(PaddingValues(16.dp)), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico(R.drawable.ic_plus, 18.dp)
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

val TopRounded = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)

fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 2.dp): Modifier = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
    )
}
