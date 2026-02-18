package com.android123av.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import com.android123av.app.screens.CategoryScreen
import com.android123av.app.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.android123av.app.network.fetchVideosDataWithResponse
import com.android123av.app.network.parseVideosFromHtml
import com.android123av.app.network.SiteManager

private const val TAG = "CategoryActivity"

class CategoryActivity : ComponentActivity() {
    private var categoryTitle: String = ""
    private var categoryHref: String = ""
    private var displayTitle: String = ""

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        categoryTitle = intent.getStringExtra("categoryTitle") ?: ""
        categoryHref = intent.getStringExtra("categoryHref") ?: ""
        displayTitle = categoryTitle

        enableEdgeToEdge()

        setContent {
            var title by remember { mutableStateOf(displayTitle) }
            
            LaunchedEffect(categoryHref) {
                if (categoryHref.contains("actresses/") && !categoryHref.contains("actresses?")) {
                    try {
                        val url = SiteManager.buildZhUrl(categoryHref)
                        val (_, paginationInfo) = withContext(Dispatchers.IO) {
                            parseVideosFromHtml(fetchVideosDataWithResponse(url, 1).second)
                        }
                        if (paginationInfo.categoryTitle.isNotEmpty()) {
                            title = paginationInfo.categoryTitle
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "获取分类标题失败: ${e.message}")
                    }
                }
            }

            MyApplicationTheme {
                CategoryScreen(
                    categoryTitle = title,
                    categoryHref = categoryHref,
                    onBack = { finish() },
                    onVideoClick = { video ->
                        val intent = Intent(this, VideoPlayerActivity::class.java)
                        intent.putExtra("video", video)
                        startActivity(intent)
                    },
                    onActressClick = { actressHref ->
                        val intent = Intent(this, CategoryActivity::class.java)
                        intent.putExtra("categoryTitle", "")
                        intent.putExtra("categoryHref", actressHref)
                        startActivity(intent)
                    },
                    onSeriesClick = { seriesHref ->
                        val intent = Intent(this, CategoryActivity::class.java)
                        intent.putExtra("categoryTitle", "")
                        intent.putExtra("categoryHref", seriesHref)
                        startActivity(intent)
                    }
                )
            }
        }
    }
}
