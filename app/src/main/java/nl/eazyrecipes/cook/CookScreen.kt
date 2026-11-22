package nl.eazyrecipes.cook

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.eazyrecipes.R
import nl.eazyrecipes.container
import nl.eazyrecipes.data.RecipeDetails
import nl.eazyrecipes.recipe.IngredientParts
import nl.eazyrecipes.ui.components.Ico
import nl.eazyrecipes.ui.components.RoundButton
import nl.eazyrecipes.ui.detail.DetailState
import nl.eazyrecipes.ui.detail.RecipeViewModel
import nl.eazyrecipes.ui.theme.BricolageDisplay
import nl.eazyrecipes.ui.theme.Eazy
import nl.eazyrecipes.ui.theme.context

class CookActions(
    val startTimer: (step: Int, seconds: Int) -> Unit = { _, _ -> },
    val dismissTimer: (id: String) -> Unit = {},
    val close: () -> Unit = {},
)

/** Kookmodus met de ViewModel van het detailscherm (zelfde porties). */
@Composable
fun CookScreen(vm: RecipeViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val d = (state as? DetailState.Found)?.details ?: return
    val context = LocalContext.current
    val kitchen = context.container.timers
    val timers by kitchen.timers.collectAsStateWithLifecycle()

    // Meldingen (Android 13+) vragen we pas bij de eerste timer: dan snap je waarom.
    var pending by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val start = { step: Int, seconds: Int ->
        kitchen.start(d.recipe.id, d.recipe.title, step, seconds)
        TimerService.ensureRunning(context)
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, "Zonder meldingen hoor je het alarm wel, maar zie je buiten de app niet welke timer het is.", Toast.LENGTH_LONG).show()
        pending?.let { (step, sec) -> start(step, sec) }
        pending = null
    }

    CookContent(
        d = d,
        factor = vm.factor,
        servings = vm.servings,
        timers = timers.filter { it.recipeId == d.recipe.id },
        actions = CookActions(
            startTimer = { step, seconds ->
                // Eén keer vragen; zei je nee, dan niet bij elke timer opnieuw zeuren.
                val prefs = context.getSharedPreferences("timers", android.content.Context.MODE_PRIVATE)
                val needsAsk = Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean(ASKED, false) &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                if (needsAsk) {
                    prefs.edit().putBoolean(ASKED, true).apply()
                    pending = step to seconds
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else start(step, seconds)
            },
            dismissTimer = { TimerService.dismiss(context, it) },
            close = onClose,
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CookContent(
    d: RecipeDetails,
    factor: Double,
    servings: Int?,
    timers: List<KitchenTimer>,
    actions: CookActions,
    now: Long? = null,
    initialStep: Int = 0,
) {
    val ctx = d.recipe.mainIngredient.context
    val bg = if (d.recipe.mainIngredient == null) Eazy.Brand else ctx.vivid
    val steps = d.steps
    val pager = rememberPagerState(initialPage = initialStep) { steps.size.coerceAtLeast(1) }
    val scope = rememberCoroutineScope()

    // Scherm blijft aan zolang de kookmodus open is.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    BackHandler { actions.close() }

    // Klok voor de aftellende knoppen; in een screenshot-test vast ([now]).
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (now == null) LaunchedEffect(timers.isNotEmpty()) {
        while (timers.isNotEmpty()) { tick = System.currentTimeMillis(); delay(250) }
    }
    // Ook `tick` lezen (hertekenen elke 250 ms), maar altijd de echte tijd: anders flitst bij de eerste timer een oude tijd.
    val clock = now ?: run { tick; System.currentTimeMillis() }

    val parts = remember(d.ingredients) { d.ingredients.map { IngredientParts.parse(it.raw) } }
    val lines = remember(parts, factor) { parts.map { p -> listOf(p.amount(factor), p.name).filter { it.isNotEmpty() }.joinToString(" ") } }
    val perStep = remember(parts, steps) { steps.map { s -> ingredientsInStep(s.text, parts.map { it.name }) } }

    Box(Modifier.fillMaxSize().background(bg)) {
        Box(Modifier.align(Alignment.BottomEnd).offset(x = 90.dp, y = (-120).dp).wrapContentSize(unbounded = true)) {
            Ico(ctx.icon, 340.dp, tint = Eazy.Ink.copy(alpha = 0.07f))
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RoundButton(R.drawable.ic_close, "Kookmodus sluiten", actions.close, background = Eazy.Paper.copy(alpha = 0.6f))
                Text(d.recipe.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Row(
                    Modifier.height(34.dp).clip(CircleShape).background(Eazy.Ink.copy(alpha = 0.12f)).padding(start = 10.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Ico(R.drawable.ph_sun, 15.dp)
                    Text("Scherm blijft aan", style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                steps.indices.forEach { i ->
                    Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(if (i <= pager.currentPage) Eazy.Ink else Eazy.Ink.copy(alpha = 0.16f)))
                }
            }

            // Timers die bij een andere stap lopen: altijd zichtbaar, tik = naar die stap.
            val elsewhere = timers.filter { it.step != pager.currentPage }.sortedBy { it.endAt }
            if (elsewhere.isNotEmpty()) {
                FlowRow(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    elsewhere.forEach { t ->
                        Surface(onClick = { scope.launch { pager.animateScrollToPage(t.step) } }, shape = CircleShape, color = if (t.ringing) Eazy.Paper else Eazy.Ink, contentColor = if (t.ringing) Eazy.Ink else Eazy.Paper) {
                            Row(Modifier.height(34.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Ico(R.drawable.ph_timer, 15.dp)
                                Text("Stap ${t.step + 1} · ${if (t.ringing) "klaar!" else formatCountdown(t.remainingSeconds(clock))}", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            HorizontalPager(pager, Modifier.weight(1f)) { page ->
                val step = steps.getOrNull(page)
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "%02d".format(page + 1),
                            style = MaterialTheme.typography.displayLarge.copy(fontFamily = BricolageDisplay, fontSize = 112.sp, lineHeight = 96.sp, letterSpacing = (-6).sp),
                        )
                        Text("van %02d".format(steps.size), style = MaterialTheme.typography.labelLarge, color = Eazy.Ink.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 14.dp))
                    }
                    step?.sectionName?.takeIf { d.steps.mapNotNull { it.sectionName }.distinct().size > 1 }?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium, color = Eazy.Ink.copy(alpha = 0.7f))
                    }
                    val text = step?.text ?: "Dit recept heeft geen stappen."
                    // Groot leesbaar vanaf het aanrecht, maar een lange stap moet nog (bijna) op één scherm passen.
                    val (size, line) = when {
                        text.length > 260 -> 20.sp to 27.sp
                        text.length > 160 -> 23.sp to 31.sp
                        else -> 26.sp to 34.sp
                    }
                    Text(
                        text,
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = MaterialTheme.typography.bodyLarge.fontFamily, fontWeight = FontWeight.Bold, fontSize = size, lineHeight = line),
                    )
                    step?.let { s ->
                        val found = remember(s.text) { StepTimers.find(s.text) }
                        found.forEach { st ->
                            val running = timers.firstOrNull { it.step == page && it.seconds == st.seconds }
                            TimerButton(st, running, clock, onStart = { actions.startTimer(page, st.seconds) }, onDismiss = { running?.let { actions.dismissTimer(it.id) } })
                        }
                    }
                    Box(Modifier.height(8.dp))
                    StepIngredients(perStep.getOrNull(page).orEmpty(), lines, servings)
                }
            }

            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val first = pager.currentPage == 0
                val last = pager.currentPage >= steps.size - 1
                Surface(
                    onClick = { if (first) actions.close() else scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                    shape = CircleShape, color = Eazy.Paper, contentColor = Eazy.Ink, modifier = Modifier.size(68.dp),
                ) { Box(contentAlignment = Alignment.Center) { Ico(R.drawable.ic_back, 24.dp, contentDescription = if (first) "Terug naar recept" else "Vorige stap") } }
                Surface(
                    onClick = { if (last) actions.close() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    shape = CircleShape, color = Eazy.Ink, contentColor = Eazy.Paper, modifier = Modifier.weight(1f).height(68.dp),
                ) {
                    Row(Modifier.padding(start = 26.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (last) "Klaar, eet smakelijk!" else "Volgende stap", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        Box(Modifier.size(50.dp).clip(CircleShape).background(Eazy.Paper), contentAlignment = Alignment.Center) {
                            Ico(if (last) R.drawable.ic_check else R.drawable.ic_chevron_right, 22.dp, tint = Eazy.Ink)
                        }
                    }
                }
            }
        }
    }
}

private const val ASKED = "askedNotifications"

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimerButton(st: StepTimer, running: KitchenTimer?, now: Long, onStart: () -> Unit, onDismiss: () -> Unit) {
    val ringing = running?.ringing == true
    // Afgegaan: knipperen tussen wit en zwart, zodat je het van een afstand ziet.
    val blink by rememberInfiniteTransition(label = "timer").animateColor(
        Eazy.Paper, Eazy.Ink, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "blink",
    )
    val bgColor = when {
        ringing -> blink
        running != null -> Eazy.Paper
        else -> Eazy.Ink
    }
    val fg = if (running != null && !ringing) Eazy.Ink else if (ringing) (if (blink.luminance() > 0.5f) Eazy.Ink else Eazy.Paper) else Eazy.Paper
    val label = when {
        ringing -> "Klaar! Tik om te stoppen"
        running != null -> "Loopt · ${formatCountdown(running.remainingSeconds(now))}"
        else -> "Start timer · ${st.label}"
    }
    val context = LocalContext.current
    // Lopende timer stoppen = lang indrukken: met natte handen tik je er zo per ongeluk op.
    Row(
        Modifier.clip(CircleShape).background(bgColor).combinedClickable(
            onClick = {
                when {
                    running == null -> onStart()
                    ringing -> onDismiss()
                    else -> Toast.makeText(context, "Houd ingedrukt om de timer te stoppen", Toast.LENGTH_SHORT).show()
                }
            },
            onLongClick = { if (running != null) onDismiss() },
        ).height(52.dp).padding(start = 14.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Ico(R.drawable.ph_timer, 20.dp, tint = fg)
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold), color = fg)
        if (running != null && !ringing) Text("houd vast = stop", style = MaterialTheme.typography.labelSmall, color = Eazy.Muted)
    }
}

private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepIngredients(inStep: List<Int>, lines: List<String>, servings: Int?) {
    if (lines.isEmpty()) return
    var all by rememberSaveable { mutableStateOf(false) }
    val shown = if (all || inStep.isEmpty()) lines.indices.toList() else inStep
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Eazy.Paper.copy(alpha = 0.55f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val what = if (shown.size == lines.size) "Ingrediënten" else "Bij deze stap"
            Text(what + (servings?.let { " · $it porties" } ?: ""), style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp), modifier = Modifier.weight(1f))
            if (inStep.isNotEmpty() && inStep.size < lines.size) {
                Text(
                    if (all) "Alleen deze stap" else "Alles tonen",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                    modifier = Modifier.clip(CircleShape).clickable { all = !all }.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            shown.forEach { i ->
                Text(
                    lines[i],
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.clip(CircleShape).background(Eazy.Paper).padding(horizontal = 11.dp, vertical = 7.dp),
                )
            }
        }
    }
}
