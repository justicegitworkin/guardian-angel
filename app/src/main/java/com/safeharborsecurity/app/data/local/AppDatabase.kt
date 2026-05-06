package com.safeharborsecurity.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.safeharborsecurity.app.data.local.dao.*
import com.safeharborsecurity.app.data.local.entity.*

@Database(
    entities = [
        AlertEntity::class,
        MessageEntity::class,
        CallLogEntity::class,
        BlockedNumberEntity::class,
        RemediationKnowledgeEntity::class,
        SafetyCheckResultEntity::class,
        CheckInEntity::class,
        PanicEventEntity::class,
        ScamTipEntity::class,
        EmailAccountEntity::class,
        ConnectedServiceEntity::class,
        NewsArticleEntity::class,
        PointTransactionEntity::class,
        PointsBalanceEntity::class,
        CameraAlertEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun alertDao(): AlertDao
    abstract fun messageDao(): MessageDao
    abstract fun callLogDao(): CallLogDao
    abstract fun blockedNumberDao(): BlockedNumberDao
    abstract fun remediationKnowledgeDao(): RemediationKnowledgeDao
    abstract fun safetyCheckResultDao(): SafetyCheckResultDao
    abstract fun checkInDao(): CheckInDao
    abstract fun panicEventDao(): PanicEventDao
    abstract fun scamTipDao(): ScamTipDao
    abstract fun emailAccountDao(): EmailAccountDao
    abstract fun connectedServiceDao(): ConnectedServiceDao
    abstract fun newsArticleDao(): NewsArticleDao
    abstract fun pointsDao(): PointsDao
    abstract fun cameraAlertDao(): CameraAlertDao

    companion object {
        const val DATABASE_NAME = "safe_harbor_db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS remediation_knowledge (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        package_name_pattern TEXT NOT NULL,
                        app_display_name TEXT NOT NULL,
                        android_min_version INTEGER NOT NULL DEFAULT 26,
                        android_max_version INTEGER NOT NULL DEFAULT ${Int.MAX_VALUE},
                        can_toggle_directly INTEGER NOT NULL DEFAULT 0,
                        settings_intent_action TEXT,
                        settings_intent_package TEXT,
                        how_to_instructions TEXT NOT NULL,
                        learn_more_url TEXT,
                        last_verified INTEGER NOT NULL DEFAULT 0,
                        source_version INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())

                val now = System.currentTimeMillis()

                db.execSQL("""
                    INSERT INTO remediation_knowledge
                    (package_name_pattern, app_display_name, android_min_version, android_max_version,
                     can_toggle_directly, settings_intent_action, settings_intent_package,
                     how_to_instructions, learn_more_url, last_verified, source_version)
                    VALUES
                    ('com.google.android.adservices', 'Android Ad Services', 33, ${Int.MAX_VALUE},
                     1, 'android.adservices.ui.SETTINGS', 'com.google.android.adservices.api',
                     'Step 1: Tap the button below to open Ad Services settings.
Step 2: Turn off "Ad Topics" so apps cannot learn what you like.
Step 3: Turn off "App-suggested ads" so apps cannot show you personalised ads.
Step 4: Turn off "Ad measurement" so advertisers cannot track you.
Step 5: Come back here and we will check it worked.',
                     'https://support.google.com/android/answer/13531498', $now, 1)
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO remediation_knowledge
                    (package_name_pattern, app_display_name, android_min_version, android_max_version,
                     can_toggle_directly, settings_intent_action, settings_intent_package,
                     how_to_instructions, learn_more_url, last_verified, source_version)
                    VALUES
                    ('com.google.android.gms', 'Google Play Services (Ads)', 26, ${Int.MAX_VALUE},
                     1, 'com.google.android.gms.settings.ADS_PRIVACY_SETTINGS', 'com.google.android.gms',
                     'Step 1: Tap the button below to open Google ad settings.
Step 2: Look for "Opt out of Ads Personalisation" and turn it ON.
Step 3: This stops Google from using your activity to show you targeted ads.
Step 4: Come back here when you are done.',
                     'https://support.google.com/ads/answer/2662922', $now, 1)
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO remediation_knowledge
                    (package_name_pattern, app_display_name, android_min_version, android_max_version,
                     can_toggle_directly, settings_intent_action, settings_intent_package,
                     how_to_instructions, learn_more_url, last_verified, source_version)
                    VALUES
                    ('com.facebook.ads', 'Facebook Audience Network', 26, ${Int.MAX_VALUE},
                     0, 'android.settings.APPLICATION_DETAILS_SETTINGS', 'com.facebook.katana',
                     'Step 1: Open the Facebook app on your phone.
Step 2: Tap the menu (three lines) at the top right.
Step 3: Go to Settings & Privacy, then Settings.
Step 4: Scroll down to "Ad Preferences" and tap it.
Step 5: Turn off "Ads based on your activity" and "Ads based on data from partners".
Step 6: Come back here when you are done.',
                     'https://www.facebook.com/help/568137493302217', $now, 1)
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO remediation_knowledge
                    (package_name_pattern, app_display_name, android_min_version, android_max_version,
                     can_toggle_directly, settings_intent_action, settings_intent_package,
                     how_to_instructions, learn_more_url, last_verified, source_version)
                    VALUES
                    ('com.zhiliaoapp.musically', 'TikTok', 26, ${Int.MAX_VALUE},
                     0, 'android.settings.APPLICATION_DETAILS_SETTINGS', 'com.zhiliaoapp.musically',
                     'Step 1: Open TikTok on your phone.
Step 2: Tap "Profile" at the bottom right.
Step 3: Tap the three lines at the top right, then "Settings and privacy".
Step 4: Tap "Privacy", then scroll down to "Personalisation and data".
Step 5: Turn off "Personalised ads".
Step 6: Go back and tap "Off-TikTok activity" and clear your data.
Step 7: Come back here when you are done.',
                     'https://support.tiktok.com/en/account-and-privacy', $now, 1)
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO remediation_knowledge
                    (package_name_pattern, app_display_name, android_min_version, android_max_version,
                     can_toggle_directly, settings_intent_action, settings_intent_package,
                     how_to_instructions, learn_more_url, last_verified, source_version)
                    VALUES
                    ('com.ss.android.ugc.trill', 'TikTok (Lite)', 26, ${Int.MAX_VALUE},
                     0, 'android.settings.APPLICATION_DETAILS_SETTINGS', 'com.ss.android.ugc.trill',
                     'Step 1: Open TikTok on your phone.
Step 2: Tap "Profile" at the bottom right.
Step 3: Tap the three lines at the top right, then "Settings and privacy".
Step 4: Tap "Privacy", then "Personalisation and data".
Step 5: Turn off "Personalised ads".
Step 6: Come back here when you are done.',
                     'https://support.tiktok.com/en/account-and-privacy', $now, 1)
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO remediation_knowledge
                    (package_name_pattern, app_display_name, android_min_version, android_max_version,
                     can_toggle_directly, settings_intent_action, settings_intent_package,
                     how_to_instructions, learn_more_url, last_verified, source_version)
                    VALUES
                    ('com.spotify.music', 'Spotify (Ad Personalisation)', 26, ${Int.MAX_VALUE},
                     0, 'android.settings.APPLICATION_DETAILS_SETTINGS', 'com.spotify.music',
                     'Step 1: Open Spotify on your phone.
Step 2: Tap the gear icon (Settings) at the top right.
Step 3: Scroll down to "Privacy and social".
Step 4: Turn off "Tailored ads" to stop personalised advertising.
Step 5: Also turn off "Facebook data" if shown.
Step 6: Come back here when you are done.',
                     'https://support.spotify.com/us/article/privacy-settings/', $now, 1)
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO remediation_knowledge
                    (package_name_pattern, app_display_name, android_min_version, android_max_version,
                     can_toggle_directly, settings_intent_action, settings_intent_package,
                     how_to_instructions, learn_more_url, last_verified, source_version)
                    VALUES
                    ('com.amazon.mShop.android.shopping', 'Amazon (Ad Tracking)', 26, ${Int.MAX_VALUE},
                     0, 'android.settings.APPLICATION_DETAILS_SETTINGS', 'com.amazon.mShop.android.shopping',
                     'Step 1: Open the Amazon app.
Step 2: Tap the menu (three lines) and go to Settings.
Step 3: Look for "Advertising Preferences".
Step 4: Turn off "Interest-Based Ads" to stop personalised advertising.
Step 5: Come back here when you are done.',
                     'https://www.amazon.com/adprefs', $now, 1)
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS safety_check_results (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        content_type TEXT NOT NULL,
                        content_preview TEXT NOT NULL,
                        verdict TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        detail_json TEXT NOT NULL,
                        thumbnail_path TEXT
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS check_in_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        check_type TEXT NOT NULL,
                        date TEXT NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS panic_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS scam_tips (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        is_read INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS email_accounts (
                        email_address TEXT NOT NULL PRIMARY KEY,
                        display_name TEXT NOT NULL,
                        provider TEXT NOT NULL,
                        is_active INTEGER NOT NULL DEFAULT 1,
                        last_sync_time INTEGER NOT NULL DEFAULT 0,
                        auth_token_encrypted TEXT NOT NULL DEFAULT '',
                        total_scanned INTEGER NOT NULL DEFAULT 0,
                        threats_found INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS connected_services (
                        service_id TEXT NOT NULL PRIMARY KEY,
                        service_name TEXT NOT NULL,
                        is_connected INTEGER NOT NULL DEFAULT 0,
                        last_sync_time INTEGER NOT NULL DEFAULT 0,
                        auth_token_encrypted TEXT NOT NULL DEFAULT '',
                        result_summary TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS news_articles (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        link TEXT NOT NULL,
                        source TEXT NOT NULL,
                        pub_date INTEGER NOT NULL,
                        is_read INTEGER NOT NULL DEFAULT 0,
                        fetched_at INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS camera_alerts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        source TEXT NOT NULL,
                        sourcePackage TEXT NOT NULL,
                        title TEXT NOT NULL,
                        message TEXT NOT NULL,
                        category TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        isRead INTEGER NOT NULL DEFAULT 0,
                        isActionable INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS points_transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        event_type TEXT NOT NULL,
                        points INTEGER NOT NULL,
                        description TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        synced INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS points_balance (
                        user_id TEXT NOT NULL PRIMARY KEY,
                        total_earned INTEGER NOT NULL DEFAULT 0,
                        current_balance INTEGER NOT NULL DEFAULT 0,
                        current_streak INTEGER NOT NULL DEFAULT 0,
                        longest_streak INTEGER NOT NULL DEFAULT 0,
                        last_active_date TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())
            }
        }
    }
}
