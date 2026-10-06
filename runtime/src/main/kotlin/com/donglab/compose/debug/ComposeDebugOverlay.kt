package com.donglab.compose.debug

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.platform.LocalView

/**
 * Debug marker that tags the calling composable with its function name.
 *
 * Injected automatically by the Compose Debug Compiler Plugin at the top of every labeled
 * `@Composable`. It emits no layout node — it only leaves a [NameTag] in the slot table.
 * [NametagTree] later finds the tagged group, measures what that composable actually drew
 * and paints the label on top of the window.
 *
 * Nothing here may throw into the host app: compositions that are not Android UI
 * (Glance, Molecule, vector graphics …) are skipped, and registration runs through [NametagSafety].
 * While [ComposeDebugConfig.enabled] is false this returns right after one state read.
 *
 * Do NOT call manually.
 */
@Composable
fun __debugComposableName(name: String) {
    if (!ComposeDebugConfig.enabled || NametagSafety.broken) return

    val composer = currentComposer
    // LayoutNode 를 쓰는 UI composition 에서만 — 그 밖의 composition 에는 LocalView 가 없을 수 있다
    if (composer.applier.current !is LayoutInfo) return
    val view = composer.localViewOrNull() ?: return

    // 이 그룹의 부모 그룹 = name 에 해당하는 Composable 그룹 (NametagTree 가 찾는 표식)
    // remember 대신 슬롯에 직접 기록 — remember 는 source information 이 켜지면 하위 그룹으로 분리됨
    val cached = composer.rememberedValue()
    if (cached !is NameTag || cached.name != name) composer.updateRememberedValue(NameTag(name))

    val compositionData = composer.compositionData
    DisposableEffect(compositionData, view) {
        val root = view.rootView
        NametagRegistry.register(root, compositionData, view)
        onDispose { NametagRegistry.unregister(root, compositionData) }
    }
}

/** Slot table marker left by [__debugComposableName]. */
internal class NameTag(val name: String)

// LocalView.current 와 같은 호출이지만, 제공되지 않은 composition 에서 예외 대신 null
@OptIn(InternalComposeApi::class)
private fun Composer.localViewOrNull(): View? =
    try {
        consume(LocalView)
    } catch (_: Throwable) {
        null
    }
