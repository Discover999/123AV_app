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

@Database(entities = [DownloadTask::class, CachedVideoDetails::class], version = 4, exportSchema = false)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun cachedVideoDetailsDao(): CachedVideoDetailsDao

    companion object {
        @Volatile
        private var INSTANCE: DownloadDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE download_tasks ADD COLUMN downloadSpeed INTEGER DEFAULT 0")
                } catch (e: Exception) {
                }
                try {
                    database.execSQL("ALTER TABLE download_tasks ADD COLUMN totalBytes INTEGER DEFAULT 0")
                } catch (e: Exception) {
                }
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS cached_video_details (
                        videoId TEXT PRIMARY KEY NOT NULL,
                        code TEXT NOT NULL,
                        releaseDate TEXT NOT NULL,
                        duration TEXT NOT NULL,
                        performer TEXT NOT NULL,
                        genres TEXT NOT NULL,
                        maker TEXT NOT NULL,
                        tags TEXT NOT NULL,
                        favouriteCount INTEGER DEFAULT 0,
                        cachedAt INTEGER DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            }
        }

        fun getInstance(context: Context): DownloadDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DownloadDatabase::class.java,
                    "download_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
