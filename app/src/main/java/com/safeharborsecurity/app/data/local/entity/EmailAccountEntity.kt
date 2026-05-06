package com.safeharborsecurity.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "email_accounts")
data class EmailAccountEntity(
    @PrimaryKey
    @ColumnInfo(name = "email_address") val emailAddress: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "provider") val provider: String, // GMAIL, OUTLOOK, YAHOO, IMAP
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "last_sync_time") val lastSyncTime: Long = 0,
    @ColumnInfo(name = "auth_token_encrypted") val authTokenEncrypted: String = "",
    @ColumnInfo(name = "total_scanned") val totalScanned: Int = 0,
    @ColumnInfo(name = "threats_found") val threatsFound: Int = 0
)
