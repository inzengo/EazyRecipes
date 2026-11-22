package nl.eazyrecipes.ui.library

import android.content.ClipboardManager
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import nl.eazyrecipes.R
import nl.eazyrecipes.importer.RecipeImporter
import nl.eazyrecipes.ui.components.BigPillButton
import nl.eazyrecipes.ui.components.Chip
import nl.eazyrecipes.ui.components.Ico
import nl.eazyrecipes.ui.theme.Eazy

/** Links in vrije tekst: ook HOOFDLETTERS, zonder afsluitende leestekens ("(zie https://x.nl/r).") . */
internal fun linksIn(text: String): List<String> =
    Regex("""(?i)https?://[^\s<>"'\])]+""").findAll(text).map { it.value.trimEnd('.', ',', ';', ':', '!', '?') }.toList()

internal fun linkIn(text: String): String? = linksIn(text).firstOrNull()

/** Wat "Plakken" in het veld zet: één link, of bij een lijst alle links onder elkaar. */
internal fun pasteText(clip: String): String = linksIn(clip).distinct().let { if (it.size > 1) it.joinToString("\n") else it.firstOrNull() ?: clip.trim() }

/**
 * Wat staat er al in het veld als "link plakken" opent?
 * 1. tekst van de vorige keer, als het venster zonder importeren dicht ging;
 * 2. anders een link van het klembord, tenzij dat recept al in de bibliotheek staat;
 * 3. anders niets.
 */
internal fun initialLinkText(draft: String, clipboard: String?, savedUrls: Set<String>): String {
    if (draft.isNotBlank()) return draft
    val all = clipboard?.let(::linksIn).orEmpty().distinct()
    if (all.size > 1) return all.joinToString("\n") // een lijst: de wachtrij slaat bestaande zelf over
    val link = all.firstOrNull() ?: return ""
    val clean = RecipeImporter.cleanUrl(link) ?: return ""
    return if (clean in savedUrls) "" else link
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLinkSheet(
    draft: String,
    savedUrls: Set<String>,
    onDismiss: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onSubmitMany: (List<String>) -> Unit = {},
    onManual: () -> Unit = {},
    onBookmarks: () -> Unit = {},
    onEazyFile: () -> Unit = {},
    onPickPhotos: () -> Unit = {},
    onCamera: () -> Unit = {},
) {
    val context = LocalContext.current
    // Wat we automatisch invulden; dat bewaren we niet als "jouw tekst" bij per ongeluk sluiten.
    var prefilled by remember { mutableStateOf("") }
    var text by remember { mutableStateOf(draft) }
    // Eén link, of een geplakte lijst (één per regel, of gewoon ergens in de tekst).
    val links = linksIn(text).mapNotNull { RecipeImporter.cleanUrl(it) }.distinct()
    val valid = links.isNotEmpty()
    val focus = remember { FocusRequester() }

    ModalBottomSheet(onDismissRequest = { onDismiss(if (text == prefilled) "" else text) }, containerColor = Eazy.Paper) {
        // Android geeft het klembord pas vrij als ons venster focus heeft: lees het (opnieuw) zodra dat zo is.
        val windowFocused = LocalWindowInfo.current.isWindowFocused
        LaunchedEffect(windowFocused) {
            if (windowFocused && text.isEmpty()) {
                initialLinkText("", readClipboard(context), savedUrls).takeIf { it.isNotEmpty() }?.let { text = it; prefilled = it }
            }
            if (windowFocused) focus.requestFocus()
        }
        Column(
            // Scrollen: met het toetsenbord open passen de andere manieren er op een kleine telefoon niet altijd onder.
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Recept toevoegen", style = MaterialTheme.typography.headlineSmall)
            Text("Plak de link van een receptpagina, of een hele lijst (één per regel).", style = MaterialTheme.typography.bodyMedium, color = Eazy.Muted)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("https://…") },
                // Altijd meerregelig: Enter = nieuwe regel (voor een lijst), ophalen gaat met de knop.
                singleLine = false,
                maxLines = 6,
                shape = MaterialTheme.shapes.medium,
                leadingIcon = { Ico(R.drawable.ic_link, 20.dp) },
                trailingIcon = {
                    // Vangnet als automatisch invullen niet lukt: één tik, en dan mag Android het klembord altijd lezen.
                    if (text.isEmpty()) TextButton(onClick = {
                        readClipboard(context)?.let { clip -> text = pasteText(clip) }
                    }) { Text("Plakken", style = MaterialTheme.typography.labelLarge, color = Eazy.Ink) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Default),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Eazy.Ink, cursorColor = Eazy.Ink),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            BigPillButton(
                if (links.size > 1) "${links.size} recepten ophalen" else "Recept ophalen",
                R.drawable.ph_sparkle, Eazy.Brand,
                onClick = { if (links.size > 1) onSubmitMany(links) else onSubmit(links.single()) },
                enabled = valid,
            )
            Text("Of op een andere manier", style = MaterialTheme.typography.labelLarge, color = Eazy.Muted, modifier = Modifier.padding(top = 6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PhotoOption(onCamera, onPickPhotos)
                AddOption(R.drawable.ph_pencil, Eazy.Brand, "Zelf invoeren", "Typ een recept over, zonder link", onManual)
                AddOption(R.drawable.ph_bookmark, Color(0xFF4D9DFF), "Bladwijzers", "Receptlinks uit Chrome, Firefox of Safari", onBookmarks)
                AddOption(R.drawable.ph_share, Color(0xFF47E3BC), ".eazy-bestand", "Een recept of collectie die iemand met je deelde", onEazyFile)
            }
        }
    }
}

/** Eén regel in "Of op een andere manier": gekleurd icoon, naam en uitleg. */
@Composable
private fun AddOption(@DrawableRes icon: Int, color: Color, label: String, note: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Eazy.Grey).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(color), contentAlignment = Alignment.Center) {
            Ico(icon, 20.dp, tint = Eazy.Ink)
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(note, style = MaterialTheme.typography.bodySmall, color = Eazy.Muted)
        }
        Ico(R.drawable.ic_chevron_right, 18.dp, tint = Eazy.Muted)
    }
}

/** Foto of screenshot: twee knoppen, want de galerij-kiezer van Android heeft geen camera. */
@Composable
private fun PhotoOption(onCamera: () -> Unit, onPick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Eazy.Grey).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFFF8AD4)), contentAlignment = Alignment.Center) {
                Ico(R.drawable.ph_camera, 20.dp, tint = Eazy.Ink)
            }
            Column(Modifier.weight(1f)) {
                Text("Foto of screenshot", style = MaterialTheme.typography.titleSmall)
                Text("Uit een kookboek of van een scherm; wij lezen de tekst", style = MaterialTheme.typography.bodySmall, color = Eazy.Muted)
            }
        }
        Row(Modifier.padding(start = 52.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Foto maken", R.drawable.ph_camera, Eazy.Paper, onClick = onCamera)
            Chip("Uit galerij", R.drawable.ph_folder, Eazy.Paper, onClick = onPick)
        }
    }
}

/** Klembord als tekst; `coerceToText` vangt ook links die als URI (niet als tekst) gekopieerd zijn. */
private fun readClipboard(context: Context): String? = runCatching {
    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
    if (clip == null || clip.itemCount == 0) null else clip.getItemAt(0).coerceToText(context)?.toString()
}.getOrNull()
