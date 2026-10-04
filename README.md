# Trending News Indonesia (Gadget & Tekno News)

A modern Android tech news aggregator app built natively with **Kotlin** and **Jetpack Compose (Material 3)**.

---

## 📱 Features & Current Implementation

- **Live Multi-Source RSS Aggregation**:
  - Fetches real-time tech news from major Indonesian media outlets (e.g., **Tempo Tekno**, **CNN Indonesia Teknologi**).
  - Parallel background fetching using Kotlin Coroutines (`async` / `awaitAll`).
  - Native XML parsing via `XmlPullParser` with host validation and deduplication.
  
- **Smart Category Filtering**:
  - Automatic category classification using RSS metadata and intelligent keyword scoring.
  - Categories: `Semua`, `Gadget`, `Smartphone`, `AI`, `Startup`, `Gaming`, `Review`, `Dunia Tekno`.

- **In-App Article Reader & Chrome Custom Tabs**:
  - Built-in preview screen displaying article details, summaries, metadata, and relative timestamps (e.g., *"10 menit lalu"*).
  - Safe, fast in-app external reading via **AndroidX Custom Tabs** with URL validation.

- **Adaptive Modern UI**:
  - Built with **Jetpack Compose** & **Material Design 3**.
  - Dynamic **Dark Theme** and **Light Theme** support based on system settings.
  - State handling for Loading, Error (with Retry action), and Empty states.

---

## 🏗️ Architecture & Tech Stack

- **Language:** Kotlin (JVM Target 17)
- **UI Framework:** Jetpack Compose + Material 3 (Compose BOM 2026.04.01)
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
    ├── MainActivity.kt                # Compose UI: App Root, HomeScreen, NewsCard, ArticleReader
    └── data/
        ├── NewsArticle.kt             # Data model for news articles
        ├── NewsRepository.kt          # Interface definition for news repositories
        └── AntaraRssNewsRepository.kt # MultiSourceNewsRepository, RssNewsRepository & category classifier
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
