package nl.eazyrecipes.importer

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume

/**
 * Laadt de pagina in een onzichtbare WebView: die voert JavaScript uit en komt zo door Cloudflare-controles
 * (bv. eefkooktzo.nl, die op gewone HTTP `403` geeft). We pollen de DOM tot er een recept in staat.
 */
class WebViewPageFetcher(
    private val context: Context,
    private val timeoutMs: Long = 25_000,
    private val pollMs: Long = 750,
) : PageFetcher {

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun fetch(url: String): FetchResult = withContext(Dispatchers.Main) {
        val webView = WebView(context.applicationContext)
        var mainFrameError: Int? = null
        var lastHtml: String? = null
        try {
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                blockNetworkImage = true // sneller; de foto halen we apart op
                // "; wv" verraadt een WebView en wordt door sommige bot-filters geweigerd.
                userAgentString = userAgentString.replace("; wv", "")
            }
            webView.webViewClient = object : WebViewClient() {
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (request.isForMainFrame) mainFrameError = response.statusCode
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                    mainFrameError = null // nieuwe pagina na een challenge-redirect
                }
            }
            webView.loadUrl(url)

            val found = withTimeoutOrNull(timeoutMs) {
                var page: FetchResult.Page? = null
                while (page == null) {
                    delay(pollMs)
                    val html = webView.outerHtml() ?: continue
                    lastHtml = html
                    if ("ld+json" in html && JsonLdRecipeParser.parse(html) != null) {
                        page = FetchResult.Page(html, webView.url ?: url)
                    }
                }
                page
            }
            found
                ?: mainFrameError?.let { FetchResult.HttpError(it) }
                ?: lastHtml?.let { FetchResult.Page(it, webView.url ?: url) }
                ?: FetchResult.Failed(TimeoutException("Pagina laadde niet binnen ${timeoutMs / 1000} s"))
        } finally {
            webView.stopLoading()
            webView.destroy()
        }
    }

    private suspend fun WebView.outerHtml(): String? = suspendCancellableCoroutine { cont ->
        evaluateJavascript("document.documentElement.outerHTML") { result ->
            // Het resultaat is een JSON-gecodeerde string (met aanhalingstekens en escapes), of "null".
            val html = runCatching { (Json.parseToJsonElement(result) as? JsonPrimitive)?.contentOrNull }.getOrNull()
            cont.resume(html)
        }
    }
}
