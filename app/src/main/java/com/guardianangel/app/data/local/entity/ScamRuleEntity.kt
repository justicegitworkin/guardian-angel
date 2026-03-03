package com.guardianangel.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Locally cached scam detection rule downloaded from the Guardian Angel
 * intelligence server (sourced from FBI / IC3 / FTC / CISA alerts).
 *
 * Array fields (keyPhrases, urgencyIndicators, impersonationTargets) are
 * stored as JSON strings to avoid a separate junction table.
 */
@Entity(tableName = "scam_rules")
data class ScamRuleEntity(
    @PrimaryKey val id: Long = 0,
    val scamType: String,
    /** JSON array of indicator phrases, e.g. ["your Medicare", "call immediately"] */
    val keyPhrases: String = "[]",
    /** JSON array of urgency words/phrases */
    val urgencyIndicators: String = "[]",
    /** JSON array of organisations being impersonated */
    val impersonationTargets: String = "[]",
    /** One plain-English sentence shown to the senior */
    val plainEnglishWarning: String,
    /** LOW | MEDIUM | HIGH | CRITICAL */
    val severity: String = "MEDIUM",
    val createdAt: Long = System.currentTimeMillis()
)
