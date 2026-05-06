package com.safeharborsecurity.app.data.local.dao

import androidx.room.*
import com.safeharborsecurity.app.data.local.entity.EmailAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmailAccountDao {
    @Query("SELECT * FROM email_accounts ORDER BY last_sync_time DESC")
    fun getAll(): Flow<List<EmailAccountEntity>>

    @Query("SELECT * FROM email_accounts WHERE is_active = 1")
    suspend fun getActive(): List<EmailAccountEntity>

    @Query("SELECT COUNT(*) FROM email_accounts")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: EmailAccountEntity)

    @Query("DELETE FROM email_accounts WHERE email_address = :email")
    suspend fun delete(email: String)

    @Query("UPDATE email_accounts SET last_sync_time = :time, total_scanned = total_scanned + :scanned, threats_found = threats_found + :threats WHERE email_address = :email")
    suspend fun updateSyncStats(email: String, time: Long, scanned: Int, threats: Int)
}
