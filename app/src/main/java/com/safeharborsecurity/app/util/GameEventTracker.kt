package com.safeharborsecurity.app.util

import com.safeharborsecurity.app.data.repository.PointsRepository
import javax.inject.Inject
import javax.inject.Singleton

enum class PointEventType(
    val points: Int,
    val displayName: String,
    val maxPerDay: Int = Int.MAX_VALUE
) {
    DAILY_APP_OPEN(50, "Daily check-in", 1),
    DAILY_STREAK_BONUS(25, "Streak bonus", 1),
    SCAM_AVOIDED(100, "Scam avoided", 10),
    SAFETY_CHECK_USED(30, "Safety check", 10),
    QR_SCANNED(20, "QR code scanned", 10),
    URL_CHECKED(20, "URL checked", 10),
    VOICEMAIL_SCANNED(30, "Voicemail scanned", 10),
    ROOM_SCANNED(50, "Room scanned", 5),
    PHOTO_CHECKED(20, "Photo checked", 10),
    NEWS_ARTICLE_READ(15, "News article read", 5),
    FAMILY_CONTACT_ADDED(200, "Family contact added", 1),
    SHARED_WITH_FAMILY(25, "Shared with family", 10),
    PERMISSION_GRANTED(50, "Permission set up", 8),
    FIRST_WEEK_COMPLETE(500, "First week complete!", 1),
    FIRST_MONTH_COMPLETE(2000, "First month complete!", 1),
    SCAMS_BLOCKED_10(1000, "10 scams blocked!", 1),
    SCAMS_BLOCKED_50(5000, "50 scams blocked!", 1),
    SAFETY_STREAK_30_DAYS(3000, "30-day safety streak!", 1),
    COACHING_COMPLETED(100, "Coaching tip reviewed", 10),
    TIP_READ(10, "Safety tip read", 1)
}

@Singleton
class GameEventTracker @Inject constructor(
    private val pointsRepository: PointsRepository
) {
    suspend fun track(event: PointEventType): Boolean {
        return pointsRepository.awardPoints(
            eventType = event.name,
            points = event.points,
            description = event.displayName,
            maxPerDay = event.maxPerDay
        )
    }
}
