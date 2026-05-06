package com.safeharborsecurity.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_numbers")
data class BlockedNumberEntity(
    @PrimaryKey val number: String,
    val reason: String = "",
    val blockedAt: Long = System.currentTimeMillis()
)
