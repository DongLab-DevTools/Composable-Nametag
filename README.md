# Composable-Nametag

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84?style=flat-square&logo=android)](https://developer.android.com)
[![Min SDK](https://img.shields.io/badge/min%20sdk-24-green?style=flat-square)](https://developer.android.com)

**[한국어 README](./README_ko.md)**

## Overview

![sample](https://github.com/DongLab-DevTools/Composable-Nametag/blob/main/.github/docs/images/sample.png)

<br>
<br>

Composable-Nametag is a debug tool that overlays the name of every `@Composable` function as a label on your screen.

**Without modifying any existing code**, the Kotlin Compiler Plugin (KCP) automatically injects labels at compile time.
When disabled, the plugin has zero runtime overhead.

<br>

## Features

- **Zero-touch instrumentation**: The Kotlin Compiler Plugin injects labels at compile time — no manual code changes needed
- **Zero overhead when disabled**: `ComposeDebugConfig.enabled = false` (default) skips all rendering immediately
- **Smart filtering**: Only labels top-level Composable functions (PascalCase); skips lambdas, `remember`, property accessors, etc.
- **Kotlin version safety**: Unsupported Kotlin versions disable only the compiler plugin — the build is never broken
- **Colorful staggered labels**: Each function gets a distinct color and vertical offset to avoid overlap

<br>

## Installation

### Option A. `plugins {}` block (standard)

Apply the plugin in each **Compose module**'s `build.gradle.kts`.
It **must be declared before** the Compose plugin.

```kotlin
// feature/home/build.gradle.kts (Compose module)
plugins {
    id("com.donglab.compose.debug.overlay") version "1.0.0" // Must be before the compose plugin
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
    // ...
}
```

> No additional `implementation` dependency is needed — the plugin adds the runtime library automatically.

### Option B. Convention Plugin

For projects using a Convention Plugin structure (e.g., `build-logic`):

**Step 1.** Add the plugin artifact to your `build-logic/build.gradle.kts`:

```kotlin
// build-logic/build.gradle.kts
dependencies {
    implementation("com.donglab.compose.debug:compose-debug-overlay-gradle:1.0.0")
}
```

**Step 2.** Apply it inside your Compose Convention Plugin, **before** the Compose plugin:

```kotlin
// e.g., AndroidComposeConventionPlugin.kt
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.donglab.compose.debug.overlay") // Must be before the compose plugin
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            // ...
        }
    }
}
```

<br>

### Requirements

- Android API 24 (Android 7.0) or higher
- Kotlin 2.1.21
- Jetpack Compose (BOM 2025.05.01 or compatible)

<br>

## Usage

### Enable the overlay

```kotlin
// Application class or wherever you want to toggle
ComposeDebugConfig.enabled = true
```

That's it. All `@Composable` function names will appear as labels on screen.

<br>

## How It Works

```
[ Compile time ]
@Composable
fun HomeScreen() {
    Column { ... }
}

       ↓ KCP auto-transforms

@Composable
fun HomeScreen() {
    __debugComposableName("HomeScreen")  ← injected
    Column { ... }
}

[ Runtime ]
enabled=true  → labels shown
enabled=false → immediate return (zero overhead)
```

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

## Kotlin Version Compatibility

The compiler plugin uses Kotlin IR internal APIs, so it is version-dependent.

- **Supported**: 2.1.21
- **Unsupported**: Logs a warning once and disables only the compiler plugin. The build proceeds normally.

```
⚠️  compose-debug-overlay: Kotlin X.Y.Z is not supported.
    → Your build and app are NOT affected.
```

<br>

## Tech Stack

- Kotlin 2.1.21
- AGP 8.6.1
- Compose BOM 2025.05.01
- Gradle 8.7

## License

Apache License 2.0
