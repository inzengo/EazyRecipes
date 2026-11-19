package nl.eazyrecipes.importer

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HttpPageFetcherTest {
    private val server = MockWebServer()

    @Before fun start() = server.start()
    @After fun stop() = server.shutdown()

    @Test
    fun `200 met UTF-8, browser-user-agent en nl-taal`() = runTest {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody("<p>crème fraîche</p>"))
        val r = HttpPageFetcher().fetch(server.url("/recept").toString())
        assertEquals("<p>crème fraîche</p>", (r as FetchResult.Page).html)
        val req = server.takeRequest()
        assertTrue(req.getHeader("User-Agent")!!.contains("Chrome"))
        assertTrue(req.getHeader("Accept-Language")!!.startsWith("nl"))
    }

    @Test
    fun `redirect geeft de uiteindelijke url`() = runTest {
        server.enqueue(MockResponse().setResponseCode(301).setHeader("Location", "/echt"))
        server.enqueue(MockResponse().setBody("ok"))
        val r = HttpPageFetcher().fetch(server.url("/kort").toString())
        assertEquals(server.url("/echt").toString(), (r as FetchResult.Page).finalUrl)
    }

    @Test
    fun `403 wordt HttpError`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))
        assertEquals(FetchResult.HttpError(403), HttpPageFetcher().fetch(server.url("/").toString()))
    }

    @Test
    fun `server weg wordt Failed`() = runTest {
        val url = server.url("/").toString()
        server.shutdown()
        assertTrue(HttpPageFetcher().fetch(url) is FetchResult.Failed)
    }
}

class ImageStoreTest {
    private val server = MockWebServer()
    @get:Rule val tmp = TemporaryFolder()

    @Before fun start() = server.start()
    @After fun stop() = server.shutdown()

    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2, 3)

    @Test
    fun `afbeelding wordt opgeslagen met juiste extensie`() = runTest {
        server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(png)))
        val dir = tmp.newFolder("images")
        val file = ImageStore(dir).download(server.url("/foto").toString())!!
        assertEquals(dir, file.parentFile)
        assertTrue(file.name.endsWith(".png"))
        assertArrayEquals(png, file.readBytes())
        assertEquals("geen .part-resten", listOf(file.name), dir.list()!!.toList())
    }

    @Test
    fun `geen afbeelding, fout of te groot geeft null en laat niets achter`() = runTest {
        val dir = tmp.newFolder("images")
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html>"))
        server.enqueue(MockResponse().setResponseCode(404))
        // Chunked: geen Content-Length vooraf, dus de limiet moet tijdens het lezen ingrijpen.
        server.enqueue(MockResponse().setHeader("Content-Type", "image/jpeg").setChunkedBody(Buffer().write(ByteArray(5000)), 512))
        val store = ImageStore(dir, maxBytes = 1000)
        repeat(3) { assertNull(store.download(server.url("/x$it").toString())) }
        assertTrue(dir.list()!!.isEmpty())
    }

    @Test
    fun `delete raakt alleen eigen bestanden`() {
        val dir = tmp.newFolder("images")
        val own = dir.resolve("a.jpg").apply { writeText("x") }
        val other = tmp.newFile("elders.jpg")
        val store = ImageStore(dir)
        store.delete(other.path)
        store.delete(own.path)
        assertTrue(other.exists())
        assertTrue(!own.exists())
    }
}
