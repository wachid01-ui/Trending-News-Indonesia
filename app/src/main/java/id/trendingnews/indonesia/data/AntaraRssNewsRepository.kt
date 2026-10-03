package id.trendingnews.indonesia.data

import android.util.Xml
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RssFeedSource(
    val id: String,
    val name: String,
    val feedUrl: String,
    val sourceUrl: String,
    val allowedArticleHosts: Set<String>,
)

object NewsSources {
    val antara = RssFeedSource(
        id = "antara",
        name = "ANTARA News",
        feedUrl = "https://www.antaranews.com/rss/terkini.xml",
        sourceUrl = "https://www.antaranews.com/rss",
        allowedArticleHosts = setOf("antaranews.com"),
    )

    val activeFeeds = listOf(antara)
}

class RssNewsRepository(private val source: RssFeedSource) : NewsRepository {
    override suspend fun getLatestNews(): List<NewsArticle> = withContext(Dispatchers.IO) {
        val connection = (URL(source.feedUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "TrendingNewsIndonesia/1.0 (Android RSS reader)")
            instanceFollowRedirects = false
        }

        try {
            check(connection.responseCode in 200..299) {
                "Feed ${source.name} gagal dimuat (${connection.responseCode})"
            }
            connection.inputStream.use { input ->
                val parser = Xml.newPullParser().apply {
                    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                    setInput(input, null)
                }
                parseFeed(parser)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseFeed(parser: XmlPullParser): List<NewsArticle> {
        val articles = mutableListOf<NewsArticle>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                parseItem(parser)?.let(articles::add)
            }
            event = parser.next()
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
        val itemDepth = parser.depth

        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_TAG && parser.depth == itemDepth && parser.name == "item") break
            if (event != XmlPullParser.START_TAG) continue

            val tag = parser.name.substringAfter(':').lowercase(Locale.ROOT)
            when (tag) {
                "title" -> title = parser.readTextValue()
                "link" -> link = parser.readTextValue()
                "description", "summary" -> description = parser.readTextValue()
                "pubdate", "published", "updated" -> date = parser.readTextValue()
                "category" -> category = parser.readTextValue()
                "content", "thumbnail" -> {
                    imageUrl = parser.getAttributeValue(null, "url")
                        ?: parser.getAttributeValue("http://search.yahoo.com/mrss/", "url")
                }
                "enclosure" -> imageUrl = parser.getAttributeValue(null, "url") ?: imageUrl
            }
        }

        val safeLink = link?.trim()?.takeIf(::isAllowedArticleUrl) ?: return null
        val safeTitle = title?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return NewsArticle(
            id = safeLink,
            title = safeTitle,
            description = description?.trim()?.takeIf(String::isNotEmpty),
            imageUrl = imageUrl?.takeIf(::isHttpsUrl),
            sourceName = source.name,
            sourceUrl = source.sourceUrl,
            articleUrl = safeLink,
            publishedAt = parseDate(date),
            category = mapCategory(category, safeTitle),
        )
    }

    private fun XmlPullParser.readTextValue(): String = nextText()

    private fun isAllowedArticleUrl(value: String): Boolean = try {
        val url = URL(value)
        (url.protocol == "https" || url.protocol == "http") &&
            source.allowedArticleHosts.any { host -> url.host == host || url.host.endsWith(".$host") }
    } catch (_: Exception) {
        false
    }

    private fun isHttpsUrl(value: String): Boolean = try {
        URL(value).protocol == "https"
    } catch (_: Exception) {
        false
    }

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

}

class AntaraRssNewsRepository : NewsRepository by RssNewsRepository(NewsSources.antara)

class MultiSourceNewsRepository(
    private val sources: List<NewsRepository>,
) : NewsRepository {
    override suspend fun getLatestNews(): List<NewsArticle> = coroutineScope {
        val results = sources.map { source ->
            async {
                try {
                    source.getLatestNews()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
        }.awaitAll()

        val successfulFeeds = results.filterNotNull()
        if (successfulFeeds.isEmpty()) throw NewsSourcesUnavailableException()

        successfulFeeds.flatten()
            .distinctBy { it.articleUrl.trimEnd('/').lowercase(Locale.ROOT) }
            .sortedByDescending { it.publishedAt?.time ?: Long.MIN_VALUE }
    }
}

class NewsSourcesUnavailableException : Exception("Semua feed berita gagal dimuat")

