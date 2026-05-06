package com.safeharborsecurity.app.data.local.dao

import androidx.room.*
import com.safeharborsecurity.app.data.local.entity.PointTransactionEntity
import com.safeharborsecurity.app.data.local.entity.PointsBalanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PointsDao {

    @Insert
    suspend fun insertTransaction(transaction: PointTransactionEntity)

    @Query("SELECT * FROM points_transactions ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentTransactions(limit: Int = 20): Flow<List<PointTransactionEntity>>

    @Query("SELECT COUNT(*) FROM points_transactions WHERE event_type = :eventType AND timestamp > :since")
    suspend fun countEventsSince(eventType: String, since: Long): Int

    @Query("SELECT * FROM points_balance WHERE user_id = 'local_user'")
    fun getBalance(): Flow<PointsBalanceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBalance(balance: PointsBalanceEntity)

    @Query("SELECT total_earned FROM points_balance WHERE user_id = 'local_user'")
    suspend fun getTotalEarnedSync(): Long?
}
