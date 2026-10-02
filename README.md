# Trending News Indonesia

Android news discovery app built with Kotlin and Jetpack Compose.

## Current stage

This first stage contains a native Home screen with category filters and local sample news. RSS/API sources, article reader, search, bookmarks, and settings are planned for later stages.

## Build

The GitHub Actions workflow builds a debug APK using JDK 17, Gradle 8.13, and Android SDK 36 on every push to `main` and pull request. It can also be started manually from the Actions tab.

To download the APK, open the successful **Android build** workflow run and download the `trending-news-debug-apk` artifact.

For a local build, install JDK 17, Gradle 8.13, and Android SDK 36, then run `gradle :app:assembleDebug` from the project folder. The APK is created at `app/build/outputs/apk/debug/app-debug.apk`.
