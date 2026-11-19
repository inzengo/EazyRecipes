package nl.eazyrecipes.importer

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RecipeImporterTest {

    private val recipeHtml =
        """<html><head><script type="application/ld+json">{"@type":"Recipe","name":"Soep"}</script></head></html>"""
    private val plainHtml = "<html><body>Geen recept</body></html>"

    /** Nep-fetcher die per aanroep het volgende antwoord geeft en bijhoudt welke URL's hij kreeg. */
    private class Fake(vararg val answers: FetchResult) : PageFetcher {
        val calls = mutableListOf<String>()
        override suspend fun fetch(url: String): FetchResult { calls += url; return answers[calls.size - 1] }
    }

    @Test
    fun `http met recept - browser wordt niet gebruikt`() = runTest {
        val http = Fake(FetchResult.Page(recipeHtml, "https://site.nl/soep"))
        val browser = Fake()
        val r = RecipeImporter(http, browser).import("https://site.nl/soep")
        assertEquals("Soep", (r as ImportResult.Success).recipe.title)
        assertEquals(false, r.usedBrowser)
        assertEquals("https://site.nl/soep", r.recipe.sourceUrl)
        assertTrue(browser.calls.isEmpty())
    }

    @Test
    fun `403 van Cloudflare - browser haalt het recept`() = runTest {
        val browser = Fake(FetchResult.Page(recipeHtml, "https://eef.nl/soep/"))
        var notified = 0
        val r = RecipeImporter(Fake(FetchResult.HttpError(403)), browser).import("https://eef.nl/soep/") { notified++ }
        assertTrue(r is ImportResult.Success && r.usedBrowser)
        assertEquals("UI hoort dat het langer duurt", 1, notified)
        assertEquals(listOf("https://eef.nl/soep/"), browser.calls)
    }

    @Test
    fun `403 en browser faalt ook - geblokkeerd`() = runTest {
        val r = RecipeImporter(Fake(FetchResult.HttpError(403)), Fake(FetchResult.Failed(IOException()))).import("https://eef.nl/x")
        assertEquals(ImportResult.Failed(ImportResult.Reason.BLOCKED, httpCode = 403), r)
    }

    @Test
    fun `pagina zonder recept - browser geprobeerd (JS-site), daarna geen recept`() = runTest {
        val browser = Fake(FetchResult.Page(plainHtml, "https://site.nl/x"))
        val r = RecipeImporter(Fake(FetchResult.Page(plainHtml, "https://site.nl/x")), browser).import("https://site.nl/x")
        assertEquals(ImportResult.NoRecipe("https://site.nl/x"), r)
        assertEquals(1, browser.calls.size)
    }

    @Test
    fun `pagina zonder recept en browser timeout - geen recept, geen netwerkfout`() = runTest {
        val r = RecipeImporter(Fake(FetchResult.Page(plainHtml, "https://site.nl/x")), Fake(FetchResult.Failed(IOException())))
            .import("https://site.nl/x")
        assertEquals(ImportResult.NoRecipe("https://site.nl/x"), r)
    }

    @Test
    fun `404 - geen browser, http-fout`() = runTest {
        val browser = Fake()
        val r = RecipeImporter(Fake(FetchResult.HttpError(404)), browser).import("https://site.nl/weg")
        assertEquals(ImportResult.Failed(ImportResult.Reason.HTTP, httpCode = 404), r)
        assertTrue(browser.calls.isEmpty())
    }

    @Test
    fun `geen netwerk - geen browser, netwerkfout`() = runTest {
        val browser = Fake()
        val r = RecipeImporter(Fake(FetchResult.Failed(IOException("offline"))), browser).import("https://site.nl/x")
        assertEquals(ImportResult.Reason.NETWORK, (r as ImportResult.Failed).reason)
        assertTrue(browser.calls.isEmpty())
    }

    @Test
    fun `zonder browser (tests, of nog niet beschikbaar) - geblokkeerd`() = runTest {
        val r = RecipeImporter(Fake(FetchResult.HttpError(403)), browser = null).import("https://eef.nl/x")
        assertEquals(ImportResult.Reason.BLOCKED, (r as ImportResult.Failed).reason)
    }

    @Test
    fun `ongeldige link`() = runTest {
        val http = Fake()
        assertEquals(ImportResult.Failed(ImportResult.Reason.INVALID_URL), RecipeImporter(http, null).import("geen link"))
        assertEquals(ImportResult.Failed(ImportResult.Reason.INVALID_URL), RecipeImporter(http, null).import("ftp://x.nl/a"))
        assertTrue(http.calls.isEmpty())
    }

    @Test
    fun `tracking-parameters en fragment worden weggehaald, ook na redirect`() = runTest {
        val http = Fake(FetchResult.Page(recipeHtml, "https://site.nl/soep/?utm_source=fb&id=3#reacties"))
        val r = RecipeImporter(http, null).import("  https://site.nl/s?fbclid=abc&utm_medium=x&p=2#top ")
        assertEquals(listOf("https://site.nl/s?p=2"), http.calls)
        assertEquals("https://site.nl/soep/?id=3", (r as ImportResult.Success).recipe.sourceUrl)
    }

    @Test
    fun cleanUrl() {
        assertEquals("https://www.leukerecepten.nl/recepten/kip-kerrie/", RecipeImporter.cleanUrl("https://www.leukerecepten.nl/recepten/kip-kerrie/?utm_campaign=nieuwsbrief"))
        assertNull(RecipeImporter.cleanUrl("mailto:a@b.nl"))
    }
}
