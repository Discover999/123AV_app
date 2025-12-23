package com.android123av.app.network

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException

/**
 * 持久化CookieJar实现，将cookies存储在SharedPreferences中
 * 确保应用重启后登录状态不会丢失
 */
class PersistentCookieJar(context: Context) : CookieJar {
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("app_cookies", Context.MODE_PRIVATE)
    private val gson = com.google.gson.Gson()
    
    companion object {
        private const val COOKIE_PREFIX = "cookie_"
        private const val COOKIE_COUNT_KEY = "cookie_count"
    }
    
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val editor = sharedPreferences.edit()
        val host = url.host
        
        // 清除该主机的旧cookies
        val existingKeys = sharedPreferences.all.keys.filter { it.startsWith("$COOKIE_PREFIX$host|") }
        existingKeys.forEach { editor.remove(it) }
        
        // 保存新的cookies
        cookies.forEachIndexed { index, cookie ->
            val key = "$COOKIE_PREFIX$host|$index"
            val cookieData = CookieData(
                name = cookie.name,
                value = cookie.value,
                domain = cookie.domain,
                path = cookie.path,
                expiresAt = cookie.expiresAt,
                secure = cookie.secure,
                httpOnly = cookie.httpOnly,
                hostOnly = cookie.hostOnly,
                persistent = cookie.persistent
            )
            val cookieJson = gson.toJson(cookieData)
            editor.putString(key, cookieJson)
        }
        
        editor.apply()
        println("DEBUG: PersistentCookieJar - Saved ${cookies.size} cookies for $host")
    }
    
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val cookies = mutableListOf<Cookie>()
        
        // 查找该主机的所有cookies
        val allPrefs = sharedPreferences.all
        allPrefs.keys.filter { it.startsWith("$COOKIE_PREFIX$host|") }.forEach { key ->
            val cookieJson = allPrefs[key] as? String
            if (cookieJson != null) {
                try {
                    val cookieData = gson.fromJson(cookieJson, CookieData::class.java)
                    val cookie = Cookie.Builder()
                        .name(cookieData.name)
                        .value(cookieData.value)
                        .domain(cookieData.domain)
                        .path(cookieData.path)
                        .expiresAt(cookieData.expiresAt)
                        .apply {
                            if (cookieData.secure) secure()
                            if (cookieData.httpOnly) httpOnly()
                            if (cookieData.hostOnly) hostOnlyDomain(cookieData.domain)
                        }
                        .build()
                    
                    // 检查cookie是否过期
                    if (cookie.expiresAt > System.currentTimeMillis()) {
                        cookies.add(cookie)
                    } else {
                        // 删除过期cookie
                        sharedPreferences.edit().remove(key).apply()
                    }
                } catch (e: Exception) {
                    println("DEBUG: PersistentCookieJar - Error parsing cookie: ${e.message}")
                    // 删除无效的cookie
                    sharedPreferences.edit().remove(key).apply()
                }
            }
        }
        
        println("DEBUG: PersistentCookieJar - Loaded ${cookies.size} valid cookies for $host")
        return cookies
    }
    
    /**
     * 清除所有cookies
     */
    fun clearAllCookies() {
        sharedPreferences.edit().clear().apply()
        println("DEBUG: PersistentCookieJar - All cookies cleared")
    }
    
    /**
     * 清除特定主机的cookies
     */
    fun clearCookiesForHost(host: String) {
        val editor = sharedPreferences.edit()
        val keys = sharedPreferences.all.keys.filter { it.startsWith("$COOKIE_PREFIX$host|") }
        keys.forEach { editor.remove(it) }
        editor.apply()
        println("DEBUG: PersistentCookieJar - Cookies cleared for $host")
    }
    
    /**
     * 检查是否有有效的登录cookies
     */
    fun hasValidCookies(host: String): Boolean {
        val httpUrl = "https://$host".toHttpUrlOrNull() ?: return false
        val cookies = loadForRequest(httpUrl)
        return cookies.isNotEmpty()
    }
    
    /**
     * Cookie数据类，用于序列化
     */
    private data class CookieData(
        val name: String,
        val value: String,
        val domain: String,
        val path: String,
        val expiresAt: Long,
        val secure: Boolean,
        val httpOnly: Boolean,
        val hostOnly: Boolean,
        val persistent: Boolean
    )
}