package nl.eazyrecipes.importer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

sealed interface FetchResult {
    /** @param finalUrl de URL na redirects. */
    data class Page(val html: String, val finalUrl: String) : FetchResult
    data class HttpError(val code: Int) : FetchResult
    data class Failed(val cause: Throwable) : FetchResult
}

fun interface PageFetcher {
    suspend fun fetch(url: String): FetchResult
}

object Http {
    /** Gewone mobiele Chrome. Sommige sites geven anders een kale of geblokkeerde pagina. */
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .header("Accept-Language", "nl-NL,nl;q=0.9,en;q=0.8")
                        .build()
                )
            }
            .build()
    }
}

/** Snelle route: gewone HTTP-request. Werkt voor de meeste receptsites. */
class HttpPageFetcher(private val client: OkHttpClient = Http.client) : PageFetcher {
    override suspend fun fetch(url: String): FetchResult = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).header("Accept", "text/html,application/xhtml+xml").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) FetchResult.HttpError(response.code)
                else FetchResult.Page(response.body?.string().orEmpty(), response.request.url.toString())
            }
        }.getOrElse { FetchResult.Failed(it) }
    }
}
