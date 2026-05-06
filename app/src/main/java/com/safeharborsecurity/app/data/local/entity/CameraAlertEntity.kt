package com.safeharborsecurity.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "camera_alerts")
data class CameraAlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,           // "Arlo", "Reolink", "Ring", "Nest", "Wyze", "Hikvision", "Dahua"
    val sourcePackage: String,    // original notification package name
    val title: String,
    val message: String,
    val category: String,         // "MOTION" | "PERSON" | "SOUND" | "DOORBELL" | "OFFLINE" | "OTHER"
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val isActionable: Boolean = false
)
