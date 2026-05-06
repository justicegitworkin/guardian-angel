package com.safeharborsecurity.app.data.local.dao

import androidx.room.*
import com.safeharborsecurity.app.data.local.entity.ScamTipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScamTipDao {

    @Query("SELECT * FROM scam_tips ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<ScamTipEntity>>

    @Insert
    suspend fun insert(tip: ScamTipEntity): Long

    @Query("UPDATE scam_tips SET is_read = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("SELECT COUNT(*) FROM scam_tips")
    suspend fun count(): Int

    @Query("DELETE FROM scam_tips WHERE id IN (SELECT id FROM scam_tips ORDER BY timestamp ASC LIMIT :count)")
    suspend fun deleteOldest(count: Int)
}
