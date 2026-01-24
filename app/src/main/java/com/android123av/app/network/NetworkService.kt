package com.android123av.app.network

import android.app.Activity
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebResourceError
import com.android123av.app.models.*
import com.android123av.app.constants.AppConstants
import com.android123av.app.constants.NetworkConstants
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import java.io.IOException
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicBoolean
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 9 Build/AD1A.240411.003.A5; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/124.0.6367.54 Mobile Safari/537.36"

fun commonHeaders(): okhttp3.Headers {
    return okhttp3.Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Accept", "*/*")
        .add("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        .build()
}

fun apiHeaders(referer: String = ""): okhttp3.Headers {
    return okhttp3.Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Accept", "application/json, text/plain, */*")
        .add("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        .add("Referer", referer)
        .add("Origin", referer.removeSuffix("/"))
        .add("X-Requested-With", "XMLHttpRequest")
        .build()
}

private val videoUrlCache = object : LinkedHashMap<String, CachedVideoUrl>(NetworkConstants.VIDEO_URL_CACHE_SIZE, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedVideoUrl>?): Boolean {
        return size > NetworkConstants.VIDEO_URL_CACHE_SIZE
    }
}

private val cacheLock = Any()

private const val CACHE_EXPIRATION_MS = NetworkConstants.CACHE_EXPIRATION_MS

data class CachedVideoUrl(
    val url: String?,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > CACHE_EXPIRATION_MS
}

fun getCachedVideoUrl(videoId: String): String? {
    synchronized(cacheLock) {
        val cached = videoUrlCache[videoId]
        return when {
            cached == null -> null
            cached.isExpired() -> {
                videoUrlCache.remove(videoId)
                null
            }
            else -> cached.url
        }
    }
}

fun cacheVideoUrl(videoId: String, url: String?) {
    synchronized(cacheLock) {
        videoUrlCache[videoId] = CachedVideoUrl(url)
    }
}

fun clearVideoUrlCache() {
    synchronized(cacheLock) {
        videoUrlCache.clear()
    }
}

fun warmupCache(context: android.content.Context, videoId: String) {
    if (videoId.isBlank() || videoId.startsWith("fav_")) return

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val url = fetchVideoUrlSync(videoId)
            cacheVideoUrl(videoId, url)
        } catch (_: Exception) {
        }
    }
}

suspend fun fetchVideoUrlParallel(context: android.content.Context, videoId: String, timeoutMs: Long = AppConstants.DEFAULT_TIMEOUT_MS): String? = withContext(Dispatchers.IO) {
    if (videoId.isBlank()) {
        return@withContext null
    }

    val cachedUrl = getCachedVideoUrl(videoId)
    if (cachedUrl != null) {
        return@withContext cachedUrl
    }
    
    val isFavorite = videoId.startsWith("fav_")
    
    var finalResult: String? = null
    
    try {
        withTimeout(timeoutMs) {
            coroutineScope {
                val httpDeferred = async {
                    if (isFavorite) {
                        null
                    } else {
                        try {
                            fetchVideoUrlSync(videoId)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                
                val webViewDeferred = async {
                    try {
                        fetchM3u8UrlWithWebViewFast(context, videoId)
                    } catch (e: Exception) {
                        null
                    }
                }
                
                val result = try {
                    select<Pair<String?, String?>> {
                        httpDeferred.onAwait { httpResult -> 
                            Pair(httpResult, "HTTP") 
                        }
                        webViewDeferred.onAwait { webViewResult -> 
                            Pair(webViewResult, "WebView") 
                        }
                    }
                } catch (e: Exception) {
                    val httpResult = try { httpDeferred.await() } catch (e: Exception) { null }
                    val webViewResult = try { webViewDeferred.await() } catch (e: Exception) { null }
                    Pair(httpResult ?: webViewResult, "Fallback")
                }
                
                finalResult = result.first
            }
        }
    } catch (e: TimeoutCancellationException) {
        finalResult = tryFetchWithFallback(context, videoId, isFavorite)
    } catch (e: Exception) {
        finalResult = tryFetchWithFallback(context, videoId, isFavorite)
    }
    
    finalResult?.let { url ->
        cacheVideoUrl(videoId, url)
    }
    
    finalResult
}

private suspend fun tryFetchWithFallback(context: android.content.Context, videoId: String, isFavorite: Boolean): String? {
    return try {
        if (isFavorite) {
            fetchM3u8UrlWithWebView(context, videoId)
        } else {
            fetchVideoUrlSync(videoId) ?: fetchM3u8UrlWithWebView(context, videoId)
        }
    } catch (e: Exception) {
        null
    }
}

private fun fetchVideoUrlSync(videoId: String): String? {
    if (videoId.isBlank() || videoId.startsWith("fav_")) {
        return null
    }
    
    val videoDetailUrl = SiteManager.buildZhUrl("v/$videoId")
    
    val request = Request.Builder()
        .url(videoDetailUrl)
        .headers(commonHeaders())
        .build()
    
    return try {
        val response = getOkHttpClient().newCall(request).execute()
        if (response.isSuccessful) {
            val html = response.body?.string() ?: ""
            val doc = Jsoup.parse(html)
            
            val videoElement = doc.selectFirst("video")
            if (videoElement != null) {
                val videoUrl = videoElement.attr("src")
                if (videoUrl.isNotBlank()) {
                    return videoUrl
                }
            }
            
            val scriptElements = doc.select("script")
            for (script in scriptElements) {
                val scriptContent = script.data()
                if (scriptContent.contains(".m3u8") || scriptContent.contains("source")) {
                    val urlPattern = """(https?:\/\/[^"]+\.m3u8[^"]*)"""
                    val matchResult = Regex(urlPattern).find(scriptContent)
                    if (matchResult != null) {
                        return matchResult.groups[1]?.value
                    }
                }
            }
            
            null
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

suspend fun fetchM3u8UrlWithWebViewFast(context: android.content.Context, videoId: String, timeoutMs: Long = AppConstants.FAST_TIMEOUT_MS): String? = withContext(Dispatchers.IO) {
    if (videoId.isBlank()) {
        return@withContext null
    }
    
    val videoDetailUrl = SiteManager.buildZhUrl("v/$videoId")
    val result = CompletableDeferred<String?>()
    
    withContext(Dispatchers.Main) {
        var webView: WebView? = null
        var timeoutHandler: Handler? = null
        var timeoutRunnable: Runnable? = null
        val foundResult = AtomicBoolean(false)
        
        val cleanup = {
            try {
                timeoutHandler?.removeCallbacks(timeoutRunnable!!)
                webView?.let { wv ->
                    Handler(Looper.getMainLooper()).post {
                        wv.stopLoading()
                        wv.destroy()
                    }
                }
            } catch (e: Exception) {
            }
        }
        
        try {
            if (context is Activity && context.isFinishing) {
                result.complete(null)
                return@withContext
            }
            
            webView = WebView(context)
            val currentWebView = webView ?: return@withContext
            
            configureWebView(currentWebView)
            
            currentWebView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val url = request.url.toString()
                    
                    if (foundResult.get() || result.isCompleted) {
                        return super.shouldInterceptRequest(view, request)
                    }
                    
                    val urlLower = url.lowercase()
                    val isVideoFile = urlLower.endsWith(".m3u8") || 
                                     urlLower.endsWith(".mp4") || 
                                     urlLower.endsWith(".mpd") ||
                                     urlLower.contains(".m3u8?") ||
                                     urlLower.contains(".mp4?") ||
                                     urlLower.contains(".mpd?")
                    
                    if (isVideoFile) {
                        if (foundResult.compareAndSet(false, true)) {
                            result.complete(url)
                            cleanup()
                        }
                    }
                    
                    return super.shouldInterceptRequest(view, request)
                }
            }
            
            timeoutHandler = Handler(context.mainLooper)
            timeoutRunnable = Runnable {
                if (!result.isCompleted) {
                    foundResult.set(true)
                    result.complete(null)
                    cleanup()
                }
            }
            timeoutHandler.postDelayed(timeoutRunnable, timeoutMs)
            
            currentWebView.loadUrl(videoDetailUrl)
            
        } catch (e: Exception) {
            if (!result.isCompleted) result.complete(null)
        }
    }
    
    result.await()
}

suspend fun fetchAllVideoParts(context: android.content.Context, videoId: String): List<VideoPart> = withContext(Dispatchers.IO) {
    if (videoId.isBlank()) {
        return@withContext emptyList()
    }
    
    val videoDetailUrl = SiteManager.buildZhUrl("v/$videoId")
    val result = CompletableDeferred<List<VideoPart>>()
    val partsList = mutableListOf<VideoPart>()
    
    withContext(Dispatchers.Main) {
        var webView: WebView? = null
        var timeoutHandler: Handler? = null
        var timeoutRunnable: Runnable? = null
        val foundResult = AtomicBoolean(false)
        var currentPartIndex = 0
        var totalParts = 0
        var isProcessing = false
        var isDestroyed = false
        
        fun cleanup() {
            if (isDestroyed) return
            isDestroyed = true
            timeoutHandler?.removeCallbacks(timeoutRunnable!!)
            webView?.let { wv ->
                Handler(Looper.getMainLooper()).post {
                    wv.stopLoading()
                    wv.destroy()
                }
            }
        }
        
        try {
            if (context is Activity && context.isFinishing) {
                result.complete(emptyList())
                return@withContext
            }
            
            webView = WebView(context)
            val currentWebView = webView ?: return@withContext
            
            configureWebView(currentWebView)
            
            currentWebView.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    
                    if (isProcessing) return
                    
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (isProcessing || isDestroyed) return@postDelayed
                        
                        val script = """
                            (function() {
                                const parts = [];
                                const scenes = document.querySelectorAll('#scenes a');
                                scenes.forEach((link, index) => {
                                    parts.push({
                                        index: index,
                                        name: link.textContent.trim() || (index + 1).toString()
                                    });
                                });
                                return JSON.stringify(parts);
                            })();
                        """.trimIndent()
                        
                        currentWebView.evaluateJavascript(script) { value ->
                            if (isDestroyed) return@evaluateJavascript
                            
                            if (value == null || value == "null") {
                                result.complete(emptyList())
                                cleanup()
                                return@evaluateJavascript
                            }
                            
                            var partsJson = value.trim()
                            if (partsJson.startsWith("\"") && partsJson.endsWith("\"")) {
                                partsJson = partsJson.substring(1, partsJson.length - 1)
                            }
                            
                            partsJson = partsJson.replace("\\\"", "\"")
                            
                            try {
                                val gson = Gson()
                                val jsonArray = com.google.gson.JsonParser.parseString(partsJson).asJsonArray
                                totalParts = jsonArray.size()
                                
                                if (totalParts > 0) {
                                    isProcessing = true
                                    
                                    jsonArray.forEach { element ->
                                        val jsonObj = element.asJsonObject
                                        val partName = jsonObj.get("name")?.asString ?: ""
                                        partsList.add(VideoPart(partName, null))
                                    }
                                    
                                    clickNextPartByIndex(currentWebView, 0, partsList, result, foundResult, ::cleanup)
                                } else {
                                    result.complete(emptyList())
                                    cleanup()
                                }
                            } catch (e: Exception) {
                                result.complete(emptyList())
                                cleanup()
                            }
                        }
                    }, AppConstants.SHORT_TIMEOUT_MS)
                }
                
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val url = request.url.toString()
                    
                    if (!isProcessing || foundResult.get() || result.isCompleted) {
                        return super.shouldInterceptRequest(view, request)
                    }
                    
                    val urlLower = url.lowercase()
                    val isVideoFile = urlLower.endsWith(".m3u8") || 
                                     urlLower.endsWith(".mp4") || 
                                     urlLower.endsWith(".mpd") ||
                                     urlLower.contains(".m3u8?") ||
                                     urlLower.contains(".mp4?") ||
                                     urlLower.contains(".mpd?")
                    
                    if (isVideoFile && currentPartIndex < totalParts) {
                        val partName = partsList[currentPartIndex].name
                        
                        val isMainVideo = urlLower.contains("/video.m3u8") || 
                                        urlLower.contains("/video.mp4") ||
                                        (!urlLower.contains("/qa/") && !urlLower.contains("/hq/") && !urlLower.contains("/sq/"))
                        
                        if (isMainVideo) {
                            partsList[currentPartIndex] = VideoPart(partName, url)
                            
                            Handler(Looper.getMainLooper()).postDelayed({
                                currentPartIndex++
                                if (currentPartIndex >= totalParts) {
                                    foundResult.set(true)
                                    result.complete(partsList.toList())
                                    cleanup()
                                } else {
                                    clickNextPartByIndex(currentWebView, currentPartIndex, partsList, result, foundResult, ::cleanup)
                                }
                            }, AppConstants.VERY_SHORT_TIMEOUT_MS)
                        }
                    }
                    
                    return super.shouldInterceptRequest(view, request)
                }
            }
            
            timeoutHandler = Handler(context.mainLooper)
            timeoutRunnable = Runnable {
                if (!result.isCompleted) {
                    foundResult.set(true)
                    result.complete(partsList.toList())
                    cleanup()
                }
            }
            
            timeoutHandler.postDelayed(timeoutRunnable, AppConstants.LONG_TIMEOUT_MS)
            
            currentWebView.loadUrl(videoDetailUrl)
            
        } catch (e: Exception) {
            if (!result.isCompleted) result.complete(emptyList())
        }
    }
    
    result.await()
}

private fun clickNextPart(
    webView: WebView,
    partsArray: Array<Map<String, Any>>,
    partsList: MutableList<VideoPart>,
    result: CompletableDeferred<List<VideoPart>>,
    foundResult: AtomicBoolean,
    cleanup: () -> Unit
) {
    if (foundResult.get() || result.isCompleted) {
        cleanup()
        return
    }
    
    val currentIndex = partsList.size
    
    if (currentIndex >= partsArray.size) {
        return
    }
    
    val partInfo = partsArray[currentIndex]
    val partName = partInfo["name"]?.toString() ?: (currentIndex + 1).toString()
    partsList.add(VideoPart(partName, null))
    
    val script = """
        (function() {
            const scenes = document.querySelectorAll('#scenes a');
            if (scenes[$currentIndex]) {
                scenes[$currentIndex].click();
                return true;
            }
            return false;
        })();
    """.trimIndent()
    
    webView.evaluateJavascript(script) { value ->
        if (value == "false") {
            foundResult.set(true)
            result.complete(partsList.toList())
            cleanup()
        }
    }
}

private fun clickNextPartByIndex(
    webView: WebView,
    index: Int,
    partsList: MutableList<VideoPart>,
    result: CompletableDeferred<List<VideoPart>>,
    foundResult: AtomicBoolean,
    cleanup: () -> Unit
) {
    if (foundResult.get() || result.isCompleted) {
        cleanup()
        return
    }
    
    if (index >= partsList.size) {
        return
    }
    
    val script = """
        (function() {
            const scenes = document.querySelectorAll('#scenes a');
            if (scenes[$index]) {
                scenes[$index].click();
                return true;
            }
            return false;
        })();
    """.trimIndent()
    
    webView.evaluateJavascript(script) { value ->
        if (value == "false") {
            foundResult.set(true)
            result.complete(partsList.toList())
            cleanup()
        }
    }
}

fun initializeNetworkService(context: Context) {
    NetworkConfig.initialize(context)
}

fun getOkHttpClient(): OkHttpClient = NetworkConfig.getOkHttpClient()

fun getPersistentCookieJar(): PersistentCookieJar? = NetworkConfig.getPersistentCookieJar()

val gson = Gson()

private fun configureWebView(webView: WebView) {
    val settings = webView.settings
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.mediaPlaybackRequiresUserGesture = false
    settings.userAgentString = USER_AGENT
    settings.cacheMode = WebSettings.LOAD_NO_CACHE
    webView.setLayerType(View.LAYER_TYPE_NONE, null)
}

private fun buildPaginatedUrl(baseUrl: String, page: Int): String {
    return if (page > 1) {
        if (baseUrl.contains("?")) "$baseUrl&page=$page" else "$baseUrl?page=$page"
    } else {
        baseUrl
    }
}

suspend fun login(username: String, password: String): LoginResponse = withContext(Dispatchers.IO) {
    val loginUrl = SiteManager.buildZhUrl("ajax/user/signin")
    val currentBaseUrl = SiteManager.getCurrentBaseUrl()
    
    val requestBody = FormBody.Builder()
        .add("username", username)
        .add("password", password)
        .add("remember_me", "1")
        .build()
    
    val request = Request.Builder()
        .url(loginUrl)
        .post(requestBody)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .headers(apiHeaders("$currentBaseUrl/"))
        .build()
    
    try {
        val response = getOkHttpClient().newCall(request).execute()
        if (response.isSuccessful) {
            val responseBody = response.body?.string() ?: ""
            gson.fromJson(responseBody, LoginResponse::class.java)
        } else {
            LoginResponse(
                status = response.code,
                result = null,
                messages = Messages(
                    all = listOf("登录失败: ${response.code}"),
                    keyed = listOf("登录失败: ${response.code}")
                )
            )
        }
    } catch (e: IOException) {
        LoginResponse(
            status = AppConstants.HTTP_STATUS_INTERNAL_ERROR,
            result = null,
            messages = Messages(
                all = listOf("网络错误: ${e.message}"),
                keyed = listOf("网络错误: ${e.message}")
            )
        )
    }
}

suspend fun resetPassword(
    username: String,
    email: String,
    newPassword: String,
    confirmPassword: String
): ResetPasswordResponse = withContext(Dispatchers.IO) {
    val resetUrl = SiteManager.buildZhUrl("ajax/user/reset")
    val currentBaseUrl = SiteManager.getCurrentBaseUrl()
    
    val requestJson = gson.toJson(mapOf(
        "username" to username,
        "email" to email,
        "password" to newPassword,
        "password_confirmation" to confirmPassword
    ))
    
    val requestBody = requestJson.toRequestBody("application/json".toMediaType())
    
    val request = Request.Builder()
        .url(resetUrl)
        .post(requestBody)
        .headers(apiHeaders("$currentBaseUrl/"))
        .build()
    
    try {
        val response = getOkHttpClient().newCall(request).execute()
        val responseBody = response.body?.string() ?: ""
        val apiResponse = gson.fromJson(responseBody, ResetPasswordResponse::class.java)
        apiResponse
    } catch (e: Exception) {
        ResetPasswordResponse(
            status = AppConstants.HTTP_STATUS_INTERNAL_ERROR,
            result = null,
            messages = Messages(
                all = listOf("网络错误: ${e.message}"),
                keyed = listOf("网络错误: ${e.message}")
            )
        )
    }
}

suspend fun fetchUserInfo(): UserInfoResponse = withContext(Dispatchers.IO) {
    val userInfoUrl = SiteManager.buildZhUrl("ajax/user/info")
    val currentBaseUrl = SiteManager.getCurrentBaseUrl()
    
    val request = Request.Builder()
        .url(userInfoUrl)
        .headers(apiHeaders("$currentBaseUrl/"))
        .build()
    
    try {
        val response = getOkHttpClient().newCall(request).execute()
        if (response.isSuccessful) {
            val responseBody = response.body?.string() ?: ""
            gson.fromJson(responseBody, UserInfoResponse::class.java)
        } else {
            UserInfoResponse(
                status = response.code,
                result = null
            )
        }
    } catch (e: IOException) {
        UserInfoResponse(
            status = AppConstants.HTTP_STATUS_INTERNAL_ERROR,
            result = null
        )
    }
}

data class EditProfileResponse(
    val status: Int,
    val result: Boolean?,
    val messages: Messages?
)

suspend fun editUserProfile(username: String, email: String): EditProfileResponse = withContext(Dispatchers.IO) {
    val editUrl = SiteManager.buildZhUrl("ajax/user/edit")
    val currentBaseUrl = SiteManager.getCurrentBaseUrl()

    val jsonBody = gson.toJson(mapOf("username" to username, "email" to email))
    val requestBody = okhttp3.RequestBody.create(
        "application/json; charset=utf-8".toMediaTypeOrNull(),
        jsonBody
    )

    val request = Request.Builder()
        .url(editUrl)
        .post(requestBody)
        .headers(apiHeaders("$currentBaseUrl/"))
        .build()
    
    try {
        val response = getOkHttpClient().newCall(request).execute()
        if (response.isSuccessful) {
            val responseBody = response.body?.string() ?: ""
            gson.fromJson(responseBody, EditProfileResponse::class.java)
        } else {
            EditProfileResponse(
                status = response.code,
                result = null,
                messages = Messages(
                    all = listOf("修改失败: ${response.code}"),
                    keyed = listOf("修改失败: ${response.code}")
                )
            )
        }
    } catch (e: IOException) {
        EditProfileResponse(
            status = AppConstants.HTTP_STATUS_INTERNAL_ERROR,
            result = null,
            messages = Messages(
                all = listOf("网络错误: ${e.message}"),
                keyed = listOf("网络错误: ${e.message}")
            )
        )
    }
}

suspend fun fetchVideosDataWithResponse(url: String, page: Int = 1): Pair<List<Video>, String> = withContext(Dispatchers.IO) {
    val fullUrl = buildPaginatedUrl(url, page)
    
    val currentBaseUrl = SiteManager.getCurrentBaseUrl()
    
    val request = Request.Builder()
        .url(fullUrl)
        .headers(commonHeaders())
        .header("Origin", "$currentBaseUrl/")
        .header("Referer", "$currentBaseUrl/")
        .cacheControl(okhttp3.CacheControl.Builder().maxStale(1, TimeUnit.HOURS).build())
        .build()

    try {
        val response = getOkHttpClient().newCall(request).execute()
        
        if (response.isSuccessful) {
            val html = response.body?.string() ?: ""
            val (videos, _) = parseVideosFromHtml(html)
            Pair(videos, html)
        } else {
            Pair(emptyList(), "")
        }
    } catch (e: IOException) {
        Pair(emptyList(), "")
    }
}

suspend fun fetchVideosData(url: String, page: Int = 1): List<Video> {
    val (videos, _) = fetchVideosDataWithResponse(url, page)
    return videos
}

suspend fun fetchVideoUrl(videoId: String): String? = withContext(Dispatchers.IO) {
    try {
        if (videoId.isBlank() || videoId.startsWith("fav_")) {
            return@withContext null
        }
        
        val videoDetailUrl = SiteManager.buildZhUrl("v/$videoId")
        
        val request = Request.Builder()
            .url(videoDetailUrl)
            .headers(commonHeaders())
            .build()
        
        val response = getOkHttpClient().newCall(request).execute()
        
        if (response.isSuccessful) {
            val html = response.body?.string() ?: ""
            val doc = Jsoup.parse(html)
            
            val videoElement = doc.selectFirst("video")
            if (videoElement != null) {
                val videoUrl = videoElement.attr("src")
                if (videoUrl.isNotBlank()) {
                    return@withContext videoUrl
                }
            }
            
            val iframeElement = doc.selectFirst("iframe[src*='video']")
            if (iframeElement != null) {
                return@withContext iframeElement.attr("src")
            }
            
            val scriptElements = doc.select("script")
            for (script in scriptElements) {
                val scriptContent = script.data()
                if (scriptContent.contains("videoUrl") || scriptContent.contains("source")) {
                    val urlPattern = """(https?:\/\/[^"]+|https?:\/\/[^']+)"""
                    val matchResult = Regex(urlPattern).find(scriptContent)
                    if (matchResult != null) {
                        return@withContext matchResult.groups[1]?.value
                    }
                }
            }
        }
    } catch (e: Exception) {
    }
    
    return@withContext null
}

suspend fun fetchM3u8UrlWithWebView(context: android.content.Context, videoId: String): String? = 
    fetchM3u8UrlWithWebViewFast(context, videoId, AppConstants.FAST_TIMEOUT_MS)

fun parseVideosFromHtml(html: String): Pair<List<Video>, PaginationInfo> {
    val videos = mutableListOf<Video>()
    val doc: Document = Jsoup.parse(html)

    val videoElements = doc.select("div.box-item")

    videoElements.forEachIndexed { index, element ->
        val thumbnailUrl = element.select("div.thumb img.lazyload").attr("data-src")
        
        val title = element.select("div.detail a").text() ?: "未知标题"
        
        val duration = element.select("div.duration").text() ?: ""
        
        val href = element.select("div.detail a").attr("href")
        val rawId = if (href.contains("/")) href.substringAfterLast("/") else href
        val id = rawId.ifEmpty { "video_${System.currentTimeMillis()}_$index" }
        
        val favouriteElement = element.select("div.favourite").firstOrNull()
        val favouriteCount = favouriteElement?.let { el ->
            val vScope = el.attr("v-scope") ?: ""
            val regex = Regex("Favourite\\('movie',\\s*(\\d+),")
            regex.find(vScope)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        } ?: 0
        
        videos.add(Video(id, title, duration, thumbnailUrl, favouriteCount = favouriteCount))
    }

    if (videos.isEmpty()) {
        return Pair(emptyList(), PaginationInfo(1, 1, false, false))
    }
    
    val paginationInfo = parsePaginationInfo(doc)
    
    return Pair(videos, paginationInfo)
}

 suspend fun searchVideos(query: String, page: Int = 1): Pair<List<Video>, PaginationInfo> = withContext(Dispatchers.IO) {
    if (query.isBlank()) return@withContext Pair(emptyList(), PaginationInfo(1, 1, false, false))
    
    val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
    val searchUrl = SiteManager.buildZhUrl("search?keyword=$encodedQuery")
    
    fetchVideosDataWithResponse(searchUrl, page).let { (videos, html) ->
        val paginationInfo = if (html.isNotEmpty()) {
            parsePaginationInfo(Jsoup.parse(html))
        } else {
            PaginationInfo(1, 1, false, false)
        }
        Pair(videos, paginationInfo)
    }
}

suspend fun fetchUserFavorites(page: Int = 1): Pair<List<Video>, PaginationInfo> = withContext(Dispatchers.IO) {
    val currentBaseUrl = SiteManager.getCurrentBaseUrl()
    val favoritesUrl = SiteManager.buildZhUrl("user/collection?page=$page")
    
    val request = Request.Builder()
        .url(favoritesUrl)
        .headers(commonHeaders())
        .header("Referer", "$currentBaseUrl/")
        .header("Origin", currentBaseUrl)
        .build()
    
    try {
        val response = getOkHttpClient().newCall(request).execute()
        
        if (response.isSuccessful) {
            val html = response.body?.string() ?: ""
            val (videos, pagination) = parseFavoritesFromHtml(html)
            return@withContext Pair(videos, pagination)
        } else {
            return@withContext Pair(emptyList(), PaginationInfo(1, 1, false, false))
        }
    } catch (e: Exception) {
        return@withContext Pair(emptyList(), PaginationInfo(1, 1, false, false))
    }
}

fun parseFavoritesFromHtml(html: String): Pair<List<Video>, PaginationInfo> {
    val videos = mutableListOf<Video>()
    
    try {
        val doc = org.jsoup.Jsoup.parse(html)
        
        val videoElements = doc.select("div.box-item")
        
        videoElements.forEachIndexed { index, element ->
            try {
                val titleElement = element.select("div.detail a").first()
                val title = titleElement?.text() ?: ""
                
                val imgElement = element.select("img").first()
                val thumbnailUrl = imgElement?.attr("data-src") ?: imgElement?.attr("src") ?: ""
                
                val durationElement = element.select("div.duration").first()
                val duration = durationElement?.text() ?: "00:00"
                
                val href = element.select("div.detail a").attr("href")
                
                val rawVideoId = if (href.contains("/")) href.substringAfterLast("/") else href
                val videoId = rawVideoId.ifEmpty { "fav_${System.currentTimeMillis()}_$index" }
                
                if (title.isNotBlank()) {
                    videos.add(Video(
                        id = videoId,
                        title = title.trim(),
                        duration = duration,
                        thumbnailUrl = thumbnailUrl.ifBlank { "https://picsum.photos/id/${(AppConstants.THUMBNAIL_RANDOM_MIN..AppConstants.THUMBNAIL_RANDOM_MAX).random()}/300/200" },
                        videoUrl = null
                    ))
                }
            } catch (_: Exception) {
            }
        }
        
        val paginationInfo = parsePaginationInfo(doc)
        
        return Pair(videos, paginationInfo)
        
    } catch (e: Exception) {
        return Pair(emptyList(), PaginationInfo(1, 1, false, false))
    }
}

fun parsePaginationInfo(doc: Document): PaginationInfo {
    val currentPageElement = doc.selectFirst("li.page-item.active span.page-link")
    val currentPage = currentPageElement?.text()?.toIntOrNull() ?: 1

    val pageLinks = doc.select("li.page-item a.page-link")
    var totalPages = currentPage

    pageLinks.forEach { link ->
        val pageNum = link.text()?.toIntOrNull()
        if (pageNum != null && pageNum > totalPages) {
            totalPages = pageNum
        }
    }

    val hasNextPage = doc.select("li.page-item a.page-link[rel*=next]").isNotEmpty()
    val hasPrevPage = doc.select("li.page-item a.page-link[rel*=prev]").isNotEmpty()

    val titleElement = doc.selectFirst("div.title h2")
    var categoryTitle = titleElement?.text() ?: ""
    
    if (categoryTitle.isEmpty()) {
        val h1Element = doc.selectFirst("h1")
        categoryTitle = h1Element?.text() ?: ""
    }

    val videoCountElement = doc.selectFirst("div.title div.text-muted")
    val videoCount = videoCountElement?.text() ?: ""

    val totalResultsText = videoCount
        .replace(",", "")
        .replace("视频", "")
        .replace(" ", "")
    val totalResults = totalResultsText.toIntOrNull() ?: 0

    val currentSort = doc.selectFirst("div.dropdown.show span.text-muted + span")?.text() ?: ""
    
    val actressDetail = try {
        val actressDetailElement = doc.selectFirst("div.detail.ml-4.text-left.title")
        val avatarElement = doc.selectFirst("div.avatar img")
        val avatarUrl = avatarElement?.attr("src")?.trim() ?: ""
        
        if (actressDetailElement != null) {
            val name = actressDetailElement.selectFirst("h3")?.text()?.trim() ?: ""
            val textMutedElements = actressDetailElement.select("div.text-muted")
            var birthday = ""
            var height = ""
            var measurements = ""
            var videoCountActress = 0
            
            textMutedElements.forEachIndexed { index, element ->
                val text = element.text().trim()
                if (text.contains("岁")) {
                    birthday = text
                } else if (text.contains("cm")) {
                    val parts = text.split("/")
                    if (parts.isNotEmpty()) {
                        height = parts[0].trim()
                    }
                    if (parts.size > 1) {
                        measurements = parts[1].trim()
                    }
                } else if (text.contains("视频")) {
                    val countText = text.replace("[^0-9]".toRegex(), "")
                    videoCountActress = countText.toIntOrNull() ?: 0
                }
            }
            
            if (name.isNotEmpty()) {
                com.android123av.app.models.ActressDetail(name, avatarUrl, birthday, height, measurements, videoCountActress)
            } else {
                null
            }
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
    
    val sortOptions = mutableListOf<com.android123av.app.models.SortOption>()
    
    val sortDropdowns = doc.select("div.dropdown-menu")
    for (sortDropdown in sortDropdowns) {
        val dropdownParent = sortDropdown.parent()
        val dropdownLabel = dropdownParent?.select("span.text-muted")?.firstOrNull()?.text() ?: ""
        
        if (dropdownLabel.contains("排序方式")) {
            val sortItems = sortDropdown.select("a.dropdown-item")
            sortItems.forEach { item ->
                val href = item.attr("href")
                if (href.contains("?sort=")) {
                    val title = item.selectFirst("span")?.text() ?: ""
                    val sortValue = href.substringAfter("?sort=").substringBefore("&")
                    if (title.isNotEmpty() && sortValue.isNotEmpty()) {
                        sortOptions.add(com.android123av.app.models.SortOption(title, sortValue, title == currentSort))
                    }
                }
            }
            break
        }
    }

    return PaginationInfo(
        currentPage = currentPage,
        totalPages = totalPages,
        hasNextPage = hasNextPage || currentPage < totalPages,
        hasPrevPage = hasPrevPage || currentPage > 1,
        totalResults = totalResults,
        categoryTitle = categoryTitle,
        videoCount = videoCount,
        currentSort = currentSort,
        sortOptions = sortOptions,
        actressDetail = actressDetail
    )
}

suspend fun fetchVideoDetails(videoId: String): VideoDetails? = withContext(Dispatchers.IO) {
    try {
        val currentBaseUrl = SiteManager.getCurrentBaseUrl()
        val videoDetailUrl = SiteManager.buildZhUrl("v/$videoId")
        
        val request = Request.Builder()
            .url(videoDetailUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Referer", "$currentBaseUrl/")
            .header("Origin", currentBaseUrl)
            .build()
        
        val response = getOkHttpClient().newCall(request).execute()
        
        if (response.isSuccessful) {
            val html = response.body?.string() ?: ""
            return@withContext parseVideoDetails(html)
        } else {
            return@withContext null
        }
    } catch (e: Exception) {
        return@withContext null
    }
}

suspend fun fetchFavouriteStatus(videoId: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val favouriteStatusUrl = SiteManager.buildZhUrl("ajax/user/favourite/status?type=movie&id=$videoId")
        
        val request = Request.Builder()
            .url(favouriteStatusUrl)
            .headers(apiHeaders(SiteManager.buildZhUrl("v/$videoId")))
            .build()
        
        val response = getOkHttpClient().newCall(request).execute()
        
        if (response.isSuccessful) {
            val responseBody = response.body?.string() ?: ""
            val gson = Gson()
            val jsonElement = gson.fromJson(responseBody, com.google.gson.JsonElement::class.java)
            val result = jsonElement.asJsonObject.get("result")?.asBoolean ?: false
            return@withContext result
        } else {
            return@withContext false
        }
    } catch (e: Exception) {
        return@withContext false
    }
}

suspend fun toggleFavourite(videoId: String, isAdd: Boolean): Boolean = withContext(Dispatchers.IO) {
    try {
        val favouriteUrl = SiteManager.buildZhUrl("ajax/user/favourite")
        val action = if (isAdd) "add" else "remove"
        
        val formBody = FormBody.Builder()
            .add("action", action)
            .add("type", "movie")
            .add("id", videoId)
            .build()
        
        val request = Request.Builder()
            .url(favouriteUrl)
            .headers(apiHeaders(SiteManager.buildZhUrl("v/$videoId")))
            .post(formBody)
            .build()
        
        val response = getOkHttpClient().newCall(request).execute()
        return@withContext response.isSuccessful
    } catch (e: Exception) {
        return@withContext false
    }
}

suspend fun fetchNavigationMenu(): Pair<List<com.android123av.app.models.MenuSection>, String?> {
    return withContext(Dispatchers.IO) {
        try {
            val url = SiteManager.buildZhUrl("dm9")
            val request = Request.Builder()
                .url(url)
                .headers(commonHeaders())
                .build()
            
            val response = getOkHttpClient().newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                val menuSections = parseNavigationMenu(html)
                Pair(menuSections, null)
            } else {
                val errorMessage = when (response.code) {
                    500, 502, 503, 504 -> "服务器暂时无法响应，请稍后重试"
                    403, 407 -> "网络访问受限，请检查网络设置"
                    404 -> "服务暂时不可用"
                    in 400..499 -> "请求失败，请稍后重试"
                    in 500..599 -> "服务器错误，请稍后重试"
                    else -> "网络连接失败，请检查网络状态"
                }
                Pair(emptyList(), errorMessage)
            }
        } catch (e: Exception) {
            val errorMessage = when {
                e.message?.contains("Connection reset", ignoreCase = true) == true -> "网络连接被重置，请检查网络状态"
                e.message?.contains("Connection refused", ignoreCase = true) == true -> "无法连接到服务器，请检查网络"
                e.message?.contains("timeout", ignoreCase = true) == true -> "连接超时，请检查网络后重试"
                e.message?.contains("No address associated", ignoreCase = true) == true -> "网络不可用，请检查网络连接"
                e.message?.contains("Network is unreachable", ignoreCase = true) == true -> "网络不可达，请检查网络设置"
                e.message?.contains("socket", ignoreCase = true) == true -> "网络连接异常，请检查网络状态"
                else -> "网络连接失败，请检查网络设置后重试"
            }
            Pair(emptyList(), errorMessage)
        }
    }
}

suspend fun fetchActresses(url: String, page: Int = 1): Pair<List<com.android123av.app.models.Actress>, PaginationInfo> {
    return withContext(Dispatchers.IO) {
        try {
            val fullUrl = buildPaginatedUrl(url, page)
            
            val request = Request.Builder()
                .url(fullUrl)
                .headers(commonHeaders())
                .build()
            
            val response = getOkHttpClient().newCall(request).execute()
            
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                return@withContext parseActressesFromHtml(html)
            } else {
                return@withContext Pair(emptyList(), PaginationInfo(1, 1, false, false))
            }
        } catch (e: Exception) {
            return@withContext Pair(emptyList(), PaginationInfo(1, 1, false, false))
        }
    }
}

suspend fun fetchSeries(url: String, page: Int = 1): Pair<List<com.android123av.app.models.Series>, PaginationInfo> {
    return withContext(Dispatchers.IO) {
        try {
            val fullUrl = buildPaginatedUrl(url, page)
            
            val request = Request.Builder()
                .url(fullUrl)
                .headers(commonHeaders())
                .build()
            
            val response = getOkHttpClient().newCall(request).execute()
            
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                return@withContext parseSeriesFromHtml(html, fullUrl)
            } else {
                return@withContext Pair(emptyList(), PaginationInfo(1, 1, false, false))
            }
        } catch (e: Exception) {
            return@withContext Pair(emptyList(), PaginationInfo(1, 1, false, false))
        }
    }
}

suspend fun fetchGenres(url: String, page: Int = 1): Pair<List<com.android123av.app.models.Genre>, PaginationInfo> {
    return withContext(Dispatchers.IO) {
        try {
            val fullUrl = buildPaginatedUrl(url, page)
            
            val request = Request.Builder()
                .url(fullUrl)
                .headers(commonHeaders())
                .build()
            
            val response = getOkHttpClient().newCall(request).execute()
            
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                return@withContext parseGenresFromHtml(html, fullUrl)
            } else {
                return@withContext Pair(emptyList(), PaginationInfo(1, 1, false, false))
            }
        } catch (e: Exception) {
            return@withContext Pair(emptyList(), PaginationInfo(1, 1, false, false))
        }
    }
}

suspend fun fetchStudios(url: String, page: Int = 1): Pair<List<com.android123av.app.models.Studio>, PaginationInfo> {
    return withContext(Dispatchers.IO) {
        try {
            val fullUrl = buildPaginatedUrl(url, page)
            
            val request = Request.Builder()
                .url(fullUrl)
                .headers(commonHeaders())
                .build()
            
            val response = getOkHttpClient().newCall(request).execute()
            
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                return@withContext parseStudiosFromHtml(html, fullUrl)
            } else {
                return@withContext Pair(emptyList(), PaginationInfo(1, 1, false, false))
            }
        } catch (e: Exception) {
            return@withContext Pair(emptyList(), PaginationInfo(1, 1, false, false))
        }
    }
}
