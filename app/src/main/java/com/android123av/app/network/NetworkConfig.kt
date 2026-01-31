package com.android123av.app.network

import android.content.Context
import com.android123av.app.constants.NetworkConstants
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

object NetworkConfig {
    private const val HTTP_CACHE_DIR = "http_cache"
    private const val CONNECTION_POOL_KEEP_ALIVE_DURATION = 5L
    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 20L
    private const val WRITE_TIMEOUT_SECONDS = 20L
    
    private var persistentCookieJar: PersistentCookieJar? = null
    private var okHttpClient: OkHttpClient? = null
    
    fun initialize(context: Context) {
        if (persistentCookieJar == null) {
            persistentCookieJar = PersistentCookieJar(context)
        }
        
        val cacheDir = File(context.cacheDir, HTTP_CACHE_DIR)
        val cache = Cache(cacheDir, NetworkConstants.HTTP_CACHE_SIZE)
        
        okHttpClient = OkHttpClient.Builder()
            .cookieJar(persistentCookieJar!!)
            .cache(cache)
            .connectionPool(ConnectionPool(NetworkConstants.CONNECTION_POOL_SIZE, CONNECTION_POOL_KEEP_ALIVE_DURATION, TimeUnit.MINUTES))
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
    
    fun getOkHttpClient(): OkHttpClient {
        return okHttpClient ?: throw IllegalStateException("NetworkService not initialized")
    }
    
    fun getPersistentCookieJar(): PersistentCookieJar? = persistentCookieJar
}
