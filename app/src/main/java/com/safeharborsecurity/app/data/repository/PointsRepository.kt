package com.safeharborsecurity.app.data.repository

import com.safeharborsecurity.app.data.local.dao.PointsDao
import com.safeharborsecurity.app.data.local.entity.PointTransactionEntity
import com.safeharborsecurity.app.data.local.entity.PointsBalanceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PointsRepository @Inject constructor(
    private val pointsDao: PointsDao
) {
    fun getBalance(): Flow<PointsBalanceEntity?> = pointsDao.getBalance()

    fun getRecentTransactions(limit: Int = 20): Flow<List<PointTransactionEntity>> =
        pointsDao.getRecentTransactions(limit)

    suspend fun awardPoints(
        eventType: String,
        points: Int,
        description: String,
        maxPerDay: Int = Int.MAX_VALUE
    ): Boolean {
        val now = System.currentTimeMillis()
        val startOfDay = LocalDate.now()
            .atStartOfDay()
            .toEpochSecond(ZoneOffset.UTC) * 1000

        // Anti-gaming: check daily limit
        val countToday = pointsDao.countEventsSince(eventType, startOfDay)
        if (countToday >= maxPerDay) return false

        // Duplicate check: ignore same event within 60 seconds
        val recentCount = pointsDao.countEventsSince(eventType, now - 60_000)
        if (recentCount > 0 && maxPerDay == 1) return false

        pointsDao.insertTransaction(
            PointTransactionEntity(
                eventType = eventType,
                points = points,
                description = description,
                timestamp = now
            )
        )

        // Update balance
        val totalEarned = (pointsDao.getTotalEarnedSync() ?: 0L) + points
        val todayStr = LocalDate.now().toString()

        // Get existing balance for streak calculation
        val existingBalance = pointsDao.getBalance().firstOrNull()
        val yesterday = LocalDate.now().minusDays(1).toString()
        val currentStreak = when {
            existingBalance == null -> 1
            existingBalance.lastActiveDate == todayStr -> existingBalance.currentStreak
            existingBalance.lastActiveDate == yesterday -> existingBalance.currentStreak + 1
            else -> 1
        }
        val longestStreak = maxOf(currentStreak, existingBalance?.longestStreak ?: 0)

        pointsDao.upsertBalance(
            PointsBalanceEntity(
                totalEarned = totalEarned,
                currentBalance = totalEarned,
                currentStreak = currentStreak,
                longestStreak = longestStreak,
                lastActiveDate = todayStr
            )
        )
        return true
    }
}
