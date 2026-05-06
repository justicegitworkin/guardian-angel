package com.safeharborsecurity.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connected_services")
data class ConnectedServiceEntity(
    @PrimaryKey
    @ColumnInfo(name = "service_id") val serviceId: String,
    @ColumnInfo(name = "service_name") val serviceName: String,
    @ColumnInfo(name = "is_connected") val isConnected: Boolean = false,
    @ColumnInfo(name = "last_sync_time") val lastSyncTime: Long = 0,
    @ColumnInfo(name = "auth_token_encrypted") val authTokenEncrypted: String = "",
    @ColumnInfo(name = "result_summary") val resultSummary: String = ""
)
