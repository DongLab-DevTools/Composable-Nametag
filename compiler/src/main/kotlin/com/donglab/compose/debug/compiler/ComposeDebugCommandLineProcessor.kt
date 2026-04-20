package com.donglab.compose.debug.compiler

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import java.util.Properties

@OptIn(ExperimentalCompilerApi::class)
class ComposeDebugCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = loadGroup() + ".compiler"

    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        CliOption(
            optionName = OPTION_SKIP_PACKAGE,
            valueDescription = "<package-prefix>",
            description = "Package FQN prefix to skip (repeatable)",
            required = false,
            allowMultipleOccurrences = true,
        ),
        CliOption(
            optionName = OPTION_SKIP_NAME_PATTERN,
            valueDescription = "<regex>",
            description = "Function name regex to skip (repeatable)",
            required = false,
            allowMultipleOccurrences = true,
        ),
        CliOption(
            optionName = OPTION_SKIP_ANNOTATION,
            valueDescription = "<annotation-fqn>",
            description = "Annotation FQN to skip (repeatable)",
            required = false,
            allowMultipleOccurrences = true,
        ),
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        val key = when (option.optionName) {
            OPTION_SKIP_PACKAGE -> KEY_SKIP_PACKAGES
            OPTION_SKIP_NAME_PATTERN -> KEY_SKIP_NAME_PATTERNS
            OPTION_SKIP_ANNOTATION -> KEY_SKIP_ANNOTATIONS
            else -> return
        }
        val existing = configuration.get(key).orEmpty()
        configuration.put(key, existing + value)
    }

    companion object {
        const val OPTION_SKIP_PACKAGE = "skipPackage"
        const val OPTION_SKIP_NAME_PATTERN = "skipNamePattern"
        const val OPTION_SKIP_ANNOTATION = "skipAnnotation"

        val KEY_SKIP_PACKAGES = CompilerConfigurationKey.create<List<String>>("composable-nametag.skipPackages")
        val KEY_SKIP_NAME_PATTERNS = CompilerConfigurationKey.create<List<String>>("composable-nametag.skipNamePatterns")
        val KEY_SKIP_ANNOTATIONS = CompilerConfigurationKey.create<List<String>>("composable-nametag.skipAnnotations")

        private fun loadGroup(): String {
            val props = Properties()
            ComposeDebugCommandLineProcessor::class.java.classLoader
                .getResourceAsStream("composable-nametag.properties")
                ?.use { props.load(it) }
            return props.getProperty("GROUP")
                ?: error("composable-nametag.properties is missing or GROUP is not set")
        }
    }
}
