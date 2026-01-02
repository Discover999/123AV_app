package com.android123av.app.network

import com.android123av.app.models.VideoDetails
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

fun parseVideoDetails(html: String): VideoDetails? {
    return try {
        val doc = Jsoup.parse(html)
        
        // 解析代码
        val code = doc.selectFirst("span:contains(代码) + span")?.text() ?: ""
        
        // 解析发布日期
        val releaseDate = doc.selectFirst("span:contains(发布日期) + span")?.text() ?: ""
        
        // 解析时长
        val duration = doc.selectFirst("span:contains(时长) + span")?.text() ?: ""
        
        // 解析女演员
        val performer = doc.selectFirst("span:contains(女演员) + span")?.text()?.trim() ?: ""
        
        // 解析类型
        val genres = doc.select("span.genre a").map { it.text() }
        
        // 解析制作人
        val maker = doc.selectFirst("span:contains(制作人) + span")?.text() ?: ""
        
        // 解析标签
        val tags = doc.select("span:contains(标签) + span a").map { it.text() }
        
        // 解析收藏数
        val favouriteCount = doc.select("span[ref=counter]").firstOrNull()?.text()?.toIntOrNull() ?: 0
        
        VideoDetails(
            code = code,
            releaseDate = releaseDate,
            duration = duration,
            performer = performer,
            genres = genres,
            maker = maker,
            tags = tags,
            favouriteCount = favouriteCount
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
