package com.safeharborsecurity.app.data.local.dao

import androidx.room.*
import com.safeharborsecurity.app.data.local.entity.ConnectedServiceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectedServiceDao {
    @Query("SELECT * FROM connected_services ORDER BY service_name")
    fun getAll(): Flow<List<ConnectedServiceEntity>>

    @Query("SELECT * FROM connected_services WHERE service_id = :id")
    suspend fun getById(id: String): ConnectedServiceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(service: ConnectedServiceEntity)

    @Query("DELETE FROM connected_services WHERE service_id = :id")
    suspend fun delete(id: String)
}
