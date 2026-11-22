package nl.eazyrecipes.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nl.eazyrecipes.R
import nl.eazyrecipes.ui.components.BigPillButton
import nl.eazyrecipes.ui.components.ErrorCard
import nl.eazyrecipes.ui.components.Ico
import nl.eazyrecipes.ui.components.RoundButton
import nl.eazyrecipes.ui.components.StatusBarBackdrop
import nl.eazyrecipes.ui.theme.Eazy
import java.io.InputStream

sealed interface BundleState {
    data object Loading : BundleState
    data class Preview(val bundle: EazyFile.Bundle, val alreadyThere: Set<String>) : BundleState {
        val newCount: Int get() = bundle.recipes.count { it.id !in alreadyThere }
    }
    data object Importing : BundleState
    data class Done(val result: ImportResult) : BundleState
    data class Failed(val message: String) : BundleState
}

/** Opent een `.eazy` (gedeeld recept, collectie of back-up): eerst een voorbeeld, dan importeren. */
class BundleImportViewModel(
    private val open: () -> InputStream?,
    private val bundles: Bundles,
    /** Importeren loopt hierin door, ook als je het scherm sluit (anders blijft er een half import achter). */
    private val appScope: kotlinx.coroutines.CoroutineScope,
) : ViewModel() {
    private val _state = MutableStateFlow<BundleState>(BundleState.Loading)
    val state: StateFlow<BundleState> = _state

    init {
        viewModelScope.launch {
            _state.value = try {
                val b = bundles.peek { open() ?: throw EazyFile.InvalidFile("Het bestand kon niet worden geopend.") }
                BundleState.Preview(b, bundles.alreadyThere(b))
            } catch (e: Exception) {
                BundleState.Failed((e as? EazyFile.InvalidFile)?.message ?: "Dit bestand kon niet worden gelezen.")
            }
        }
    }

    fun import() {
        if (_state.value !is BundleState.Preview) return
        _state.value = BundleState.Importing
        appScope.launch {
            _state.value = try {
                BundleState.Done(bundles.import { open() ?: throw EazyFile.InvalidFile("Het bestand kon niet worden geopend.") })
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                BundleState.Failed((e as? EazyFile.InvalidFile)?.message ?: "Importeren is mislukt. Er is niets half achtergebleven; probeer het opnieuw.")
            }
        }
    }
}

@Composable
fun BundleImportScreen(vm: BundleImportViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    BundleImportContent(state, onImport = vm::import, onClose = onClose)
}

@Composable
fun BundleImportContent(state: BundleState, onImport: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Eazy.Grey)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state != BundleState.Importing) RoundButton(R.drawable.ic_close, "Sluiten", onClose, background = Eazy.Paper)
            when (state) {
                BundleState.Loading, BundleState.Importing -> Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Eazy.Ink)
                }
                is BundleState.Failed -> ErrorCard("Kan dit bestand niet openen", state.message)
                is BundleState.Preview -> Preview(state)
                is BundleState.Done -> Done(state.result)
            }
        }
        StatusBarBackdrop(Eazy.Grey)
        val bottom = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)
        when (state) {
            is BundleState.Preview -> BigPillButton(
                if (state.newCount == 0) "Alles staat er al" else if (state.newCount == 1) "1 recept toevoegen" else "${state.newCount} recepten toevoegen",
                R.drawable.ic_check, Eazy.Brand, onImport, bottom, enabled = state.newCount > 0 || state.bundle.collections.isNotEmpty(),
            )
            is BundleState.Done, is BundleState.Failed -> BigPillButton("Klaar", R.drawable.ic_check, Eazy.Brand, onClose, bottom)
            else -> Unit
        }
    }
}

@Composable
private fun Preview(p: BundleState.Preview) {
    val b = p.bundle
    val title = when (b.kind) {
        EazyFile.Kind.RECIPE -> "Recept\nopenen"
        EazyFile.Kind.COLLECTION -> b.collections.firstOrNull()?.name ?: "Collectie"
        EazyFile.Kind.BACKUP -> "Back-up\nterugzetten"
    }
    Text(title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 12.dp))
    Text(
        buildString {
            append(if (b.recipes.size == 1) "1 recept" else "${b.recipes.size} recepten")
            if (p.alreadyThere.isNotEmpty()) append(" · ${p.alreadyThere.size} ${if (p.alreadyThere.size == 1) "staat" else "staan"} er al (die slaan we over)")
            if (b.kind == EazyFile.Kind.BACKUP) append(". Je huidige recepten blijven gewoon staan.")
        },
        style = MaterialTheme.typography.bodyMedium,
        color = Eazy.Muted,
    )
    b.recipes.take(12).forEach { r ->
        val there = r.id in p.alreadyThere
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Eazy.Paper).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(if (there) Eazy.Grey else Eazy.Brand), contentAlignment = Alignment.Center) {
                Ico(if (there) R.drawable.ic_check else R.drawable.ic_plus, 16.dp, tint = Eazy.Ink)
            }
            Column(Modifier.weight(1f)) {
                Text(r.title, style = MaterialTheme.typography.titleMedium)
                if (there) Text("Staat al in je bibliotheek", style = MaterialTheme.typography.bodySmall, color = Eazy.Muted)
            }
        }
    }
    if (b.recipes.size > 12) Text("en nog ${b.recipes.size - 12} recepten", style = MaterialTheme.typography.bodyMedium, color = Eazy.Muted)
}

@Composable
private fun Done(r: ImportResult) {
    Text("Gelukt!", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 12.dp))
    Text(
        buildString {
            append(if (r.added == 1) "1 recept toegevoegd" else "${r.added} recepten toegevoegd")
            if (r.alreadyThere > 0) append(", ${r.alreadyThere} stond${if (r.alreadyThere == 1) "" else "en"} er al")
            append(".")
            r.collection?.let { append(" Ze staan in de collectie \"$it\".") }
        },
        style = MaterialTheme.typography.bodyLarge,
    )
}
