package id.trendingnews.indonesia.data

import java.util.Date

data class NewsArticle(
    val id: String,
    val title: String,
    val description: String?,
    val imageUrl: String?,
    val sourceName: String,
    val sourceUrl: String,
    val articleUrl: String,
    val publishedAt: Date?,
    val category: String,
    val isBookmarked: Boolean = false,
    val sourceCount: Int? = null,
    val popularityScore: Double? = null,
)
