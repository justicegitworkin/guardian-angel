package com.guardianangel.app.di

import android.content.Context
import androidx.room.Room
import com.guardianangel.app.data.local.AppDatabase
import com.guardianangel.app.data.local.MIGRATION_1_2
import com.guardianangel.app.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
        .addMigrations(MIGRATION_1_2)
        .build()
    }

    @Provides
    fun provideAlertDao(db: AppDatabase): AlertDao = db.alertDao()

    @Provides
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideCallLogDao(db: AppDatabase): CallLogDao = db.callLogDao()

    @Provides
    fun provideBlockedNumberDao(db: AppDatabase): BlockedNumberDao = db.blockedNumberDao()
}
