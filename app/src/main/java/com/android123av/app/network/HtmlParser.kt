package com.android123av.app.network

import com.android123av.app.models.VideoDetails
import com.android123av.app.models.MenuItem
import com.android123av.app.models.MenuSection
import com.android123av.app.models.Actress
import com.android123av.app.models.Genre
import com.android123av.app.models.Series
import com.android123av.app.models.PaginationInfo
import com.android123av.app.models.SortOption
import com.android123av.app.models.Studio
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

fun parseActressesFromHtml(html: String): Pair<List<Actress>, PaginationInfo> {
    return try {
        val doc = Jsoup.parse(html)
        val actresses = mutableListOf<Actress>()
        
        val actressElements = doc.select("div.box-item")
        
        actressElements.forEachIndexed { index, element ->
            val linkElement = element.selectFirst("a")
            if (linkElement != null) {
                val name = linkElement.selectFirst("div.name")?.text()?.trim() ?: ""
                val avatarUrl = linkElement.selectFirst("div.avatar img")?.attr("src")?.trim() ?: ""
                val href = linkElement.attr("href").trim()
                val id = if (href.contains("/")) href.substringAfterLast("/") else href
                
                val videoCountText = linkElement.selectFirst("div.detail div.text-muted")?.text()?.trim() ?: "0"
                val videoCount = videoCountText.replace("[^0-9]".toRegex(), "").toIntOrNull() ?: 0
                
                if (name.isNotEmpty() && id.isNotEmpty()) {
                    actresses.add(Actress(id, name, avatarUrl, videoCount))
                }
            }
        }
        
        val paginationInfo = parseActressesPaginationInfo(doc)
        
        Pair(actresses, paginationInfo)
    } catch (e: Exception) {
        android.util.Log.e("ActressParser", "Error parsing actresses", e)
        e.printStackTrace()
        Pair(emptyList(), PaginationInfo(1, 1, false, false))
    }
}

fun parseActressesPaginationInfo(doc: org.jsoup.nodes.Document): PaginationInfo {
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

    val titleElement = doc.selectFirst("div.title h2")
    val categoryTitle = titleElement?.text() ?: ""
    
    val finalTitle = categoryTitle.ifEmpty {
        val h1Element = doc.selectFirst("h1")
        h1Element?.text() ?: "热门女演员"
    }

    val videoCountElement = doc.selectFirst("div.title div.text-muted")
    val videoCount = videoCountElement?.text() ?: ""

    val totalResultsText = videoCount
        .replace(",", "")
        .replace("女演员", "")
        .replace(" ", "")
    val totalResults = totalResultsText.toIntOrNull() ?: 0

    val currentSort = doc.selectFirst("div.dropdown.show span.text-muted + span")?.text() ?: ""
    
    val sortOptions = mutableListOf<SortOption>()
    
    val sortDropdowns = doc.select("div.dropdown-menu")
    for (sortDropdown in sortDropdowns) {
        val dropdownParent = sortDropdown.parent()
        val dropdownLabel = dropdownParent?.select("span.text-muted")?.firstOrNull()?.text() ?: ""
        
        if (dropdownLabel.contains("排序方式")) {
            val sortItems = sortDropdown.select("a.dropdown-item")
            sortItems.forEach { item ->
                val href = item.attr("href")
                if (href.contains("?sort=")) {
                    val title = item.selectFirst("span")?.text() ?: ""
                    val sortValue = href.substringAfter("?sort=").substringBefore("&")
                    if (title.isNotEmpty() && sortValue.isNotEmpty()) {
                        sortOptions.add(SortOption(title, sortValue, sortValue == currentSort))
                    }
                }
            }
            break
        }
    }

    return PaginationInfo(
        currentPage = currentPage,
        totalPages = totalPages,
        hasNextPage = hasNextPage || currentPage < totalPages,
        hasPrevPage = hasPrevPage || currentPage > 1,
        totalResults = totalResults,
        categoryTitle = finalTitle,
        videoCount = videoCount,
        currentSort = currentSort,
        sortOptions = sortOptions
    )
}

fun parseGenresFromHtml(html: String, currentUrl: String = ""): Pair<List<Genre>, PaginationInfo> {
    return try {
        val doc = Jsoup.parse(html)
        val genresList = mutableListOf<Genre>()
        
        var genreElements = doc.select("div.bl-item")
        
        if (genreElements.isEmpty()) {
            genreElements = doc.select("div.box-item")
        }
        
        if (genreElements.isEmpty()) {
            genreElements = doc.select("div.item")
        }
        
        genreElements.forEachIndexed { index, element ->
            val linkElement = element.selectFirst("a")
            if (linkElement != null) {
                val href = linkElement.attr("href").trim()
                
                val id = if (href.contains("/")) href.substringAfterLast("/") else href
                
                var name = element.attr("title").trim()
                
                if (name.isEmpty()) {
                    val nameElement = linkElement.selectFirst("div.name")
                    if (nameElement != null) {
                        name = nameElement.text().trim()
                    }
                }
                
                if (name.isEmpty()) {
                    val imgElement = linkElement.selectFirst("img")
                    if (imgElement != null) {
                        name = imgElement.attr("alt")?.trim() ?: ""
                    }
                }
                
                if (name.isEmpty()) {
                    val textElement = linkElement.selectFirst(":not(:has(img))")
                    if (textElement != null) {
                        name = textElement.text().trim()
                    }
                }
                
                val videoCountText = linkElement.selectFirst("div.text-muted")?.text()?.trim() ?: 
                                     element.selectFirst("div.text-muted")?.text()?.trim() ?: "0"
                
                val videoCount = videoCountText.replace("[^0-9]".toRegex(), "").toIntOrNull() ?: 0
                
                if (name.isNotEmpty()) {
                    val finalId = id.ifEmpty { "genre_${System.currentTimeMillis()}_$index" }
                    genresList.add(Genre(finalId, name, videoCount))
                }
            }
        }
        
        val paginationInfo = parseGenresPaginationInfo(doc, currentUrl)
        
        Pair(genresList, paginationInfo)
    } catch (e: Exception) {
        android.util.Log.e("parseGenresFromHtml", "Error parsing genres", e)
        e.printStackTrace()
        Pair(emptyList(), PaginationInfo(1, 1, false, false))
    }
}

fun parseGenresPaginationInfo(doc: org.jsoup.nodes.Document, currentUrl: String = ""): PaginationInfo {
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

    val titleElement = doc.selectFirst("div.title h2")
    val categoryTitle = titleElement?.text() ?: ""
    
    val finalTitle = if (categoryTitle.isEmpty()) {
        val h1Element = doc.selectFirst("h1")
        h1Element?.text() ?: "类型"
    } else {
        categoryTitle
    }

    val videoCountElement = doc.selectFirst("div.title div.text-muted")
    val videoCount = videoCountElement?.text() ?: ""

    val totalResultsText = videoCount
        .replace(",", "")
        .replace("项目", "")
        .replace(" ", "")
    val totalResults = totalResultsText.toIntOrNull() ?: 0

    val sortOptions = mutableListOf<SortOption>()
    
    val currentSort = if (currentUrl.contains("?sort=")) {
        currentUrl.substringAfter("?sort=").substringBefore("&")
    } else {
        ""
    }
    
    val sortDropdowns = doc.select("div.dropdown-menu")
    for (sortDropdown in sortDropdowns) {
        val dropdownParent = sortDropdown.parent()
        val dropdownLabel = dropdownParent?.select("span.text-muted")?.firstOrNull()?.text() ?: ""
        
        if (dropdownLabel.contains("排序方式")) {
            val sortItems = sortDropdown.select("a.dropdown-item")
            sortItems.forEach { item ->
                val href = item.attr("href")
                if (href.contains("?sort=")) {
                    val title = item.selectFirst("span")?.text() ?: ""
                    val sortValue = href.substringAfter("?sort=").substringBefore("&")
                    if (title.isNotEmpty() && sortValue.isNotEmpty()) {
                        sortOptions.add(SortOption(title, sortValue, sortValue == currentSort))
                    }
                }
            }
            break
        }
    }

    return PaginationInfo(
        currentPage = currentPage,
        totalPages = totalPages,
        hasNextPage = hasNextPage || currentPage < totalPages,
        hasPrevPage = hasPrevPage || currentPage > 1,
        totalResults = totalResults,
        categoryTitle = finalTitle,
        videoCount = videoCount,
        currentSort = currentSort,
        sortOptions = sortOptions
    )
}

fun parseStudiosFromHtml(html: String, currentUrl: String = ""): Pair<List<Studio>, PaginationInfo> {
    return try {
        val doc = Jsoup.parse(html)
        val studiosList = mutableListOf<Studio>()
        
        var studioElements = doc.select("div.bl-item")
        
        if (studioElements.isEmpty()) {
            studioElements = doc.select("div.box-item")
        }
        
        if (studioElements.isEmpty()) {
            studioElements = doc.select("div.item")
        }
        
        studioElements.forEachIndexed { index, element ->
            val linkElement = element.selectFirst("a")
            if (linkElement != null) {
                val href = linkElement.attr("href").trim()
                
                val id = if (href.contains("/")) href.substringAfterLast("/") else href
                
                var name = element.attr("title").trim()
                
                if (name.isEmpty()) {
                    val nameElement = linkElement.selectFirst("div.name")
                    if (nameElement != null) {
                        name = nameElement.text().trim()
                    }
                }
                
                if (name.isEmpty()) {
                    val imgElement = linkElement.selectFirst("img")
                    if (imgElement != null) {
                        name = imgElement.attr("alt")?.trim() ?: ""
                    }
                }
                
                if (name.isEmpty()) {
                    val textElement = linkElement.selectFirst(":not(:has(img))")
                    if (textElement != null) {
                        name = textElement.text().trim()
                    }
                }
                
                val videoCountText = linkElement.selectFirst("div.text-muted")?.text()?.trim() ?: 
                                     element.selectFirst("div.text-muted")?.text()?.trim() ?: "0"
                
                val videoCount = videoCountText.replace("[^0-9]".toRegex(), "").toIntOrNull() ?: 0
                
                if (name.isNotEmpty()) {
                    val finalId = id.ifEmpty { "studio_${System.currentTimeMillis()}_$index" }
                    studiosList.add(Studio(finalId, name, videoCount))
                }
            }
        }
        
        val paginationInfo = parseStudiosPaginationInfo(doc, currentUrl)
        
        Pair(studiosList, paginationInfo)
    } catch (e: Exception) {
        android.util.Log.e("parseStudiosFromHtml", "Error parsing studios", e)
        e.printStackTrace()
        Pair(emptyList(), PaginationInfo(1, 1, false, false))
    }
}

fun parseStudiosPaginationInfo(doc: org.jsoup.nodes.Document, currentUrl: String = ""): PaginationInfo {
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

    val titleElement = doc.selectFirst("div.title h2")
    val categoryTitle = titleElement?.text() ?: ""
    
    val finalTitle = if (categoryTitle.isEmpty()) {
        val h1Element = doc.selectFirst("h1")
        h1Element?.text() ?: "制作人"
    } else {
        categoryTitle
    }

    val videoCountElement = doc.selectFirst("div.title div.text-muted")
    val videoCount = videoCountElement?.text() ?: ""

    val totalResultsText = videoCount
        .replace(",", "")
        .replace("项目", "")
        .replace(" ", "")
    val totalResults = totalResultsText.toIntOrNull() ?: 0

    val sortOptions = mutableListOf<SortOption>()
    
    val currentSort = if (currentUrl.contains("?sort=")) {
        currentUrl.substringAfter("?sort=").substringBefore("&")
    } else {
        ""
    }
    
    val sortDropdowns = doc.select("div.dropdown-menu")
    for (sortDropdown in sortDropdowns) {
        val dropdownParent = sortDropdown.parent()
        val dropdownLabel = dropdownParent?.select("span.text-muted")?.firstOrNull()?.text() ?: ""
        
        if (dropdownLabel.contains("排序方式")) {
            val sortItems = sortDropdown.select("a.dropdown-item")
            sortItems.forEach { item ->
                val href = item.attr("href")
                if (href.contains("?sort=")) {
                    val title = item.selectFirst("span")?.text() ?: ""
                    val sortValue = href.substringAfter("?sort=").substringBefore("&")
                    if (title.isNotEmpty() && sortValue.isNotEmpty()) {
                        sortOptions.add(SortOption(title, sortValue, sortValue == currentSort))
                    }
                }
            }
            break
        }
    }

    return PaginationInfo(
        currentPage = currentPage,
        totalPages = totalPages,
        hasNextPage = hasNextPage || currentPage < totalPages,
        hasPrevPage = hasPrevPage || currentPage > 1,
        totalResults = totalResults,
        categoryTitle = finalTitle,
        videoCount = videoCount,
        currentSort = currentSort,
        sortOptions = sortOptions
    )
}

fun parseSeriesFromHtml(html: String, currentUrl: String = ""): Pair<List<Series>, PaginationInfo> {
    return try {
        val doc = Jsoup.parse(html)
        val seriesList = mutableListOf<Series>()
        
        val seriesElements = doc.select("div.bl-item")
        
        seriesElements.forEachIndexed { index, element ->
            val linkElement = element.selectFirst("a")
            if (linkElement != null) {
                val href = linkElement.attr("href").trim()
                
                val id = if (href.contains("/")) href.substringAfterLast("/") else href
                
                var name = element.attr("title").trim()
                
                if (name.isEmpty()) {
                    val nameElement = linkElement.selectFirst("div.name")
                    if (nameElement != null) {
                        name = nameElement.text().trim()
                    }
                }
                
                val videoCountText = linkElement.selectFirst("div.text-muted")?.text()?.trim() ?: "0"
                
                val videoCount = videoCountText.replace("[^0-9]".toRegex(), "").toIntOrNull() ?: 0
                
                if (name.isNotEmpty() && id.isNotEmpty()) {
                    seriesList.add(Series(id, name, videoCount))
                }
            }
        }
        
        val paginationInfo = parseSeriesPaginationInfo(doc, currentUrl)
        
        Pair(seriesList, paginationInfo)
    } catch (e: Exception) {
        e.printStackTrace()
        Pair(emptyList(), PaginationInfo(1, 1, false, false))
    }
}

fun parseSeriesPaginationInfo(doc: org.jsoup.nodes.Document, currentUrl: String = ""): PaginationInfo {
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

    val titleElement = doc.selectFirst("div.title h2")
    val categoryTitle = titleElement?.text() ?: ""
    
    val finalTitle = if (categoryTitle.isEmpty()) {
        val h1Element = doc.selectFirst("h1")
        h1Element?.text() ?: "系列"
    } else {
        categoryTitle
    }

    val videoCountElement = doc.selectFirst("div.title div.text-muted")
    val videoCount = videoCountElement?.text() ?: ""

    val totalResultsText = videoCount
        .replace(",", "")
        .replace("项目", "")
        .replace(" ", "")
    val totalResults = totalResultsText.toIntOrNull() ?: 0

    val sortOptions = mutableListOf<SortOption>()
    
    val currentSort = if (currentUrl.contains("?sort=")) {
        currentUrl.substringAfter("?sort=").substringBefore("&")
    } else {
        ""
    }
    
    val sortDropdowns = doc.select("div.dropdown-menu")
    for (sortDropdown in sortDropdowns) {
        val dropdownParent = sortDropdown.parent()
        val dropdownLabel = dropdownParent?.select("span.text-muted")?.firstOrNull()?.text() ?: ""
        
        if (dropdownLabel.contains("排序方式")) {
            val sortItems = sortDropdown.select("a.dropdown-item")
            sortItems.forEach { item ->
                val href = item.attr("href")
                if (href.contains("?sort=")) {
                    val title = item.selectFirst("span")?.text() ?: ""
                    val sortValue = href.substringAfter("?sort=").substringBefore("&")
                    if (title.isNotEmpty() && sortValue.isNotEmpty()) {
                        sortOptions.add(SortOption(title, sortValue, sortValue == currentSort))
                    }
                }
            }
            break
        }
    }

    return PaginationInfo(
        currentPage = currentPage,
        totalPages = totalPages,
        hasNextPage = hasNextPage || currentPage < totalPages,
        hasPrevPage = hasPrevPage || currentPage > 1,
        totalResults = totalResults,
        categoryTitle = finalTitle,
        videoCount = videoCount,
        currentSort = currentSort,
        sortOptions = sortOptions
    )
}
