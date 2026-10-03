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

    val tempo = RssFeedSource(
        id = "tempo",
        name = "Tempo.co",
        feedUrl = "https://rss.tempo.co/",
        sourceUrl = "https://www.tempo.co",
        allowedArticleHosts = setOf("tempo.co"),
    )

    val cnnIndonesia = RssFeedSource(
        id = "cnnindonesia",
        name = "CNN Indonesia",
        feedUrl = "https://www.cnnindonesia.com/rss",
        sourceUrl = "https://www.cnnindonesia.com",
        allowedArticleHosts = setOf("cnnindonesia.com"),
    )

    val tribunnews = RssFeedSource(
        id = "tribunnews",
        name = "Tribun News",
        feedUrl = "https://www.tribunnews.com/rss",
        sourceUrl = "https://www.tribunnews.com",
        allowedArticleHosts = setOf("tribunnews.com"),
    )

    val activeFeeds = listOf(antara, tempo, cnnIndonesia, tribunnews)
}

class RssNewsRepository(private val source: RssFeedSource) : NewsRepository {
    override suspend fun getLatestNews(): List<NewsArticle> = withContext(Dispatchers.IO) {
        val connection = (URL(source.feedUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "TrendingNewsIndonesia/1.0 (Android RSS reader)")
            instanceFollowRedirects = true
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
        val feedCategories = mutableListOf<String>()
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
                "category" -> parser.readTextValue().trim().takeIf(String::isNotEmpty)?.let(feedCategories::add)
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
            category = mapCategory(feedCategories, safeTitle),
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
        val cleaned = value.trim()
        // Format RFC 822 (ANTARA, Tribun): "Mon, 01 Jan 2024 10:00:00 +0700"
        // Format ISO 8601 (Tempo, CNN):    "2024-01-01T10:00:00+07:00"
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss z",
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd HH:mm:ss",
        )
        for (fmt in formats) {
            try { return SimpleDateFormat(fmt, Locale.US).parse(cleaned) } catch (_: Exception) {}
        }
        return null
    }

    private fun mapCategory(feedCategories: List<String>, title: String): String {
        // Feed labels are more authoritative than guessing from the headline.
        feedCategories
            .mapNotNull(::categoryFromFeedLabel)
            .firstOrNull()
            ?.let { return it }

        // Use the headline only for inference. RSS descriptions can mention several
        // subjects and would otherwise make unrelated categories win by accident.
        val text = normalizeCategoryText(title)
        val keywordGroups = linkedMapOf(
            "Ekonomi" to listOf(
                "ekonomi", "bisnis", "pasar modal", "pasar saham", "saham", "bursa", "rupiah",
                "investasi", "inflasi", "deflasi", "harga emas", "harga bbm", "harga pangan",
                "perbankan", "suku bunga", "ekspor", "impor", "apbn", "pajak", "pertumbuhan ekonomi",
                "emiten", "ihsg", "energi dan sumber daya mineral",
            ),
            "Teknologi" to listOf(
                "teknologi", "tekno", "iptek", "kecerdasan buatan", "artificial intelligence", "ai",
                "gadget", "ponsel", "smartphone", "aplikasi", "internet", "digital", "siber",
                "robot", "perangkat lunak", "software", "startup", "pusat data", "data center",
            ),
            "Olahraga" to listOf(
                "olahraga", "sepak bola", "sepakbola", "timnas", "bulutangkis", "badminton",
                "basket", "voli", "motogp", "formula 1", "piala dunia", "atlet",
                "olimpiade", "medali", "pertandingan", "turnamen", "persib", "persija", "pssi",
            ),
            "Hiburan" to listOf(
                "hiburan", "film", "musik", "konser", "penyanyi", "aktor", "aktris", "artis",
                "selebritas", "selebriti", "sinetron", "serial", "festival musik",
            ),
            "Dunia" to listOf(
                "internasional", "mancanegara", "luar negeri", "konflik global", "ktt internasional",
                "pemerintah amerika serikat", "presiden amerika serikat", "pemerintah china",
                "pemerintah jepang", "pemerintah korea selatan", "pemerintah australia",
            ),
            "Nasional" to listOf(
                "nasional", "pemerintah indonesia", "presiden prabowo", "dpr ri", "mahkamah agung",
                "mahkamah konstitusi", "polri", "tni", "kementerian", "gubernur", "bupati",
                "wali kota", "pemilu", "pilkada", "kpk", "kejaksaan", "bencana di indonesia",
            ),
        )

        val scores = keywordGroups.mapValues { (_, keywords) ->
            keywords.count { keyword -> containsWholePhrase(text, keyword) }
        }.filterValues { it > 0 }
        val highestScore = scores.values.maxOrNull()
        val bestMatches = scores.filterValues { it == highestScore }.keys

        // Do not mislabel an unclear story as Nasional. Keep it visible in Trending
        // and let users find it under the explicit fallback category.
        return bestMatches.singleOrNull() ?: "Lainnya"
    }

    private fun categoryFromFeedLabel(label: String): String? {
        val normalized = normalizeCategoryText(label)
        return when {
            listOf("dunia", "internasional", "international", "mancanegara", "asean").any { containsWholePhrase(normalized, it) } -> "Dunia"
            listOf("ekonomi", "bisnis", "finansial", "keuangan", "bursa", "market").any { containsWholePhrase(normalized, it) } -> "Ekonomi"
            listOf("teknologi", "tekno", "iptek", "sains", "gadget").any { containsWholePhrase(normalized, it) } -> "Teknologi"
            listOf("olahraga", "sport", "sepakbola", "sepak bola", "bulutangkis").any { containsWholePhrase(normalized, it) } -> "Olahraga"
            listOf("hiburan", "entertainment", "seleb", "film", "musik", "seni").any { containsWholePhrase(normalized, it) } -> "Hiburan"
            listOf(
                "nasional", "politik", "hukum", "polhukam", "metro", "humaniora", "daerah",
                "kriminal", "pemerintahan", "peristiwa", "pemilu",
            ).any { containsWholePhrase(normalized, it) } -> "Nasional"
            else -> null
        }
    }

    private fun normalizeCategoryText(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    private fun containsWholePhrase(text: String, phrase: String): Boolean =
        " $text ".contains(" ${normalizeCategoryText(phrase)} ")

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

