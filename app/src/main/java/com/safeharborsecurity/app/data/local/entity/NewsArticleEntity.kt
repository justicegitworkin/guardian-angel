package com.safeharborsecurity.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "news_articles")
data class NewsArticleEntity(
    @PrimaryKey val id: String, // MD5 of link
    val title: String,
    val summary: String,
    val link: String,
    val source: String, // AARP, FTC, FBI, BBB, Snopes
    @ColumnInfo(name = "pub_date") val pubDate: Long,
    @ColumnInfo(name = "is_read") val isRead: Boolean = false,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long = System.currentTimeMillis()
)
