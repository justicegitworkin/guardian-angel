package com.guardianangel.app.data.local.dao

import androidx.room.*
import com.guardianangel.app.data.local.entity.ScamRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScamRuleDao {

    /** Live list of HIGH/CRITICAL rules, newest first — drives the Home badge. */
    @Query("""
        SELECT * FROM scam_rules
        WHERE severity IN ('HIGH','CRITICAL')
        ORDER BY CASE severity WHEN 'CRITICAL' THEN 0 ELSE 1 END, createdAt DESC
    """)
    fun observeHighCriticalRules(): Flow<List<ScamRuleEntity>>

    /** All rules sorted by severity then recency — used in the "View threats" dialog. */
    @Query("""
        SELECT * FROM scam_rules
        ORDER BY CASE severity
            WHEN 'CRITICAL' THEN 0
            WHEN 'HIGH'     THEN 1
            WHEN 'MEDIUM'   THEN 2
            ELSE 3 END,
        createdAt DESC
    """)
    fun observeAllRules(): Flow<List<ScamRuleEntity>>

    /**
     * Top [limit] HIGH/CRITICAL rules created after [since] ms.
     * Used to inject intelligence context into Claude API prompts.
     */
    @Query("""
        SELECT * FROM scam_rules
        WHERE severity IN ('HIGH','CRITICAL') AND createdAt > :since
        ORDER BY CASE severity WHEN 'CRITICAL' THEN 0 ELSE 1 END, createdAt DESC
        LIMIT :limit
    """)
    suspend fun getRecentHighCritical(since: Long, limit: Int = 10): List<ScamRuleEntity>

    /** Upsert a batch of rules received from the server. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<ScamRuleEntity>)

    /** Prune rules older than [before] ms to keep the DB small. */
    @Query("DELETE FROM scam_rules WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long)
}
