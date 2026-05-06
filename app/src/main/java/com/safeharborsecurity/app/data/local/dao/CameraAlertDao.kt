package com.safeharborsecurity.app.data.local.dao

import androidx.room.*
import com.safeharborsecurity.app.data.local.entity.CameraAlertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CameraAlertDao {

    @Query("SELECT * FROM camera_alerts ORDER BY timestamp DESC LIMIT 200")
    fun getAllAlerts(): Flow<List<CameraAlertEntity>>

    @Query("SELECT * FROM camera_alerts WHERE category = :category ORDER BY timestamp DESC LIMIT 200")
    fun getAlertsByCategory(category: String): Flow<List<CameraAlertEntity>>

    @Query("SELECT COUNT(*) FROM camera_alerts WHERE isRead = 0")
    fun unreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alert: CameraAlertEntity): Long

    @Query("UPDATE camera_alerts SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("UPDATE camera_alerts SET isRead = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM camera_alerts WHERE timestamp < :before")
    suspend fun pruneOlderThan(before: Long)
}
