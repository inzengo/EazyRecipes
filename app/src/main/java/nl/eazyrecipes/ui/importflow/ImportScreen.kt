package nl.eazyrecipes.ui.importflow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.eazyrecipes.R
import nl.eazyrecipes.importer.ParsedStep
import nl.eazyrecipes.ui.editor.RecipeEditor
import nl.eazyrecipes.ui.components.Chip
import nl.eazyrecipes.ui.components.DashedAddButton
import nl.eazyrecipes.ui.components.ErrorCard
import nl.eazyrecipes.ui.components.Ico
import nl.eazyrecipes.ui.components.PhotoStatusBar
import nl.eazyrecipes.ui.components.PhotoTopScrim
import nl.eazyrecipes.ui.components.RecipePhoto
import nl.eazyrecipes.ui.components.RoundButton
import nl.eazyrecipes.ui.components.StatTile
import nl.eazyrecipes.ui.components.TopRounded
import nl.eazyrecipes.ui.siteLabel
import nl.eazyrecipes.ui.theme.ContextColor
import nl.eazyrecipes.ui.theme.Eazy

@Composable
fun ImportScreen(
    url: String,
    vm: ImportViewModel,
    onClose: () -> Unit,
    onSaved: (String) -> Unit,
    onOpenExisting: (String) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    when (val s = state) {
        is ImportUiState.Loading -> if (s.photoCount > 0) PhotoLoadingView(s.photoCount, onClose) else LoadingView(url, s.viaBrowser, onClose)
        is ImportUiState.Failed -> FailedView(s, onRetry = vm::retry, onManual = vm::manual, onClose = onClose)
        is ImportUiState.Editing -> RecipeEditor(
            draft = s.draft,
            imagePath = null,
            onEdit = vm::edit,
            onClose = onClose,
            closeLabel = "Niet opslaan",
            saveLabel = "Opslaan in bibliotheek",
            saving = s.saving,
            canSave = s.canSave,
            saveError = s.saveError,
            onSave = vm::save,
            banner = { if (s.duplicateOf != null) DuplicateBanner { onOpenExisting(s.duplicateOf) } },
        )
        is ImportUiState.Saved -> LaunchedEffect(s.id) { onSaved(s.id) }
    }
}

@Composable
private fun LoadingView(url: String, viaBrowser: Boolean, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Eazy.Grey).statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RoundButton(R.drawable.ic_close, "Annuleren", onClose, background = Eazy.Paper)
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator(color = Eazy.Ink, strokeWidth = 4.dp, modifier = Modifier.size(40.dp))
        Text("Recept\nophalen…", style = MaterialTheme.typography.displaySmall)
        Chip(siteLabel(url) ?: url, R.drawable.ic_link, Eazy.Paper)
        if (viaBrowser) {
            Text(
                "Deze site houdt robots tegen. We openen hem nu onzichtbaar in een browser; dat duurt even.",
                style = MaterialTheme.typography.bodyMedium,
                color = Eazy.Muted,
            )
        }
    }
}

@Composable
private fun PhotoLoadingView(count: Int, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Eazy.Grey).statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RoundButton(R.drawable.ic_close, "Annuleren", onClose, background = Eazy.Paper)
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator(color = Eazy.Ink, strokeWidth = 4.dp, modifier = Modifier.size(40.dp))
        Text("Tekst\nlezen…", style = MaterialTheme.typography.displaySmall)
        Chip(if (count == 1) "1 foto" else "$count foto's", R.drawable.ph_camera, Eazy.Paper)
        Text(
            "Daarna zie je het recept zoals we het gelezen hebben. Kijk het even na: een foto lezen gaat niet altijd foutloos.",
            style = MaterialTheme.typography.bodyMedium,
            color = Eazy.Muted,
        )
    }
}

@Composable
private fun FailedView(s: ImportUiState.Failed, onRetry: () -> Unit, onManual: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Eazy.Grey).statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RoundButton(R.drawable.ic_close, "Sluiten", onClose, background = Eazy.Paper)
        Spacer(Modifier.height(8.dp))
        ErrorCard(s.title, s.message) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.canRetry) Chip("Opnieuw proberen", null, Eazy.Ink, contentColor = Eazy.Paper, onClick = onRetry)
                Chip("Zelf invoeren", R.drawable.ph_pencil, Eazy.Brand, onClick = onManual)
                Chip("Terug", null, Eazy.Grey, onClick = onClose)
            }
        }
    }
}

@Composable
private fun DuplicateBanner(onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Eazy.Brand).padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(R.drawable.ph_bookmark, 20.dp, tint = Eazy.Ink)
        Text(
            "Dit recept staat al in je bibliotheek.",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        TextButton(onClick = onOpen) { Text("Bekijk", style = MaterialTheme.typography.labelMedium, color = Eazy.Ink) }
    }
}

