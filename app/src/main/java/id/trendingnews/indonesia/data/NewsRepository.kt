package id.trendingnews.indonesia.data

interface NewsRepository {
    suspend fun getLatestNews(): List<NewsArticle>
}
