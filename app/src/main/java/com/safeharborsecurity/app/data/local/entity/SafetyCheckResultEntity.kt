package com.safeharborsecurity.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "safety_check_results")
data class SafetyCheckResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "content_type") val contentType: String,  // "TEXT" | "URL" | "IMAGE" | "EMAIL"
    @ColumnInfo(name = "content_preview") val contentPreview: String,
    val verdict: String,        // "SAFE" | "SUSPICIOUS" | "DANGEROUS"
    val summary: String,
    @ColumnInfo(name = "detail_json") val detailJson: String,
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String? = null
)
