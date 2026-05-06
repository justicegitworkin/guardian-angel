package com.safeharborsecurity.app.util

import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale

data class RssArticle(
    val title: String,
    val link: String,
    val summary: String,
    val pubDate: Long,
    val source: String,
    val id: String // MD5 of link
)

object RssFeedParser {

    private const val TAG = "RssFeedParser"

    fun parse(xml: String, source: String): List<RssArticle> {
        val articles = mutableListOf<RssArticle>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var insideItem = false
            var title = ""
            var link = ""
            var description = ""
            var pubDate = ""
            var currentTag = ""

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name?.lowercase() ?: ""
                        if (currentTag == "item" || currentTag == "entry") {
                            insideItem = true
                            title = ""
                            link = ""
                            description = ""
                            pubDate = ""
                        }
                        // Atom feeds use <link href="..."/>
                        if (insideItem && currentTag == "link" && parser.getAttributeValue(null, "href") != null) {
                            link = parser.getAttributeValue(null, "href") ?: ""
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (insideItem) {
                            val text = parser.text?.trim() ?: ""
                            when (currentTag) {
                                "title" -> title = text
                                "link" -> if (link.isBlank()) link = text
                                // RSS uses <description>, sometimes <content:encoded>;
                                // Atom uses <summary> and <content>. We accept any of
                                // them and prefer the longer text (content tends to
                                // hold the full body, description the teaser).
                                "description", "summary", "content", "content:encoded" -> {
                                    if (text.length > description.length) description = text
                                }
                                "pubdate", "published", "updated", "dc:date" -> pubDate = text
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val endTag = parser.name?.lowercase() ?: ""
                        if (endTag == "item" || endTag == "entry") {
                            insideItem = false
                            if (title.isNotBlank() && link.isNotBlank()) {
                                articles.add(
                                    RssArticle(
                                        title = cleanHtml(title),
                                        link = link.trim(),
                                        summary = cleanHtml(description).take(300),
                                        pubDate = parseDate(pubDate),
                                        source = source,
                                        id = md5(link.trim())
                                    )
                                )
                            }
                        }
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse RSS from $source: ${e.message}")
        }
        return articles.filter { ScamArticleFilter.isScamRelevant(it.title, it.summary) }
    }

    private fun cleanHtml(html: String): String {
        return html
            .replace(Regex("<[^>]*>"), "") // strip HTML tags
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun parseDate(dateStr: String): Long {
        if (dateStr.isBlank()) return System.currentTimeMillis()
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",      // RFC 822
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "yyyy-MM-dd'T'HH:mm:ssZ",            // ISO 8601
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US)
                return sdf.parse(dateStr)?.time ?: continue
            } catch (_: Exception) { }
        }
        Log.w(TAG, "Could not parse date: $dateStr")
        return System.currentTimeMillis()
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
