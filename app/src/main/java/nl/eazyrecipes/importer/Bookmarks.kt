package nl.eazyrecipes.importer

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

data class Bookmark(val title: String, val url: String, val folder: String)

/**
 * Leest een bladwijzers-export van Chrome, Firefox, Edge of Safari ("Netscape bookmark file": `<DT><H3>Map</H3><DL>…
 * <DT><A HREF="…">Titel</A>`). Alleen http(s)-links; de map is het pad van mappen erboven ("Eten › Pasta").
 */
object Bookmarks {
    fun parse(html: String): List<Bookmark> {
        val doc = Jsoup.parse(html)
        return doc.select("a[href]").mapNotNull { a ->
            val url = a.attr("href").trim()
            if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return@mapNotNull null
            Bookmark(a.text().trim().ifEmpty { url }.take(300), url, folderOf(a))
        }.distinctBy { it.url }
    }

    private fun folderOf(a: Element): String =
        a.parents().filter { it.tagName() == "dl" }
            .mapNotNull { dl -> dl.previousElementSibling()?.takeIf { it.tagName() == "h3" }?.text()?.trim() }
            .reversed()
            .joinToString(" › ")
}
