package com.safeharborsecurity.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "points_transactions")
data class PointTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "event_type") val eventType: String,
    val points: Int,
    val description: String,
    val timestamp: Long,
    val synced: Boolean = false
)
