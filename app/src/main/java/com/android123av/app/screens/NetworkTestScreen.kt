package com.android123av.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.TimeUnit

data class NetworkEndpoint(
    val name: String,
    val url: String,
    val description: String
)

data class IPInfo(
    val ip: String,
    val countryCode: String,
    val country: String,
    val organization: String,
    val isp: String
)

data class TestResult(
    val endpoint: NetworkEndpoint,
    val status: TestStatus,
    val responseTime: Long,
    val errorMessage: String? = null
)

enum class TestStatus {
    PENDING,
    TESTING,
    SUCCESS,
    FAILED,
    TIMEOUT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkTestScreen(
    onNavigateBack: () -> Unit
) {
    val endpoints = listOf(
        NetworkEndpoint("123AV 主站", "https://123av.com/zh", "视频内容服务器"),
        NetworkEndpoint("CDN 资源", "https://cdn.123av.com", "静态资源 CDN"),
        NetworkEndpoint("API 接口", "https://123av.com/api", "数据接口"),
        NetworkEndpoint("视频流", "https://stream.123av.com", "视频流服务器"),
        NetworkEndpoint("图片资源", "https://img.123av.com", "图片 CDN")
    )
    
    var results by remember { mutableStateOf<List<TestResult>>(emptyList()) }
    var isTesting by remember { mutableStateOf(false) }
    var currentIPInfo by remember { mutableStateOf(IPInfo(ip = "-", countryCode = "-", country = "-", organization = "", isp = "-")) }
    var isRefreshingIP by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        currentIPInfo = fetchIPInfo()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("可用性检测") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            IPAddressCard(
                ipInfo = currentIPInfo,
                isRefreshing = isRefreshingIP,
                onRefresh = {
                    if (!isRefreshingIP) {
                        isRefreshingIP = true
                        coroutineScope.launch {
                            currentIPInfo = fetchIPInfo()
                            isRefreshingIP = false
                        }
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    if (!isTesting) {
                        isTesting = true
                        results = emptyList()
                        coroutineScope.launch {
                            testAllEndpoints(endpoints) { result ->
                                results = results + result
                            }
                            isTesting = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                enabled = !isTesting
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("测试中...")
                } else {
                    Icon(
                        imageVector = Icons.Default.NetworkPing,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("开始测试")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (results.isNotEmpty()) {
                TestSummary(results = results)
            }
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(endpoints) { endpoint ->
                    val result = results.find { it.endpoint == endpoint }
                    TestResultCard(
                        endpoint = endpoint,
                        result = result,
                        isTesting = isTesting && result == null
                    )
                }
            }
        }
    }
}

@Composable
private fun IPAddressCard(
    ipInfo: IPInfo?,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "当前 IP 信息",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (ipInfo != null && ipInfo.ip != "-" && ipInfo.country != "-") {
                    Text(
                        text = "${ipInfo.countryCode} - ${ipInfo.country}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "IP: ${ipInfo.ip}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Text(
                        text = "运营商: ${ipInfo.isp}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                } else {
                    Text(
                        text = "-:-:-",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private suspend fun fetchIPInfo(): IPInfo {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.ip.sb/geoip")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()
                
                val json = JSONObject(response)
                IPInfo(
                    ip = json.optString("ip", "-"),
                    countryCode = json.optString("country_code", "-"),
                    country = json.optString("country", "-"),
                    organization = json.optString("organization", ""),
                    isp = json.optString("isp", "-")
                )
            } else {
                IPInfo(ip = "-", countryCode = "-", country = "-", organization = "", isp = "-")
            }
        } catch (e: Exception) {
            IPInfo(ip = "-", countryCode = "-", country = "-", organization = "", isp = "-")
        }
    }
}

@Composable
private fun TestSummary(results: List<TestResult>) {
    val successCount = results.count { it.status == TestStatus.SUCCESS }
    val failedCount = results.count { it.status == TestStatus.FAILED || it.status == TestStatus.TIMEOUT }
    val avgResponseTime = if (results.isNotEmpty()) {
        results.filter { it.status == TestStatus.SUCCESS }.map { it.responseTime }.average()
    } else 0.0
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$successCount",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "成功",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$failedCount",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "失败",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${avgResponseTime.toLong()}ms",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = "平均延迟",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TestResultCard(
    endpoint: NetworkEndpoint,
    result: TestResult?,
    isTesting: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (result?.status) {
                TestStatus.SUCCESS -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                TestStatus.FAILED, TestStatus.TIMEOUT -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        when (result?.status) {
                            TestStatus.SUCCESS -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            TestStatus.FAILED, TestStatus.TIMEOUT -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                            TestStatus.TESTING -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                when (result?.status) {
                    TestStatus.TESTING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    TestStatus.SUCCESS -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    TestStatus.FAILED, TestStatus.TIMEOUT -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = endpoint.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = endpoint.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = endpoint.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                when (result?.status) {
                    TestStatus.SUCCESS -> {
                        Text(
                            text = "${result.responseTime}ms",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    TestStatus.FAILED -> {
                        Text(
                            text = "连接失败",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    TestStatus.TIMEOUT -> {
                        Text(
                            text = "超时",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    TestStatus.TESTING -> {
                        Text(
                            text = "测试中...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    else -> {
                        Text(
                            text = "等待测试",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private suspend fun testAllEndpoints(
    endpoints: List<NetworkEndpoint>,
    onResult: (TestResult) -> Unit
) {
    endpoints.forEach { endpoint ->
        val result = testEndpoint(endpoint)
        onResult(result)
    }
}

private suspend fun testEndpoint(endpoint: NetworkEndpoint): TestResult {
    return withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var lastErrorMessage: String? = null
        var timedOut = false

        try {
            kotlinx.coroutines.withTimeout(1000L) {
                val url = URL(endpoint.url)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 1000
                connection.readTimeout = 1000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) NetworkTest/1.0")

                val responseCode = connection.responseCode
                val responseTime = System.currentTimeMillis() - startTime

                connection.disconnect()

                if (responseCode in 200..399) {
                    TestResult(
                        endpoint = endpoint,
                        status = TestStatus.SUCCESS,
                        responseTime = responseTime
                    )
                } else {
                    TestResult(
                        endpoint = endpoint,
                        status = TestStatus.FAILED,
                        responseTime = 0,
                        errorMessage = "HTTP $responseCode"
                    )
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            TestResult(
                endpoint = endpoint,
                status = TestStatus.TIMEOUT,
                responseTime = 0,
                errorMessage = "连接超时"
            )
        } catch (e: java.net.SocketTimeoutException) {
            TestResult(
                endpoint = endpoint,
                status = TestStatus.TIMEOUT,
                responseTime = 0,
                errorMessage = "连接超时"
            )
        } catch (e: Exception) {
            TestResult(
                endpoint = endpoint,
                status = TestStatus.FAILED,
                responseTime = 0,
                errorMessage = e.message ?: "未知错误"
            )
        }
    }
}
