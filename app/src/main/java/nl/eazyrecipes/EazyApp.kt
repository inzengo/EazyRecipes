package nl.eazyrecipes

import android.app.Application
import android.content.Context
import nl.eazyrecipes.data.EazyDatabase
import nl.eazyrecipes.data.RecipeRepository
import nl.eazyrecipes.importer.HttpPageFetcher
import nl.eazyrecipes.importer.ImageStore
import nl.eazyrecipes.importer.RecipeImporter
import nl.eazyrecipes.importer.WebViewPageFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class EazyApp : Application() {
    val container by lazy { AppContainer(this) }
}

/** Handmatige dependency-injectie: één plek waar alles wordt aangemaakt. */
class AppContainer(context: Context) {
    val repository = RecipeRepository(EazyDatabase.get(context))
    val images = ImageStore(File(context.filesDir, "images"))
    val importer = RecipeImporter(http = HttpPageFetcher(), browser = WebViewPageFetcher(context))
    val bundles = nl.eazyrecipes.share.Bundles(repository, images)
    val batch by lazy { nl.eazyrecipes.importer.BatchImport(importer, repository, images, appScope) }
    val timers = nl.eazyrecipes.cook.KitchenTimers(context.getSharedPreferences("timers", Context.MODE_PRIVATE))
    val shopping = nl.eazyrecipes.data.ShoppingRepository(EazyDatabase.get(context))
    /** Wat je bij "In huis" hebt aangetikt; ook de boodschappenlijst kijkt mee. */
    val pantry = nl.eazyrecipes.ui.pantry.PantryStore(context.getSharedPreferences("pantry", Context.MODE_PRIVATE))
    /** Voor schrijfacties die niet mogen stoppen als een scherm (en zijn ViewModel) verdwijnt, zoals een notitie opslaan. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // Eenmalig (v0.7.2): foto's van vóór het verkleinen tot 1200 px kleiner maken; dat maakt scrollen sneller.
        val prefs = context.getSharedPreferences("upgrades", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(SHRUNK_IMAGES, false)) {
            appScope.launch(Dispatchers.IO) {
                images.shrinkAll()
                prefs.edit().putBoolean(SHRUNK_IMAGES, true).apply()
            }
        }
    }

    private companion object {
        const val SHRUNK_IMAGES = "images_shrunk_1200"
    }
}

val Context.container: AppContainer get() = (applicationContext as EazyApp).container
