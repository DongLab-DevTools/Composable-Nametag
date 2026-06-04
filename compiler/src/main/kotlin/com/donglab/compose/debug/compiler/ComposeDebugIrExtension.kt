package com.donglab.compose.debug.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

class ComposeDebugIrExtension(
    private val configuration: CompilerConfiguration,
) : IrGenerationExtension {
    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
    ) {
        val skipConfig = SkipConfig(
            packages = configuration.get(ComposeDebugCommandLineProcessor.KEY_SKIP_PACKAGES).orEmpty(),
            namePatterns = configuration.get(ComposeDebugCommandLineProcessor.KEY_SKIP_NAME_PATTERNS).orEmpty(),
            annotations = configuration.get(ComposeDebugCommandLineProcessor.KEY_SKIP_ANNOTATIONS).orEmpty(),
        )
        moduleFragment.transform(ComposeDebugTransformer(pluginContext, skipConfig), null)
    }
}
