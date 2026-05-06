package com.safeharborsecurity.app.data.local.dao

import androidx.room.*
import com.safeharborsecurity.app.data.local.entity.NewsArticleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NewsArticleDao {

    @Query("SELECT * FROM news_articles ORDER BY pub_date DESC")
    fun getAll(): Flow<List<NewsArticleEntity>>

    @Query("SELECT * FROM news_articles ORDER BY pub_date DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<NewsArticleEntity>>

    @Query("SELECT * FROM news_articles WHERE source = :source ORDER BY pub_date DESC")
    fun getBySource(source: String): Flow<List<NewsArticleEntity>>

    @Query("SELECT COUNT(*) FROM news_articles WHERE is_read = 0")
    fun getUnreadCount(): Flow<Int>

    @Upsert
    suspend fun upsert(article: NewsArticleEntity)

    @Upsert
    suspend fun upsertAll(articles: List<NewsArticleEntity>)

    @Query("UPDATE news_articles SET is_read = 1 WHERE id = :id")
    suspend fun markAsRead(id: String)

    @Query("UPDATE news_articles SET is_read = 1")
    suspend fun markAllAsRead()

    @Query("DELETE FROM news_articles WHERE fetched_at < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("SELECT COUNT(*) FROM news_articles")
    suspend fun count(): Int

    @Query("DELETE FROM news_articles WHERE id IN (SELECT id FROM news_articles ORDER BY pub_date ASC LIMIT :count)")
    suspend fun deleteOldest(count: Int)

    @Query("SELECT * FROM news_articles")
    suspend fun getAllSync(): List<NewsArticleEntity>

    @Query("DELETE FROM news_articles WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    /** Clear All — wipes every saved article. Backs the home-screen
     *  "Clear All" button on the news section. The next NewsSyncWorker
     *  run repopulates from the RSS feeds. */
    @Query("DELETE FROM news_articles")
    suspend fun deleteAll()
}
