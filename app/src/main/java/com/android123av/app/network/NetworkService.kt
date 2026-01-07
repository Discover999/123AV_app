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
import com.android123av.app.models.*
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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
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

private val videoUrlCache = object : LinkedHashMap<String, CachedVideoUrl>(50, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedVideoUrl>?): Boolean {
        return size > 50
    }
}

private val cacheLock = Any()

private const val CACHE_EXPIRATION_MS = 30 * 60 * 1000L

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
    
    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val url = fetchVideoUrlSync(videoId)
            cacheVideoUrl(videoId, url)
        } catch (e: Exception) {
        }
    }
}

suspend fun fetchVideoUrlParallel(context: android.content.Context, videoId: String, timeoutMs: Long = 8000): String? = withContext(Dispatchers.IO) {
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
        val response = okHttpClient.newCall(request).execute()
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

suspend fun fetchM3u8UrlWithWebViewFast(context: android.content.Context, videoId: String, timeoutMs: Long = 5000): String? = withContext(Dispatchers.IO) {
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
            
            val settings = currentWebView.settings
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString = USER_AGENT
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            
            currentWebView.setLayerType(View.LAYER_TYPE_NONE, null)
            
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
            e.printStackTrace()
            if (!result.isCompleted) result.complete(null)
        }
    }
    
    result.await()
}

private var persistentCookieJar: PersistentCookieJar? = null

lateinit var okHttpClient: OkHttpClient
    private set

fun initializeNetworkService(context: Context) {
    if (persistentCookieJar == null) {
        persistentCookieJar = PersistentCookieJar(context)
    }
    
    val cacheDir = File(context.cacheDir, "http_cache")
    val cache = Cache(cacheDir, 50 * 1024 * 1024)
    
    okHttpClient = OkHttpClient.Builder()
        .cookieJar(persistentCookieJar!!)
        .cache(cache)
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}

fun getPersistentCookieJar(): PersistentCookieJar? = persistentCookieJar

val gson = Gson()

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
        val response = okHttpClient.newCall(request).execute()
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
        e.printStackTrace()
        LoginResponse(
            status = 500,
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
        val response = okHttpClient.newCall(request).execute()
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
        e.printStackTrace()
        UserInfoResponse(
            status = 500,
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
        val response = okHttpClient.newCall(request).execute()
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
        e.printStackTrace()
        EditProfileResponse(
            status = 500,
            result = null,
            messages = Messages(
                all = listOf("网络错误: ${e.message}"),
                keyed = listOf("网络错误: ${e.message}")
            )
        )
    }
}

suspend fun fetchVideosDataWithResponse(url: String, page: Int = 1): Pair<List<Video>, String> = withContext(Dispatchers.IO) {
    val fullUrl = if (page > 1) {
        if (url.contains("?")) {
            "$url&page=$page"
        } else {
            "$url?page=$page"
        }
    } else {
        url
    }
    
    val currentBaseUrl = SiteManager.getCurrentBaseUrl()
    
    val request = Request.Builder()
        .url(fullUrl)
        .headers(commonHeaders())
        .header("Origin", "$currentBaseUrl/")
        .header("Referer", "$currentBaseUrl/")
        .cacheControl(okhttp3.CacheControl.Builder().maxStale(1, TimeUnit.HOURS).build())
        .build()

    try {
        val response = okHttpClient.newCall(request).execute()
        
        if (response.isSuccessful) {
            val html = response.body?.string() ?: ""
            val (videos, _) = parseVideosFromHtml(html)
            Pair(videos, html)
        } else {
            Pair(emptyList(), "")
        }
    } catch (e: IOException) {
        e.printStackTrace()
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
        
        val response = okHttpClient.newCall(request).execute()
        
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
        e.printStackTrace()
    }
    
    return@withContext null
}

suspend fun fetchM3u8UrlWithWebView(context: android.content.Context, videoId: String): String? = 
    fetchM3u8UrlWithWebViewFast(context, videoId, 5000)

fun parseVideosFromHtml(html: String): Pair<List<Video>, PaginationInfo> {
    val videos = mutableListOf<Video>()
    val doc: Document = Jsoup.parse(html)

    val videoElements = doc.select("div.box-item")

    videoElements.forEach { element ->
        val thumbnailUrl = element.select("div.thumb img.lazyload").attr("data-src")
        
        val title = element.select("div.detail a").text() ?: "未知标题"
        
        val duration = element.select("div.duration").text() ?: ""
        
        val href = element.select("div.detail a").attr("href")
        val id = if (href.contains("/")) href.substringAfterLast("/") else href
        
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

 suspend fun searchVideos(query: String, page: Int = 1): List<Video> = withContext(Dispatchers.IO) {
     if (query.isBlank()) return@withContext emptyList()
     
     val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
     val searchUrl = SiteManager.buildZhUrl("search?keyword=$encodedQuery")
     
     fetchVideosData(searchUrl, page)
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
        val response = okHttpClient.newCall(request).execute()
        
        if (response.isSuccessful) {
            val html = response.body?.string() ?: ""
            val (videos, pagination) = parseFavoritesFromHtml(html)
            return@withContext Pair(videos, pagination)
        } else {
            return@withContext Pair(emptyList(), PaginationInfo(1, 1, false, false))
        }
    } catch (e: Exception) {
        e.printStackTrace()
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
                
                val videoId = if (href.contains("/")) href.substringAfterLast("/") else href
                
                val actualVideoUrl = null
                
                if (title.isNotBlank()) {
                    videos.add(Video(
                        id = videoId,
                        title = title.trim(),
                        duration = duration,
                        thumbnailUrl = thumbnailUrl.ifBlank { "https://picsum.photos/id/${(200..300).random()}/300/200" },
                        videoUrl = actualVideoUrl
                    ))
                }
                
            } catch (e: Exception) {
            }
        }
        
        val paginationInfo = parsePaginationInfo(doc)
        
        return Pair(videos, paginationInfo)
        
    } catch (e: Exception) {
        e.printStackTrace()
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

    return PaginationInfo(
        currentPage = currentPage,
        totalPages = totalPages,
        hasNextPage = hasNextPage || currentPage < totalPages,
        hasPrevPage = hasPrevPage || currentPage > 1
    )
}

suspend fun fetchVideoDetails(videoId: String): VideoDetails? = withContext(Dispatchers.IO) {
    try {
        val currentBaseUrl = SiteManager.getCurrentBaseUrl()
        val videoDetailUrl = SiteManager.buildZhUrl("v/$videoId")
        
        val request = Request.Builder()
            .url(videoDetailUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 9 Build/AD1A.240411.003.A5; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/124.0.6367.54 Mobile Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Referer", "$currentBaseUrl/")
            .header("Origin", currentBaseUrl)
            .build()
        
        val response = okHttpClient.newCall(request).execute()
        
        if (response.isSuccessful) {
            val html = response.body?.string() ?: ""
            return@withContext parseVideoDetails(html)
        } else {
            return@withContext null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext null
    }
}
