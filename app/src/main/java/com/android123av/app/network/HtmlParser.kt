package com.android123av.app.network

import com.android123av.app.models.VideoDetails
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

fun parseVideoDetails(html: String): VideoDetails? {
    return try {
        val doc = Jsoup.parse(html)
        
        // 解析代码
        val code = doc.select("div.detail-item div").find { div ->
            div.select("span").firstOrNull()?.text()?.contains("代码") == true
        }?.select("span")?.getOrNull(1)?.text() ?: ""
        
        // 解析发布日期
        val releaseDate = doc.select("div.detail-item div").find { div ->
            div.select("span").firstOrNull()?.text()?.contains("发布日期") == true
        }?.select("span")?.getOrNull(1)?.text() ?: ""
        
        // 解析时长
        val duration = doc.select("div.detail-item div").find { div ->
            div.select("span").firstOrNull()?.text()?.contains("时长") == true
        }?.select("span")?.getOrNull(1)?.text() ?: ""
        
        // 解析类型
        val genres = doc.select("div.detail-item div").find { div ->
            div.select("span").firstOrNull()?.text()?.contains("类型") == true
        }?.select("span.genre a")?.map { it.text() } ?: emptyList()
        
        // 解析制作人
        val maker = doc.select("div.detail-item div").find { div ->
            div.select("span").firstOrNull()?.text()?.contains("制作人") == true
        }?.select("span a")?.firstOrNull()?.text() ?: ""
        
        // 解析标签
        val tags = doc.select("div.detail-item div").find { div ->
            div.select("span").firstOrNull()?.text()?.contains("标签") == true
        }?.select("span a")?.map { it.text() } ?: emptyList()
        
        VideoDetails(
            code = code,
            releaseDate = releaseDate,
            duration = duration,
            genres = genres,
            maker = maker,
            tags = tags
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}