package nl.eazyrecipes.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.content.FileProvider
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.time.LocalDate

/** Delen via het Android-deelmenu (WhatsApp, mail, Drive, …). */
object Sharing {

    /** Apps die een bericht boven hun limiet stilletjes niet versturen: die krijgen een ingekorte tekst. */
    internal val messageLimits = mapOf("com.discord" to 2000)

    fun shareText(context: Context, subject: String, text: String, sourceUrl: String? = null) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, text)
        val perApp = Bundle()
        messageLimits.forEach { (pkg, limit) ->
            if (text.length > limit) perApp.putBundle(pkg, Bundle().apply { putString(Intent.EXTRA_TEXT, RecipeText.fit(text, limit, sourceUrl)) })
        }
        val chooser = Intent.createChooser(send, "Recept delen")
        if (!perApp.isEmpty) chooser.putExtra(Intent.EXTRA_REPLACEMENT_EXTRAS, perApp)
        context.startActivity(chooser)
    }

    /** Schrijft een `.eazy` in de cache en opent het deelmenu ermee. Mislukt het, dan een melding (geen crash). */
    suspend fun shareFile(context: Context, name: String, write: suspend (OutputStream) -> Unit) {
        val file = try {
            withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                // Oude gedeelde bestanden opruimen; pas na een uur, want een mail- of Drive-app kan ze nog aan het lezen zijn.
                dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 60 * 60 * 1000 }?.forEach { it.delete() }
                File(dir, "${safeName(name)}.${EazyFile.EXTENSION}").also { f -> f.outputStream().use { write(it) } }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Toast.makeText(context, "Delen is mislukt: ${(e as? java.io.IOException)?.message ?: "probeer het opnieuw"}", Toast.LENGTH_LONG).show()
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).setType("application/octet-stream")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(name, uri)
        context.startActivity(Intent.createChooser(send, "Delen als bestand"))
    }

    fun backupName(today: LocalDate = LocalDate.now()) = "EazyRecipes-back-up-$today.${EazyFile.EXTENSION}"

    /** Bestandsnaam zonder tekens die Android of Windows niet slikt. */
    internal fun safeName(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|\n\r\t]"""), " ").replace(Regex("""\s+"""), " ").trim().take(60).ifEmpty { "recept" }
}
