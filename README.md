
# 🎧 Cynk
## Sink In With Music.

Cynk is a modern, feature-rich Android music application focused on music discovery, high-fidelity playback, deep personalization, and real-time social listening. Built natively with Kotlin and Jetpack Compose, Cynk provides a fast, ad-free streaming experience with full offline support, synchronized lyrics, and synchronized group sessions.

---

## 📖 About

Cynk is crafted for music lovers who want complete control over their listening experience. Whether you want to explore personalized recommendations, import playlists from Spotify and YouTube, listen offline during travel, track your detailed listening habits with rich statistics, or stream tracks in real-time with friends across the world using **Cynk Together**, Cynk brings all your music needs together in a beautifully designed Material 3 interface.

---

## ✨ Features

### 🎵 Music Discovery & Playback
- **Fast & Ad-Free Streaming**: Seamless, uninterrupted audio playback powered by Android Media3 ExoPlayer.
- **Audio Engine**: Gapless playback, loudness normalization (EBU R128), system equalizer integration, silence skipping, and speed/pitch adjustments.
- **Background & Lockscreen Playback**: Full MediaSession integration with system notification controls and media metadata.
- **Canvas Video Backgrounds**: Support for Spotify Canvas looping video visuals during playback.

### 🔎 Music Search
- **Comprehensive Search**: Search across songs, albums, artists, playlists, community mixes, and podcasts.
- **Search Filters & Suggestions**: Quick filter chips and instant autocomplete recommendations.

### 📥 Playlist Importing
- **Spotify Playlist Import**: Seamlessly resolve and import public Spotify playlists directly into your local Cynk library.
- **YouTube & URL Importing**: Import public and unlisted YouTube / YouTube Music playlist links.
- **Local Playlist Management**: Create, edit, reorder, and export custom playlists locally.

### ⬇️ Music Downloads & Offline Listening
- **Offline Caching**: Download songs and full albums for offline playback anytime, anywhere.
- **Smart Cache Management**: Dedicated download manager with storage quota controls and automated cache cleanup.

### ❤️ Library & Personalization
- **Favorites & Bookmarks**: Save songs, favorite artists, and custom playlists to your personal library.
- **Auto-Backup & Restore**: Export and import your local library, history, and settings safely.

### 📊 Listening Statistics
- **Detailed Listening Analytics**: Track top played tracks, top artists, total listening time, and play counts over time.
- **Periodic Stats**: View listening trends across weeks, months, or all-time listening history.

### 🎧 Personalized Recommendations
- **Dynamic Home Feed**: Curated shelves including quick picks, mood mixes, trending charts, and new releases.
- **"More of What You Like" Engine**: Smart recommendation engine based on your actual listening patterns.
- **Mood & Activity Chips**: Instant genre and mood filtering (Relax, Workout, Focus, Commute, Party).

### 👥 Cynk Together — Social Listening
- **Real-Time Group Sessions**: Stream and synchronize music with friends in real time via high-performance WebSocket relays.
- **Room Code Sharing**: Easily invite friends with simple 6-character room codes or deep links.
- **Host Permission Controls**: Configurable permissions for track addition, playback controls, and participant queue management.
- **Low-Latency Time Sync**: Microsecond clock synchronization engine ensuring synchronized audio across diverse network conditions.

### 📝 Real-Time Lyrics
- **Synchronized Karaoke Lyrics**: Live syllable and word-synced lyrics with fluid Compose animations.
- **Multi-Source Providers**: Automatic aggregation and fallback across LRCLIB, Kugou, and BetterLyrics.
- **Translation & Romanization**: Multi-language support with Japanese/Chinese character romanization.

### 🎨 Modern Android UI
- **Material 3 Expressive Design**: Clean, fluid animations and responsive layouts built 100% in Jetpack Compose.
- **Dynamic Theming**: Color extraction from album artwork matching your device's Material You theme.
- **Discord Rich Presence**: Integrated Kizzy Discord RPC support showcasing your current playback status.

---

## 👥 Cynk Together

**Cynk Together** is Cynk's built-in social listening feature that lets multiple users listen to the exact same track at the exact same millisecond timestamp, regardless of where they are in the world.

### How It Works:
1. **Host a Session**: The host starts a session and receives a unique 6-character room code (e.g. `CYNK7X`).
2. **Share & Join**: Friends enter the room code to connect directly through the high-performance online WebSocket relay.
3. **Synchronized Playback**: Play, pause, seek, and queue modifications are instantly broadcast to all participants.
4. **Clock Synchronization**: An internal drift-compensation clock periodically calculates round-trip latency to guarantee aligned playback timestamps.
5. **Relay Server**: Includes a standalone, lightweight Node.js/TypeScript synchronization relay (`cynk-together-server`) easily deployable on any cloud provider (Render, Railway, Fly.io, VPS).

---

## 📥 Playlist Import & Downloads

- **Cross-Platform Compatibility**: Easily migrate to Cynk by importing playlist URLs from Spotify and YouTube Music without manual recreation.
- **Local Storage Management**: Downloaded audio tracks and cached metadata are safely indexed in Room and local app storage.
- **Custom Quality Selection**: Choose preferred audio stream bitrates for streaming vs. offline storage to save bandwidth.

---

## 🧠 Architecture

Cynk follows modern Android architecture patterns utilizing **Unidirectional Data Flow (UDF)**, **MVVM**, and modular component separation:

```
┌─────────────────────────────────────────────────────────────┐
│                    Jetpack Compose UI                       │
│      (Screens, Navigation, Theming, ViewModels)             │
└──────────────────────────────┬──────────────────────────────┘
                               │ StateFlow / Events
┌──────────────────────────────▼──────────────────────────────┐
│                    Domain & Playback Layer                  │
│   (MusicService, Media3 ExoPlayer, Cynk Together Client)    │
└──────────────┬───────────────────────────────┬──────────────┘
               │                               │
┌──────────────▼──────────────┐ ┌──────────────▼──────────────┐
│       Local Data Layer      │ │      Remote Network Layer   │
│   (Room Database, DataStore,│ │   (InnerTube, LRCLIB,       │
│    Offline Audio Storage)   │ │    Kugou, Together Relay)   │
└─────────────────────────────┘ └─────────────────────────────┘
```

- **UI Layer (`app/src/main/kotlin/.../ui`)**: Pure Jetpack Compose with reactive state management via Kotlin `StateFlow`.
- **Playback Engine (`app/src/main/kotlin/.../playback`)**: Foreground Media3 `MusicService` maintaining ExoPlayer instances, media queues, notification lifecycles, and audio effects.
- **Together Layer (`app/src/main/kotlin/.../together`)**: Ktor WebSocket client communicating with the Cynk Together Relay server for real-time room synchronization.
- **InnerTube API (`innertube`)**: Type-safe Kotlin multiplatform-ready client handling YouTube Music endpoints, search queries, charts, and stream resolution.
- **Database Layer (`app/src/main/kotlin/.../db`)**: SQLite backed by Android Room for local songs, playlists, cached artist metadata, and listening history.

---

## 🛠 Tech Stack

| Category | Technology |
|:---|:---|
| **Language** | [Kotlin](https://kotlinlang.org/) |
| **UI Framework** | [Jetpack Compose](https://developer.android.com/jetpack/compose) + [Material 3](https://m3.material.io/) |
| **Audio Playback** | [AndroidX Media3](https://developer.android.com/guide/topics/media/media3) / [ExoPlayer](https://github.com/androidx/media) |
| **Architecture** | MVVM + Unidirectional Data Flow (UDF) |
| **Dependency Injection** | [Dagger Hilt](https://dagger.dev/hilt/) |
| **Local Database** | [Room](https://developer.android.com/training/data-storage/room) + SQLite |
| **Asynchronous & Flow** | [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines) & StateFlow / SharedFlow |
| **Image Loading** | [Coil](https://coil-kt.github.io/coil/) |
| **Networking & WebSockets** | [Ktor Client](https://ktor.io/) & [Retrofit](https://square.github.io/retrofit/) / OkHttp |
| **Relay Backend** | [Node.js](https://nodejs.org/) + [TypeScript](https://www.typescriptlang.org/) + `ws` |
| **Build System** | Gradle Kotlin DSL (`build.gradle.kts`) |

---

## 📂 Project Structure

```text
Cynk/
├── app/                      # Main Android application module (UI, ViewModels, Playback, DB)
│   └── src/main/kotlin/com/nikhil/yt/
│       ├── db/               # Room Database entities, DAOs, and migrations
│       ├── download/         # Offline download manager and audio cache
│       ├── lyrics/           # Lyrics provider manager, romanization, and synchronization
│       ├── playback/         # Media3 MusicService, player connection, queue managers
│       ├── spotify/          # Spotify playlist and metadata resolver
│       ├── together/         # Cynk Together client, host, models, and clock sync
│       ├── ui/               # Jetpack Compose screens, components, themes, menus
│       └── viewmodels/       # Screen ViewModels (Home, Library, Stats, Search, etc.)
├── innertube/                # YouTube Music InnerTube parser and API client
├── cynk-together-server/     # Node.js/TypeScript real-time WebSocket relay server
├── betterlyrics/             # BetterLyrics provider integration
├── lrclib/                   # LRCLIB API client integration
├── kugou/                    # Kugou lyrics provider integration
├── lastfm/                   # Last.fm scrobbling and artist info integration
├── kizzy/                    # Discord Rich Presence RPC integration
└── canvas/                   # Spotify Canvas video playback integration
```

---

## 📸 Screenshots

> *Screenshots will be added in upcoming releases.*

| Home & Discover | Now Playing & Lyrics | Cynk Together | Statistics |
|:---:|:---:|:---:|:---:|
| *(Coming Soon)* | *(Coming Soon)* | *(Coming Soon)* | *(Coming Soon)* |

---

## 🚀 Building the Project

### Prerequisites
- **Android Studio**: Ladybug (2024.2.1) or newer
- **JDK**: Java 17 or higher
- **Android SDK**: API Level 35 (compileSdk), API Level 26+ (minSdk)

### Build Android App via CLI
```bash
# Clone the repository
git clone https://github.com/ayushhh7/Cynk.git
cd Cynk

# Run unit tests
./gradlew testArm64DebugUnitTest

# Build Debug APK
./gradlew assembleDebug

# Built APK will be located at:
# app/build/outputs/apk/arm64/debug/app-arm64-debug.apk
```

### Build & Run Cynk Together Relay Server
```bash
cd cynk-together-server

# Install dependencies
npm install

# Build TypeScript
npm run build

# Start Relay Server
npm start
```

---

## ⚖️ License

This project is licensed under the **GNU General Public License v3.0** (GPL-3.0). See the [LICENSE](LICENSE) file for full details.

```
Cynk — Android Music Client
Copyright (C) 2026

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
```

---

## 🙌 Acknowledgements

Cynk builds upon and is grateful to the broader open-source music community. Sincere appreciation and credit to the upstream projects, contributors, and libraries that made this possible:

- **[InnerTune](https://github.com/z-huang/InnerTune)** by Z-Huang — foundational YouTube Music architecture and concept.
- **[SimpMusic](https://github.com/maxrave-dev/SimpMusic)** by maxrave-dev — streaming design, UI components, and player utilities.
- **[ViMusic](https://github.com/vfsfitvnm/ViMusic)** by vfsfitvnm — pioneering Android music client innovations.
- **[Velune](https://github.com/nikhilvishwakarma00/Velune)** by Nikhil Vishwakarma — codebase lineage and initial structure.
- **[BetterLyrics](https://github.com/boc-boc-boc/better-lyrics)**, **[LRCLIB](https://lrclib.net/)**, and **[Kugou](https://www.kugou.com/)** — lyrics databases and synchronization providers.
- **[Kizzy](https://github.com/dead8309/Kizzy)** by dead8309 — Discord Rich Presence client.
- **[NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor)** — YouTube streaming utilities.
