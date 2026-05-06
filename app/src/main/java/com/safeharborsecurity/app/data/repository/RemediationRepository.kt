package com.safeharborsecurity.app.data.repository

import com.safeharborsecurity.app.data.local.dao.RemediationKnowledgeDao
import com.safeharborsecurity.app.data.local.entity.RemediationKnowledgeEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemediationRepository @Inject constructor(
    private val dao: RemediationKnowledgeDao
) {
    fun getAll(): Flow<List<RemediationKnowledgeEntity>> = dao.getAll()

    suspend fun getAllOnce(): List<RemediationKnowledgeEntity> = dao.getAllOnce()

    suspend fun findForPackage(packageName: String): RemediationKnowledgeEntity? =
        dao.findMatchingPackage(packageName)

    suspend fun upsert(entity: RemediationKnowledgeEntity): Long = dao.upsert(entity)

    suspend fun upsertAll(entities: List<RemediationKnowledgeEntity>) = dao.upsertAll(entities)
}
