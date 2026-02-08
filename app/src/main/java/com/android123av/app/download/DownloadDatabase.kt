package com.android123av.app.download

import android.content.Context
import androidx.room.*
import androidx.room.Dao
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadTaskDao {
    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<DownloadTask>>
    
    @Query("SELECT * FROM download_tasks WHERE status = :status ORDER BY createdAt DESC")
    fun getTasksByStatus(status: DownloadStatus): Flow<List<DownloadTask>>
    
    @Query("SELECT * FROM download_tasks WHERE videoId = :videoId LIMIT 1")
    suspend fun getTaskByVideoId(videoId: String): DownloadTask?
    
    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): DownloadTask?
    
    @Query("SELECT * FROM download_tasks WHERE id = :id")
    fun observeTaskById(id: Long): Flow<DownloadTask?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: DownloadTask): Long
    
    @Update
    suspend fun updateTask(task: DownloadTask)
    
    @Query("UPDATE download_tasks SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: DownloadStatus)
    
    @Query("UPDATE download_tasks SET progress = :progress, downloadedBytes = :downloadedBytes, downloadSpeed = :speed, totalBytes = :totalBytes WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Float, downloadedBytes: Long, speed: Long, totalBytes: Long)
    
    @Query("UPDATE download_tasks SET status = :status, completedAt = :completedAt WHERE id = :id")
    suspend fun markCompleted(id: Long, status: DownloadStatus, completedAt: Long)
    
    @Query("UPDATE download_tasks SET status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun markFailed(id: Long, status: DownloadStatus, errorMessage: String)
    
    @Delete
    suspend fun deleteTask(task: DownloadTask)
    
    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long)
    
    @Query("SELECT COUNT(*) FROM download_tasks WHERE status = :status")
    suspend fun getTaskCountByStatus(status: DownloadStatus): Int
}

@Dao
interface CachedVideoDetailsDao {
    @Query("SELECT * FROM cached_video_details WHERE videoId = :videoId LIMIT 1")
    suspend fun getVideoDetails(videoId: String): CachedVideoDetails?
    
    @Query("SELECT * FROM cached_video_details WHERE videoId = :videoId")
    fun observeVideoDetails(videoId: String): Flow<CachedVideoDetails?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideoDetails(details: CachedVideoDetails)
    
    @Query("DELETE FROM cached_video_details WHERE videoId = :videoId")
    suspend fun deleteVideoDetails(videoId: String)
    
    @Query("DELETE FROM cached_video_details WHERE cachedAt < :timestamp")
    suspend fun deleteOldCache(timestamp: Long): Int
    
    @Query("SELECT COUNT(*) FROM cached_video_details")
    suspend fun getCacheCount(): Int
}

@Database(entities = [DownloadTask::class, CachedVideoDetails::class], version = 1, exportSchema = false)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun cachedVideoDetailsDao(): CachedVideoDetailsDao

    companion object {
        @Volatile
        private var INSTANCE: DownloadDatabase? = null

        fun getInstance(context: Context): DownloadDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): DownloadDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                DownloadDatabase::class.java,
                "download_database"
            ).build()
        }
    }
}
