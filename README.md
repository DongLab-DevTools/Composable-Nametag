# Composable-Nametag

[![Hits](https://myhits.vercel.app/api/hit/https%3A%2F%2Fgithub.com%2FDongLab-DevTools%2FComposable-Nametag%3Ftab%3Dreadme-ov-file?color=blue&label=hits&size=small)](https://myhits.vercel.app)
[![Platform](https://img.shields.io/badge/platform-Android-3DDC84?style=flat-square&logo=android)](https://developer.android.com)
[![Min SDK](https://img.shields.io/badge/min%20sdk-24-green?style=flat-square)](https://developer.android.com)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.dongx0915.composable.nametag/composable-nametag-runtime)](https://central.sonatype.com/artifact/io.github.dongx0915.composable.nametag/composable-nametag-runtime)
![GitHub stars](https://img.shields.io/github/stars/DongLab-DevTools/Composable-Nametag.svg)

**[한국어 README](./README_ko.md)**

## Overview

<img width="2048" height="1152" alt="image" src="https://github.com/user-attachments/assets/7043eb0a-0571-4709-b2b8-787bdf1d40b6" />


<br>
<br>

Composable-Nametag is a debug tool that overlays the name of every `@Composable` function as a label on your screen.

**Without modifying any existing code**, the Kotlin Compiler Plugin (KCP) automatically injects labels at compile time.  
See each Composable's name directly on screen, making layout debugging and code review faster.

<br>

## Screenshots
<div align="center">
    <img width="800" alt="image" src="https://github.com/user-attachments/assets/7d1f5e8a-0263-4db2-b7ef-350f666782d0" />
</div>

<br>

## Features

- **Auto injection** — The Compiler Plugin injects labels at compile time without touching your existing code.
- **Debug only** — The compiler plugin and runtime are applied only to `debug` builds. Release builds contain zero library code — no IR injection, no runtime dependency.
- **Zero overhead** — Works via IR transformation at compile time. In release builds, nothing is injected or included at all.
- **Noise filtering** — Only PascalCase Composables get labels; lambdas, `remember`, property accessors, etc. are ignored.
- **Build safe** — Unsupported Kotlin versions only disable the compiler plugin — the build always succeeds.

<br>
<br>

## Installation

### Repository Setup

Choose one of the following depending on where the library is hosted.

#### Maven Central (default)

Add `mavenCentral()` to your plugin repositories in `settings.gradle.kts`:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()  // ← required
        google()
    }
}
```

If using Convention Plugin, also add to `build-logic/settings.gradle.kts` `dependencyResolutionManagement.repositories`.

#### GitHub Packages

The library is also available via GitHub Packages. Add the repository to `settings.gradle.kts`:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        maven {
            url = uri("https://maven.pkg.github.com/{owner}/{repo}")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.token").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

If using Convention Plugin, also add the same `maven { ... }` block to `build-logic/settings.gradle.kts` `dependencyResolutionManagement.repositories`.

> Set credentials in `~/.gradle/gradle.properties`:
> ```properties
> gpr.user=your-github-username
> gpr.token=ghp_xxxxxxxxxxxxxxxxxxxx  # read:packages scope required
> ```

<br>
<br>

### Option A. `plugins {}` block (standard)

Apply the plugin in each **Compose module**'s `build.gradle.kts`.
It **must be declared before** the Compose plugin.

```kotlin
// feature/home/build.gradle.kts (Compose module)
plugins {
    id("{group-id}") version "{library-version}" // Must be before the Compose plugin
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
    // ...
}
```

> [!note]
> No additional `implementation` dependency is needed — the plugin adds the runtime library automatically.
>
> The `{group-id}` is the GROUP configured during publishing (e.g., `io.github.dongx0915.composable.nametag` for Maven Central).

<br>
<br>

### Option B. Convention Plugin

For projects using a Convention Plugin structure (e.g., `build-logic`):

<br>

**Step 1.** Add the plugin artifact to your `build-logic/convention/build.gradle.kts`:

```kotlin
dependencies {
    implementation("{group-id}:composable-nametag-gradle:{library-version}")
}
```

<br>

**Step 2.** Apply it inside your Compose Convention Plugin, **before** the Compose plugin:

```kotlin
// e.g., AndroidComposeConventionPlugin.kt
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("{group-id}") // Must be before the Compose plugin
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            // ...
        }
    }
}
```

<br>
<br>

### Requirements

- Android API 24 (Android 7.0) or higher
- Kotlin **2.1.21 ~ 2.3.20** (see [Supported Versions](#kotlin-version-compatibility))
- Jetpack Compose (BOM 2025.05.01 or compatible)
- JDK 17+

<br>
<br>

## Usage

### Enable the overlay

```kotlin
// Application class or wherever you want to toggle
ComposeDebugConfig.enabled = true
```

That's it. All `@Composable` function names will appear as labels on screen.

<br>
<br>

## How It Works

<div align="center">
    <img src="docs/architecture_en.svg" width="700" alt="How Composable-Nametag Works" />
</div>

<br>
<br>

## Filtering Rules

| Condition | Behavior |
|-----------|----------|
| PascalCase `@Composable` | Label shown |
| camelCase (remember, modifier, etc.) | Skipped |
| Lambda / anonymous | Skipped |
| Property accessor | Skipped |
| `__` prefix | Skipped |

<br>
<br>

## Kotlin Version Compatibility

The compiler plugin uses Kotlin IR internal APIs, so it is published **per Kotlin version**.
The Gradle plugin auto-detects your Kotlin version and resolves the matching compiler artifact.

| Kotlin Version | Supported |
|---------------|-----------|
| 2.1.21 | ✅ |
| 2.2.0 | ✅ |
| 2.2.10 | ✅ |
| 2.2.20 | ✅ |
| 2.2.21 | ✅ |
| 2.3.0 | ✅ |
| 2.3.10 | ✅ |
| 2.3.20 | ✅ |

- **Unsupported versions**: Logs a warning once and disables only the compiler plugin. The build proceeds normally.

```
⚠️  compose-debug-overlay: Kotlin X.Y.Z is not supported.
    → Your build and app are NOT affected.
```

<br>
<br>

## Release Safety

Composable-Nametag is **completely excluded from release builds**:

| | Debug Build | Release Build |
|---|---|---|
| Compiler Plugin (IR injection) | Active | Not applied |
| Runtime Library (APK) | Included | Not included |

- The Gradle plugin uses `debugImplementation` — the runtime library is not packaged into release APKs.
- The compiler plugin's `isApplicable` returns `false` for non-debug compilations — no code is injected into release builds.
- **Result**: Release APK contains zero traces of this library. No performance impact, no binary size increase.

<br>
<br>

## Tech Stack

- Kotlin 2.1.21 ~ 2.3.20
- AGP 8.6.1
- Compose BOM 2025.05.01
- Gradle 8.7

<br>
<br>

## License

Apache License 2.0
