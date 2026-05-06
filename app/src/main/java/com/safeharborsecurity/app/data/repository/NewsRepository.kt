package com.safeharborsecurity.app.data.repository

import android.util.Log
import com.safeharborsecurity.app.data.local.dao.NewsArticleDao
import com.safeharborsecurity.app.data.local.entity.NewsArticleEntity
import com.safeharborsecurity.app.util.RssFeedParser
import com.safeharborsecurity.app.util.ScamArticleFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class NewsSource(
    val name: String,
    val url: String,
    val color: Long // ARGB color
)

@Singleton
class NewsRepository @Inject constructor(
    private val newsArticleDao: NewsArticleDao
) {
    companion object {
        private const val TAG = "NewsRepository"
        private const val MAX_ARTICLES = 100
        private const val MAX_AGE_DAYS = 30L

        // RSS sources, in approximate "richest scam content first" order so a
        // partial sync still produces useful articles. We deliberately include
        // multiple FTC and FBI feeds — the elder-specific ones publish rarely
        // (sometimes weeks between posts), so we backfill from broader feeds
        // and let ScamArticleFilter pick the relevant articles.
        val SOURCES = listOf(
            // AARP — most reliable, frequent, elder-focused
            NewsSource("AARP", "https://www.aarp.org/money/scams-fraud/rss.xml", 0xFFE31837),
            // FTC consumer alerts — official scam alerts, frequent
            NewsSource("FTC", "https://consumer.ftc.gov/blog/rss", 0xFF1A3A6B),
            // FTC business alerts — sometimes carry scam-of-the-week content
            NewsSource("FTC", "https://www.ftc.gov/feeds/press-release-consumer-protection.xml", 0xFF1A3A6B),
            // BBB scam alerts — frequent, consumer-focused
            NewsSource("BBB", "https://www.bbb.org/all/rss/news-releases", 0xFF0066CC),
            // FBI IC3 (Internet Crime Complaint Center) PSAs — broader than elder-only feed
            NewsSource("FBI", "https://www.ic3.gov/Home/Rss", 0xFF003366),
            // FBI national press feed — backfill when IC3 quiet
            NewsSource("FBI", "https://www.fbi.gov/feeds/pressrel/rss.xml", 0xFF003366),
            // Snopes scams category — fact-checks of viral scam stories
            NewsSource("Snopes", "https://www.snopes.com/category/facts/scams/feed/", 0xFF555555),
            // Krebs on Security — heavy scam/fraud reporting, weekly cadence
            NewsSource("Krebs", "https://krebsonsecurity.com/feed/", 0xFF8B4513),
            // The Hacker News — frequent malware/phishing coverage
            NewsSource("Hacker News", "https://feeds.feedburner.com/TheHackersNews", 0xFF333333)
        )
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "SafeCompanion/1.0 (Android; RSS Reader)")
                .build()
            chain.proceed(request)
        }
        .build()

    fun getRecentArticles(limit: Int = 5): Flow<List<NewsArticleEntity>> =
        newsArticleDao.getRecent(limit)

    fun getAllArticles(): Flow<List<NewsArticleEntity>> =
        newsArticleDao.getAll()

    fun getArticlesBySource(source: String): Flow<List<NewsArticleEntity>> =
        newsArticleDao.getBySource(source)

    fun getUnreadCount(): Flow<Int> =
        newsArticleDao.getUnreadCount()

    suspend fun markAsRead(id: String) =
        newsArticleDao.markAsRead(id)

    suspend fun markAllAsRead() =
        newsArticleDao.markAllAsRead()

    /** Wipes every saved news article. The home-screen Clear All flow uses
     *  this; the next NewsSyncWorker run will repopulate from RSS. */
    suspend fun clearAll() = newsArticleDao.deleteAll()

    suspend fun syncAllFeeds(): Int = withContext(Dispatchers.IO) {
        var totalNew = 0
        var totalRaw = 0
        for (source in SOURCES) {
            try {
                val (raw, kept) = fetchFeedWithStats(source)
                totalRaw += raw
                if (kept.isNotEmpty()) {
                    newsArticleDao.upsertAll(kept)
                    totalNew += kept.size
                }
                Log.d(
                    TAG,
                    "Sync ${source.name}: raw=$raw kept=${kept.size} url=${source.url}"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync ${source.name} (${source.url}): ${e.message}")
            }
        }
        Log.d(TAG, "Sync complete: $totalRaw raw articles → $totalNew kept across ${SOURCES.size} sources")

        // Re-filter existing articles: remove any that don't pass the scam filter
        try {
            val allExisting = newsArticleDao.getAllSync()
            val nonScamIds = allExisting
                .filter { !ScamArticleFilter.isScamRelevant(it.title, it.summary) }
                .map { it.id }
            if (nonScamIds.isNotEmpty()) {
                newsArticleDao.deleteByIds(nonScamIds)
                Log.d(TAG, "Removed ${nonScamIds.size} non-scam articles from database")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean non-scam articles: ${e.message}")
        }

        // Cleanup: delete articles older than 30 days
        val cutoff = System.currentTimeMillis() - (MAX_AGE_DAYS * 24 * 60 * 60 * 1000)
        newsArticleDao.deleteOlderThan(cutoff)

        // Keep max 100 articles
        val count = newsArticleDao.count()
        if (count > MAX_ARTICLES) {
            newsArticleDao.deleteOldest(count - MAX_ARTICLES)
        }

        totalNew
    }

    private fun fetchFeed(source: NewsSource): List<NewsArticleEntity> =
        fetchFeedWithStats(source).second

    /**
     * Returns (raw_count, filtered_articles). Raw count is the number of
     * <item> entries the feed returned before scam-relevance filtering;
     * useful for telling "feed is dead" apart from "filter rejected
     * everything."
     */
    private fun fetchFeedWithStats(source: NewsSource): Pair<Int, List<NewsArticleEntity>> {
        val request = Request.Builder().url(source.url).build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.w(TAG, "${source.name} returned HTTP ${response.code} for ${source.url}")
            return 0 to emptyList()
        }
        val xml = response.body?.string() ?: return 0 to emptyList()
        val rawCount = Regex("<(item|entry)[\\s>]", RegexOption.IGNORE_CASE)
            .findAll(xml).count()
        val parsed = RssFeedParser.parse(xml, source.name)
        val mapped = parsed.map { article ->
            NewsArticleEntity(
                id = article.id,
                title = article.title,
                summary = article.summary,
                link = article.link,
                source = article.source,
                pubDate = article.pubDate
            )
        }
        return rawCount to mapped
    }

    fun getSourceColor(sourceName: String): Long {
        return SOURCES.find { it.name == sourceName }?.color ?: 0xFF555555
    }
}
