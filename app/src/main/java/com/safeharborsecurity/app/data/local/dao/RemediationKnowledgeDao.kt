package com.safeharborsecurity.app.data.local.dao

import androidx.room.*
import com.safeharborsecurity.app.data.local.entity.RemediationKnowledgeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RemediationKnowledgeDao {

    @Query("SELECT * FROM remediation_knowledge ORDER BY app_display_name ASC")
    fun getAll(): Flow<List<RemediationKnowledgeEntity>>

    @Query("SELECT * FROM remediation_knowledge ORDER BY app_display_name ASC")
    suspend fun getAllOnce(): List<RemediationKnowledgeEntity>

    @Query("SELECT * FROM remediation_knowledge WHERE package_name_pattern = :pattern LIMIT 1")
    suspend fun findByPattern(pattern: String): RemediationKnowledgeEntity?

    @Query("""
        SELECT * FROM remediation_knowledge
        WHERE :packageName LIKE '%' || package_name_pattern || '%'
           OR package_name_pattern LIKE '%' || :packageName || '%'
        LIMIT 1
    """)
    suspend fun findMatchingPackage(packageName: String): RemediationKnowledgeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RemediationKnowledgeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<RemediationKnowledgeEntity>)

    @Query("DELETE FROM remediation_knowledge WHERE id = :id")
    suspend fun deleteById(id: Long)
}
