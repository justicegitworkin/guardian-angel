package com.safeharborsecurity.app.data.local.dao

import androidx.room.*
import com.safeharborsecurity.app.data.local.entity.PanicEventEntity

@Dao
interface PanicEventDao {

    @Insert
    suspend fun insert(event: PanicEventEntity): Long

    @Query("SELECT COUNT(*) FROM panic_events WHERE timestamp > :since")
    suspend fun countSince(since: Long): Int
}
