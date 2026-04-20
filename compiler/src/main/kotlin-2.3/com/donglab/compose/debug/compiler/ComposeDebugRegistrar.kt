package com.donglab.compose.debug.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import java.util.Properties

@OptIn(ExperimentalCompilerApi::class)
class ComposeDebugRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = loadGroup() + ".compiler"
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(
        configuration: CompilerConfiguration,
    ) {
        IrGenerationExtension.registerExtension(ComposeDebugIrExtension(configuration))
    }

    private fun loadGroup(): String {
        val props = Properties()
        javaClass.classLoader.getResourceAsStream("composable-nametag.properties")?.use {
            props.load(it)
        }
        return props.getProperty("GROUP")
            ?: error("composable-nametag.properties is missing or GROUP is not set")
    }
}
