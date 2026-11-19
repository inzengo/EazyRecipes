package nl.eazyrecipes.ui.importflow

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import nl.eazyrecipes.data.MainIngredient
import nl.eazyrecipes.importer.ParsedStep
import nl.eazyrecipes.ui.components.BigPillButton
import nl.eazyrecipes.ui.components.Chip
import nl.eazyrecipes.ui.components.DashedAddButton
import nl.eazyrecipes.ui.components.ErrorCard
import nl.eazyrecipes.ui.components.Ico
import nl.eazyrecipes.ui.components.RecipePhoto
import nl.eazyrecipes.ui.components.RoundButton
import nl.eazyrecipes.ui.components.StatTile
import nl.eazyrecipes.ui.components.TopRounded
import nl.eazyrecipes.ui.siteLabel
import nl.eazyrecipes.ui.theme.ContextColor
import nl.eazyrecipes.ui.theme.Eazy
import nl.eazyrecipes.ui.theme.context

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
        is ImportUiState.Loading -> LoadingView(url, s.viaBrowser, onClose)
        is ImportUiState.Failed -> FailedView(s, onRetry = vm::retry, onClose = onClose)
        is ImportUiState.Editing -> EditView(s, vm, onClose, onOpenExisting)
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
private fun FailedView(s: ImportUiState.Failed, onRetry: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Eazy.Grey).statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RoundButton(R.drawable.ic_close, "Sluiten", onClose, background = Eazy.Paper)
        Spacer(Modifier.height(8.dp))
        ErrorCard(s.title, s.message) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.canRetry) Chip("Opnieuw proberen", null, Eazy.Ink, contentColor = Eazy.Paper, onClick = onRetry)
                Chip("Terug", null, Eazy.Grey, onClick = onClose)
            }
        }
    }
}

@Composable
private fun EditView(s: ImportUiState.Editing, vm: ImportViewModel, onClose: () -> Unit, onOpenExisting: (String) -> Unit) {
    val d = s.draft
    val ctx = d.mainIngredient.context
    Box(Modifier.fillMaxSize().background(Eazy.Paper)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // --- foto ---
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                RecipePhoto(null, d.imageUrl, ctx, Modifier.fillMaxSize(), iconSize = 160.dp)
                RoundButton(R.drawable.ic_close, "Niet opslaan", onClose, Modifier.statusBarsPadding().padding(16.dp))
            }
            // --- witte kaart over de foto ---
            Column(
                Modifier.offset(y = (-36).dp).background(Eazy.Paper, TopRounded).padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text("Voorbeeld: controleer en pas aan", style = MaterialTheme.typography.labelLarge, color = Eazy.Muted)

                if (s.duplicateOf != null) DuplicateBanner { onOpenExisting(s.duplicateOf) }

                ContextPicker(d.mainIngredient) { pick -> vm.edit { it.copy(mainIngredient = pick) } }

                siteLabel(d.sourceUrl)?.let { Chip(it, R.drawable.ic_link, Eazy.Grey, contentColor = Eazy.Muted) }

                EditableText(
                    value = d.title,
                    onChange = { t -> vm.edit { it.copy(title = t) } },
                    style = MaterialTheme.typography.headlineMedium,
                    placeholder = "Naam van het recept",
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(R.drawable.ph_clock, "minuten", ctx, Modifier.weight(1f)) {
                        NumberField(d.totalMinutes) { n -> vm.edit { it.copy(totalMinutes = n) } }
                    }
                    StatTile(R.drawable.ph_users, "porties", ctx, Modifier.weight(1f)) {
                        NumberField(d.servings) { n -> vm.edit { it.copy(servings = n) } }
                    }
                    StatTile(R.drawable.ph_basket, "ingrediënten", ctx, Modifier.weight(1f)) {
                        Text("${d.ingredients.count { it.isNotBlank() }}", style = MaterialTheme.typography.titleLarge)
                    }
                }

                Section("Ingrediënten") {
                    d.ingredients.forEachIndexed { i, line ->
                        EditableRow(
                            value = line,
                            onChange = { t -> vm.edit { it.copy(ingredients = it.ingredients.replaceAt(i, t)) } },
                            onRemove = { vm.edit { it.copy(ingredients = it.ingredients.without(i)) } },
                            placeholder = "bv. 200 gr kipfilet",
                        )
                    }
                    DashedAddButton("Ingrediënt toevoegen", { vm.edit { it.copy(ingredients = it.ingredients + "") } })
                }

                Section("Bereiding") {
                    d.steps.forEachIndexed { i, step ->
                        val prev = d.steps.getOrNull(i - 1)?.section
                        if (step.section != null && step.section != prev && d.steps.mapNotNull { it.section }.distinct().size > 1) {
                            Text(step.section, style = MaterialTheme.typography.titleMedium, color = Eazy.Muted, modifier = Modifier.padding(top = 6.dp))
                        }
                        StepEditor(
                            number = i + 1,
                            step = step,
                            ctx = ctx,
                            onChange = { t -> vm.edit { it.copy(steps = it.steps.replaceAt(i, it.steps[i].copy(text = t))) } },
                            onRemove = { vm.edit { it.copy(steps = it.steps.without(i)) } },
                        )
                    }
                    DashedAddButton("Stap toevoegen", {
                        vm.edit { it.copy(steps = it.steps + ParsedStep("", it.steps.lastOrNull()?.section)) }
                    })
                }
            }
        }

        // --- vaste knop onderaan ---
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            s.saveError?.let {
                Text(it, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clip(CircleShape).background(Eazy.Paper).padding(horizontal = 14.dp, vertical = 8.dp))
            }
            BigPillButton(
                text = if (s.saving) "Opslaan…" else "Opslaan in bibliotheek",
                icon = R.drawable.ic_check,
                accent = ctx.vivid.takeIf { d.mainIngredient != null } ?: Eazy.Brand,
                onClick = vm::save,
                enabled = s.canSave,
            )
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

@Composable
private fun ContextPicker(selected: MainIngredient?, onPick: (MainIngredient) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Soort", style = MaterialTheme.typography.labelLarge, color = Eazy.Muted)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MainIngredient.entries.forEach { m ->
                val c = m.context
                val on = m == selected
                Chip(c.label, c.icon, if (on) c.vivid else Eazy.Grey, onClick = { onPick(m) }, selected = on)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        content()
    }
}

@Composable
private fun EditableText(
    value: String,
    onChange: (String) -> Unit,
    style: TextStyle,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = style.copy(color = Eazy.Ink),
        cursorBrush = SolidColor(Eazy.Ink),
        keyboardOptions = keyboard,
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = style, color = Eazy.Subtle)
                inner()
            }
        },
    )
}

@Composable
private fun NumberField(value: Int?, onChange: (Int?) -> Unit) {
    EditableText(
        value = value?.toString().orEmpty(),
        onChange = { t -> onChange(t.filter(Char::isDigit).take(4).toIntOrNull()) },
        style = MaterialTheme.typography.titleLarge,
        placeholder = "–",
        keyboard = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun EditableRow(value: String, onChange: (String) -> Unit, onRemove: () -> Unit, placeholder: String) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(Eazy.Grey).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditableText(value, onChange, MaterialTheme.typography.bodyLarge, placeholder, Modifier.weight(1f).padding(vertical = 10.dp))
        RemoveButton(onRemove)
    }
}

@Composable
private fun StepEditor(number: Int, step: ParsedStep, ctx: ContextColor, onChange: (String) -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Eazy.Grey).padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("$number", style = MaterialTheme.typography.headlineSmall, color = ctx.deep, modifier = Modifier.width(24.dp))
        EditableText(step.text, onChange, MaterialTheme.typography.bodyMedium, "Beschrijf deze stap", Modifier.weight(1f))
        RemoveButton(onRemove)
    }
}

@Composable
private fun RemoveButton(onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = androidx.compose.ui.graphics.Color.Transparent, contentColor = Eazy.Subtle, modifier = Modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) { Ico(R.drawable.ic_close, 16.dp, contentDescription = "Verwijderen") }
    }
}

private fun <T> List<T>.replaceAt(i: Int, value: T): List<T> = toMutableList().also { it[i] = value }
private fun <T> List<T>.without(i: Int): List<T> = toMutableList().also { it.removeAt(i) }
