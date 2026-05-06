package com.safeharborsecurity.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for RssFeedParser. Robolectric is required because the parser
 * uses android.util.Log on parse errors and android-relevant date code.
 *
 * Bug history these tests guard against:
 *  - News feed wasn't picking up Krebs articles → check content:encoded
 *    is read for RSS feeds.
 *  - The "longest body wins" logic in summary selection.
 *  - ScamArticleFilter is called by the parser, so feeds that contain
 *    only scam-relevant items pass through fully.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])  // Stable Robolectric SDK to avoid CI network fetches.
class RssFeedParserTest {

    @Test
    fun `parses simple RSS 2 feed with single scam item`() {
        val xml = """
            <rss version="2.0">
              <channel>
                <title>Test Feed</title>
                <item>
                  <title>New phishing scam targets seniors</title>
                  <link>https://example.com/scam-1</link>
                  <description>A fraud campaign is hitting elderly users.</description>
                  <pubDate>Mon, 01 Jan 2024 12:00:00 +0000</pubDate>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val articles = RssFeedParser.parse(xml, "TestSource")
        assertThat(articles).hasSize(1)
        assertThat(articles[0].title).contains("phishing scam")
        assertThat(articles[0].link).isEqualTo("https://example.com/scam-1")
        assertThat(articles[0].source).isEqualTo("TestSource")
    }

    @Test
    fun `non-scam items are filtered out`() {
        // The parser invokes ScamArticleFilter — purely-political items
        // should not appear in the output.
        val xml = """
            <rss version="2.0">
              <channel>
                <item>
                  <title>Trump posts AI image of senator</title>
                  <link>https://example.com/political</link>
                  <description>Political news, no scam content.</description>
                </item>
                <item>
                  <title>New gift card scam targets grandparents</title>
                  <link>https://example.com/scam</link>
                  <description>Scammers asked an 80-year-old for iTunes cards.</description>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val articles = RssFeedParser.parse(xml, "Mixed")
        assertThat(articles).hasSize(1)
        assertThat(articles[0].title).contains("gift card scam")
    }

    @Test
    fun `parses Atom feed with entry tag`() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <title>Romance scam costs widow fifty thousand dollars</title>
                <link href="https://example.com/atom-1"/>
                <summary>Online dating fraud is on the rise.</summary>
                <published>2024-01-01T12:00:00Z</published>
              </entry>
            </feed>
        """.trimIndent()
        val articles = RssFeedParser.parse(xml, "AtomSource")
        assertThat(articles).hasSize(1)
        assertThat(articles[0].title).contains("Romance scam")
        assertThat(articles[0].link).isEqualTo("https://example.com/atom-1")
    }

    @Test
    fun `prefers content_encoded over short description for RSS`() {
        // Krebs and similar feeds put the short teaser in <description>
        // and the full body in <content:encoded>. The parser should keep
        // the longer text so the news card has more substance.
        val longBody = "Phishing scam details. ".repeat(20)
        val xml = """
            <rss xmlns:content="http://purl.org/rss/1.0/modules/content/">
              <channel>
                <item>
                  <title>Phishing campaign hits banks</title>
                  <link>https://example.com/krebs</link>
                  <description>Short teaser.</description>
                  <content:encoded>$longBody</content:encoded>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val articles = RssFeedParser.parse(xml, "Krebs")
        assertThat(articles).hasSize(1)
        // Summary should be the longer one (capped at 300 chars).
        assertThat(articles[0].summary.length).isAtLeast(50)
    }

    @Test
    fun `strips HTML tags from title and description`() {
        val xml = """
            <rss version="2.0">
              <channel>
                <item>
                  <title>&lt;b&gt;Robocall&lt;/b&gt; scam alert</title>
                  <link>https://example.com/robocall</link>
                  <description>&lt;p&gt;Avoid &lt;a href="x"&gt;suspicious&lt;/a&gt; calls about phishing.&lt;/p&gt;</description>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val articles = RssFeedParser.parse(xml, "Test")
        assertThat(articles).hasSize(1)
        assertThat(articles[0].title).doesNotContain("<b>")
        assertThat(articles[0].title).doesNotContain("</b>")
        assertThat(articles[0].summary).doesNotContain("<a")
    }

    @Test
    fun `decodes HTML entities`() {
        val xml = """
            <rss version="2.0">
              <channel>
                <item>
                  <title>Scam &amp; fraud alert</title>
                  <link>https://example.com/x</link>
                  <description>Text with &quot;quotes&quot; and &#39;apostrophes&#39; about phishing.</description>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val articles = RssFeedParser.parse(xml, "Test")
        assertThat(articles).hasSize(1)
        assertThat(articles[0].title).contains("&")
        assertThat(articles[0].title).doesNotContain("&amp;")
        assertThat(articles[0].summary).contains("\"")
    }

    @Test
    fun `articles missing title or link are skipped`() {
        val xml = """
            <rss version="2.0">
              <channel>
                <item>
                  <title>Phishing scam</title>
                  <description>No link.</description>
                </item>
                <item>
                  <link>https://example.com/no-title</link>
                  <description>No title for phishing.</description>
                </item>
                <item>
                  <title>Valid scam article</title>
                  <link>https://example.com/valid</link>
                  <description>Proper phishing description.</description>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val articles = RssFeedParser.parse(xml, "Test")
        assertThat(articles).hasSize(1)
        assertThat(articles[0].link).isEqualTo("https://example.com/valid")
    }

    @Test
    fun `same link produces same id`() {
        val xml1 = """
            <rss><channel><item>
              <title>First phishing scam</title>
              <link>https://example.com/article-42</link>
              <description>Phishing description.</description>
            </item></channel></rss>
        """.trimIndent()
        val xml2 = """
            <rss><channel><item>
              <title>First phishing scam</title>
              <link>https://example.com/article-42</link>
              <description>Phishing description.</description>
            </item></channel></rss>
        """.trimIndent()
        val a = RssFeedParser.parse(xml1, "A").first()
        val b = RssFeedParser.parse(xml2, "B").first()
        // Same link → same MD5 id → upsert deduplication works correctly.
        assertThat(a.id).isEqualTo(b.id)
    }

    @Test
    fun `malformed xml returns empty list, no crash`() {
        val articles = RssFeedParser.parse("<rss<<<>>not valid xml", "Bad")
        assertThat(articles).isEmpty()
    }

    @Test
    fun `empty feed returns empty list`() {
        val xml = "<rss version=\"2.0\"><channel><title>Empty</title></channel></rss>"
        val articles = RssFeedParser.parse(xml, "Empty")
        assertThat(articles).isEmpty()
    }
}
