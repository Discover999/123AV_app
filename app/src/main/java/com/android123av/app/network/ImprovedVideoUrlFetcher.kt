package com.android123av.app.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.activity.ComponentActivity
import com.android123av.app.constants.AppConstants
import com.android123av.app.constants.NetworkConstants
import com.android123av.app.models.Video
import com.android123av.app.models.VideoPart
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object ImprovedVideoUrlFetcher {
    private val cachedUrls = ConcurrentHashMap<String, CachedVideoUrl>()
    private val fetchAttempts = ConcurrentHashMap<String, AtomicInteger>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var webView: WebView? = null

    data class CachedVideoUrl(
        val url: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class FetchResult(
        val url: String?,
        val source: FetchSource,
        val attemptCount: Int,
        val durationMs: Long
    )

    enum class FetchSource {
        CACHE,
        HTTP_DIRECT,
        HTTP_PARSED,
        WEBVIEW,
        FALLBACK,
        VIDEO_PART
    }

    suspend fun fetchVideoUrl(
        context: Context,
        video: Video,
        maxRetries: Int = 3,
        timeoutMs: Long = AppConstants.WEBVIEW_TIMEOUT_MS
    ): FetchResult {
        val startTime = System.currentTimeMillis()
        val videoId = video.id

        if (!video.videoUrl.isNullOrBlank()) {
            cacheUrl(videoId, video.videoUrl)
            return FetchResult(
                url = video.videoUrl,
                source = FetchSource.CACHE,
                attemptCount = 0,
                durationMs = System.currentTimeMillis() - startTime
            )
        }

        val cached = getCachedUrl(videoId)
        if (cached != null) {
            return FetchResult(
                url = cached,
                source = FetchSource.CACHE,
                attemptCount = 0,
                durationMs = System.currentTimeMillis() - startTime
            )
        }

        var lastError: Exception? = null
        val attemptCounter = fetchAttempts.getOrPut(videoId) { AtomicInteger(0) }

        repeat(maxRetries) { attempt ->
            attemptCounter.incrementAndGet()
            val delayMs = calculateExponentialBackoff(attempt)
            if (attempt > 0) {
                delay(delayMs)
            }

            try {
                val url = fetchWithParallelStrategy(context, videoId, timeoutMs)
                if (url != null) {
                    cacheUrl(videoId, url)
                    return FetchResult(
                        url = url,
                        source = determineSource(url),
                        attemptCount = attemptCounter.get(),
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            } catch (e: Exception) {
                lastError = e
            }
        }

        try {
            val fallbackUrl = fetchFromVideoParts(context, videoId, timeoutMs)
            if (fallbackUrl != null) {
                cacheUrl(videoId, fallbackUrl)
                return FetchResult(
                    url = fallbackUrl,
                    source = FetchSource.VIDEO_PART,
                    attemptCount = attemptCounter.get(),
                    durationMs = System.currentTimeMillis() - startTime
                )
            }
        } catch (e: Exception) {
            lastError = e
        }

        return FetchResult(
            url = null,
            source = FetchSource.FALLBACK,
            attemptCount = attemptCounter.get(),
            durationMs = System.currentTimeMillis() - startTime
        )
    }

    private suspend fun fetchWithParallelStrategy(
        context: Context,
        videoId: String,
        timeoutMs: Long
    ): String? = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) {
            return@withContext null
        }

        val isFavorite = videoId.startsWith("fav_")
        var result: String? = null

        try {
            withTimeout(timeoutMs) {
                coroutineScope {
                    val httpDeferred = async {
                        if (isFavorite) {
                            null
                        } else {
                            tryFetchHttpUrl(videoId)
                        }
                    }

                    val webViewDeferred = async {
                        try {
                            fetchM3u8UrlWithWebViewImproved(context, videoId, timeoutMs)
                        } catch (e: Exception) {
                            null
                        }
                    }

                    try {
                        result = select<String?> {
                            httpDeferred.onAwait { httpResult ->
                                if (!httpResult.isNullOrBlank()) httpResult else null
                            }
                            webViewDeferred.onAwait { webViewResult ->
                                if (!webViewResult.isNullOrBlank()) webViewResult else null
                            }
                        }
                    } catch (e: Exception) {
                        result = try {
                            httpDeferred.await() ?: webViewDeferred.await()
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            result = tryFetchWithFallback(context, videoId, isFavorite, timeoutMs)
        } catch (e: Exception) {
            result = tryFetchWithFallback(context, videoId, isFavorite, timeoutMs)
        }

        result
    }

    private suspend fun tryFetchHttpUrl(videoId: String): String? {
        return try {
            val videoDetailUrl = SiteManager.buildZhUrl("v/$videoId")
            val request = Request.Builder()
                .url(videoDetailUrl)
                .headers(commonHeaders())
                .build()

            val response = getOkHttpClient().newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                parseVideoUrlFromHtml(html)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseVideoUrlFromHtml(html: String): String? {
        return try {
            val doc = Jsoup.parse(html)

            val videoElement = doc.selectFirst("video")
            if (videoElement != null) {
                val videoUrl = videoElement.attr("src")
                if (videoUrl.isNotBlank()) {
                    return videoUrl
                }
            }

            val iframeElement = doc.selectFirst("iframe[src*='video']")
            if (iframeElement != null) {
                val src = iframeElement.attr("src")
                if (src.isNotBlank()) {
                    return src
                }
            }

            val scriptElements = doc.select("script")
            val patterns = listOf(
                """(https?://[^"]+\.m3u8[^"]*)""",
                """source:\s*["']([^"']+\.m3u8[^"']*)["']""",
                """videoUrl:\s*["']([^"']+)["']""",
                """["'](https?://[^"']+\.mp4[^"']*)["']""",
                """["'](https?://[^"']+\.m3u8[^"']*)["']"""
            )

            for (pattern in patterns) {
                val matchResult = Regex(pattern, RegexOption.IGNORE_CASE).find(html)
                if (matchResult != null) {
                    val url = matchResult.groupValues[1]
                    if (url.isNotBlank() && (url.contains(".m3u8") || url.contains(".mp4"))) {
                        return url
                    }
                }
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchM3u8UrlWithWebViewImproved(
        context: Context,
        videoId: String,
        timeoutMs: Long
    ): String? = withContext(Dispatchers.Main) {
        if (videoId.isBlank()) {
            return@withContext null
        }

        val videoDetailUrl = SiteManager.buildZhUrl("v/$videoId")
        val result = CompletableDeferred<String?>()
        val foundResult = AtomicBoolean(false)

        try {
            if (context is ComponentActivity && context.isFinishing) {
                result.complete(null)
                return@withContext null
            }

            val currentWebView = webView ?: WebView(context).also {
                webView = it
                configureWebView(it)
            }

            val cleanup = {
                try {
                    currentWebView.stopLoading()
                } catch (e: Exception) {
                }
            }

            currentWebView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    if (foundResult.get() || result.isCompleted) {
                        return super.shouldInterceptRequest(view, request)
                    }

                    val url = request.url.toString().lowercase()
                    val isVideoFile = url.endsWith(".m3u8") ||
                            url.endsWith(".mp4") ||
                            url.endsWith(".mpd") ||
                            url.contains(".m3u8?") ||
                            url.contains(".mp4?") ||
                            url.contains(".mpd?")

                    if (isVideoFile) {
                        if (foundResult.compareAndSet(false, true)) {
                            result.complete(request.url.toString())
                            cleanup()
                        }
                    }

                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (!result.isCompleted && !foundResult.get()) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (!result.isCompleted) {
                                result.complete(null)
                            }
                        }, 3000)
                    }
                }
            }

            val timeoutHandler = Handler(Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                if (!result.isCompleted) {
                    foundResult.set(true)
                    result.complete(null)
                    cleanup()
                }
            }
            timeoutHandler.postDelayed(timeoutRunnable, timeoutMs)

            currentWebView.loadUrl(videoDetailUrl)

        } catch (e: Exception) {
            if (!result.isCompleted) {
                result.complete(null)
            }
        }

        result.await()
    }

    private suspend fun tryFetchWithFallback(
        context: Context,
        videoId: String,
        isFavorite: Boolean,
        timeoutMs: Long
    ): String? {
        return try {
            if (isFavorite) {
                fetchM3u8UrlWithWebViewImproved(context, videoId, timeoutMs)
            } else {
                tryFetchHttpUrl(videoId)
                    ?: fetchM3u8UrlWithWebViewImproved(context, videoId, timeoutMs)
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchFromVideoParts(
        context: Context,
        videoId: String,
        timeoutMs: Long
    ): String? {
        return try {
            val parts = fetchAllVideoParts(context, videoId)
            parts.firstOrNull { !it.url.isNullOrBlank() }?.url
        } catch (e: Exception) {
            null
        }
    }

    private fun determineSource(url: String): FetchSource {
        return when {
            url.contains(".m3u8") && url.startsWith("http") -> FetchSource.HTTP_PARSED
            url.contains("video") || url.contains("iframe") -> FetchSource.HTTP_DIRECT
            else -> FetchSource.WEBVIEW
        }
    }

    private fun calculateExponentialBackoff(attempt: Int): Long {
        val baseDelay = 500L
        val maxDelay = 5000L
        val delay = baseDelay * (1 shl attempt).coerceAtMost(16)
        return delay.coerceAtMost(maxDelay)
    }

    private fun cacheUrl(videoId: String, url: String) {
        cachedUrls[videoId] = CachedVideoUrl(url)
        cleanupOldCache()
    }

    private fun getCachedUrl(videoId: String): String? {
        val cached = cachedUrls[videoId] ?: return null
        val expirationMs = AppConstants.VIDEO_CACHE_EXPIRATION_MS
        if (System.currentTimeMillis() - cached.timestamp > expirationMs) {
            cachedUrls.remove(videoId)
            return null
        }
        return cached.url
    }

    private fun cleanupOldCache() {
        if (cachedUrls.size > NetworkConstants.VIDEO_URL_CACHE_SIZE * 2) {
            val expirationMs = AppConstants.VIDEO_CACHE_EXPIRATION_MS
            cachedUrls.entries.removeIf { entry ->
                System.currentTimeMillis() - entry.value.timestamp > expirationMs
            }
        }
    }

    fun clearCache() {
        cachedUrls.clear()
        fetchAttempts.clear()
    }

    fun clearWebView() {
        try {
            webView?.destroy()
            webView = null
        } catch (e: Exception) {
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var releaseRunnable: Runnable? = null

    /**
     * 在指定延迟后释放共享 WebView（默认 60s 无活动后释放）。
     */
    fun scheduleReleaseAfter(delayMs: Long = 60_000L) {
        try {
            cancelScheduledRelease()
            val r = Runnable {
                try {
                    clearWebView()
                } catch (_: Exception) {
                }
                releaseRunnable = null
            }
            releaseRunnable = r
            mainHandler.postDelayed(r, delayMs)
        } catch (e: Exception) {
        }
    }

    fun cancelScheduledRelease() {
        try {
            releaseRunnable?.let { mainHandler.removeCallbacks(it) }
            releaseRunnable = null
        } catch (e: Exception) {
        }
    }

    /**
     * 立即释放共享 WebView
     */
    fun immediateRelease() {
        try {
            cancelScheduledRelease()
            clearWebView()
        } catch (e: Exception) {
        }
    }

    fun getCacheSize(): Int = cachedUrls.size

    fun getAttemptCount(videoId: String): Int = fetchAttempts[videoId]?.get() ?: 0

    private fun configureWebView(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            blockNetworkImage = false
            allowFileAccess = true
            allowContentAccess = true
        }
    }

    fun prefetchVideoUrl(context: Context, videoId: String) {
        if (getCachedUrl(videoId) != null) return
        if (videoId.startsWith("fav_")) return

        scope.launch {
            try {
                val url = tryFetchHttpUrl(videoId)
                url?.let { cacheUrl(videoId, it) }
            } catch (e: Exception) {
            }
        }
    }

    fun warmUpWebView(context: Context) {
        try {
            if (webView != null) return
            // create and configure a hidden WebView on main thread
            Handler(Looper.getMainLooper()).post {
                try {
                    val wv = WebView(context)
                    configureWebView(wv)
                    // load a lightweight blank page to initialize
                    wv.loadUrl("about:blank")
                    webView = wv
                } catch (e: Exception) {
                }
            }
        } catch (e: Exception) {
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> useSharedWebView(context: Context, block: suspend (WebView) -> T): T = withContext(Dispatchers.Main) {
        val currentWebView = webView ?: WebView(context).also {
            webView = it
            configureWebView(it)
        }

        try {
            block(currentWebView)
        } catch (e: Exception) {
            throw e
        }
    }
}

suspend fun improvedFetchVideoUrl(
    context: Context,
    video: Video,
    maxRetries: Int = 3,
    timeoutMs: Long = AppConstants.WEBVIEW_TIMEOUT_MS
): String? {
    return ImprovedVideoUrlFetcher.fetchVideoUrl(
        context = context,
        video = video,
        maxRetries = maxRetries,
        timeoutMs = timeoutMs
    ).url
}

suspend fun improvedFetchVideoUrlWithDetails(
    context: Context,
    video: Video,
    maxRetries: Int = 3,
    timeoutMs: Long = AppConstants.WEBVIEW_TIMEOUT_MS
): ImprovedVideoUrlFetcher.FetchResult {
    return ImprovedVideoUrlFetcher.fetchVideoUrl(
        context = context,
        video = video,
        maxRetries = maxRetries,
        timeoutMs = timeoutMs
    )
}
