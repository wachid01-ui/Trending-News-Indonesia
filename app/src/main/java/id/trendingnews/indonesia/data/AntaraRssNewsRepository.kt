package id.trendingnews.indonesia.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AntaraRssNewsRepository : NewsRepository {
    override suspend fun getLatestNews(): List<NewsArticle> = withContext(Dispatchers.IO) {
        val connection = (URL(FEED_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "TrendingNewsIndonesia/1.0 (Android RSS reader)")
            instanceFollowRedirects = false
        }
        try {
            check(connection.responseCode in 200..299) { "Feed ANTARA gagal dimuat (${connection.responseCode})" }
            connection.inputStream.use { input ->
                val parser = Xml.newPullParser().apply {
                    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                    setInput(input, null)
                }
                parseFeed(parser)
            }
        } finally { connection.disconnect() }
    }

    private fun parseFeed(parser: XmlPullParser): List<NewsArticle> {
        val articles = mutableListOf<NewsArticle>()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "item") {
                parseItem(parser)?.let(articles::add)
            }
            parser.next()
        }
        return articles
    }

    private fun parseItem(parser: XmlPullParser): NewsArticle? {
        var title: String? = null
        var link: String? = null
        var description: String? = null
        var imageUrl: String? = null
        var date: String? = null
        var category: String? = null
        val depth = parser.depth
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_TAG && parser.depth == depth && parser.name == "item") break
            if (event != XmlPullParser.START_TAG) continue
            when (parser.name.substringAfter(':').lowercase(Locale.ROOT)) {
                "title" -> title = parser.nextText()
                "link" -> link = parser.nextText()
                "description", "summary" -> description = parser.nextText()
                "pubdate", "published", "updated" -> date = parser.nextText()
                "category" -> category = parser.nextText()
                "content", "thumbnail" -> imageUrl = parser.getAttributeValue(null, "url")
                    ?: parser.getAttributeValue("http://search.yahoo.com/mrss/", "url")
                "enclosure" -> imageUrl = parser.getAttributeValue(null, "url") ?: imageUrl
            }
        }
        val safeLink = link?.trim()?.takeIf(::isAllowedArticleUrl) ?: return null
        val safeTitle = title?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return NewsArticle(
            id = safeLink, title = safeTitle,
            description = description?.trim()?.takeIf(String::isNotEmpty),
            imageUrl = imageUrl?.takeIf(::isHttpsUrl),
            sourceName = SOURCE_NAME, sourceUrl = SOURCE_URL, articleUrl = safeLink,
            publishedAt = parseDate(date), category = mapCategory(category, safeTitle),
        )
    }

    private fun isAllowedArticleUrl(value: String): Boolean = try {
        val url = URL(value)
        (url.protocol == "https" || url.protocol == "http") &&
            (url.host == "antaranews.com" || url.host.endsWith(".antaranews.com"))
    } catch (_: Exception) { false }

    private fun isHttpsUrl(value: String): Boolean = try {
        URL(value).protocol == "https"
    } catch (_: Exception) { false }

    private fun parseDate(value: String?): Date? {
        if (value.isNullOrBlank()) return null
        return try { SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).parse(value.trim()) }
        catch (_: Exception) { null }
    }

    private fun mapCategory(feedCategory: String?, title: String): String {
        val value = (feedCategory.orEmpty() + " " + title).lowercase(Locale.ROOT)
        return when {
            listOf("ekonomi", "bisnis", "pasar", "rupiah", "investasi").any(value::contains) -> "Ekonomi"
            listOf("tekno", "teknologi", "digital", "kecerdasan buatan", "ai ").any(value::contains) -> "Teknologi"
            listOf("olahraga", "sepak bola", "sepakbola", "timnas", "bulutangkis").any(value::contains) -> "Olahraga"
            listOf("hiburan", "film", "musik", "selebritas").any(value::contains) -> "Hiburan"
            listOf("dunia", "internasional", "asean").any(value::contains) -> "Dunia"
            else -> "Nasional"
        }
    }

    private companion object {
        const val FEED_URL = "https://www.antaranews.com/rss/terkini.xml"
        const val SOURCE_NAME = "ANTARA News"
        const val SOURCE_URL = "https://www.antaranews.com/rss"
    }
}
