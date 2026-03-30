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
 * Usage:
 * ```kotlin
 * plugins {
 *     id("com.donglab.compose.debug.overlay") version "1.0.0"
 * }
 * ```
 */
class ComposeDebugOverlayPlugin : KotlinCompilerPluginSupportPlugin {

    override fun apply(target: Project) {
        super.apply(target)

        // Always add runtime dependency regardless of Kotlin version compatibility.
        // This ensures ComposeDebugConfig and __debugComposableName are on the classpath
        // even when the compiler plugin is disabled.
        // When disabled: __debugComposableName() is never called → zero overhead.
        // When enabled: compiler injects calls → labels appear on screen.
        target.dependencies.add(
            "implementation",
            "$GROUP_ID:$RUNTIME_ARTIFACT_ID:$VERSION",
        )
    }

    /**
     * Determines whether the compiler plugin is applied to this compilation.
     * Returns false for unsupported Kotlin versions → compiler plugin is skipped entirely.
     */
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        val project = kotlinCompilation.target.project
        val kotlinVersion = project.resolveKotlinVersion()
        val isSupported = kotlinVersion in SUPPORTED_KOTLIN_VERSIONS

        // Log warning once per build (using root project extra property)
        val root = project.rootProject
        val warnKey = "compose.debug.overlay.warned"
        val alreadyWarned = root.extensions.extraProperties.has(warnKey)
        if (!isSupported && !alreadyWarned) {
            root.extensions.extraProperties.set(warnKey, true)
            project.logger.warn(
                buildString {
                    appendLine()
                    appendLine("⚠️  compose-debug-overlay: Kotlin $kotlinVersion is not supported.")
                    appendLine("    Supported versions: $SUPPORTED_KOTLIN_VERSIONS")
                    appendLine("    The debug overlay compiler plugin will be DISABLED for this build.")
                    appendLine("    The runtime library is still included but has no effect (zero overhead).")
                    appendLine("    → Your build and app are NOT affected.")
                }
            )
        }

        return isSupported
    }

    override fun getCompilerPluginId(): String = COMPILER_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = GROUP_ID,
        artifactId = COMPILER_ARTIFACT_ID,
        version = VERSION,
    )

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>,
    ): Provider<List<SubpluginOption>> {
        return kotlinCompilation.target.project.provider { emptyList() }
    }

    /**
     * Resolves the Kotlin plugin version from the project.
     * Falls back to "unknown" if detection fails — which triggers the fallback path.
     */
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
        private const val GROUP_ID = "com.donglab.compose.debug"
        private const val COMPILER_PLUGIN_ID = "com.donglab.compose.debug.overlay.compiler"
        private const val COMPILER_ARTIFACT_ID = "compose-debug-overlay-compiler"
        private const val RUNTIME_ARTIFACT_ID = "compose-debug-overlay-runtime"
        private const val VERSION = "1.0.0"

        /**
         * Kotlin versions that this compiler plugin has been tested against.
         * The IR API (`irCall`, `IrBlockBody`, etc.) can change between minor versions,
         * so we only enable the compiler plugin for known-good versions.
         *
         * To add support for a new version:
         * 1. Build the compiler module against that Kotlin version
         * 2. Run the sample app to verify no NoSuchMethodError / internal compiler errors
         * 3. Add the version string here
         */
        val SUPPORTED_KOTLIN_VERSIONS = setOf(
            "2.1.21",
        )

    }
}
