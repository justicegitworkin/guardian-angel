package com.safeharborsecurity.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "check_in_events")
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "check_type") val checkType: String,  // "MANUAL" | "PASSIVE"
    val date: String  // "2026-03-08" format
)
