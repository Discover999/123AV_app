package com.android123av.app.network

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VideoSite(
    val id: String,
    val name: String,
    val baseUrl: String,
    val description: String,
    val isDefault: Boolean = false
)

object SiteManager {
    private const val PREFS_NAME = "site_prefs"
    private const val KEY_SELECTED_SITE_ID = "selected_site_id"

    private const val DEFAULT_SITE_ID = "123av_com"

    private var sharedPreferences: SharedPreferences? = null

    private val defaultSite = VideoSite(
        id = "123av_com",
        name = "123AV 主站",
        baseUrl = "https://123av.com",
        description = "主站点",
        isDefault = true
    )

    private val availableSitesList = listOf(
        VideoSite(
            id = "123av_com",
            name = "123AV 主站",
            baseUrl = "https://123av.com",
            description = "主站点",
            isDefault = true
        ),
        VideoSite(
            id = "123av_ws",
            name = "123AV WS",
            baseUrl = "https://123av.ws",
            description = "备用站点1"
        ),
        VideoSite(
            id = "1av_to",
            name = "1AV TO",
            baseUrl = "https://1av.to",
            description = "备用站点2"
        )
    )

    private val _selectedSite = MutableStateFlow(defaultSite)
    val selectedSite: StateFlow<VideoSite> = _selectedSite.asStateFlow()

    private val _sites = MutableStateFlow<List<VideoSite>>(availableSitesList)
    val sites: StateFlow<List<VideoSite>> = _sites.asStateFlow()

    private var siteChangeListener: ((VideoSite) -> Unit)? = null

    val availableSites: List<VideoSite>
        get() = availableSitesList

    private fun getDefaultSite(): VideoSite {
        return defaultSite
    }

    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadSelectedSite()
    }

    private fun loadSelectedSite() {
        sharedPreferences?.let { prefs ->
            val savedSiteId = prefs.getString(KEY_SELECTED_SITE_ID, DEFAULT_SITE_ID)
            val site = availableSites.find { it.id == savedSiteId } ?: getDefaultSite()
            _selectedSite.value = site
        }
    }

    fun selectSite(siteId: String) {
        val site = availableSites.find { it.id == siteId } ?: getDefaultSite()
        
        sharedPreferences?.edit()?.apply {
            putString(KEY_SELECTED_SITE_ID, siteId)
            apply()
        }

        val previousSite = _selectedSite.value
        _selectedSite.value = site

        if (previousSite.id != site.id) {
            siteChangeListener?.invoke(site)
        }
    }

    fun selectSite(site: VideoSite) {
        selectSite(site.id)
    }

    fun getCurrentSite(): VideoSite {
        return _selectedSite.value
    }

    fun getCurrentBaseUrl(): String {
        return _selectedSite.value.baseUrl
    }

    fun getCurrentSiteId(): String {
        return _selectedSite.value.id
    }

    fun getCurrentSiteName(): String {
        return _selectedSite.value.name
    }

    fun setSiteChangeListener(listener: (VideoSite) -> Unit) {
        siteChangeListener = listener
    }

    fun removeSiteChangeListener() {
        siteChangeListener = null
    }

    fun isSiteAvailable(siteId: String): Boolean {
        return availableSites.any { it.id == siteId }
    }

    fun buildUrl(path: String): String {
        val baseUrl = getCurrentBaseUrl().removeSuffix("/")
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return "$baseUrl$cleanPath"
    }

    fun buildZhUrl(path: String): String {
        return buildUrl("/zh/$path")
    }
}
