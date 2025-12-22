package com.android123av.app.network

import android.app.Activity
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

// OkHttpClient实例
val okHttpClient = OkHttpClient()

// Gson实例
val gson = Gson()

// 模拟数据
val sampleVideos = listOf(
    Video(id = "1", title = "test1", duration = "50:30", thumbnailUrl = "https://picsum.photos/id/237/300/200", videoUrl = "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4"),
    Video(id = "2", title = "test2", duration = "1:08:45", thumbnailUrl = "https://picsum.photos/id/238/300/200", videoUrl = "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4"),
    Video(id = "3", title = "test3", duration = "5:20", thumbnailUrl = "https://picsum.photos/id/239/300/200", videoUrl = "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4"),
    Video(id = "4", title = "test4", duration = "1:15:10", thumbnailUrl = "https://picsum.photos/id/240/300/200", videoUrl = "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4"),
    Video(id = "5", title = "test5", duration = "7:30", thumbnailUrl = "https://picsum.photos/id/241/300/200", videoUrl = "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4"),
    Video(id = "6", title = "test6", duration = "12:45", thumbnailUrl = "https://picsum.photos/id/242/300/200", videoUrl = "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4")
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
        .header("Origin", "https://surrit.store")
        .header("Referer", "https://surrit.store/")
        .header("User-Agent", "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Mobile Safari/537.36")
        .header("Accept", "*/*")
        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
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
        // 构建视频详情页URL
        val videoDetailUrl = "https://123av.com/zh/v/$videoId"
        
        // 针对收藏视频添加获取视频URL的详细日志
        if (videoId == "fav_custom") {
            println("DEBUG: 开始使用fetchVideoUrl获取收藏视频URL")
            println("DEBUG: 请求时间: ${System.currentTimeMillis()}")
            println("DEBUG: 视频ID: $videoId")
            println("DEBUG: 详情页URL: $videoDetailUrl")
        }
        
        // 发送请求获取视频详情页HTML
        val request = Request.Builder()
            .url(videoDetailUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 9 Build/AD1A.240411.003.A5; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/124.0.6367.54 Mobile Safari/537.36")
            .header("Accept", "*/*")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .build()
        
        // 针对收藏视频添加请求头详细日志
        if (videoId == "fav_custom") {
            println("DEBUG: 收藏视频请求头信息")
            println("DEBUG: User-Agent: ${request.header("User-Agent")}")
            println("DEBUG: Accept: ${request.header("Accept")}")
            println("DEBUG: Accept-Language: ${request.header("Accept-Language")}")
        }
        
        val response = okHttpClient.newCall(request).execute()
        if (response.isSuccessful) {
            val html = response.body?.string() ?: ""
            val doc = Jsoup.parse(html)
            
            // 查找包含视频播放URL的元素
            // 这里需要根据实际网页结构调整选择器
            // 示例：假设视频URL在某个script标签或iframe中
            val videoElement = doc.selectFirst("video")
            if (videoElement != null) {
                return@withContext videoElement.attr("src")
            }
            
            // 尝试查找iframe中的视频URL
            val iframeElement = doc.selectFirst("iframe[src*='video']")
            if (iframeElement != null) {
                return@withContext iframeElement.attr("src")
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
                        return@withContext matchResult.groups[1]?.value
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        
        // 针对收藏视频添加异常日志
        if (videoId == "fav_custom") {
            println("DEBUG: 收藏视频URL获取异常")
            println("DEBUG: 异常时间: ${System.currentTimeMillis()}")
            println("DEBUG: 异常类型: ${e.javaClass.name}")
            println("DEBUG: 异常消息: ${e.message}")
            println("DEBUG: 异常堆栈:")
            e.printStackTrace()
        }
    }
    
    // 如果无法获取视频URL，返回null
    return@withContext null
}

// 使用WebView拦截网络请求获取M3U8链接的函数
suspend fun fetchM3u8UrlWithWebView(context: android.content.Context, videoId: String): String? = withContext(Dispatchers.IO) {
    val videoDetailUrl = "https://123av.com/zh/v/$videoId"
    val result = CompletableDeferred<String?>()
    
    // 针对收藏视频添加详细日志
    if (videoId == "fav_custom") {
        println("DEBUG: WebView方式获取收藏视频URL")
        println("DEBUG: 请求URL: $videoDetailUrl")
        println("DEBUG: 请求时间: ${System.currentTimeMillis()}")
    }
    
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
                    
                    // 针对收藏视频添加请求拦截日志
                    if (videoId == "fav_custom") {
                        println("DEBUG: WebView拦截到请求")
                        println("DEBUG: 请求URL: $url")
                        println("DEBUG: 请求方法: ${request.method}")
                        println("DEBUG: 请求头: ${request.requestHeaders}")
                    }
                    
                    // 检查是否是M3U8请求
                    if (url.contains(".m3u8") && !result.isCompleted) {
                        // 针对收藏视频添加M3U8拦截成功日志
                        if (videoId == "fav_custom") {
                            println("DEBUG: WebView成功拦截到收藏视频M3U8链接")
                            println("DEBUG: M3U8 URL: $url")
                            println("DEBUG: 响应时间: ${System.currentTimeMillis()}")
                        }
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
            timeoutHandler.postDelayed(timeoutRunnable, 10000) // 10秒超时
            
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
        
        // 创建Video对象并添加到列表
        // 注意：这里videoUrl暂时为null，需要在点击视频时再获取
        videos.add(Video(id, title, duration, thumbnailUrl))
    }

    // 如果没有解析到视频，返回空列表
    if (videos.isEmpty()) {
        return Pair(emptyList(), PaginationInfo(1, 1, false, false))
    }

    // 解析分页信息
    val paginationInfo = parsePaginationInfo(doc)
    
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


