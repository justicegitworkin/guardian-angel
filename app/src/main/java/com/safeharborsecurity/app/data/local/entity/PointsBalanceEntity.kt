package com.safeharborsecurity.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "points_balance")
data class PointsBalanceEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: String = "local_user",
    @ColumnInfo(name = "total_earned") val totalEarned: Long = 0,
    @ColumnInfo(name = "current_balance") val currentBalance: Long = 0,
    @ColumnInfo(name = "current_streak") val currentStreak: Int = 0,
    @ColumnInfo(name = "longest_streak") val longestStreak: Int = 0,
    @ColumnInfo(name = "last_active_date") val lastActiveDate: String = ""
)
