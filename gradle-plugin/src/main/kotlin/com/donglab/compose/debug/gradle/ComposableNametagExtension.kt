package com.donglab.compose.debug.gradle

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import javax.inject.Inject

/**
 * DSL for configuring which `@Composable` functions to skip labeling.
 *
 * ```kotlin
 * composableNametag {
 *     skipPackages.addAll("com.myapp.internal", "com.myapp.designsystem.foundation")
 *     skipNamePatterns.addAll(".*Preview$", "^Themed.*")
 *     skipAnnotations.add("com.myapp.debug.NoNametag")
 * }
 * ```
 *
 * All filters are applied in addition to the built-in skip rules.
 */
abstract class ComposableNametagExtension @Inject constructor(objects: ObjectFactory) {
    /** Package FQN prefixes. A function whose declaring file package starts with any of these is skipped. */
    val skipPackages: ListProperty<String> = objects.listProperty(String::class.java)

    /** Java regex patterns. A function whose simple name fully matches any pattern is skipped. */
    val skipNamePatterns: ListProperty<String> = objects.listProperty(String::class.java)

    /** Fully-qualified annotation class names. A function annotated with any of these is skipped. */
    val skipAnnotations: ListProperty<String> = objects.listProperty(String::class.java)

    companion object {
        const val NAME = "composableNametag"
    }
}
