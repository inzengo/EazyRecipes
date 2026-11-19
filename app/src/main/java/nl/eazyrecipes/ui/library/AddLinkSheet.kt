package nl.eazyrecipes.ui.library

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
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
import nl.eazyrecipes.ui.components.Ico
import nl.eazyrecipes.ui.theme.Eazy

internal fun linkIn(text: String): String? = Regex("""https?://\S+""").find(text)?.value

/**
 * Wat staat er al in het veld als "link plakken" opent?
 * 1. tekst van de vorige keer, als het venster zonder importeren dicht ging;
 * 2. anders een link van het klembord, tenzij dat recept al in de bibliotheek staat;
 * 3. anders niets.
 */
internal fun initialLinkText(draft: String, clipboard: String?, savedUrls: Set<String>): String {
    if (draft.isNotBlank()) return draft
    val link = clipboard?.let(::linkIn) ?: return ""
    val clean = RecipeImporter.cleanUrl(link) ?: return ""
    return if (clean in savedUrls) "" else link
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLinkSheet(draft: String, savedUrls: Set<String>, onDismiss: (String) -> Unit, onSubmit: (String) -> Unit) {
    val context = LocalContext.current
    // Wat we automatisch invulden; dat bewaren we niet als "jouw tekst" bij per ongeluk sluiten.
    var prefilled by remember { mutableStateOf("") }
    var text by remember { mutableStateOf(draft) }
    val valid = RecipeImporter.cleanUrl(text) != null
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
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Recept toevoegen", style = MaterialTheme.typography.headlineSmall)
            Text("Plak de link van een receptpagina.", style = MaterialTheme.typography.bodyMedium, color = Eazy.Muted)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("https://…") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                leadingIcon = { Ico(R.drawable.ic_link, 20.dp) },
                trailingIcon = {
                    // Vangnet als automatisch invullen niet lukt: één tik, en dan mag Android het klembord altijd lezen.
                    if (text.isEmpty()) TextButton(onClick = {
                        readClipboard(context)?.let { clip -> text = linkIn(clip) ?: clip.trim() }
                    }) { Text("Plakken", style = MaterialTheme.typography.labelLarge, color = Eazy.Ink) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (valid) onSubmit(text.trim()) }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Eazy.Ink, cursorColor = Eazy.Ink),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            BigPillButton("Recept ophalen", R.drawable.ph_sparkle, Eazy.Brand, onClick = { onSubmit(text.trim()) }, enabled = valid)
        }
    }
}

/** Klembord als tekst; `coerceToText` vangt ook links die als URI (niet als tekst) gekopieerd zijn. */
private fun readClipboard(context: Context): String? = runCatching {
    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
    if (clip == null || clip.itemCount == 0) null else clip.getItemAt(0).coerceToText(context)?.toString()
}.getOrNull()
