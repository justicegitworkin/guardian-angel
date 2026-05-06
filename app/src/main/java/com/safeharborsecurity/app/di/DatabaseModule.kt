package com.safeharborsecurity.app.di

import android.content.Context
import androidx.room.Room
import com.safeharborsecurity.app.data.local.AppDatabase
import com.safeharborsecurity.app.data.local.dao.*
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
        ).addMigrations(
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7
        )
         .fallbackToDestructiveMigration()
         .build()
    }

    @Provides fun provideAlertDao(db: AppDatabase): AlertDao = db.alertDao()
    @Provides fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()
    @Provides fun provideCallLogDao(db: AppDatabase): CallLogDao = db.callLogDao()
    @Provides fun provideBlockedNumberDao(db: AppDatabase): BlockedNumberDao = db.blockedNumberDao()
    @Provides fun provideRemediationKnowledgeDao(db: AppDatabase): RemediationKnowledgeDao = db.remediationKnowledgeDao()
    @Provides fun provideSafetyCheckResultDao(db: AppDatabase): SafetyCheckResultDao = db.safetyCheckResultDao()
    @Provides fun provideCheckInDao(db: AppDatabase): CheckInDao = db.checkInDao()
    @Provides fun providePanicEventDao(db: AppDatabase): PanicEventDao = db.panicEventDao()
    @Provides fun provideScamTipDao(db: AppDatabase): ScamTipDao = db.scamTipDao()
    @Provides fun provideEmailAccountDao(db: AppDatabase): EmailAccountDao = db.emailAccountDao()
    @Provides fun provideConnectedServiceDao(db: AppDatabase): ConnectedServiceDao = db.connectedServiceDao()
    @Provides fun provideNewsArticleDao(db: AppDatabase): NewsArticleDao = db.newsArticleDao()
    @Provides fun providePointsDao(db: AppDatabase): PointsDao = db.pointsDao()
    @Provides fun provideCameraAlertDao(db: AppDatabase): CameraAlertDao = db.cameraAlertDao()
}
