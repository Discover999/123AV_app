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
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.io.File
import java.util.concurrent.TimeUnit
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.LinkedHashMap
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

// 视频URL缓存 - 使用LRU策略，内存中缓存最近访问的50个视频URL
private val videoUrlCache = object : LinkedHashMap<String, CachedVideoUrl>(50, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedVideoUrl>?): Boolean {
        return size > 50
    }
}

// 缓存锁
private val cacheLock = Any()

// 缓存过期时间：30分钟
private const val CACHE_EXPIRATION_MS = 30 * 60 * 1000L

/**
 * 缓存的视频URL数据类
 */
data class CachedVideoUrl(
    val url: String?,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > CACHE_EXPIRATION_MS
}

/**
 * 获取缓存的视频URL（带过期检查）
 */
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

/**
 * 缓存视频URL
 */
fun cacheVideoUrl(videoId: String, url: String?) {
    synchronized(cacheLock) {
        videoUrlCache[videoId] = CachedVideoUrl(url)
    }
}

/**
 * 清除视频URL缓存
 */
fun clearVideoUrlCache() {
    synchronized(cacheLock) {
        videoUrlCache.clear()
    }
}

/**
 * 预热指定视频ID的缓存（异步）
 */
fun warmupCache(context: android.content.Context, videoId: String) {
    if (videoId.isBlank() || videoId.startsWith("fav_")) return
    
    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val url = fetchVideoUrlSync(videoId)
            cacheVideoUrl(videoId, url)
        } catch (e: Exception) {
            // 静默忽略预热错误
        }
    }
}

/**
 * 并行获取视频URL - 同时尝试HTTP和WebView方式，取最快返回的结果
 * @param context Android上下文
 * @param videoId 视频ID
 * @param timeoutMs 超时时间（毫秒）
 * @return 视频URL或null
 */
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

/**
 * 备用获取方法
 */
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

/**
 * 同步获取视频URL（HTTP方式）
 */
private fun fetchVideoUrlSync(videoId: String): String? {
    if (videoId.isBlank() || videoId.startsWith("fav_")) {
        return null
    }
    
    val videoDetailUrl = "https://123av.com/zh/v/$videoId"
    
    val request = Request.Builder()
        .url(videoDetailUrl)
        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 9 Build/AD1A.240411.003.A5; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/124.0.6367.54 Mobile Safari/537.36")
        .header("Accept", "*/*")
        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
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

/**
 * 快速WebView获取M3U8链接 - 优化版本，减少超时时间和开销
 */
suspend fun fetchM3u8UrlWithWebViewFast(context: android.content.Context, videoId: String, timeoutMs: Long = 5000): String? = withContext(Dispatchers.IO) {
    if (videoId.isBlank()) {
        return@withContext null
    }
    
    val videoDetailUrl = "https://123av.com/zh/v/$videoId"
    val result = CompletableDeferred<String?>()
    
    withContext(Dispatchers.Main) {
        var webView: WebView? = null
        var timeoutHandler: Handler? = null
        var timeoutRunnable: Runnable? = null
        
        val cleanup = {
            try {
                timeoutHandler?.removeCallbacks(timeoutRunnable!!)
                webView?.stopLoading()
                webView?.destroy()
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
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            
            currentWebView.setLayerType(View.LAYER_TYPE_NONE, null)
            
            var foundResult = false
            
            currentWebView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val url = request.url.toString()
                    
                    if (foundResult || result.isCompleted) {
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
                        foundResult = true
                        result.complete(url)
                        cleanup()
                    }
                    
                    return super.shouldInterceptRequest(view, request)
                }
            }
            
            timeoutHandler = Handler(context.mainLooper)
            timeoutRunnable = Runnable {
                if (!result.isCompleted) {
                    foundResult = true
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

// 全局变量用于存储PersistentCookieJar实例
private var persistentCookieJar: PersistentCookieJar? = null

// OkHttpClient实例 - 配置持久化cookie管理器
lateinit var okHttpClient: OkHttpClient
    private set

/**
 * 初始化网络服务，必须在应用启动时调用
 */
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

/**
 * 获取PersistentCookieJar实例
 */
fun getPersistentCookieJar(): PersistentCookieJar? = persistentCookieJar

// Gson实例
val gson = Gson()

// 登录请求函数
suspend fun login(username: String, password: String): LoginResponse = withContext(Dispatchers.IO) {
    val loginUrl = "https://123av.com/zh/ajax/user/signin"
    
    val requestBody = FormBody.Builder()
        .add("username", username)
        .add("password", password)
        .add("remember_me", "1")
        .build()
    
    val request = Request.Builder()
        .url(loginUrl)
        .post(requestBody)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
        .header("Accept", "application/json, text/plain, */*")
        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        .header("Referer", "https://123av.com/")
        .header("Origin", "https://123av.com")
        .header("X-Requested-With", "XMLHttpRequest")
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

// 获取用户信息函数
suspend fun fetchUserInfo(): UserInfoResponse = withContext(Dispatchers.IO) {
    val userInfoUrl = "https://123av.com/zh/ajax/user/info"
    
    val request = Request.Builder()
        .url(userInfoUrl)
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
        .header("Accept", "application/json, text/plain, */*")
        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        .header("Referer", "https://123av.com/")
        .header("Origin", "https://123av.com")
        .header("X-Requested-With", "XMLHttpRequest")
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

// 获取网络数据的函数（带响应内容）
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
    
    val request = Request.Builder()
        .url(fullUrl)
        .header("Origin", "https://123av.com/")
        .header("Referer", "https://123av.com/")
        .header("User-Agent", "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Mobile Safari/537.36")
        .header("Accept", "*/*")
        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
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

// 根据视频ID获取视频播放URL的函数
suspend fun fetchVideoUrl(videoId: String): String? = withContext(Dispatchers.IO) {
    try {
        if (videoId.isBlank() || videoId.startsWith("fav_")) {
            return@withContext null
        }
        
        val videoDetailUrl = "https://123av.com/zh/v/$videoId"
        
        val request = Request.Builder()
            .url(videoDetailUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 9 Build/AD1A.240411.003.A5; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/124.0.6367.54 Mobile Safari/537.36")
            .header("Accept", "*/*")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
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

// 使用WebView拦截网络请求获取M3U8链接的函数
suspend fun fetchM3u8UrlWithWebView(context: android.content.Context, videoId: String): String? = 
    fetchM3u8UrlWithWebViewFast(context, videoId, 5000)

// 解析HTML获取视频信息的函数
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

// 搜索视频的函数
 suspend fun searchVideos(query: String, page: Int = 1): List<Video> = withContext(Dispatchers.IO) {
    if (query.isBlank()) return@withContext emptyList()
    
    val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
    val searchUrl = "https://123av.com/zh/search?keyword=$encodedQuery"
    
    fetchVideosData(searchUrl, page)
}

// 获取用户收藏视频的函数
suspend fun fetchUserFavorites(page: Int = 1): Pair<List<Video>, PaginationInfo> = withContext(Dispatchers.IO) {
    val favoritesUrl = "https://123av.com/zh/user/collection?page=$page"
    
    val request = Request.Builder()
        .url(favoritesUrl)
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        .header("Referer", "https://123av.com/")
        .header("Origin", "https://123av.com")
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

// 解析收藏页面HTML的函数
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

// 解析分页信息的辅助函数
fun parsePaginationInfo(doc: Document): PaginationInfo {
    // 查找当前页码
    val currentPageElement = doc.selectFirst("li.page-item.active span.page-link")
    val currentPage = currentPageElement?.text()?.toIntOrNull() ?: 1

    // 查找所有页码链接
    val pageLinks = doc.select("li.page-item a.page-link")
    var totalPages = currentPage

    // 遍历所有页码链接，找到最大的页码
    pageLinks.forEach { link ->
        val pageNum = link.text()?.toIntOrNull()
        if (pageNum != null && pageNum > totalPages) {
            totalPages = pageNum
        }
    }

    // 检查是否有下一页和上一页
    val hasNextPage = doc.select("li.page-item a.page-link[rel*=next]").isNotEmpty()
    val hasPrevPage = doc.select("li.page-item a.page-link[rel*=prev]").isNotEmpty()

    return PaginationInfo(
        currentPage = currentPage,
        totalPages = totalPages,
        hasNextPage = hasNextPage || currentPage < totalPages,
        hasPrevPage = hasPrevPage || currentPage > 1
    )
}

// 获取视频详情信息的函数
suspend fun fetchVideoDetails(videoId: String): VideoDetails? = withContext(Dispatchers.IO) {
    try {
        // 构建视频详情页URL
        val videoDetailUrl = "https://123av.com/zh/v/$videoId"
        
        val request = Request.Builder()
            .url(videoDetailUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 9 Build/AD1A.240411.003.A5; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/124.0.6367.54 Mobile Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Referer", "https://123av.com/")
            .header("Origin", "https://123av.com")
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


