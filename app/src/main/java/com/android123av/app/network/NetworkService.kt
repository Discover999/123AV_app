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
private val videoUrlCache = object : LinkedHashMap<String, String?>(50, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>?): Boolean {
        return size > 50
    }
}

// 缓存锁
private val cacheLock = Any()

/**
 * 获取缓存的视频URL
 */
fun getCachedVideoUrl(videoId: String): String? {
    synchronized(cacheLock) {
        return videoUrlCache[videoId]
    }
}

/**
 * 缓存视频URL
 */
fun cacheVideoUrl(videoId: String, url: String?) {
    synchronized(cacheLock) {
        videoUrlCache[videoId] = url
    }
}

/**
 * 清除视频URL缓存
 */
fun clearVideoUrlCache() {
    synchronized(cacheLock) {
        videoUrlCache.clear()
        println("DEBUG: 视频URL缓存已清除")
    }
}

/**
 * 从缓存中移除指定的视频URL
 */
fun removeVideoUrlFromCache(videoId: String) {
    synchronized(cacheLock) {
        videoUrlCache.remove(videoId)
        println("DEBUG: 已从缓存中移除 videoId: $videoId")
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
    // 检查缓存
    val cachedUrl = getCachedVideoUrl(videoId)
    if (cachedUrl != null) {
        println("DEBUG: [Parallel] 使用缓存的URL: $cachedUrl")
        return@withContext cachedUrl
    }
    
    // 检查是否是收藏视频
    val isFavorite = videoId.startsWith("fav_")
    
    println("DEBUG: [Parallel] 开始并行获取视频URL, videoId: $videoId, timeout: ${timeoutMs}ms")
    println("DEBUG: [Parallel] 是否是收藏视频: $isFavorite")
    
    val result = try {
        withTimeout(timeoutMs) {
            coroutineScope {
                val httpDeferred = async {
                    if (isFavorite) {
                        null
                    } else {
                        fetchVideoUrlSync(videoId)
                    }
                }
                
                val webViewDeferred = async {
                    fetchM3u8UrlWithWebViewFast(context, videoId)
                }
                
                // 等待最快完成的结果
                val result = try {
                    select<Pair<String?, String?>> {
                        httpDeferred.onAwait { httpResult -> 
                            println("DEBUG: [Parallel] HTTP方式先返回: $httpResult")
                            Pair(httpResult, "HTTP") 
                        }
                        webViewDeferred.onAwait { webViewResult -> 
                            println("DEBUG: [Parallel] WebView方式先返回: $webViewResult")
                            Pair(webViewResult, "WebView") 
                        }
                    }
                } catch (e: Exception) {
                    println("DEBUG: [Parallel] 并行获取异常: ${e.message}")
                    // 尝试获取另一个结果
                    val httpResult = try { httpDeferred.await() } catch (e: Exception) { null }
                    val webViewResult = try { webViewDeferred.await() } catch (e: Exception) { null }
                    Pair(httpResult ?: webViewResult, "Fallback")
                }
                
                result.first
            }
        }
    } catch (e: TimeoutCancellationException) {
        println("DEBUG: [Parallel] 获取超时，尝试获取任何可用的结果")
        // 超时后尝试获取任何可用的结果
        val httpResult = try { fetchVideoUrlSync(videoId) } catch (e: Exception) { null }
        if (httpResult != null) {
            httpResult
        } else {
            try { 
                fetchM3u8UrlWithWebViewFast(context, videoId, 3000) 
            } catch (e: Exception) { 
                null 
            }
        }
    } catch (e: Exception) {
        println("DEBUG: [Parallel] 获取异常: ${e.message}")
        // 降级到传统方法
        try {
            if (isFavorite) {
                fetchM3u8UrlWithWebView(context, videoId)
            } else {
                fetchVideoUrlSync(videoId) ?: fetchM3u8UrlWithWebView(context, videoId)
            }
        } catch (e2: Exception) {
            null
        }
    }
    
    // 缓存结果
    result?.let { url ->
        cacheVideoUrl(videoId, url)
        println("DEBUG: [Parallel] 缓存视频URL: $url")
    }
    
    result
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
            
            // 查找包含视频播放URL的元素
            val videoElement = doc.selectFirst("video")
            if (videoElement != null) {
                val videoUrl = videoElement.attr("src")
                if (videoUrl.isNotBlank()) {
                    println("DEBUG: [HTTP] Found video URL in video element: $videoUrl")
                    return videoUrl
                }
            }
            
            // 尝试从script标签中提取视频URL
            val scriptElements = doc.select("script")
            for (script in scriptElements) {
                val scriptContent = script.data()
                if (scriptContent.contains(".m3u8") || scriptContent.contains("source")) {
                    val urlPattern = """(https?:\/\/[^"]+\.m3u8[^"]*)"""
                    val matchResult = Regex(urlPattern).find(scriptContent)
                    if (matchResult != null) {
                        val foundUrl = matchResult.groups[1]?.value
                        println("DEBUG: [HTTP] Found M3U8 URL in script: $foundUrl")
                        return foundUrl
                    }
                }
            }
            
            null
        } else {
            null
        }
    } catch (e: Exception) {
        println("DEBUG: [HTTP] Exception: ${e.message}")
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
    
    println("DEBUG: [WebViewFast] 开始获取, timeout: ${timeoutMs}ms")
    
    // 在主线程创建WebView
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
                // 忽略清理异常
            }
        }
        
        try {
            // 检查上下文有效性
            if (context is Activity && context.isFinishing) {
                result.complete(null)
                return@withContext
            }
            
            webView = WebView(context)
            val currentWebView = webView ?: return@withContext
            
            // 快速配置
            val settings = currentWebView.settings
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            
            // 最小化WebView设置
            currentWebView.setLayerType(View.LAYER_TYPE_NONE, null)
            
            var foundResult = false
            
            currentWebView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val url = request.url.toString().lowercase()
                    
                    if (foundResult || result.isCompleted) {
                        return super.shouldInterceptRequest(view, request)
                    }
                    
                    // 快速匹配视频URL
                    if (url.endsWith(".m3u8") || url.contains(".m3u8?") || url.endsWith(".mp4")) {
                        foundResult = true
                        println("DEBUG: [WebViewFast] 拦截到视频链接: $url")
                        result.complete(url)
                        cleanup()
                    }
                    
                    return super.shouldInterceptRequest(view, request)
                }
            }
            
            // 设置超时
            timeoutHandler = Handler(context.mainLooper)
            timeoutRunnable = Runnable {
                if (!result.isCompleted) {
                    foundResult = true
                    result.complete(null)
                    cleanup()
                }
            }
            timeoutHandler.postDelayed(timeoutRunnable, timeoutMs)
            
            // 加载页面
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
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES)) // 增加连接池大小
        .connectTimeout(10, TimeUnit.SECONDS) // 减少连接超时
        .readTimeout(20, TimeUnit.SECONDS) // 减少读取超时
        .writeTimeout(20, TimeUnit.SECONDS) // 减少写入超时
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    
    println("DEBUG: NetworkService initialized with PersistentCookieJar and optimizations")
}

/**
 * 获取PersistentCookieJar实例
 */
fun getPersistentCookieJar(): PersistentCookieJar? = persistentCookieJar

// Gson实例
val gson = Gson()

// 模拟数据
val sampleVideos = listOf(
    Video(id = "1", title = "test1", duration = "50:30", thumbnailUrl = "https://picsum.photos/id/237/300/200", videoUrl = "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4", favouriteCount = 12345),
    Video(id = "2", title = "test2", duration = "1:08:45", thumbnailUrl = "https://picsum.photos/id/238/300/200", videoUrl = "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4", favouriteCount = 5678),
    Video(id = "3", title = "test3", duration = "5:20", thumbnailUrl = "https://picsum.photos/id/239/300/200", videoUrl = "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4", favouriteCount = 999),
    Video(id = "4", title = "test4", duration = "1:15:10", thumbnailUrl = "https://picsum.photos/id/240/300/200", videoUrl = "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4", favouriteCount = 123),
)

// 登录请求函数
suspend fun login(username: String, password: String): LoginResponse = withContext(Dispatchers.IO) {
    val loginUrl = "https://123av.com/zh/ajax/user/signin"
    
    // 创建请求体 - 密码无加密
    val requestBody = FormBody.Builder()
        .add("username", username)
        .add("password", password) // 密码无加密
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
            // 解析登录响应
            gson.fromJson(responseBody, LoginResponse::class.java)
        } else {
            // 请求失败，创建一个错误响应对象
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
        // 异常情况下返回失败响应
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
            println("DEBUG: Raw user info response: $responseBody")
            // 解析用户信息响应
            val userInfoResponse = gson.fromJson(responseBody, UserInfoResponse::class.java)
            println("DEBUG: Parsed user info response: $userInfoResponse")
            userInfoResponse
        } else {
            // 请求失败，创建一个错误响应对象
            println("DEBUG: User info request failed with code: ${response.code}")
            UserInfoResponse(
                status = response.code,
                result = null
            )
        }
    } catch (e: IOException) {
        println("DEBUG: User info request exception: ${e.message}")
        e.printStackTrace()
        // 异常情况下返回失败响应
        UserInfoResponse(
            status = 500,
            result = null
        )
    }
}

// 获取网络数据的函数（带响应内容）
suspend fun fetchVideosDataWithResponse(url: String, page: Int = 1): Pair<List<Video>, String> = withContext(Dispatchers.IO) {
    // 构建带分页参数的URL
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
            // 解析HTML并提取视频信息和分页数据
            val (videos, paginationInfo) = parseVideosFromHtml(html)
            // 这里我们返回视频列表和HTML内容，后续需要修改HomeScreen来处理分页信息
            Pair(videos, html)
        } else {
            // 如果请求失败，返回空列表
            Pair(emptyList(), "")
        }
    } catch (e: IOException) {
        e.printStackTrace()
        // 异常情况下返回空列表
        Pair(emptyList(), "")
    }
}

// 保持原函数的兼容性
suspend fun fetchVideosData(url: String, page: Int = 1): List<Video> {
    val (videos, _) = fetchVideosDataWithResponse(url, page)
    return videos
}

// 根据视频ID获取视频播放URL的函数
suspend fun fetchVideoUrl(videoId: String): String? = withContext(Dispatchers.IO) {
    try {
        // 检查视频ID是否有效
        if (videoId.isBlank()) {
            println("DEBUG: Empty video ID, skipping fetchVideoUrl")
            return@withContext null
        }
        
        // 对于收藏视频，直接返回null，让WebView方式处理
        if (videoId.startsWith("fav_")) {
            println("DEBUG: Favorite video ID detected: $videoId, will use WebView method")
            return@withContext null
        }
        
        // 构建视频详情页URL
        val videoDetailUrl = "https://123av.com/zh/v/$videoId"
        
        println("DEBUG: 开始使用fetchVideoUrl获取视频URL")
        println("DEBUG: 请求时间: ${System.currentTimeMillis()}")
        println("DEBUG: 视频ID: $videoId")
        println("DEBUG: 详情页URL: $videoDetailUrl")
        
        // 发送请求获取视频详情页HTML
        val request = Request.Builder()
            .url(videoDetailUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 9 Build/AD1A.240411.003.A5; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/124.0.6367.54 Mobile Safari/537.36")
            .header("Accept", "*/*")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .build()
        
        val response = okHttpClient.newCall(request).execute()
        println("DEBUG: fetchVideoUrl response code: ${response.code}")
        
        if (response.isSuccessful) {
            val html = response.body?.string() ?: ""
            val doc = Jsoup.parse(html)
            
            // 查找包含视频播放URL的元素
            // 这里需要根据实际网页结构调整选择器
            // 示例：假设视频URL在某个script标签或iframe中
            val videoElement = doc.selectFirst("video")
            if (videoElement != null) {
                val videoUrl = videoElement.attr("src")
                println("DEBUG: Found video URL in video element: $videoUrl")
                return@withContext videoUrl
            }
            
            // 尝试查找iframe中的视频URL
            val iframeElement = doc.selectFirst("iframe[src*='video']")
            if (iframeElement != null) {
                val iframeUrl = iframeElement.attr("src")
                println("DEBUG: Found video URL in iframe: $iframeUrl")
                return@withContext iframeUrl
            }
            
            // 尝试从script标签中提取视频URL
            val scriptElements = doc.select("script")
            for (script in scriptElements) {
                val scriptContent = script.data()
                if (scriptContent.contains("videoUrl") || scriptContent.contains("source")) {
                    // 使用正则表达式提取URL
                    val urlPattern = """(https?:\/\/[^"]+|https?:\/\/[^']+)"""
                    val matchResult = Regex(urlPattern).find(scriptContent)
                    if (matchResult != null) {
                        val foundUrl = matchResult.groups[1]?.value
                        println("DEBUG: Found video URL in script: $foundUrl")
                        return@withContext foundUrl
                    }
                }
            }
            
            println("DEBUG: No video URL found in HTML content")
        } else {
            println("DEBUG: Failed to fetch video details, response code: ${response.code}")
        }
    } catch (e: Exception) {
        println("DEBUG: fetchVideoUrl exception")
        println("DEBUG: 异常时间: ${System.currentTimeMillis()}")
        println("DEBUG: 异常类型: ${e.javaClass.name}")
        println("DEBUG: 异常消息: ${e.message}")
        e.printStackTrace()
    }
    
    // 如果无法获取视频URL，返回null
    println("DEBUG: fetchVideoUrl returning null for videoId: $videoId")
    return@withContext null
}

// 使用WebView拦截网络请求获取M3U8链接的函数
suspend fun fetchM3u8UrlWithWebView(context: android.content.Context, videoId: String): String? = withContext(Dispatchers.IO) {
    // 检查视频ID是否有效
    if (videoId.isBlank()) {
        println("DEBUG: Empty video ID, skipping WebView fetch")
        return@withContext null
    }
    
    val videoDetailUrl = "https://123av.com/zh/v/$videoId"
    val result = CompletableDeferred<String?>()
    
    println("DEBUG: WebView方式获取视频URL")
    println("DEBUG: 请求URL: $videoDetailUrl")
    println("DEBUG: 请求时间: ${System.currentTimeMillis()}")
    println("DEBUG: 视频ID: $videoId")
    
    // 在主线程创建和配置WebView
    withContext(Dispatchers.Main) {
        var webView: WebView? = null
        var timeoutHandler: Handler? = null
        var timeoutRunnable: Runnable? = null
        
        // 清理WebView资源的内部函数 - 确保始终在主线程执行
        fun cleanupWebView() {
            // 始终在主线程执行清理操作，避免线程问题
            Handler(Looper.getMainLooper()).post {
                try {
                    // 移除超时处理
                    val handler = timeoutHandler
                    val runnable = timeoutRunnable
                    if (handler != null && runnable != null) {
                        handler.removeCallbacks(runnable)
                    }
                    
                    // 清理WebView
                    val view = webView
                    view?.let {
                        it.stopLoading()
                        it.removeAllViews()
                        it.clearHistory()
                        it.clearCache(true)
                        it.settings.javaScriptEnabled = false
                        try {
                            it.destroy()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    webView = null
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        try {
            // 检查上下文是否有效
            if (context is Activity && context.isFinishing) {
                if (!result.isCompleted) result.complete(null)
                return@withContext
            }
            
            // 创建WebView
            webView = WebView(context)
            
            // 保存当前webView实例到局部变量
            val currentWebView = webView ?: return@withContext
            
            // 配置WebView设置
            val settings = currentWebView.settings
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 9 Build/AD1A.240411.003.A5; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/124.0.6367.54 Mobile Safari/537.36"
            settings.allowContentAccess = true
            settings.allowFileAccess = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            
            // 启用硬件加速相关设置
            currentWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            
            // 设置WebViewClient来拦截网络请求
            currentWebView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    val url = request.url.toString()
                    
                    // 如果已经获取到结果，不再处理后续请求
                    if (result.isCompleted) {
                        return super.shouldInterceptRequest(view, request)
                    }
                    
                    // 快速检查URL扩展名（避免不必要的字符串操作）
                    val urlLower = url.lowercase()
                    val isVideoFile = urlLower.endsWith(".m3u8") || 
                                     urlLower.endsWith(".mp4") || 
                                     urlLower.endsWith(".mpd") ||
                                     urlLower.contains(".m3u8?") ||
                                     urlLower.contains(".mp4?") ||
                                     urlLower.contains(".mpd?")
                    
                    if (isVideoFile) {
                        println("DEBUG: WebView成功拦截到视频链接: $url")
                        result.complete(url)
                        // 清理WebView资源
                        cleanupWebView()
                    }
                    
                    return super.shouldInterceptRequest(view, request)
                }
            }
            
            // 加载视频详情页
            currentWebView.loadUrl(videoDetailUrl)
            
            // 针对收藏视频添加加载页面日志
            if (videoId == "fav_custom") {
                println("DEBUG: WebView开始加载视频详情页")
                println("DEBUG: 页面URL: $videoDetailUrl")
            }
            
            // 设置超时处理
            timeoutHandler = Handler(context.mainLooper)
            timeoutRunnable = Runnable {
                if (!result.isCompleted) {
                    result.complete(null)
                    cleanupWebView()
                }
            }
            timeoutHandler.postDelayed(timeoutRunnable, 5000) // 减少到5秒超时
            
        } catch (e: Exception) {
            e.printStackTrace()
            if (!result.isCompleted) result.complete(null)
            cleanupWebView()
        }
    }
    
    // 等待结果
    val url = result.await()
    println("DEBUG: Fetched M3U8 URL: $url")
    return@withContext url
}

// 解析HTML获取视频信息的函数
fun parseVideosFromHtml(html: String): Pair<List<Video>, PaginationInfo> {
    val videos = mutableListOf<Video>()
    val doc: Document = Jsoup.parse(html)

    // 根据新的网页结构解析视频数据
    val videoElements = doc.select("div.box-item")

    videoElements.forEach { element ->
        // 提取视频封面图URL
        val thumbnailUrl = element.select("div.thumb img.lazyload").attr("data-src")
        
        // 提取视频标题
        val title = element.select("div.detail a").text() ?: "未知标题"
        
        // 提取视频时长
        val duration = element.select("div.duration").text() ?: ""
        
        // 提取视频ID（从href属性中获取，如 "v/huntc-500" -> "huntc-500"）
        val href = element.select("div.detail a").attr("href")
        val id = if (href.contains("/")) href.substringAfterLast("/") else href
        
        // 提取收藏量（从 v-scope="Favourite('movie', 357792, 0)" 中提取第二个参数）
        val favouriteElement = element.select("div.favourite").firstOrNull()
        val favouriteCount = favouriteElement?.let { el ->
            val vScope = el.attr("v-scope") ?: ""
            val regex = Regex("Favourite\\('movie',\\s*(\\d+),")
            regex.find(vScope)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        } ?: 0
        
        // 创建Video对象并添加到列表
        videos.add(Video(id, title, duration, thumbnailUrl, favouriteCount = favouriteCount))
    }

    // 如果没有解析到视频，返回空列表
    if (videos.isEmpty()) {
        return Pair(emptyList(), PaginationInfo(1, 1, false, false))
    }

    // 解析分页信息
    val paginationInfo = parsePaginationInfo(doc)
    
    println("DEBUG: parseVideosFromHtml - 解析到 ${videos.size} 个视频")

    return Pair(videos, paginationInfo)
}

// 搜索视频的函数
 suspend fun searchVideos(query: String, page: Int = 1): List<Video> = withContext(Dispatchers.IO) {
    if (query.isBlank()) return@withContext emptyList()
    
    // 构建搜索URL
    val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
    val searchUrl = "https://123av.com/zh/search?keyword=$encodedQuery"
    
    // 使用现有的fetchVideosData函数获取搜索结果
    fetchVideosData(searchUrl, page)
}

// 获取用户收藏视频的函数
suspend fun fetchUserFavorites(page: Int = 1): Pair<List<Video>, PaginationInfo> = withContext(Dispatchers.IO) {
    val favoritesUrl = "https://123av.com/zh/user/collection?page=$page"
    
    println("DEBUG: Fetching user favorites from $favoritesUrl, page: $page")
    
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
        println("DEBUG: Favorites response code: ${response.code}")
        
        if (response.isSuccessful) {
            val html = response.body?.string() ?: ""
            println("DEBUG: Favorites HTML length: ${html.length}")
            
            // 解析收藏页面HTML获取视频信息和分页
            val (videos, pagination) = parseFavoritesFromHtml(html)
            println("DEBUG: Parsed ${videos.size} favorite videos, pagination: $pagination")
            return@withContext Pair(videos, pagination)
        } else {
            println("DEBUG: Favorites request failed with code: ${response.code}")
            return@withContext Pair(emptyList(), PaginationInfo(1, 1, false, false))
        }
    } catch (e: Exception) {
        println("DEBUG: Favorites request exception: ${e.message}")
        e.printStackTrace()
        return@withContext Pair(emptyList(), PaginationInfo(1, 1, false, false))
    }
}

// 解析收藏页面HTML的函数
fun parseFavoritesFromHtml(html: String): Pair<List<Video>, PaginationInfo> {
    val videos = mutableListOf<Video>()
    
    try {
        val doc = org.jsoup.Jsoup.parse(html)
        
        println("DEBUG: Starting to parse favorites HTML, document title: ${doc.title()}")
        
        // 根据你提供的HTML结构，收藏页面的视频项在 div.box-item 中
        val videoElements = doc.select("div.box-item")
        println("DEBUG: Found ${videoElements.size} video elements with selector 'div.box-item'")
        
        videoElements.forEachIndexed { index, element ->
            try {
                println("DEBUG: Processing element $index")
                
                // 提取标题 - 从 div.detail a 标签中获取
                val titleElement = element.select("div.detail a").first()
                val title = titleElement?.text() ?: ""
                println("DEBUG: Found title: $title")
                
                // 提取封面图片URL - 从 img 标签的 data-src 属性中获取
                val imgElement = element.select("img").first()
                val thumbnailUrl = imgElement?.attr("data-src") ?: imgElement?.attr("src") ?: ""
                println("DEBUG: Found thumbnail URL: $thumbnailUrl")
                
                // 提取时长 - 从 div.duration 中获取
                val durationElement = element.select("div.duration").first()
                val duration = durationElement?.text() ?: "00:00"
                println("DEBUG: Found duration: $duration")
                
                // 提取视频链接和ID - 使用与首页相同的逻辑
                val href = element.select("div.detail a").attr("href")
                println("DEBUG: Found href: $href")
                
                // 提取视频ID（从href属性中获取，如 "v/huntc-500" -> "huntc-500"）
                // 使用与parseVideosFromHtml相同的逻辑
                val videoId = if (href.contains("/")) href.substringAfterLast("/") else href
                println("DEBUG: Extracted video ID: $videoId")
                
                // 重要：收藏视频不应该直接设置videoUrl，因为href是网页链接而不是视频文件
                // 让VideoPlayerScreen通过WebView方法提取实际的视频URL
                val actualVideoUrl = null
                
                if (title.isNotBlank()) {
                    videos.add(Video(
                        id = videoId,
                        title = title.trim(),
                        duration = duration,
                        thumbnailUrl = thumbnailUrl.ifBlank { "https://picsum.photos/id/${(200..300).random()}/300/200" },
                        videoUrl = actualVideoUrl
                    ))
                    println("DEBUG: Successfully added favorite video $index: title='$title', id='$videoId', thumbnail='$thumbnailUrl', videoUrl=null (will use WebView extraction)")
                } else {
                    println("DEBUG: Skipping element $index - no title found")
                }
                
            } catch (e: Exception) {
                println("DEBUG: Error processing element $index: ${e.message}")
            }
        }
        
        println("DEBUG: Final parsing result - found ${videos.size} videos from favorites")
        
        // 解析分页信息
        val paginationInfo = parsePaginationInfo(doc)
        
        println("DEBUG: Successfully parsed ${videos.size} favorite videos, pagination: $paginationInfo")
        return Pair(videos, paginationInfo)
        
    } catch (e: Exception) {
        println("DEBUG: Error parsing favorites HTML: ${e.message}")
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
            // 使用HtmlParser解析视频详情
            return@withContext parseVideoDetails(html)
        } else {
            println("DEBUG: Failed to fetch video details, response code: ${response.code}")
            return@withContext null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        println("DEBUG: Exception while fetching video details: ${e.message}")
        return@withContext null
    }
}


