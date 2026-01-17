package com.android123av.app.network

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import com.android123av.app.models.PaginationInfo
import com.android123av.app.models.SortOption

object HtmlParserUtils {
    
    fun extractLinkInfo(element: Element): Pair<String, String>? {
        val linkElement = element.selectFirst("a") ?: return null
        val href = linkElement.attr("href").trim()
        val id = if (href.contains("/")) href.substringAfterLast("/") else href
        return Pair(href, id)
    }
    
    fun extractName(element: Element): String {
        var name = element.attr("title").trim()
        
        if (name.isEmpty()) {
            val linkElement = element.selectFirst("a")
            if (linkElement != null) {
                val nameElement = linkElement.selectFirst("div.name")
                if (nameElement != null) {
                    name = nameElement.text().trim()
                }
            }
        }
        
        if (name.isEmpty()) {
            val linkElement = element.selectFirst("a")
            if (linkElement != null) {
                val imgElement = linkElement.selectFirst("img")
                if (imgElement != null) {
                    name = imgElement.attr("alt")?.trim() ?: ""
                }
            }
        }
        
        if (name.isEmpty()) {
            val linkElement = element.selectFirst("a")
            if (linkElement != null) {
                val textElement = linkElement.selectFirst(":not(:has(img))")
                if (textElement != null) {
                    name = textElement.text().trim()
                }
            }
        }
        
        return name
    }
    
    fun extractVideoCount(element: Element): Int {
        val linkElement = element.selectFirst("a")
        val videoCountText = linkElement?.selectFirst("div.text-muted")?.text()?.trim() ?: 
                             element.selectFirst("div.text-muted")?.text()?.trim() ?: "0"
        return videoCountText.replace("[^0-9]".toRegex(), "").toIntOrNull() ?: 0
    }
    
    fun extractAvatarUrl(element: Element): String {
        val linkElement = element.selectFirst("a") ?: return ""
        return linkElement.selectFirst("div.avatar img")?.attr("src")?.trim() ?: ""
    }
    
    fun parsePaginationInfo(doc: Document, currentUrl: String = "", defaultTitle: String = "", isActressPage: Boolean = false): PaginationInfo {
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
            h1Element?.text() ?: defaultTitle
        }

        val videoCountElement = doc.selectFirst("div.title div.text-muted")
        val videoCount = videoCountElement?.text() ?: ""

        val totalResultsText = if (isActressPage) {
            videoCount
                .replace(",", "")
                .replace("女演员", "")
                .replace(" ", "")
        } else {
            videoCount
                .replace(",", "")
                .replace("视频", "")
                .replace("女演员", "")
                .replace("项目", "")
                .replace(" ", "")
        }
        val totalResults = totalResultsText.toIntOrNull() ?: 0

        val currentSort = if (isActressPage) {
            doc.selectFirst("div.dropdown.show span.text-muted + span")?.text() ?: ""
        } else if (currentUrl.contains("?sort=")) {
            currentUrl.substringAfter("?sort=").substringBefore("&")
        } else {
            ""
        }
        
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
}
