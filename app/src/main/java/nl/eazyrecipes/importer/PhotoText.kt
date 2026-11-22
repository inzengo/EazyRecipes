package nl.eazyrecipes.importer

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Tekst van foto's of screenshots lezen met ML Kit, op het toestel zelf (er gaat niets naar internet). */
object PhotoText {

    /** Groot genoeg voor kleine letters op een kookboekpagina, klein genoeg voor het geheugen (een 50 MP-foto is ~200 MB). */
    private const val MAX_SIDE = 2500

    /**
     * Alle tekst van [uris], in volgorde: een recept over twee screenshots wordt één tekst.
     * Tekstblokken (een kolom, een alinea) komen op eigen regels, zodat de [PhotoRecipeParser] ze uit elkaar houdt.
     * @throws PhotoUnreadable als een foto niet (meer) te openen is.
     */
    suspend fun read(context: Context, uris: List<Uri>): String = withContext(Dispatchers.IO) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            uris.map { uri ->
                val image = load(context.contentResolver, uri)
                val result = suspendCancellableCoroutine { cont ->
                    recognizer.process(image)
                        .addOnSuccessListener { cont.resume(it) }
                        .addOnFailureListener { cont.resumeWithException(it) }
                }
                result.textBlocks.joinToString("\n") { it.text }
            }.joinToString("\n")
        } finally {
            recognizer.close()
        }
    }

    /** Verkleind inlezen en rechtop (EXIF). Toestemming verlopen (bv. na herstart van de app) = niet te openen. */
    private fun load(resolver: ContentResolver, uri: Uri): InputImage = try {
        if (Build.VERSION.SDK_INT >= 28) {
            // ImageDecoder past de EXIF-draaiing zelf toe.
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                val (w, h) = info.size.width to info.size.height
                val scale = minOf(1.0, MAX_SIDE.toDouble() / maxOf(w, h))
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            InputImage.fromBitmap(bitmap, 0)
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            val bitmap: Bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: throw IOException("Geen afbeelding")
            val rotation = resolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
            InputImage.fromBitmap(bitmap, rotation)
        }
    } catch (e: IOException) {
        throw PhotoUnreadable(e)
    } catch (e: SecurityException) {
        throw PhotoUnreadable(e)
    } catch (e: OutOfMemoryError) {
        throw PhotoUnreadable(e)
    }
}

class PhotoUnreadable(cause: Throwable) : IOException("Foto kan niet worden geopend", cause)
