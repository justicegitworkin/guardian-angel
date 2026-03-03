package com.guardianangel.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.guardianangel.app.data.local.dao.*
import com.guardianangel.app.data.local.entity.*
import java.util.concurrent.TimeUnit

/**
 * v1 → v2: removed raw message content (alerts.content) and call
 * transcript (call_logs.transcript) to ensure user content is never
 * written to disk.  Both tables are recreated without those columns.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // ── alerts: drop 'content' column ─────────────────────────────
        database.execSQL("""
            CREATE TABLE alerts_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                type TEXT NOT NULL,
                sender TEXT NOT NULL,
                riskLevel TEXT NOT NULL,
                confidence REAL NOT NULL,
                reason TEXT NOT NULL,
                action TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                isRead INTEGER NOT NULL DEFAULT 0,
                isBlocked INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        database.execSQL("""
            INSERT INTO alerts_new (id, type, sender, riskLevel, confidence, reason, action, timestamp, isRead, isBlocked)
            SELECT id, type, sender, riskLevel, confidence, reason, action, timestamp, isRead, isBlocked
            FROM alerts
        """.trimIndent())
        database.execSQL("DROP TABLE alerts")
        database.execSQL("ALTER TABLE alerts_new RENAME TO alerts")

        // ── call_logs: drop 'transcript' column ───────────────────────
        database.execSQL("""
            CREATE TABLE call_logs_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                callerNumber TEXT NOT NULL,
                durationSeconds INTEGER NOT NULL DEFAULT 0,
                riskLevel TEXT NOT NULL DEFAULT 'UNKNOWN',
                summary TEXT NOT NULL DEFAULT '',
                timestamp INTEGER NOT NULL,
                isBlocked INTEGER NOT NULL DEFAULT 0,
                wasScreened INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        database.execSQL("""
            INSERT INTO call_logs_new (id, callerNumber, durationSeconds, riskLevel, summary, timestamp, isBlocked, wasScreened)
            SELECT id, callerNumber, durationSeconds, riskLevel, summary, timestamp, isBlocked, wasScreened
            FROM call_logs
        """.trimIndent())
        database.execSQL("DROP TABLE call_logs")
        database.execSQL("ALTER TABLE call_logs_new RENAME TO call_logs")
    }
}

/** v2 → v3: add scam_rules table for real-time intelligence sync. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS scam_rules (
                id                    INTEGER PRIMARY KEY NOT NULL DEFAULT 0,
                scamType              TEXT    NOT NULL,
                keyPhrases            TEXT    NOT NULL DEFAULT '[]',
                urgencyIndicators     TEXT    NOT NULL DEFAULT '[]',
                impersonationTargets  TEXT    NOT NULL DEFAULT '[]',
                plainEnglishWarning   TEXT    NOT NULL,
                severity              TEXT    NOT NULL DEFAULT 'MEDIUM',
                createdAt             INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }
}

@Database(
    entities = [
        AlertEntity::class,
        MessageEntity::class,
        CallLogEntity::class,
        BlockedNumberEntity::class,
        ScamRuleEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun alertDao(): AlertDao
    abstract fun messageDao(): MessageDao
    abstract fun callLogDao(): CallLogDao
    abstract fun blockedNumberDao(): BlockedNumberDao
    abstract fun scamRuleDao(): ScamRuleDao

    companion object {
        const val DATABASE_NAME = "guardian_angel_db"
    }
}
