package nl.eazyrecipes

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.eazyrecipes.ui.categories.CategoriesScreen
import nl.eazyrecipes.ui.labels.LabelsScreen
import nl.eazyrecipes.share.BundleImportScreen
import nl.eazyrecipes.share.BundleImportViewModel
import nl.eazyrecipes.ui.labels.LabelsViewModel
import nl.eazyrecipes.ui.categories.CategoriesViewModel
import nl.eazyrecipes.ui.theme.CategorySet
import nl.eazyrecipes.ui.theme.LocalCategories
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import nl.eazyrecipes.cook.CookScreen
import nl.eazyrecipes.cook.TimerService
import nl.eazyrecipes.ui.detail.RecipeScreen
import nl.eazyrecipes.ui.detail.RecipeViewModel
import nl.eazyrecipes.ui.editor.EditRecipeScreen
import nl.eazyrecipes.ui.editor.EditRecipeViewModel
import nl.eazyrecipes.ui.importflow.ImportScreen
import nl.eazyrecipes.ui.importflow.ImportViewModel
import nl.eazyrecipes.ui.home.HomeScreen
import nl.eazyrecipes.ui.library.LibraryViewModel
import nl.eazyrecipes.ui.pantry.PantryScreen
import nl.eazyrecipes.ui.pantry.PantryViewModel
import nl.eazyrecipes.ui.theme.EazyTheme

class MainActivity : ComponentActivity() {

    /** Link uit "Delen → EazyRecipes" die nog naar het importscherm moet. */
    private var pendingShare by mutableStateOf<String?>(null)

    /** Een geopend/gedeeld .eazy-bestand dat nog naar het importscherm moet. */
    private var pendingFile by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // De app is altijd licht: donkere systeem-iconen, ook als de telefoon in donkere modus staat.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (savedInstanceState == null) {
            pendingShare = extractSharedUrl(intent)
            pendingFile = extractSharedFile(intent, packageName)
        }
        // Liepen er nog kookwekkers (app was afgesloten)? Dan de service weer starten.
        TimerService.ensureRunning(this)
        val c = container
        setContent {
            // Categorieën van de gebruiker voor alle schermen (kleur, icoon, naam per recept). Pas tekenen als ze er zijn
            // (een paar ms): anders flitsen tegels en kleuren bij het opstarten van leeg naar gevuld.
            val categoriesFlow = remember { c.repository.observeCategories() }
            val categories by categoriesFlow.collectAsStateWithLifecycle(null)
            val loaded = categories ?: return@setContent
            EazyTheme {
              CompositionLocalProvider(LocalCategories provides remember(loaded) { CategorySet(loaded) }) {
                val nav = rememberNavController()

                LaunchedEffect(pendingFile) {
                    pendingFile?.let { uri ->
                        nav.navigate(Routes.bundle(uri)) { launchSingleTop = true }
                        pendingFile = null
                    }
                }
                LaunchedEffect(pendingShare) {
                    pendingShare?.let { url ->
                        nav.navigate(Routes.import(url)) { launchSingleTop = true }
                        pendingShare = null
                    }
                }

                NavHost(nav, startDestination = Routes.LIBRARY) {
                    composable(Routes.LIBRARY) {
                        val library = viewModel { LibraryViewModel(c.repository) }
                        HomeScreen(
                            library = library,
                            onOpen = { nav.navigate(Routes.recipe(it)) },
                            onImport = { nav.navigate(Routes.import(it)) },
                            onOpenCategories = { nav.navigate(Routes.CATEGORIES) },
                            onOpenLabels = { nav.navigate(Routes.LABELS) },
                            onOpenFile = { nav.navigate(Routes.bundle(it)) },
                            pantry = {
                                val vm = viewModel { PantryViewModel(c.repository, getSharedPreferences("pantry", MODE_PRIVATE)) }
                                PantryScreen(vm, onOpen = { nav.navigate(Routes.recipe(it)) })
                            },
                        )
                    }
                    composable(
                        Routes.IMPORT,
                        arguments = listOf(navArgument("url") { type = NavType.StringType }),
                    ) { entry ->
                        val url = entry.arguments?.getString("url").orEmpty()
                        val vm = viewModel { ImportViewModel(url, c.importer, c.repository, c.images) }
                        ImportScreen(
                            url = url,
                            vm = vm,
                            onClose = { if (!nav.popBackStack()) finish() },
                            onSaved = { id -> nav.navigate(Routes.recipe(id)) { popUpTo(Routes.LIBRARY) } },
                            onOpenExisting = { id -> nav.navigate(Routes.recipe(id)) { popUpTo(Routes.LIBRARY) } },
                        )
                    }
                    composable(
                        Routes.RECIPE,
                        arguments = listOf(navArgument("id") { type = NavType.StringType }),
                    ) { entry ->
                        val id = entry.arguments?.getString("id").orEmpty()
                        val vm = viewModel { RecipeViewModel(id, c.repository, c.images, c.appScope) }
                        RecipeScreen(vm, onBack = { nav.popBackStack() }, onEdit = { nav.navigate(Routes.edit(id)) }, onCook = { nav.navigate(Routes.cook(id)) })
                    }
                    composable(
                        Routes.BUNDLE,
                        arguments = listOf(navArgument("uri") { type = NavType.StringType }),
                    ) { entry ->
                        val uri = Uri.parse(entry.arguments?.getString("uri").orEmpty())
                        val vm = viewModel { BundleImportViewModel({ contentResolver.openInputStream(uri) }, c.bundles, c.appScope) }
                        BundleImportScreen(vm, onClose = { if (!nav.popBackStack()) finish() })
                    }
                    composable(Routes.LABELS) {
                        val vm = viewModel { LabelsViewModel(c.repository) }
                        LabelsScreen(vm, onBack = { nav.popBackStack() })
                    }
                    composable(Routes.CATEGORIES) {
                        val vm = viewModel { CategoriesViewModel(c.repository) }
                        CategoriesScreen(vm, onBack = { nav.popBackStack() })
                    }
                    composable(
                        Routes.COOK,
                        arguments = listOf(navArgument("id") { type = NavType.StringType }),
                    ) { entry ->
                        val id = entry.arguments?.getString("id").orEmpty()
                        // Zelfde ViewModel als het detailscherm eronder: dan kloppen de gekozen porties.
                        val parent = remember(entry) { nav.getBackStackEntry(Routes.RECIPE) }
                        val vm = viewModel(parent) { RecipeViewModel(id, c.repository, c.images, c.appScope) }
                        CookScreen(vm, onClose = { nav.popBackStack() })
                    }
                    composable(
                        Routes.EDIT,
                        arguments = listOf(navArgument("id") { type = NavType.StringType }),
                    ) { entry ->
                        val id = entry.arguments?.getString("id").orEmpty()
                        val vm = viewModel { EditRecipeViewModel(id, c.repository) }
                        EditRecipeScreen(vm, onDone = { nav.popBackStack() })
                    }
                }
              }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractSharedUrl(intent)?.let { pendingShare = it }
        extractSharedFile(intent, packageName)?.let { pendingFile = it }
    }
}

private object Routes {
    const val LIBRARY = "library"
    const val IMPORT = "import?url={url}"
    const val RECIPE = "recipe/{id}"
    const val EDIT = "recipe/{id}/edit"
    const val COOK = "recipe/{id}/cook"
    const val CATEGORIES = "categories"
    const val LABELS = "labels"
    const val BUNDLE = "bundle?uri={uri}"
    fun bundle(uri: Uri) = "bundle?uri=${Uri.encode(uri.toString())}"
    fun import(url: String) = "import?url=${Uri.encode(url)}"
    // Ids komen soms uit een gedeeld bestand: altijd coderen, dan breekt een vreemd teken de route niet.
    fun recipe(id: String) = "recipe/${Uri.encode(id)}"
    fun edit(id: String) = "recipe/${Uri.encode(id)}/edit"
    fun cook(id: String) = "recipe/${Uri.encode(id)}/cook"
}

private val urlRegex = Regex("""https?://\S+""")

/** Een bestand dat met "Openen met" of "Delen" binnenkomt (een .eazy, hopelijk; het importscherm controleert dat). */
@Suppress("DEPRECATION")
internal fun extractSharedFile(intent: Intent?, ownPackage: String): Uri? {
    val uri: Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> if (intent.type == "text/plain") null else intent.getParcelableExtra(Intent.EXTRA_STREAM)
        else -> null
    }
    // Alleen content://, en niet onze eigen gedeelde bestanden: een file://-pad zouden we met onze eigen rechten
    // openen (een andere app zou ons zo eigen privébestanden kunnen laten lezen).
    return uri?.takeIf { it.scheme == "content" && it.authority != "$ownPackage.files" }
}

/** Browsers delen vaak "Titel\nhttps://..." — pak de eerste URL uit de tekst. */
internal fun extractSharedUrl(intent: Intent?): String? {
    if (intent?.action != Intent.ACTION_SEND) return null
    val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
    return urlRegex.find(text)?.value
}
