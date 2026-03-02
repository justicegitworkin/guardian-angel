package com.guardianangel.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores the call screening result.
 * The call transcript is NEVER stored — only the risk summary from analysis.
 */
@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val callerNumber: String,
    val durationSeconds: Long = 0,
    val riskLevel: String = "UNKNOWN",  // "SAFE" | "SUSPICIOUS" | "SCAM"
    val summary: String = "",
    // transcript field removed in v2 — call audio text is never persisted
    val timestamp: Long = System.currentTimeMillis(),
    val isBlocked: Boolean = false,
    val wasScreened: Boolean = false
)
