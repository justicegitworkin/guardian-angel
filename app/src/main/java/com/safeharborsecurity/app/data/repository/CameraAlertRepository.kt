package com.safeharborsecurity.app.data.repository

import com.safeharborsecurity.app.data.local.dao.CameraAlertDao
import com.safeharborsecurity.app.data.local.entity.CameraAlertEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraAlertRepository @Inject constructor(
    private val dao: CameraAlertDao
) {
    fun all(): Flow<List<CameraAlertEntity>> = dao.getAllAlerts()
    fun byCategory(category: String): Flow<List<CameraAlertEntity>> = dao.getAlertsByCategory(category)
    fun unreadCount(): Flow<Int> = dao.unreadCount()

    suspend fun add(alert: CameraAlertEntity): Long = dao.insert(alert)
    suspend fun markRead(id: Long) = dao.markRead(id)
    suspend fun markAllRead() = dao.markAllRead()
    suspend fun pruneOlderThan(timestampMs: Long) = dao.pruneOlderThan(timestampMs)
}
