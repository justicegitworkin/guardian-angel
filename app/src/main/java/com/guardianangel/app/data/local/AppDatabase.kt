package com.guardianangel.app.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.guardianangel.app.data.local.dao.*
import com.guardianangel.app.data.local.entity.*

@Database(
    entities = [
        AlertEntity::class,
        MessageEntity::class,
        CallLogEntity::class,
        BlockedNumberEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun alertDao(): AlertDao
    abstract fun messageDao(): MessageDao
    abstract fun callLogDao(): CallLogDao
    abstract fun blockedNumberDao(): BlockedNumberDao

    companion object {
        const val DATABASE_NAME = "guardian_angel_db"
    }
}
