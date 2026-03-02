package com.guardianangel.app.data.local.dao

import androidx.room.*
import com.guardianangel.app.data.local.entity.CallLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallLogDao {

    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun getAllCallLogs(): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE id = :id")
    suspend fun getCallLogById(id: Long): CallLogEntity?

    @Query("SELECT * FROM call_logs WHERE callerNumber = :number ORDER BY timestamp DESC LIMIT 1")
    suspend fun getMostRecentForNumber(number: String): CallLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(log: CallLogEntity): Long

    @Update
    suspend fun updateCallLog(log: CallLogEntity)

    @Query("UPDATE call_logs SET isBlocked = 1 WHERE callerNumber = :number")
    suspend fun blockNumber(number: String)

    @Query("DELETE FROM call_logs")
    suspend fun deleteAll()
}
