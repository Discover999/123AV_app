package com.android123av.app.network

import com.android123av.app.models.VideoDetails
import com.android123av.app.models.MenuItem
import com.android123av.app.models.MenuSection
import com.android123av.app.models.Actress
import com.android123av.app.models.Genre
import com.android123av.app.models.Series
import com.android123av.app.models.PaginationInfo
import com.android123av.app.models.Studio
import com.android123av.app.utils.ExceptionHandler
import org.jsoup.Jsoup

fun parseVideoDetails(html: String): VideoDetails? {
    return ExceptionHandler.safeCallOrNull("parseVideoDetails") {
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
    }
}

fun parseNavigationMenu(html: String): List<MenuSection> {
    return ExceptionHandler.safeCall("parseNavigationMenu", emptyList()) {
        val doc = Jsoup.parse(html)
        val menuSections = mutableListOf<MenuSection>()
        
        val navElement = doc.selectFirst("div.col-auto.nav-wrap ul#nav")
        if (navElement == null) {
            return@safeCall menuSections
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
    }
}

fun parseActressesFromHtml(html: String): Pair<List<Actress>, PaginationInfo> {
    return ExceptionHandler.safeCall("parseActressesFromHtml", Pair(emptyList(), PaginationInfo(1, 1, false, false))) {
        val doc = Jsoup.parse(html)
        val actresses = mutableListOf<Actress>()
        
        val actressElements = doc.select("div.box-item")
        
        actressElements.forEach { element ->
            val linkElement = element.selectFirst("a")
            if (linkElement != null) {
                val name = linkElement.selectFirst("div.name")?.text()?.trim() ?: ""
                val avatarUrl = HtmlParserUtils.extractAvatarUrl(element)
                val href = linkElement.attr("href").trim()
                val id = if (href.contains("/")) href.substringAfterLast("/") else href
                
                val videoCountText = linkElement.selectFirst("div.detail div.text-muted")?.text()?.trim() ?: "0"
                val videoCount = videoCountText.replace("[^0-9]".toRegex(), "").toIntOrNull() ?: 0
                
                if (name.isNotEmpty() && id.isNotEmpty()) {
                    actresses.add(Actress(id, name, avatarUrl, videoCount))
                }
            }
        }
        
        val paginationInfo = HtmlParserUtils.parsePaginationInfo(doc, "", "热门女演员", true)
        
        Pair(actresses, paginationInfo)
    }
}




fun parseGenresFromHtml(html: String, currentUrl: String = ""): Pair<List<Genre>, PaginationInfo> {
    return ExceptionHandler.safeCall("parseGenresFromHtml", Pair(emptyList(), PaginationInfo(1, 1, false, false))) {
        val doc = Jsoup.parse(html)
        val genresList = mutableListOf<Genre>()
        
        var genreElements = doc.select("div.bl-item")
        
        if (genreElements.isEmpty()) {
            genreElements = doc.select("div.box-item")
        }
        
        if (genreElements.isEmpty()) {
            genreElements = doc.select("div.item")
        }
        
        genreElements.forEach { element ->
            val linkInfo = HtmlParserUtils.extractLinkInfo(element)
            if (linkInfo != null) {
                val (_, id) = linkInfo
                val name = HtmlParserUtils.extractName(element)
                val videoCount = HtmlParserUtils.extractVideoCount(element)
                
                if (name.isNotEmpty()) {
                    val finalId = id.ifEmpty { "genre_${System.currentTimeMillis()}_${genresList.size}" }
                    genresList.add(Genre(finalId, name, videoCount))
                }
            }
        }
        
        val paginationInfo = HtmlParserUtils.parsePaginationInfo(doc, currentUrl, "类型")
        
        Pair(genresList, paginationInfo)
    }
}




fun parseStudiosFromHtml(html: String, currentUrl: String = ""): Pair<List<Studio>, PaginationInfo> {
    return ExceptionHandler.safeCall("parseStudiosFromHtml", Pair(emptyList(), PaginationInfo(1, 1, false, false))) {
        val doc = Jsoup.parse(html)
        val studiosList = mutableListOf<Studio>()
        
        var studioElements = doc.select("div.bl-item")
        
        if (studioElements.isEmpty()) {
            studioElements = doc.select("div.box-item")
        }
        
        if (studioElements.isEmpty()) {
            studioElements = doc.select("div.item")
        }
        
        studioElements.forEach { element ->
            val linkInfo = HtmlParserUtils.extractLinkInfo(element)
            if (linkInfo != null) {
                val (_, id) = linkInfo
                val name = HtmlParserUtils.extractName(element)
                val videoCount = HtmlParserUtils.extractVideoCount(element)
                
                if (name.isNotEmpty()) {
                    val finalId = id.ifEmpty { "studio_${System.currentTimeMillis()}_${studiosList.size}" }
                    studiosList.add(Studio(finalId, name, videoCount))
                }
            }
        }
        
        val paginationInfo = HtmlParserUtils.parsePaginationInfo(doc, currentUrl, "制作人")
        
        Pair(studiosList, paginationInfo)
    }
}




fun parseSeriesFromHtml(html: String, currentUrl: String = ""): Pair<List<Series>, PaginationInfo> {
    return ExceptionHandler.safeCall("parseSeriesFromHtml", Pair(emptyList(), PaginationInfo(1, 1, false, false))) {
        val doc = Jsoup.parse(html)
        val seriesList = mutableListOf<Series>()
        
        val seriesElements = doc.select("div.bl-item")
        
        seriesElements.forEach { element ->
            val linkInfo = HtmlParserUtils.extractLinkInfo(element)
            if (linkInfo != null) {
                val (_, id) = linkInfo
                val name = HtmlParserUtils.extractName(element)
                val videoCount = HtmlParserUtils.extractVideoCount(element)
                
                if (name.isNotEmpty() && id.isNotEmpty()) {
                    seriesList.add(Series(id, name, videoCount))
                }
            }
        }
        
        val paginationInfo = HtmlParserUtils.parsePaginationInfo(doc, currentUrl, "系列")
        
        Pair(seriesList, paginationInfo)
    }
}



