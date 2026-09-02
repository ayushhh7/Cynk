# 🛠️ Engineering & Build Guide

This document defines the protocols for setting up the development environment, understanding the underlying technology stack, and compiling **Cynk** from source.

---

## 🏗️ Operational Readiness

To ensure build stability and environment parity, the following hardware and software configurations are recommended.

### **Development Environment**

* **IDE:** [Android Studio](https://developer.android.com/studio) **Ladybug (2024.2.1) or newer**.
* **Java Runtime:** **JDK 21** (Amazon Corretto or Azul Zulu recommended for deterministic builds).
* **Android SDK:** API Level 36 (target), API Level 26 (minimum).
* **Version Control:** Git 2.40+.

### **Technical DNA (Skill Requirements)**

The Cynk codebase is built on a modern, reactive architecture. Contributors are expected to have a high level of familiarity with:

* **Kotlin (Advanced):** Proficiency in Coroutines, Flow API, and functional paradigms.
* **Jetpack Compose:** Understanding of State Hoisting, Recomposition optimization, and Material 3 design systems.
* **Gradle (KTS):** Ability to navigate Kotlin DSL build scripts and Version Catalogs (`libs.versions.toml`).
* **Modern Android Architecture:** Deep understanding of MVVM, Repository patterns, and UDF (Unidirectional Data Flow).

---

## 📐 Architectural Manifesto

Cynk follows a clean modular architecture:

1. **UI Layer (Compose):** Handles user interactions and renders state emitted by ViewModels.
2. **Domain Layer:** Contains business logic, Use Cases, recommendation algorithms, and high-level audio processing interfaces.
3. **Data Layer:** Manages the single source of truth—coordinating between YouTube Music, Room database, and caching pipelines.
4. **Service Layer (Media3):** A specialized background layer managing `MediaSession`, ExoPlayer, and the real-time Cynk Together synchronization engine.

---

## 🚀 Environment Initialization

1. **Clone the Source:**
```bash
git clone https://github.com/ayushhh7/Cynk.git
cd Cynk
```

2. **Secret Management:**
Cynk uses a modular properties system. If your build requires specific API keys (e.g., Discord Client IDs, Last.fm credentials), define them in your `local.properties`:
```properties
# Path to your Android SDK
sdk.dir=/Users/yourname/Library/Android/sdk
```

3. **Syncing the Core:**
Open the project in Android Studio. The IDE will automatically trigger a Gradle sync. We use **Version Catalogs** to ensure all dependencies (Media3, Hilt, Compose) are locked to tested versions.

---

## 📦 Build Pipelines

Use the Gradle Wrapper to execute verified build scripts.

| Command | Output | Context |
| --- | --- | --- |
| `./gradlew assembleArm64Debug` | `app-arm64-debug.apk` | Fast local testing on ARM64 device/emulator. |
| `./gradlew assembleDebug` | `app-debug.apk` | Universal debug build for testing. |
| `./gradlew assembleRelease` | `app-release.apk` | Production-ready, R8-optimized build. |
| `./gradlew bundleRelease` | `app-release.aab` | Optimized bundle for distribution. |
| `./gradlew clean` | `N/A` | Flushes build cache to resolve sync issues. |

---

## 🛡️ Code Quality & Static Analysis

Before initiating a Pull Request, every contributor must run the following quality gates:

* **Linting:** `./gradlew lintDebug` (Ensures adherence to Android XML/Compose standards).
* **Unit Tests:** `./gradlew testArm64DebugUnitTest` (Runs architectural unit tests).
* **Build Check:** `./gradlew assembleArm64Debug` (Verifies successful compilation).

---

## ⚖️ Troubleshooting

> [!IMPORTANT]
> **Heap Memory:** If you experience `GC overhead limit exceeded`, ensure your `gradle.properties` has sufficient memory allocated:
> `org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g`

> [!WARNING]
> **Java Version:** Cynk uses Java 21 toolchain. Make sure Android Studio is configured to use JDK 21 in `Settings -> Build, Execution, Deployment -> Build Tools -> Gradle`.

---

<div align="center">
<sub>Cynk: Sink In With Music.</sub>
</div>