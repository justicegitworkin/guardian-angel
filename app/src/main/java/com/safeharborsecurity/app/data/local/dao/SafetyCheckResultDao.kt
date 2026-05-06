package com.safeharborsecurity.app.data.local.dao

import androidx.room.*
import com.safeharborsecurity.app.data.local.entity.SafetyCheckResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SafetyCheckResultDao {

    @Query("SELECT * FROM safety_check_results ORDER BY timestamp DESC")
    fun getAll(): Flow<List<SafetyCheckResultEntity>>

    @Query("SELECT * FROM safety_check_results ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<SafetyCheckResultEntity>>

    @Query("SELECT * FROM safety_check_results WHERE id = :id")
    suspend fun getById(id: Long): SafetyCheckResultEntity?

    @Insert
    suspend fun insert(result: SafetyCheckResultEntity): Long

    @Query("DELETE FROM safety_check_results WHERE id = :id")
    suspend fun deleteById(id: Long)
}
