package nl.eazyrecipes.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ImageStoreScaleTest {
    @get:Rule val tmp = TemporaryFolder()

    private val resolver get() = ApplicationProvider.getApplicationContext<Context>().contentResolver

    @Test
    fun `galerijfoto wordt een verkleinde jpeg in de eigen map`() = runBlocking {
        val photo = tmp.newFile("groot.png")
        Bitmap.createBitmap(3200, 2000, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, photo.outputStream())
        val dir = tmp.newFolder("images")

        val saved = ImageStore(dir).saveFromUri(resolver, Uri.fromFile(photo), maxSide = 1600)

        assertNotNull(saved)
        assertEquals(dir.canonicalFile, saved!!.canonicalFile.parentFile)
        assertEquals("jpg", saved.extension)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(saved.path, bounds)
        assertEquals(1600, bounds.outWidth)
        assertEquals(1000, bounds.outHeight)
    }

    @Test
    fun `geen afbeelding geeft null`() = runBlocking {
        val notImage = tmp.newFile("tekst.png").apply { writeText("geen foto") }
        assertNull(ImageStore(tmp.newFolder("images")).saveFromUri(resolver, Uri.fromFile(notImage)))
    }
}
