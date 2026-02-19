package com.android123av.app

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.android123av.app.network.initializeNetworkService
import com.android123av.app.network.ImprovedVideoUrlFetcher
import com.android123av.app.network.syncCookiesForCurrentSite
import com.android123av.app.network.SiteManager
import com.android123av.app.cache.VideoSessionCache
import com.android123av.app.network.clearVideoUrlCache

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 初始化站点与网络
        SiteManager.initialize(applicationContext)
        initializeNetworkService(applicationContext)

        // 在主线程稍后执行：预热 WebView 并同步 cookies
        Handler(Looper.getMainLooper()).post {
            try {
                ImprovedVideoUrlFetcher.warmUpWebView(applicationContext)
            } catch (e: Exception) {
            }

            try {
                syncCookiesForCurrentSite(applicationContext)
            } catch (e: Exception) {
            }
        }

        // 注册 Activity 生命周期回调，用于前台取消释放计划、后台调度释放
        registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            private var startedCount = 0

            override fun onActivityStarted(activity: android.app.Activity) {
                startedCount++
                // 进入前台，取消任何延迟释放并确保 WebView 已预热
                try {
                    ImprovedVideoUrlFetcher.cancelScheduledRelease()
                    ImprovedVideoUrlFetcher.warmUpWebView(applicationContext)
                } catch (_: Exception) {
                }
            }

            override fun onActivityStopped(activity: android.app.Activity) {
                startedCount = (startedCount - 1).coerceAtLeast(0)
                if (startedCount == 0) {
                    // 应用切到后台，延迟释放共享 WebView（默认 60s）
                    try {
                        ImprovedVideoUrlFetcher.scheduleReleaseAfter()
                    } catch (_: Exception) {
                    }
                    // 清理过期缓存
                    VideoSessionCache.cleanExpired()
                }
            }

            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // 内存紧张时立即释放 WebView 并清理缓存
        try {
            ImprovedVideoUrlFetcher.immediateRelease()
        } catch (_: Exception) {
        }
        @Suppress("DEPRECATION")
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            VideoSessionCache.clear()
            clearVideoUrlCache()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try {
            ImprovedVideoUrlFetcher.immediateRelease()
        } catch (_: Exception) {
        }
        VideoSessionCache.clear()
        clearVideoUrlCache()
    }

    override fun onTerminate() {
        super.onTerminate()
        // 应用退出时清除所有会话缓存
        VideoSessionCache.clear()
        clearVideoUrlCache()
    }
}
