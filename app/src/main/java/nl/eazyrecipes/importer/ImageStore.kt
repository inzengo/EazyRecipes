package nl.eazyrecipes.importer

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.util.UUID

/** Bewaart receptfoto's lokaal (app-opslag), zodat de bibliotheek offline werkt en niet stukgaat als de bron verdwijnt. */
class ImageStore(
    private val dir: File,
    private val client: OkHttpClient = Http.client,
    private val maxBytes: Long = 15L * 1024 * 1024,
) {
    /** @return het lokale bestand, of `null` als downloaden mislukt (dan houden we alleen de bron-URL). */
    suspend fun download(url: String): File? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).header("Accept", "image/*").build()
            client.newCall(request).execute().use { r ->
                val body = r.body ?: return@use null
                val type = body.contentType()
                if (!r.isSuccessful || type?.type != "image" || body.contentLength() > maxBytes) return@use null
                val ext = when (type.subtype.lowercase()) {
                    "png" -> "png"; "webp" -> "webp"; "gif" -> "gif"; "avif" -> "avif"; else -> "jpg"
                }
                dir.mkdirs()
                val name = UUID.randomUUID().toString()
                val tmp = File(dir, "$name.part")
                val target = File(dir, "$name.$ext")
                if (body.byteStream().use { copyLimited(it, tmp) } && tmp.renameTo(target)) target
                else { tmp.delete(); null }
            }
        }.getOrNull()
    }

    /**
     * Slaat een foto uit de galerij op als JPEG van maximaal [maxSide] px, rechtop gedraaid (EXIF).
     * Telefoonfoto's zijn vaak 4000 px en 5-10 MB; voor een omslag is dat zonde van de opslag.
     * @return het lokale bestand, of `null` als het geen leesbare afbeelding is.
     */
    suspend fun saveFromUri(resolver: ContentResolver, uri: Uri, maxSide: Int = 1600): File? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                    val (w, h) = info.size.width to info.size.height
                    val scale = minOf(1.0, maxSide.toDouble() / maxOf(w, h))
                    decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                // Android 8: geen ImageDecoder. Verkleinen met inSampleSize; EXIF-draaiing wordt hier niet toegepast.
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                    ?.let { b ->
                        // inSampleSize verkleint alleen in machten van 2: de rest exact schalen.
                        val scale = maxSide.toDouble() / maxOf(b.width, b.height)
                        if (scale >= 1.0) b
                        else Bitmap.createScaledBitmap(b, (b.width * scale).toInt().coerceAtLeast(1), (b.height * scale).toInt().coerceAtLeast(1), true)
                    }
            } ?: return@runCatching null
            saveBitmap(bitmap)
        }.getOrNull()
    }

    internal fun saveBitmap(bitmap: Bitmap): File? {
        dir.mkdirs()
        val name = UUID.randomUUID().toString()
        val tmp = File(dir, "$name.part")
        val target = File(dir, "$name.jpg")
        val ok = runCatching { tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) } }.getOrDefault(false)
        return if (ok && tmp.renameTo(target)) target else { tmp.delete(); null }
    }

    /** @return false als de download groter is dan [maxBytes]. */
    private fun copyLimited(input: InputStream, target: File): Boolean = target.outputStream().use { out ->
        val buf = ByteArray(64 * 1024)
        var written = 0L
        var n = input.read(buf)
        while (n >= 0) {
            written += n
            if (written > maxBytes) return false
            out.write(buf, 0, n)
            n = input.read(buf)
        }
        true
    }

    /** Verwijdert alleen bestanden in onze eigen map. */
    fun delete(path: String?) {
        val file = path?.let(::File) ?: return
        if (file.canonicalFile.parentFile == dir.canonicalFile) file.delete()
    }
}
