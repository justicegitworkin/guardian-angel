package com.guardianangel.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val callerNumber: String,
    val durationSeconds: Long = 0,
    val riskLevel: String = "UNKNOWN",   // "SAFE" | "SUSPICIOUS" | "SCAM"
    val summary: String = "",
    val transcript: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isBlocked: Boolean = false,
    val wasScreened: Boolean = false
)
