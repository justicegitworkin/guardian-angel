package com.guardianangel.app.data.local.dao

import androidx.room.*
import com.guardianangel.app.data.local.entity.AlertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {

    @Query("SELECT * FROM alerts ORDER BY timestamp DESC")
    fun getAllAlerts(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE type = :type ORDER BY timestamp DESC")
    fun getAlertsByType(type: String): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE riskLevel = :riskLevel ORDER BY timestamp DESC")
    fun getAlertsByRisk(riskLevel: String): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentAlerts(limit: Int = 10): Flow<List<AlertEntity>>

    @Query("SELECT COUNT(*) FROM alerts WHERE riskLevel = 'SCAM' AND timestamp >= :since")
    suspend fun countScamsBlocked(since: Long): Int

    @Query("SELECT * FROM alerts WHERE id = :id")
    suspend fun getAlertById(id: Long): AlertEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: AlertEntity): Long

    @Update
    suspend fun updateAlert(alert: AlertEntity)

    @Query("UPDATE alerts SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("UPDATE alerts SET isBlocked = 1 WHERE sender = :sender")
    suspend fun blockBySender(sender: String)

    @Delete
    suspend fun deleteAlert(alert: AlertEntity)

    @Query("DELETE FROM alerts")
    suspend fun deleteAll()
}
