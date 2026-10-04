# Teknonesia

A modern Android tech & gadget news aggregator app for Indonesia, built natively with **Kotlin** and **Jetpack Compose (Material 3)**.

---

## 📱 Features & Highlights

- **Live Multi-Source RSS Aggregation**:
  - Fetches real-time tech news from top Indonesian publishers (**CNN Indonesia Teknologi**, **Antara Tekno**, and **Tempo Bisnis & Tekno**).
  - Parallel background fetching using Kotlin Coroutines (`async` / `awaitAll`).
  - Native XML parsing via `XmlPullParser` with host validation, deduplication, and HTML sanitization.
  
- **Coil Async Image Loading**:
  - Automatically loads and caches rich news article thumbnails with crossfade transitions and placeholder gradients.

- **Smart Category Filtering**:
  - Automatic category classification using RSS metadata and intelligent keyword scoring.
  - Categories: `Semua`, `Gadget`, `Smartphone`, `AI`, `Startup`, `Gaming`, `Review`, `Dunia Tekno`.

- **Real-Time In-App Search**:
  - Instant live search by title and description with clear actions.

- **Bookmark & Saved Articles**:
  - Bookmark favorite articles to read offline / later with dedicated Bookmark screen.

- **In-App Article Reader & Chrome Custom Tabs**:
  - Built-in reader screen displaying article details, summaries, metadata, and relative timestamps (e.g., *"10 menit lalu"*).
  - Social sharing via Android Share Sheet.
  - Safe, fast external reading via **AndroidX Custom Tabs**.

- **Multi-Tab Modern UI**:
  - **Beranda (Home)**, **Bookmark**, and **Pengaturan (Settings)** tabs.
  - Settings allows users to toggle active RSS sources on/off.
  - Dynamic **Dark Theme** and **Light Theme** support.

---

## 🏗️ Architecture & Tech Stack

- **Language:** Kotlin (JVM Target 17)
- **UI Framework:** Jetpack Compose + Material 3 (Compose BOM 2026.04.01)
- **Image Loader:** Coil Compose (`io.coil-kt:coil-compose:2.7.0`)
- **Asynchronous:** Kotlin Coroutines (`Dispatchers.IO`, `async`, `awaitAll`)
- **Networking & Parsing:** `HttpURLConnection`, Android `XmlPullParser`
- **External Web Reader:** `androidx.browser:browser` (Custom Tabs)
- **Minimum SDK:** Android 7.0 (API Level 24)
- **Target / Compile SDK:** Android 15 / API Level 36

---

## 📁 Project Structure

```text
app/src/main/
├── AndroidManifest.xml
└── java/id/trendingnews/indonesia/
    ├── MainActivity.kt                # Compose UI: MainScreen, HomeScreen, BookmarkScreen, SettingsScreen, NewsCard, ArticleReader
    └── data/
        ├── NewsArticle.kt             # Data model for news articles
        ├── NewsRepository.kt          # Interface definition for news repositories
        └── AntaraRssNewsRepository.kt # MultiSourceNewsRepository, RssNewsRepository, NewsSources & category classifier
```

---

## 🚀 Build & CI/CD

### Automated Build (GitHub Actions)
A CI workflow runs on every push and pull request to `main` using **JDK 17**, **Gradle 8.13**, and **Android SDK 36**.
- **Download APK:** Open the latest successful **Android build** workflow run in the GitHub Actions tab and download the `trending-news-debug-apk` artifact.

### Local Build
Ensure you have **JDK 17** and **Android SDK 36** installed. Run the following command from the root directory:

```bash
gradle :app:assembleDebug
```

The compiled APK will be generated at:
```text
app/build/outputs/apk/debug/app-debug.apk
```
