package com.android123av.app.download

import android.content.Context
import androidx.room.*
import androidx.room.Dao
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
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: DownloadTask): Long
    
    @Update
    suspend fun updateTask(task: DownloadTask)
    
    @Query("UPDATE download_tasks SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: DownloadStatus)
    
    @Query("UPDATE download_tasks SET progress = :progress, downloadedBytes = :downloadedBytes, totalBytes = :totalBytes WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Int, downloadedBytes: Long, totalBytes: Long)
    
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

@Database(entities = [DownloadTask::class], version = 1, exportSchema = false)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadTaskDao(): DownloadTaskDao
    
    companion object {
        @Volatile
        private var INSTANCE: DownloadDatabase? = null
        
        fun getInstance(context: Context): DownloadDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DownloadDatabase::class.java,
                    "download_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
