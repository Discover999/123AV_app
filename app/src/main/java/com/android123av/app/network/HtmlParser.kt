package com.android123av.app.network

import com.android123av.app.models.VideoDetails
import com.android123av.app.models.MenuItem
import com.android123av.app.models.MenuSection
import org.jsoup.Jsoup

fun parseVideoDetails(html: String): VideoDetails? {
    return try {
        val doc = Jsoup.parse(html)
        
        val code = doc.selectFirst("span:contains(代码) + span")?.text() ?: ""
        
        val releaseDate = doc.selectFirst("span:contains(发布日期) + span")?.text() ?: ""
        
        val duration = doc.selectFirst("span:contains(时长) + span")?.text() ?: ""
        
        val performer = doc.selectFirst("span:contains(女演员) + span")?.text()?.trim() ?: ""
        
        val genres = doc.select("span.genre a").map { it.text() }
        
        val maker = doc.selectFirst("span:contains(制作人) + span")?.text() ?: ""
        
        val tags = doc.select("span:contains(标签) + span a").map { it.text() }
        
        val favouriteCount = doc.select("span[ref=counter]").firstOrNull()?.text()?.toIntOrNull() ?: 0
        
        val realId = doc.select("button.favourite").firstOrNull()?.attr("v-scope")?.let { vScope ->
            val regex = Regex("Favourite\\('movie',\\s*(\\d+)")
            regex.find(vScope)?.groupValues?.get(1) ?: ""
        } ?: ""
        
        VideoDetails(
            code = code,
            releaseDate = releaseDate,
            duration = duration,
            performer = performer,
            genres = genres,
            maker = maker,
            tags = tags,
            favouriteCount = favouriteCount,
            realId = realId
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun parseNavigationMenu(html: String): List<MenuSection> {
    return try {
        val doc = Jsoup.parse(html)
        val menuSections = mutableListOf<MenuSection>()
        
        val navElement = doc.selectFirst("div.col-auto.nav-wrap ul#nav")
        if (navElement == null) {
            return menuSections
        }
        
        val menuItems = navElement.select("li.has-child")
        
        for (menuItem in menuItems) {
            val titleElement = menuItem.selectFirst("> a")
            if (titleElement == null) continue
            
            val title = titleElement.text().trim()
            val subMenuList = menuItem.select("> ul > li > a")
            
            val items = subMenuList.mapNotNull { subLink ->
                val itemTitle = subLink.text().trim()
                val href = subLink.attr("href").trim()
                if (itemTitle.isNotEmpty() && href.isNotEmpty()) {
                    MenuItem(itemTitle, href)
                } else {
                    null
                }
            }
            
            if (title.isNotEmpty() && items.isNotEmpty() && !title.contains("更多站点", ignoreCase = true)) {
                menuSections.add(MenuSection(title, items))
            }
        }
        
        menuSections
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}
