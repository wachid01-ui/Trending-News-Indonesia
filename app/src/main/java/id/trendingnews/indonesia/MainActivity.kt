package id.trendingnews.indonesia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.trendingnews.indonesia.data.AntaraRssNewsRepository
import id.trendingnews.indonesia.data.NewsArticle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val categories = listOf("Trending", "Nasional", "Ekonomi", "Teknologi", "Olahraga", "Hiburan", "Dunia")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TrendingNewsApp() }
    }
}

@Composable
private fun TrendingNewsApp() {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
        HomeScreen()
    }
}

@Composable
private fun HomeScreen() {
    var selectedCategory by remember { mutableStateOf("Trending") }
    var articles by remember { mutableStateOf<List<NewsArticle>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val repository = remember { AntaraRssNewsRepository() }
    val scope = rememberCoroutineScope()
    suspend fun refreshNews() {
        isLoading = true
        loadError = null
        try {
            articles = repository.getLatestNews()
        } catch (_: Exception) {
            loadError = "Feed berita gagal dimuat. Periksa koneksi internet, lalu coba lagi."
        }
        isLoading = false
    }
    LaunchedEffect(Unit) { refreshNews() }
    val today = remember {
        SimpleDateFormat("EEEE, d MMMM yyyy", Locale("id", "ID")).format(Date())
    }
    val visibleArticles = remember(selectedCategory, articles) {
        if (selectedCategory == "Trending") articles
        else articles.filter { it.category == selectedCategory }
    }

    Scaffold(
        bottomBar = {
            BottomAppBar(containerColor = MaterialTheme.colorScheme.surface) {
                listOf("Beranda" to "⌂", "Bookmark" to "♡", "Pengaturan" to "⚙").forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(
                        selected = index == 0,
                        onClick = { },
                        icon = { Text(icon, fontSize = 21.sp) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer),
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp),
        ) {
            item {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Trending News", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(today.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("id", "ID")) else it.toString() }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Box(Modifier.size(46.dp).clickable { }, contentAlignment = Alignment.Center) {
                                Text("⌕", fontSize = 30.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("Jelajahi berita", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                }
            }
            item {
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    items(categories) { category ->
                        val selected = category == selectedCategory
                        Surface(
                            modifier = Modifier.clickable { selectedCategory = category },
                            shape = RoundedCornerShape(50),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                category,
                                modifier = Modifier.padding(horizontal = 17.dp, vertical = 10.dp),
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (selectedCategory == "Trending") "Berita pilihan" else selectedCategory, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${visibleArticles.size} berita", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
            }
            if (isLoading && articles.isEmpty()) {
                item { Text("Memuat berita…", Modifier.padding(horizontal = 20.dp, vertical = 28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else if (loadError != null && articles.isEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                        Text(loadError.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { scope.launch { refreshNews() } }) { Text("Coba lagi") }
                    }
                }
            } else if (visibleArticles.isEmpty()) {
                item {
                    Text(if (articles.isEmpty()) "Tidak ada berita tersedia." else "Belum ada berita untuk kategori ini.", Modifier.padding(horizontal = 20.dp, vertical = 28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(visibleArticles, key = { it.id }) { article ->
                NewsCard(article, Modifier.padding(horizontal = 20.dp, vertical = 7.dp))
            }
        }
    }
}

@Composable
private fun NewsCard(article: NewsArticle, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(148.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFF1565C0), Color(0xFF64B5F6)))),
                contentAlignment = Alignment.Center,
            ) {
                Text("✦", fontSize = 44.sp, color = Color.White.copy(alpha = 0.88f))
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                    shape = RoundedCornerShape(30.dp),
                    color = Color.Black.copy(alpha = 0.28f),
                ) {
                    Text(article.category, Modifier.padding(horizontal = 11.dp, vertical = 6.dp), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, end = 12.dp, bottom = 16.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(article.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, lineHeight = 23.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(9.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(article.sourceName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("  •  ${relativeTime(article.publishedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text("♡", Modifier.clickable { }.padding(horizontal = 7.dp, vertical = 2.dp), fontSize = 25.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun relativeTime(publishedAt: Date?): String {
    if (publishedAt == null) return "waktu tidak tersedia"
    val minutes = ((System.currentTimeMillis() - publishedAt.time) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "baru saja"
        minutes < 60 -> "$minutes menit lalu"
        minutes < 24 * 60 -> "${minutes / 60} jam lalu"
        else -> "${minutes / (24 * 60)} hari lalu"
    }
}
