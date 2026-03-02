package com.guardianangel.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,           // "SMS" | "CALL"
    val sender: String,
    val content: String,
    val riskLevel: String,      // "SAFE" | "WARNING" | "SCAM"
    val confidence: Float,
    val reason: String,
    val action: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val isBlocked: Boolean = false
)
