package com.donglab.compose.debug.compiler

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class ComposeDebugTransformer(
    private val pluginContext: IrPluginContext,
) : IrElementTransformerVoidWithContext() {

    private val composableAnnotationFqName = FqName("androidx.compose.runtime.Composable")

    private val debugOverlayFnSymbol: IrSimpleFunctionSymbol? by lazy {
        val callableId = CallableId(
            packageName = FqName("com.donglab.compose.debug"),
            callableName = Name.identifier("__debugComposableName"),
        )
        pluginContext.referenceFunctions(callableId).firstOrNull()
    }

    override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        val result = super.visitFunctionNew(declaration)

        val fnSymbol = debugOverlayFnSymbol ?: return result
        if (!declaration.hasAnnotation(composableAnnotationFqName)) return result

        val functionName = declaration.name.asString()
        if (shouldSkip(functionName, declaration)) return result

        val body = declaration.body as? IrBlockBody ?: return result

        val builder = DeclarationIrBuilder(pluginContext, declaration.symbol)
        val debugCall = builder.irCall(fnSymbol).apply {
            putValueArgument(0, builder.irString(functionName))
        }

        body.statements.add(0, debugCall)
        return result
    }

    private fun shouldSkip(name: String, declaration: IrFunction): Boolean {
        if (name.startsWith("<")) return true
        if (name == "invoke") return true
        if (declaration is IrSimpleFunction && declaration.correspondingPropertySymbol != null) return true
        if (name.length <= 1) return true
        if (name.first().isLowerCase()) return true
        if (name.startsWith("__")) return true
        return false
    }
}
