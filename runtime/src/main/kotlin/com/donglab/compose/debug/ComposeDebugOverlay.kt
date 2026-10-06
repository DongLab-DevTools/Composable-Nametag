package com.donglab.compose.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.platform.LocalView

/**
 * Debug marker that tags the calling composable with its function name.
 *
 * Injected automatically by the Compose Debug Compiler Plugin at the top of every labeled
 * `@Composable`. It emits no layout node — it only leaves a [NameTag] in the slot table.
 * [NametagTree] later finds the tagged group, measures what that composable actually drew
 * and paints the label on top of the window.
 *
 * Do NOT call manually.
 */
@Composable
fun __debugComposableName(name: String) {
    if (!ComposeDebugConfig.enabled) return

    // 이 그룹의 부모 그룹 = name 에 해당하는 Composable 그룹 (NametagTree 가 찾는 표식)
    // remember 대신 슬롯에 직접 기록 — remember 는 source information 이 켜지면 하위 그룹으로 분리됨
    val composer = currentComposer
    val cached = composer.rememberedValue()
    if (cached !is NameTag || cached.name != name) composer.updateRememberedValue(NameTag(name))

    val compositionData = composer.compositionData
    val view = LocalView.current
    DisposableEffect(compositionData, view) {
        val root = view.rootView
        NametagRegistry.register(root, compositionData, view)
        onDispose { NametagRegistry.unregister(root, compositionData) }
    }
}

/** Slot table marker left by [__debugComposableName]. */
internal class NameTag(val name: String)
