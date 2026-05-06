package com.safeharborsecurity.app.data.local.dao

import androidx.room.*
import com.safeharborsecurity.app.data.local.entity.CallLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallLogDao {

    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun getAllCallLogs(): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE id = :id")
    suspend fun getCallLogById(id: Long): CallLogEntity?

    @Query("SELECT * FROM call_logs WHERE callerNumber = :number ORDER BY timestamp DESC LIMIT 1")
    suspend fun getMostRecentForNumber(number: String): CallLogEntity?

    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recentSync(limit: Int): List<CallLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(log: CallLogEntity): Long

    @Update
    suspend fun updateCallLog(log: CallLogEntity)

    @Query("UPDATE call_logs SET isBlocked = 1 WHERE callerNumber = :number")
    suspend fun blockNumber(number: String)

    @Query("SELECT COUNT(*) FROM call_logs WHERE timestamp >= :since")
    suspend fun countSince(since: Long): Int
}
