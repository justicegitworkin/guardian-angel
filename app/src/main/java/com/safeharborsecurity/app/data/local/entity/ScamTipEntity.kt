package com.safeharborsecurity.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scam_tips")
data class ScamTipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val title: String,
    val content: String,
    @androidx.room.ColumnInfo(name = "is_read") val isRead: Boolean = false
)
