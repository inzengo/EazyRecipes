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
    /** Voor schrijfacties die niet mogen stoppen als een scherm (en zijn ViewModel) verdwijnt, zoals een notitie opslaan. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

val Context.container: AppContainer get() = (applicationContext as EazyApp).container
