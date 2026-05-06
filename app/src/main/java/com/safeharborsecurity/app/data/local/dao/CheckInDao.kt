package com.safeharborsecurity.app.data.local.dao

import androidx.room.*
import com.safeharborsecurity.app.data.local.entity.CheckInEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInDao {

    @Query("SELECT * FROM check_in_events WHERE date = :date LIMIT 1")
    suspend fun getCheckInForDate(date: String): CheckInEntity?

    @Query("SELECT * FROM check_in_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<CheckInEntity>>

    @Query("SELECT COUNT(*) FROM check_in_events WHERE date = :date")
    suspend fun countForDate(date: String): Int

    @Insert
    suspend fun insert(event: CheckInEntity): Long
}
