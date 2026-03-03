package com.guardianangel.app.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.guardianangel.app.BuildConfig
import com.guardianangel.app.data.remote.ClaudeApiService
import com.guardianangel.app.data.remote.ScamIntelligenceApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ScamIntelRetrofit

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
                    )
                }
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.anthropic.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideClaudeApiService(retrofit: Retrofit): ClaudeApiService {
        return retrofit.create(ClaudeApiService::class.java)
    }

    /**
     * Separate Retrofit instance for the dynamic intelligence server URL.
     * The base URL is a placeholder; all calls use @Url to override it.
     */
    @Provides
    @Singleton
    @ScamIntelRetrofit
    fun provideScamIntelRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://placeholder.invalid/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideScamIntelligenceApiService(@ScamIntelRetrofit retrofit: Retrofit): ScamIntelligenceApiService {
        return retrofit.create(ScamIntelligenceApiService::class.java)
    }
}
