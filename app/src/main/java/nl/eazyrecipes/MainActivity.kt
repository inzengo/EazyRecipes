package nl.eazyrecipes

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // De app is altijd licht: donkere systeem-iconen, ook als de telefoon in donkere modus staat.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (savedInstanceState == null) pendingShare = extractSharedUrl(intent)
        // Liepen er nog kookwekkers (app was afgesloten)? Dan de service weer starten.
        TimerService.ensureRunning(this)
        val c = container
        setContent {
            EazyTheme {
                val nav = rememberNavController()

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractSharedUrl(intent)?.let { pendingShare = it }
    }
}

private object Routes {
    const val LIBRARY = "library"
    const val IMPORT = "import?url={url}"
    const val RECIPE = "recipe/{id}"
    const val EDIT = "recipe/{id}/edit"
    const val COOK = "recipe/{id}/cook"
    fun import(url: String) = "import?url=${Uri.encode(url)}"
    fun recipe(id: String) = "recipe/$id"
    fun edit(id: String) = "recipe/$id/edit"
    fun cook(id: String) = "recipe/$id/cook"
}

private val urlRegex = Regex("""https?://\S+""")

/** Browsers delen vaak "Titel\nhttps://..." — pak de eerste URL uit de tekst. */
internal fun extractSharedUrl(intent: Intent?): String? {
    if (intent?.action != Intent.ACTION_SEND) return null
    val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
    return urlRegex.find(text)?.value
}
