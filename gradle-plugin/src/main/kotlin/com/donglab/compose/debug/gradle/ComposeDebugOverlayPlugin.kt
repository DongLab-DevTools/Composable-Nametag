package com.donglab.compose.debug.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

/**
 * Gradle plugin that automatically registers:
 * 1. The compiler plugin on `kotlinCompilerPluginClasspath` (only for supported Kotlin versions)
 * 2. The runtime library as an `implementation` dependency (always, regardless of Kotlin version)
 *
 * ## Kotlin version safety
 * The compiler plugin uses Kotlin internal IR APIs that change between versions.
 * If the project's Kotlin version is not in [SUPPORTED_KOTLIN_VERSIONS]:
 * - A warning is logged once per project
 * - The compiler plugin is **not** applied (no IR transformation)
 * - The runtime library is still added (`__debugComposableName()` is never called → zero impact)
 * - **Build is never broken** by this plugin
 *
 * ## Versioning
 * The compiler artifact uses `{kotlin-version}-{library-version}` format (e.g. `2.1.21-0.0.4-alpha01`),
 * similar to KSP. The Gradle plugin automatically resolves the correct compiler version
 * based on the project's Kotlin version.
 *
 * Usage:
 * ```kotlin
 * plugins {
 *     id("io.github.dongx0915.composable.nametag") version "0.0.4-alpha01"
 * }
 * ```
 */
class ComposeDebugOverlayPlugin : KotlinCompilerPluginSupportPlugin {

    private var resolvedKotlinVersion: String? = null

    override fun apply(target: Project) {
        super.apply(target)

        target.extensions.create(
            ComposableNametagExtension.NAME,
            ComposableNametagExtension::class.java,
        )

        target.dependencies.add(
            "debugImplementation",
            "$GROUP_ID:$RUNTIME_ARTIFACT_ID:$VERSION",
        )
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        val project = kotlinCompilation.target.project

        val isDebug = kotlinCompilation.name.contains("debug", ignoreCase = true)
        if (!isDebug) return false

        val kotlinVersion = project.resolveKotlinVersion()
        resolvedKotlinVersion = kotlinVersion
        val isSupported = kotlinVersion in SUPPORTED_KOTLIN_VERSIONS

        val root = project.rootProject
        val warnKey = "compose.debug.overlay.warned"
        val alreadyWarned = root.extensions.extraProperties.has(warnKey)
        if (!isSupported && !alreadyWarned) {
            root.extensions.extraProperties.set(warnKey, true)
            project.logger.warn(
                buildString {
                    appendLine()
                    appendLine("⚠️  composable-nametag: Kotlin $kotlinVersion is not supported.")
                    appendLine("    Supported versions: $SUPPORTED_KOTLIN_VERSIONS")
                    appendLine("    The compiler plugin will be DISABLED for this build.")
                    appendLine("    The runtime library is still included but has no effect (zero overhead).")
                    appendLine("    → Your build and app are NOT affected.")
                }
            )
        }

        return isSupported
    }

    override fun getCompilerPluginId(): String = COMPILER_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact {
        val kotlinVersion = resolvedKotlinVersion ?: "2.1.21"
        return SubpluginArtifact(
            groupId = GROUP_ID,
            artifactId = COMPILER_ARTIFACT_ID,
            version = "$kotlinVersion-$VERSION",
        )
    }

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>,
    ): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val extension = project.extensions.findByType(ComposableNametagExtension::class.java)
        return project.provider {
            if (extension == null) return@provider emptyList()
            buildList {
                extension.skipPackages.orNull.orEmpty().forEach {
                    add(SubpluginOption(OPTION_SKIP_PACKAGE, it))
                }
                extension.skipNamePatterns.orNull.orEmpty().forEach {
                    add(SubpluginOption(OPTION_SKIP_NAME_PATTERN, it))
                }
                extension.skipAnnotations.orNull.orEmpty().forEach {
                    add(SubpluginOption(OPTION_SKIP_ANNOTATION, it))
                }
            }
        }
    }

    private fun Project.resolveKotlinVersion(): String {
        return try {
            val ext = extensions.findByName("kotlin")
                ?: project.rootProject.extensions.findByName("kotlin")
            ext?.javaClass?.getMethod("getCoreLibrariesVersion")
                ?.invoke(ext) as? String
                ?: detectFromClasspath()
        } catch (_: Exception) {
            detectFromClasspath()
        }
    }

    private fun Project.detectFromClasspath(): String {
        return try {
            buildscript.configurations
                .flatMap { it.resolvedConfiguration.resolvedArtifacts }
                .firstOrNull { it.moduleVersion.id.group == "org.jetbrains.kotlin" && it.name == "kotlin-gradle-plugin" }
                ?.moduleVersion?.id?.version
                ?: configurations
                    .filter { it.isCanBeResolved }
                    .flatMap { runCatching { it.resolvedConfiguration.resolvedArtifacts }.getOrDefault(emptySet()) }
                    .firstOrNull { it.moduleVersion.id.group == "org.jetbrains.kotlin" && it.name.startsWith("kotlin-stdlib") }
                    ?.moduleVersion?.id?.version
                ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }

    companion object {
        private val GROUP_ID = BuildConfig.GROUP
        private val COMPILER_PLUGIN_ID = "${BuildConfig.GROUP}.compiler"
        private const val COMPILER_ARTIFACT_ID = "composable-nametag-compiler"
        private const val RUNTIME_ARTIFACT_ID = "composable-nametag-runtime"
        private val VERSION = BuildConfig.VERSION

        val SUPPORTED_KOTLIN_VERSIONS = setOf(
            "2.1.21",
            "2.2.0", "2.2.10", "2.2.20", "2.2.21",
            "2.3.0", "2.3.10", "2.3.20",
        )

        private const val OPTION_SKIP_PACKAGE = "skipPackage"
        private const val OPTION_SKIP_NAME_PATTERN = "skipNamePattern"
        private const val OPTION_SKIP_ANNOTATION = "skipAnnotation"
    }
}
