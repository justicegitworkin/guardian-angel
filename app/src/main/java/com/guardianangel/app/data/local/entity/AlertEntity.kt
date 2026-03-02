package com.guardianangel.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores the AI analysis result for an SMS or call alert.
 * The original message body is NEVER stored here — only the risk assessment.
 */
@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,           // "SMS" | "CALL"
    val sender: String,
    // content field removed in v2 — raw message text is never persisted
    val riskLevel: String,      // "SAFE" | "WARNING" | "SCAM"
    val confidence: Float,
    val reason: String,
    val action: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val isBlocked: Boolean = false
)
