package com.safeharborsecurity.app.data.local.dao

import androidx.room.*
import com.safeharborsecurity.app.data.local.entity.AlertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {

    @Query("SELECT * FROM alerts ORDER BY timestamp DESC")
    fun getAllAlerts(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE type = :type ORDER BY timestamp DESC")
    fun getAlertsByType(type: String): Flow<List<AlertEntity>>

    /** Multi-type query — used by Messages → Texts to combine legacy "SMS"
     *  alerts with new "SCREEN_SMS" alerts from the screen scanner under one
     *  view. */
    @Query("SELECT * FROM alerts WHERE type IN (:types) ORDER BY timestamp DESC")
    fun getAlertsByTypes(types: List<String>): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE riskLevel = :riskLevel ORDER BY timestamp DESC")
    fun getAlertsByRisk(riskLevel: String): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentAlerts(limit: Int = 10): Flow<List<AlertEntity>>

    @Query("SELECT COUNT(*) FROM alerts WHERE riskLevel = 'SCAM' AND timestamp >= :since")
    suspend fun countScamsBlocked(since: Long): Int

    @Query("SELECT * FROM alerts WHERE type = :type AND timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getAlertsByTypeSince(type: String, since: Long): List<AlertEntity>

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

    /** Clear All — wipes every row in the alerts table. Used by the home
     *  screen "Clear All" action. Sample-data seeding only runs on first
     *  install, so clearing won't repopulate. */
    @Query("DELETE FROM alerts")
    suspend fun deleteAllAlerts()
}
