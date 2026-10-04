package id.trendingnews.indonesia

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import id.trendingnews.indonesia.data.MultiSourceNewsRepository
import id.trendingnews.indonesia.data.NewsArticle
import id.trendingnews.indonesia.data.NewsSources
import id.trendingnews.indonesia.data.RssNewsRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val categories = listOf("Semua", "Gadget", "Smartphone", "AI", "Startup", "Gaming", "Review", "Dunia Tekno")

enum class ScreenTab(val label: String, val icon: String) {
    BERANDA("Beranda", "⌂"),
    BOOKMARK("Bookmark", "♥"),
    PENGATURAN("Pengaturan", "⚙"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TrendingNewsApp() }
    }
}

@Composable
private fun TrendingNewsApp() {
    val isDark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
        MainScreen()
    }
}

@Composable
private fun MainScreen() {
    var currentTab by remember { mutableStateOf(ScreenTab.BERANDA) }
    var selectedCategory by remember { mutableStateOf("Semua") }
    var articles by remember { mutableStateOf<List<NewsArticle>>(emptyList()) }
    var bookmarkedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selectedArticle by remember { mutableStateOf<NewsArticle?>(null) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showDisclaimerDialog by remember { mutableStateOf(true) }
    var enabledSourceIds by remember {
        mutableStateOf(NewsSources.activeFeeds.map { it.id }.toSet())
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    suspend fun fetchNews() {
        isLoading = true
        loadError = null
        try {
            val activeRepos = NewsSources.activeFeeds
                .filter { it.id in enabledSourceIds }
                .map(::RssNewsRepository)

            if (activeRepos.isEmpty()) {
                articles = emptyList()
                loadError = "Semua sumber berita dinonaktifkan di Pengaturan."
            } else {
                val repository = MultiSourceNewsRepository(activeRepos)
                articles = repository.getLatestNews()
            }
        } catch (_: Exception) {
            loadError = "Feed berita gagal dimuat. Periksa koneksi internet, lalu coba lagi."
        }
        isLoading = false
    }

    LaunchedEffect(enabledSourceIds) {
        fetchNews()
    }

    fun toggleBookmark(articleId: String) {
        bookmarkedIds = if (articleId in bookmarkedIds) {
            bookmarkedIds - articleId
        } else {
            bookmarkedIds + articleId
        }
    }

    val today = remember {
        SimpleDateFormat("EEEE, d MMMM yyyy", Locale("id", "ID")).format(Date())
    }

    val filteredArticles = remember(articles, selectedCategory, searchQuery, currentTab, bookmarkedIds) {
        var list = when (currentTab) {
            ScreenTab.BERANDA -> {
                if (selectedCategory == "Semua") articles
                else articles.filter { it.category == selectedCategory }
            }
            ScreenTab.BOOKMARK -> {
                articles.filter { it.id in bookmarkedIds }
            }
            ScreenTab.PENGATURAN -> emptyList()
        }

        if (searchQuery.isNotBlank()) {
            val query = searchQuery.trim().lowercase(Locale.ROOT)
            list = list.filter {
                it.title.lowercase(Locale.ROOT).contains(query) ||
                    (it.description?.lowercase(Locale.ROOT)?.contains(query) == true)
            }
        }
        list
    }

    BackHandler(enabled = selectedArticle != null || isSearchActive || currentTab != ScreenTab.BERANDA) {
        if (selectedArticle != null) {
            selectedArticle = null
        } else if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        } else if (currentTab != ScreenTab.BERANDA) {
            currentTab = ScreenTab.BERANDA
        }
    }

    if (showDisclaimerDialog) {
        DisclaimerDialog(onDismiss = { showDisclaimerDialog = false })
    }

    Scaffold(
        bottomBar = {
            if (selectedArticle == null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    ScreenTab.entries.forEach { tab ->
                        val isSelected = currentTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                currentTab = tab
                                if (tab != ScreenTab.BERANDA) isSearchActive = false
                            },
                            icon = { Text(tab.icon, fontSize = 20.sp) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (selectedArticle != null) {
            val isBookmarked = selectedArticle!!.id in bookmarkedIds
            ArticleReader(
                article = selectedArticle!!,
                isBookmarked = isBookmarked,
                modifier = Modifier.padding(padding),
                onBack = { selectedArticle = null },
                onToggleBookmark = { toggleBookmark(selectedArticle!!.id) },
                onShare = { shareArticle(context, selectedArticle!!) },
            )
        } else {
            when (currentTab) {
                ScreenTab.BERANDA -> {
                    HomeScreenContent(
                        today = today,
                        selectedCategory = selectedCategory,
                        onCategorySelect = { selectedCategory = it },
                        articles = filteredArticles,
                        bookmarkedIds = bookmarkedIds,
                        isLoading = isLoading,
                        loadError = loadError,
                        isSearchActive = isSearchActive,
                        searchQuery = searchQuery,
                        onSearchToggle = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) searchQuery = ""
                        },
                        onSearchQueryChange = { searchQuery = it },
                        onArticleClick = { selectedArticle = it },
                        onBookmarkClick = { toggleBookmark(it.id) },
                        onRetry = { scope.launch { fetchNews() } },
                        modifier = Modifier.padding(padding),
                    )
                }
                ScreenTab.BOOKMARK -> {
                    BookmarkScreenContent(
                        articles = filteredArticles,
                        bookmarkedIds = bookmarkedIds,
                        onArticleClick = { selectedArticle = it },
                        onBookmarkClick = { toggleBookmark(it.id) },
                        modifier = Modifier.padding(padding),
                    )
                }
                ScreenTab.PENGATURAN -> {
                    SettingsScreenContent(
                        enabledSourceIds = enabledSourceIds,
                        onToggleSource = { sourceId, isEnabled ->
                            enabledSourceIds = if (isEnabled) {
                                enabledSourceIds + sourceId
                            } else {
                                enabledSourceIds - sourceId
                            }
                        },
                        onShowDisclaimer = { showDisclaimerDialog = true },
                        onRefreshNews = { scope.launch { fetchNews() } },
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }
}

@Composable
private fun DisclaimerDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✦", fontSize = 22.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Teknonesia",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Deklarasi & Panduan Singkat",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))

                Text(
                    "📌 Deklarasi Agregator Berita & HAKI",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Teknonesia adalah aplikasi agregator berita independen. Kami menyajikan ringkasan berita dari feed RSS publik yang disediakan oleh masing-masing media penerbit (seperti CNN Indonesia, ANTARA News, dan Tempo).\n\nSeluruh hak cipta, merek dagang, naskah berita, foto, dan hak kekayaan intelektual (HAKI) sepenuhnya tetap menjadi milik sah masing-masing penerbit berita asli. Kami tidak mengklaim kepemilikan atas konten tersebut dan selalu menyertakan tautan langsung ke situs resmi penerbit.",
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    "💡 Panduan Singkat Penggunaan",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "• Filter Kategori: Pilih topik favorit (Gadget, Smartphone, AI, Gaming, Startup, dll.).\n" +
                    "• Pencarian (⌕): Ketuk ikon search di pojok atas untuk mencari topik atau berita spesifik.\n" +
                    "• Baca & Simpan: Ketuk berita untuk membaca ringkasan atau simpan ke Bookmark (♡) untuk dibaca nanti.\n" +
                    "• Baca Artikel Lengkap: Ketuk tombol 'Baca Artikel Lengkap di Web' untuk langsung diarahkan ke portal resmi penerbit.",
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(22.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Saya Mengerti & Lanjutkan", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun HomeScreenContent(
    today: String,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit,
    articles: List<NewsArticle>,
    bookmarkedIds: Set<String>,
    isLoading: Boolean,
    loadError: String?,
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onArticleClick: (NewsArticle) -> Unit,
    onBookmarkClick: (NewsArticle) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp),
    ) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Teknonesia",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            today.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("id", "ID")) else it.toString() },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = if (isSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Box(
                            Modifier.size(46.dp).clickable(onClick = onSearchToggle),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (isSearchActive) "✕" else "⌕",
                                fontSize = if (isSearchActive) 18.sp else 28.sp,
                                color = if (isSearchActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = isSearchActive, enter = fadeIn(), exit = fadeOut()) {
                    Column {
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Cari berita teknologi…") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    Text(
                                        "✕",
                                        modifier = Modifier.clickable { onSearchQueryChange("") }.padding(8.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    "Jelajahi berita teknologi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                items(categories) { category ->
                    val selected = category == selectedCategory
                    Surface(
                        modifier = Modifier.clickable { onCategorySelect(category) },
                        shape = RoundedCornerShape(50),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            category,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
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
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (searchQuery.isNotBlank()) "Hasil Pencarian"
                    else if (selectedCategory == "Semua") "Semua berita terkini"
                    else selectedCategory,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${articles.size} berita",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        if (isLoading && articles.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Memuat berita teknologi…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (loadError != null && articles.isEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                    Text(loadError, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRetry) { Text("Coba lagi") }
                }
            }
        } else if (articles.isEmpty()) {
            item {
                Text(
                    if (searchQuery.isNotBlank()) "Tidak ditemukan berita dengan kata kunci \"$searchQuery\"."
                    else "Belum ada berita untuk kategori ini.",
                    Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(articles, key = { it.id }) { article ->
            val isBookmarked = article.id in bookmarkedIds
            NewsCard(
                article = article,
                isBookmarked = isBookmarked,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 7.dp),
                onClick = { onArticleClick(article) },
                onBookmarkClick = { onBookmarkClick(article) },
            )
        }
    }
}

@Composable
private fun BookmarkScreenContent(
    articles: List<NewsArticle>,
    bookmarkedIds: Set<String>,
    onArticleClick: (NewsArticle) -> Unit,
    onBookmarkClick: (NewsArticle) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp),
    ) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)) {
                Text(
                    "Berita Tersimpan",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${bookmarkedIds.size} artikel tersimpan",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (articles.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("♡", fontSize = 56.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Belum ada berita yang disimpan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Klik ikon bookmark (♡) pada berita yang menarik untuk membacanya nanti.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                    )
                }
            }
        } else {
            items(articles, key = { it.id }) { article ->
                NewsCard(
                    article = article,
                    isBookmarked = true,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 7.dp),
                    onClick = { onArticleClick(article) },
                    onBookmarkClick = { onBookmarkClick(article) },
                )
            }
        }
    }
}

@Composable
private fun SettingsScreenContent(
    enabledSourceIds: Set<String>,
    onToggleSource: (String, Boolean) -> Unit,
    onShowDisclaimer: () -> Unit,
    onRefreshNews: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Text(
            "Pengaturan",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(20.dp))

        Text(
            "Sumber Berita RSS Aktif",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(16.dp)) {
                NewsSources.activeFeeds.forEachIndexed { index, source ->
                    val isEnabled = source.id in enabledSourceIds
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(source.name, fontWeight = FontWeight.Medium)
                            Text(
                                source.sourceUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { onToggleSource(source.id, it) },
                        )
                    }
                    if (index < NewsSources.activeFeeds.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Aplikasi & Informasi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Teknonesia", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Versi 1.0 • Berita Gadget & Teknologi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onShowDisclaimer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Deklarasi & Panduan Aplikasi")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRefreshNews,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Muat Ulang Berita")
                }
            }
        }
    }
}

@Composable
private fun NewsCard(
    article: NewsArticle,
    isBookmarked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onBookmarkClick: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(160.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFF1565C0), Color(0xFF64B5F6)))),
                contentAlignment = Alignment.Center,
            ) {
                if (!article.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(article.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = article.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text("✦", fontSize = 44.sp, color = Color.White.copy(alpha = 0.88f))
                }

                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                    shape = RoundedCornerShape(30.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                ) {
                    Text(
                        article.category,
                        Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, end = 12.dp, bottom = 16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        article.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 22.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            article.sourceName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "  •  ${relativeTime(article.publishedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = if (isBookmarked) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onBookmarkClick),
                ) {
                    Text(
                        if (isBookmarked) "♥" else "♡",
                        Modifier.padding(8.dp),
                        fontSize = 22.sp,
                        color = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArticleReader(
    article: NewsArticle,
    isBookmarked: Boolean,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    onShare: () -> Unit,
) {
    val context = LocalContext.current
    var openError by remember(article.id) { mutableStateOf(false) }
    val uri = remember(article.articleUrl) { Uri.parse(article.articleUrl) }
    val isAllowedUrl = uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank()

    BackHandler(onBack = onBack)

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "‹  Kembali",
                    Modifier.clickable(onClick = onBack).padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "↗ Bagikan",
                        Modifier.clickable(onClick = onShare).padding(horizontal = 8.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (isBookmarked) "♥" else "♡",
                        Modifier.clickable(onClick = onToggleBookmark).padding(8.dp),
                        fontSize = 24.sp,
                        color = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (!article.imageUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)),
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(article.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = article.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    article.category,
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(article.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(
                "${article.sourceName} • ${relativeTime(article.publishedAt)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                uri.host.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(20.dp))

            Text(
                article.description?.takeIf(String::isNotBlank)
                    ?: "Baca berita selengkapnya langsung dari situs penerbit asli.",
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 26.sp,
            )
            Spacer(Modifier.height(24.dp))

            Button(
                enabled = isAllowedUrl,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                onClick = {
                    try {
                        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
                        openError = false
                    } catch (_: ActivityNotFoundException) {
                        openError = true
                    } catch (_: SecurityException) {
                        openError = true
                    }
                },
            ) {
                Text("Baca Artikel Lengkap di Web")
            }

            if (!isAllowedUrl || openError) {
                Spacer(Modifier.height(8.dp))
                Text(
                    if (!isAllowedUrl) "Tautan artikel tidak valid atau tidak aman."
                    else "Browser tidak dapat dibuka. Coba lagi nanti.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun shareArticle(context: Context, article: NewsArticle) {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, "${article.title}\n\nBaca selengkapnya di: ${article.articleUrl}")
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Bagikan Berita")
    context.startActivity(shareIntent)
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
