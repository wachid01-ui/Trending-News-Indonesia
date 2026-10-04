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
    val antaraTekno = RssFeedSource(
        id = "antara_tekno",
        name = "Antara Tekno",
        feedUrl = "https://www.antaranews.com/rss/tekno.xml",
        sourceUrl = "https://tekno.antaranews.com",
        allowedArticleHosts = setOf("antaranews.com"),
    )

    val cnnTeknologi = RssFeedSource(
        id = "cnn_teknologi",
        name = "CNN Indonesia Teknologi",
        feedUrl = "https://www.cnnindonesia.com/teknologi/rss",
        sourceUrl = "https://www.cnnindonesia.com/teknologi",
        allowedArticleHosts = setOf("cnnindonesia.com"),
    )

    val tempoTekno = RssFeedSource(
        id = "tempo_tekno",
        name = "Tempo Bisnis & Tekno",
        feedUrl = "https://rss.tempo.co/bisnis",
        sourceUrl = "https://tempo.co",
        allowedArticleHosts = setOf("tempo.co"),
    )

    // Backward compatibility alias
    val antara = antaraTekno

    val activeFeeds = listOf(antaraTekno, cnnTeknologi, tempoTekno)
}

class RssNewsRepository(private val source: RssFeedSource) : NewsRepository {
    override suspend fun getLatestNews(): List<NewsArticle> = withContext(Dispatchers.IO) {
        val connection = (URL(source.feedUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Teknonesia/1.0 (Android RSS reader)")
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
        val safeTitle = cleanHtmlText(title?.trim())?.takeIf(String::isNotEmpty) ?: return null
        val cleanedDesc = cleanHtmlText(description)
        val extractedImage = (imageUrl ?: extractImageUrlFromHtml(description))?.takeIf(::isHttpsUrl)

        return NewsArticle(
            id = safeLink,
            title = safeTitle,
            description = cleanedDesc,
            imageUrl = extractedImage,
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

    private fun extractImageUrlFromHtml(html: String?): String? {
        if (html == null) return null
        val match = Regex("""<img[^>]+src=["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE).find(html)
        return match?.groupValues?.get(1)?.takeIf(::isHttpsUrl)
    }

    private fun cleanHtmlText(text: String?): String? {
        if (text == null) return null
        return text.replace(Regex("<[^>]*>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
            .takeIf(String::isNotEmpty)
    }

    private fun parseDate(value: String?): Date? {
        if (value.isNullOrBlank()) return null
        val cleaned = value.trim()
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
        feedCategories
            .mapNotNull(::categoryFromFeedLabel)
            .firstOrNull()
            ?.let { return it }

        val text = normalizeCategoryText(title)
        val keywordGroups = linkedMapOf(
            "AI" to listOf(
                "kecerdasan buatan", "artificial intelligence", "ai", "chatgpt", "gpt",
                "llm", "generative ai", "machine learning", "deep learning", "neural network",
                "google gemini", "openai", "copilot", "midjourney", "stable diffusion",
                "robot ai", "model bahasa", "large language",
            ),
            "Smartphone" to listOf(
                "smartphone", "ponsel", "hp", "handphone", "iphone", "android",
                "samsung galaxy", "xiaomi", "oppo", "vivo", "realme", "oneplus",
                "pixel", "snapdragon", "dimensity", "chipset", "kamera hp",
                "layar hp", "baterai hp", "harga hp",
            ),
            "Gadget" to listOf(
                "gadget", "laptop", "tablet", "smartwatch", "jam tangan pintar",
                "earphone", "headphone", "tws", "speaker bluetooth", "drone",
                "kamera mirrorless", "kamera dslr", "proyektor", "monitor",
                "keyboard", "mouse", "ssd", "harddisk", "ram", "aksesori",
                "wearable", "ipad", "macbook", "surface",
            ),
            "Gaming" to listOf(
                "gaming", "game", "gamer", "esport", "esports",
                "playstation", "ps5", "ps4", "xbox", "nintendo", "switch",
                "steam", "pc gaming", "gpu", "vga", "rtx", "rx", "fps",
                "mobile legend", "pubg", "free fire", "valorant", "minecraft",
            ),
            "Startup" to listOf(
                "startup", "unicorn", "pendanaan", "venture capital", "ipo teknologi",
                "seri a", "seri b", "seri c", "valuasi", "founder", "co-founder",
                "gojek", "tokopedia", "bukalapak", "traveloka", "fintech", "edtech",
                "healthtech", "proptech", "agritech",
            ),
            "Review" to listOf(
                "review", "ulasan", "hands on", "unboxing", "benchmark",
                "spesifikasi", "harga dan spesifikasi", "uji coba", "tes",
                "vs", "perbandingan", "terbaik", "rekomendasi",
            ),
            "Dunia Tekno" to listOf(
                "google", "apple", "microsoft", "meta", "amazon", "tesla",
                "nvidia", "intel", "amd", "qualcomm", "samsung electronics",
                "silicon valley", "elon musk", "tim cook", "satya nadella",
                "mark zuckerberg", "big tech", "antitrust teknologi",
            ),
        )

        val scores = keywordGroups.mapValues { (_, keywords) ->
            keywords.count { keyword -> containsWholePhrase(text, keyword) }
        }.filterValues { it > 0 }
        val highestScore = scores.values.maxOrNull()
        val bestMatches = scores.filterValues { it == highestScore }.keys

        return bestMatches.singleOrNull() ?: "Gadget"
    }

    private fun categoryFromFeedLabel(label: String): String? {
        val normalized = normalizeCategoryText(label)
        return when {
            listOf("ai", "artificial intelligence", "kecerdasan buatan", "machine learning").any { containsWholePhrase(normalized, it) } -> "AI"
            listOf("smartphone", "ponsel", "hp", "iphone", "android").any { containsWholePhrase(normalized, it) } -> "Smartphone"
            listOf("gadget", "laptop", "tablet", "wearable", "aksesori").any { containsWholePhrase(normalized, it) } -> "Gadget"
            listOf("gaming", "game", "esport", "konsol").any { containsWholePhrase(normalized, it) } -> "Gaming"
            listOf("startup", "unicorn", "fintech", "venture").any { containsWholePhrase(normalized, it) } -> "Startup"
            listOf("review", "ulasan", "unboxing", "benchmark").any { containsWholePhrase(normalized, it) } -> "Review"
            listOf("google", "apple", "microsoft", "meta", "amazon", "dunia tekno", "global tech").any { containsWholePhrase(normalized, it) } -> "Dunia Tekno"
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

class AntaraRssNewsRepository : NewsRepository by RssNewsRepository(NewsSources.antaraTekno)

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
