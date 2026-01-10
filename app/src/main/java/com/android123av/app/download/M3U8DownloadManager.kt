package com.android123av.app.download

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import android.widget.Toast
import androidx.core.net.toUri
import kotlinx.coroutines.flow.Flow
import kotlin.math.max
import kotlin.math.min

class M3U8DownloadManager(private val context: Context) {

    private val okHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val database = DownloadDatabase.getInstance(context)
    private val downloadTaskDao = database.downloadTaskDao()

    private var downloadJobs = mutableMapOf<Long, Job>()

    companion object {
        private const val TAG = "M3U8DownloadManager"
        private const val BUFFER_SIZE = 65536
        private const val KEY_PREFIX = "#EXT-X-KEY:METHOD="
        private const val SEGMENT_PREFIX = "#EXTINF:"
        private const val DEFAULT_THREAD_COUNT = 4
        private const val MAX_THREAD_COUNT = 8
        private const val SPEED_CALCULATION_INTERVAL_MS = 500L
        private const val MIN_SEGMENTS_FOR_MULTI_THREAD = 10
        private const val SPEED_UPDATE_INTERVAL_MS = 200L
        private const val PROGRESS_UPDATE_MIN_DELTA = 0.5f
    }

    suspend fun startDownload(taskId: Long) {
        if (downloadJobs.containsKey(taskId)) {
            Log.d(TAG, "Download already in progress for task: $taskId")
            return
        }

        val job = CoroutineScope(Dispatchers.IO).launch {
            downloadM3U8(taskId)
        }

        downloadJobs[taskId] = job
    }

    fun pauseDownload(taskId: Long) {
        downloadJobs[taskId]?.cancel()
        downloadJobs.remove(taskId)

        CoroutineScope(Dispatchers.IO).launch {
            downloadTaskDao.updateStatus(taskId, DownloadStatus.PAUSED)
        }
    }

    fun cancelDownload(taskId: Long) {
        downloadJobs[taskId]?.cancel()
        downloadJobs.remove(taskId)

        CoroutineScope(Dispatchers.IO).launch {
            val task = downloadTaskDao.getTaskById(taskId)
            task?.let {
                deleteDownloadFiles(it.savePath)
                downloadTaskDao.deleteTaskById(taskId)
            }
        }
    }

    fun resumeDownload(taskId: Long) {
        val task = runBlocking {
            downloadTaskDao.getTaskById(taskId)
        }
        task?.let {
            CoroutineScope(Dispatchers.IO).launch {
                downloadTaskDao.updateStatus(taskId, DownloadStatus.PENDING)
                startDownload(taskId)
            }
        }
    }

    fun deleteDownload(taskId: Long) {
        cancelDownload(taskId)
    }

    suspend fun getTaskById(taskId: Long): DownloadTask? {
        return downloadTaskDao.getTaskById(taskId)
    }
    
    suspend fun getTaskByVideoId(videoId: String): DownloadTask? {
        return downloadTaskDao.getTaskByVideoId(videoId)
    }
    
    fun observeTaskById(taskId: Long): Flow<DownloadTask?> {
        return downloadTaskDao.observeTaskById(taskId)
    }

    suspend fun insertTask(task: DownloadTask): Long {
        return downloadTaskDao.insertTask(task)
    }

    private suspend fun downloadM3U8(taskId: Long) {
        val task = downloadTaskDao.getTaskById(taskId) ?: return

        try {
            downloadTaskDao.updateStatus(taskId, DownloadStatus.DOWNLOADING)

            val saveDir = File(task.savePath)
            if (!saveDir.exists()) {
                saveDir.mkdirs()
            }

            val baseUrl = task.downloadUrl ?: task.videoUrl
            val playlistContent = fetchContent(baseUrl)

            if (playlistContent.isNullOrEmpty()) {
                throw Exception("Failed to fetch m3u8 playlist")
            }

            val isMasterPlaylist = isMasterPlaylist(playlistContent)
            val playlistUrl: String

            if (isMasterPlaylist) {
                playlistUrl = selectBestQuality(playlistContent, baseUrl)
                val mediaPlaylistContent = fetchContent(playlistUrl)

                if (mediaPlaylistContent.isNullOrEmpty()) {
                    throw Exception("Failed to fetch media playlist")
                }

                downloadMediaPlaylist(taskId, mediaPlaylistContent, playlistUrl, saveDir)
            } else {
                playlistUrl = baseUrl
                downloadMediaPlaylist(taskId, playlistContent, playlistUrl, saveDir)
            }

            downloadTaskDao.markCompleted(taskId, DownloadStatus.COMPLETED, System.currentTimeMillis())

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "下载完成: ${task.title}", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Download failed for task $taskId", e)
            downloadTaskDao.markFailed(taskId, DownloadStatus.FAILED, e.message ?: "Unknown error")

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } finally {
            downloadJobs.remove(taskId)
        }
    }

    private suspend fun downloadMediaPlaylist(
        taskId: Long,
        playlistContent: String,
        playlistUrl: String,
        saveDir: File
    ) {
        val segments = parseMediaPlaylist(playlistContent)
        val totalSegments = segments.size

        if (totalSegments == 0) {
            throw Exception("No segments found in playlist")
        }

        val playlistDir = File(saveDir, "playlist")
        if (!playlistDir.exists()) {
            playlistDir.mkdirs()
        }

        val segmentDir = File(saveDir, "segments")
        if (!segmentDir.exists()) {
            segmentDir.mkdirs()
        }

        val keyUrl = extractKeyUrl(playlistContent)
        var keyData: ByteArray? = null

        if (keyUrl != null) {
            keyData = fetchKey(keyUrl)
        }

        val threadCount = calculateThreadCount(totalSegments)
        Log.d(TAG, "Using $threadCount threads for $totalSegments segments")

        val downloadedBytes = if (totalSegments >= MIN_SEGMENTS_FOR_MULTI_THREAD) {
            downloadSegmentsMultiThread(
                taskId = taskId,
                segments = segments,
                segmentDir = segmentDir,
                playlistUrl = playlistUrl,
                keyData = keyData,
                threadCount = threadCount,
                totalSegments = totalSegments
            )
        } else {
            downloadSegmentsSingleThread(
                taskId = taskId,
                segments = segments,
                segmentDir = segmentDir,
                playlistUrl = playlistUrl,
                keyData = keyData,
                totalSegments = totalSegments
            )
        }

        mergeSegments(taskId, segmentDir, saveDir, totalSegments)

        val outputFile = File(saveDir, "video.mp4")
        val totalBytes = if (outputFile.exists()) outputFile.length() else 0L
        downloadTaskDao.updateProgress(taskId, 100f, downloadedBytes, 0, totalBytes)
    }

    private fun calculateThreadCount(totalSegments: Int): Int {
        return when {
            totalSegments < MIN_SEGMENTS_FOR_MULTI_THREAD -> 1
            totalSegments < 50 -> min(MAX_THREAD_COUNT, max(DEFAULT_THREAD_COUNT, totalSegments / 10))
            else -> MAX_THREAD_COUNT
        }
    }

    private suspend fun downloadSegmentsSingleThread(
        taskId: Long,
        segments: List<DownloadSegment>,
        segmentDir: File,
        playlistUrl: String,
        keyData: ByteArray?,
        totalSegments: Int
    ): Long {
        var downloadedBytes = 0L
        var lastSpeedUpdateTime = System.currentTimeMillis()
        var bytesSinceLastUpdate = 0L
        var lastReportedProgress = 0f
        val speedHistory = ArrayDeque<Long>(5)
        val estimatedTotalBytes = totalSegments * 512000L

        for ((index, segment) in segments.withIndex()) {
            if (!isActive(taskId)) {
                throw Exception("Download cancelled")
            }

            val segmentFile = File(segmentDir, "segment_$index.ts")
            val segmentUrl = resolveUrl(segment.url, playlistUrl)

            val bytesDownloaded = downloadSegmentWithProgress(segmentUrl, segmentFile) { bytesRead ->
                bytesSinceLastUpdate += bytesRead
                downloadedBytes += bytesRead
            }

            if (keyData != null) {
                decryptSegment(segmentFile, keyData)
            }

            val currentTime = System.currentTimeMillis()
            val elapsedTime = currentTime - lastSpeedUpdateTime
            val progress = ((downloadedBytes.toFloat() / max(1L, estimatedTotalBytes)) * 100f).coerceIn(0f, 99.99f)

            if (elapsedTime >= SPEED_CALCULATION_INTERVAL_MS) {
                val instantSpeed = if (elapsedTime > 0) {
                    (bytesSinceLastUpdate * 1000) / elapsedTime
                } else 0L

                speedHistory.addLast(instantSpeed)
                if (speedHistory.size > 5) speedHistory.removeFirst()
                val smoothedSpeed = if (speedHistory.isNotEmpty()) speedHistory.average().toLong() else instantSpeed

                downloadTaskDao.updateProgress(taskId, progress, downloadedBytes, smoothedSpeed, estimatedTotalBytes)
                lastSpeedUpdateTime = currentTime
                bytesSinceLastUpdate = 0
            } else if (progress - lastReportedProgress >= PROGRESS_UPDATE_MIN_DELTA) {
                val currentSpeed = if (speedHistory.isNotEmpty()) speedHistory.last() else 0L
                downloadTaskDao.updateProgress(taskId, progress, downloadedBytes, currentSpeed, estimatedTotalBytes)
                lastReportedProgress = progress
            }
        }

        val finalSpeed = speedHistory.takeLast(3).average().toLong()
        downloadTaskDao.updateProgress(taskId, 100f, downloadedBytes, finalSpeed, estimatedTotalBytes)

        return downloadedBytes
    }

    private suspend fun downloadSegmentsMultiThread(
        taskId: Long,
        segments: List<DownloadSegment>,
        segmentDir: File,
        playlistUrl: String,
        keyData: ByteArray?,
        threadCount: Int,
        totalSegments: Int
    ): Long {
        val downloadedBytesMap = java.util.concurrent.ConcurrentHashMap<Int, Long>()
        var totalDownloadedBytes = 0L
        var lastSpeedUpdateTime = System.currentTimeMillis()
        var bytesSinceLastUpdate = 0L
        var lastReportedProgress = 0f
        val speedHistory = ArrayDeque<Long>(5)
        val estimatedTotalBytes = totalSegments * 512000L

        val segmentChunks = segments.chunked(ceilDiv(totalSegments, threadCount).coerceAtLeast(1))

        coroutineScope {
            val jobs = segmentChunks.mapIndexed { chunkIndex, chunkSegments ->
                launch(Dispatchers.IO) {
                    chunkSegments.forEachIndexed { segmentIndex, segment ->
                        val globalIndex = chunkIndex * (segments.size / threadCount.coerceAtLeast(1)) + segmentIndex
                        val segmentFile = File(segmentDir, "segment_$globalIndex.ts")
                        val segmentUrl = resolveUrl(segment.url, playlistUrl)

                        val bytesDownloaded = downloadSegmentWithProgress(segmentUrl, segmentFile) { bytesRead ->
                            downloadedBytesMap[globalIndex] = (downloadedBytesMap[globalIndex] ?: 0L) + bytesRead
                            bytesSinceLastUpdate += bytesRead
                            totalDownloadedBytes = downloadedBytesMap.values.sum()
                        }

                        if (keyData != null) {
                            decryptSegment(segmentFile, keyData)
                        }
                    }
                }
            }

            launch(Dispatchers.IO) {
                while (jobs.any { it.isActive }) {
                    delay(SPEED_UPDATE_INTERVAL_MS)

                    val currentBytes = downloadedBytesMap.values.sum()
                    val currentTime = System.currentTimeMillis()
                    val elapsedTime = currentTime - lastSpeedUpdateTime

                    if (elapsedTime >= SPEED_UPDATE_INTERVAL_MS) {
                        val instantSpeed = if (elapsedTime > 0) {
                            (bytesSinceLastUpdate * 1000) / elapsedTime
                        } else 0L

                        speedHistory.addLast(instantSpeed)
                        if (speedHistory.size > 5) speedHistory.removeFirst()
                        val smoothedSpeed = if (speedHistory.isNotEmpty()) speedHistory.average().toLong() else instantSpeed

                        val progress = ((currentBytes.toFloat() / max(1L, estimatedTotalBytes)) * 100f).coerceIn(0f, 99.99f)

                        if (progress - lastReportedProgress >= PROGRESS_UPDATE_MIN_DELTA) {
                            downloadTaskDao.updateProgress(taskId, progress, currentBytes, smoothedSpeed, estimatedTotalBytes)
                            lastReportedProgress = progress
                        }

                        lastSpeedUpdateTime = currentTime
                        bytesSinceLastUpdate = 0
                    }
                }
            }

            jobs.forEach { it.join() }
        }

        val finalProgress = 100f
        val finalSpeed = speedHistory.takeLast(3).average().toLong()
        downloadTaskDao.updateProgress(taskId, finalProgress, totalDownloadedBytes, finalSpeed, estimatedTotalBytes)

        return totalDownloadedBytes
    }

    private fun calculateEstimatedTotalBytes(segments: List<DownloadSegment>): Long {
        return segments.sumOf { it.estimatedBytes.coerceAtLeast(1024L) }
    }

    private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b

    private suspend fun downloadSegmentWithProgress(
        url: String,
        outputFile: File,
        onBytesRead: (Long) -> Unit
    ): Long {
        val request = Request.Builder()
            .url(url)
            .build()

        var totalBytesRead = 0L

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to download segment: ${response.code}")
            }

            val contentLength = response.body?.contentLength() ?: -1L

            response.body?.let { body ->
                FileOutputStream(outputFile).use { fos ->
                    body.byteStream().use { inputStream ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            fos.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            onBytesRead(bytesRead.toLong())
                        }
                    }
                }
            }
        }

        return totalBytesRead
    }

    private suspend fun downloadSegment(url: String, outputFile: File) {
        val request = Request.Builder()
            .url(url)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to download segment: ${response.code}")
            }

            response.body?.let { body ->
                FileOutputStream(outputFile).use { fos ->
                    body.byteStream().use { inputStream ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            fos.write(buffer, 0, bytesRead)
                        }
                    }
                }
            }
        }
    }

    @Suppress("CIPHER_INSTANCE_WITH_WEAK_MODE")
    private fun decryptSegment(segmentFile: File, key: ByteArray) {
        val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
        val iv = extractIV(segmentFile)
        val keySpec = javax.crypto.spec.SecretKeySpec(key, "AES")
        val ivSpec = javax.crypto.spec.IvParameterSpec(iv)

        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, ivSpec)

        val encryptedData = segmentFile.readBytes()
        val decryptedData = cipher.doFinal(encryptedData)

        FileOutputStream(segmentFile).use { fos ->
            fos.write(decryptedData)
        }
    }

    private fun extractIV(segmentFile: File): ByteArray {
        return ByteArray(16) { 0 }
    }

    private suspend fun mergeSegments(
        taskId: Long,
        segmentDir: File,
        saveDir: File,
        totalSegments: Int
    ) {
        val outputFile = File(saveDir, "video.mp4")

        FileOutputStream(outputFile).use { fos ->
            for (i in 0 until totalSegments) {
                val segmentFile = File(segmentDir, "segment_$i.ts")
                if (segmentFile.exists()) {
                    fos.write(segmentFile.readBytes())
                    segmentFile.delete()
                }
            }
        }

        segmentDir.delete()
    }

    private fun calculateTotalBytes(segmentDir: File, totalSegments: Int): Long {
        var totalBytes = 0L
        for (i in 0 until totalSegments) {
            val segmentFile = File(segmentDir, "segment_$i.ts")
            if (segmentFile.exists()) {
                totalBytes += segmentFile.length()
            }
        }
        return totalBytes
    }

    private fun isMasterPlaylist(content: String): Boolean {
        return content.contains("#EXT-X-STREAM-INF")
    }

    private fun selectBestQuality(content: String, baseUrl: String): String {
        val lines = content.split("\n")
        var bestBandwidth = 0
        var bestUrl: String? = null

        var currentBandwidth = 0
        var currentUrl: String? = null

        for (line in lines) {
            when {
                line.startsWith("#EXT-X-STREAM-INF:BANDWIDTH=") -> {
                    val bandwidthStr = line.substringAfter("BANDWIDTH=")
                        .substringBefore(",")
                        .substringBefore("\n")
                        .trim()
                    currentBandwidth = bandwidthStr.toIntOrNull() ?: 0
                }
                line.startsWith("#EXT-X-STREAM-INF:") -> {
                    val bandwidthStr = line.substringAfter("BANDWIDTH=")
                        .substringBefore(",")
                        .substringBefore("\n")
                        .trim()
                    currentBandwidth = bandwidthStr.toIntOrNull() ?: 0
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    if (currentBandwidth > bestBandwidth) {
                        bestBandwidth = currentBandwidth
                        bestUrl = resolveUrl(line.trim(), baseUrl)
                    }
                    currentBandwidth = 0
                }
            }
        }

        return bestUrl ?: content.lines().firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: baseUrl
    }

    private fun parseMediaPlaylist(content: String): List<DownloadSegment> {
        val segments = mutableListOf<DownloadSegment>()
        val lines = content.split("\n")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.startsWith(SEGMENT_PREFIX)) {
                val durationStr = line.substringAfter(SEGMENT_PREFIX)
                    .substringBefore(",")
                    .trim()
                val duration = durationStr.toFloatOrNull() ?: 0f

                if (i + 1 < lines.size) {
                    val urlLine = lines[i + 1].trim()
                    if (urlLine.isNotEmpty() && !urlLine.startsWith("#")) {
                        segments.add(
                            DownloadSegment(
                                url = urlLine,
                                duration = duration,
                                bandwidth = null,
                                resolution = null
                            )
                        )
                        i++
                    }
                }
            }

            i++
        }

        return segments
    }

    private fun extractKeyUrl(content: String): String? {
        val keyLine = content.lines().find { it.contains(KEY_PREFIX) } ?: return null
        val method = keyLine.substringAfter(KEY_PREFIX)

        if (method.contains("AES-128")) {
            val urlStart = keyLine.indexOf("URI=\"") + 5
            val urlEnd = keyLine.indexOf("\"", urlStart)

            if (urlStart > 4 && urlEnd > urlStart) {
                return keyLine.substring(urlStart, urlEnd).removeSurrounding("\"")
            }
        }

        return null
    }

    private suspend fun fetchContent(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    Log.e(TAG, "Failed to fetch content: ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching content", e)
            null
        }
    }

    private suspend fun fetchKey(url: String): ByteArray? {
        return try {
            val request = Request.Builder()
                .url(url)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.bytes()
                } else {
                    Log.e(TAG, "Failed to fetch key: ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching key", e)
            null
        }
    }

    private fun resolveUrl(url: String, baseUrl: String): String {
        return when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("/") -> {
                val baseUri = baseUrl.toUri()
                "${baseUri.scheme}://${baseUri.host}$url"
            }
            else -> {
                val lastSlashIndex = baseUrl.lastIndexOf('/')
                if (lastSlashIndex > 0) {
                    baseUrl.substring(0, lastSlashIndex + 1) + url
                } else {
                    baseUrl + "/" + url
                }
            }
        }
    }

    private fun deleteDownloadFiles(path: String) {
        val dir = File(path)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    private fun isActive(taskId: Long): Boolean {
        return downloadJobs[taskId]?.isActive == true
    }

    fun getActiveDownloads(): Flow<List<DownloadTask>> {
        return downloadTaskDao.getTasksByStatus(DownloadStatus.DOWNLOADING)
    }

    fun getCompletedDownloads(): Flow<List<DownloadTask>> {
        return downloadTaskDao.getTasksByStatus(DownloadStatus.COMPLETED)
    }

    fun getAllDownloads(): Flow<List<DownloadTask>> {
        return downloadTaskDao.getAllTasks()
    }
}
