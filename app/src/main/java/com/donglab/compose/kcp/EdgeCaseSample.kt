package com.donglab.compose.kcp

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.Path
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorComposable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

// 라이브러리가 앱에 영향을 주지 않는지 확인하는 경계 사례
// - 벡터 composition 안의 Composable (UI 가 아닌 applier)
// - LocalView 가 없는 headless composition (Molecule · Glance 같은 경우)
// - @ReadOnlyComposable 함수를 조건부로 호출 (컴파일러가 마커를 넣지 않아야 함)
// - 별도 윈도우(Dialog) 의 라벨

private val EdgeGray = Color(0xFF3A3A3A)
private val EdgeText = Color(0xFFB0B0B0)

@Composable
internal fun EdgeCases() {
    var accent by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VectorIcon()
            AccentSwatch(accent)
            EdgeButton("Toggle accent") { accent = !accent }
            EdgeButton("Open dialog") { showDialog = true }
        }
        HeadlessPresenterHost()
    }

    if (showDialog) EdgeDialog(onDismiss = { showDialog = false })
}

@Composable
private fun EdgeButton(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 12.sp,
        modifier = Modifier
            .background(EdgeGray, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

// ── 벡터 composition ──────────────────────────────────────

@Composable
private fun VectorIcon() {
    val painter = rememberVectorPainter(
        defaultWidth = 40.dp,
        defaultHeight = 40.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = false,
    ) { _, _ -> StarPath() }
    Image(painter = painter, contentDescription = null, modifier = Modifier.size(40.dp))
}

@Composable
@VectorComposable
private fun StarPath() {
    Path(
        pathData = PathParser().parsePathString("M12,2L15,9L22,9L16.5,13.5L18.5,21L12,16.8L5.5,21L7.5,13.5L2,9L9,9Z").toNodes(),
        fill = SolidColor(Color(0xFFFFCA28)),
    )
}

// ── @ReadOnlyComposable 조건부 호출 ───────────────────────

@Composable
private fun AccentSwatch(accent: Boolean) {
    val color = if (accent) AccentColor() else EdgeGray
    Box(modifier = Modifier.size(40.dp).background(color, RoundedCornerShape(8.dp)))
}

@Composable
@ReadOnlyComposable
private fun AccentColor(): Color {
    return if (isSystemInDarkTheme()) Color(0xFF80CBC4) else Color(0xFF00897B)
}

// ── LocalView 가 없는 headless composition ────────────────

@Composable
private fun HeadlessPresenterHost() {
    var ticks by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val scope = CoroutineScope(AndroidUiDispatcher.Main)
        val recomposer = Recomposer(scope.coroutineContext)
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        val composition = Composition(UnitApplier, recomposer)
        composition.setContent { HeadlessPresenter(onTick = { ticks++ }) }
        onDispose {
            composition.dispose()
            recomposer.cancel()
            scope.cancel()
        }
    }
    Text(text = "Headless composition ticks: $ticks", color = EdgeText, fontSize = 12.sp)
}

@Composable
private fun HeadlessPresenter(onTick: () -> Unit) {
    LaunchedEffect(Unit) { onTick() }
}

private object UnitApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = Unit
    override fun insertBottomUp(index: Int, instance: Unit) = Unit
    override fun remove(index: Int, count: Int) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun onClear() = Unit
}

// ── 별도 윈도우 ───────────────────────────────────────────

@Composable
private fun EdgeDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        DialogCard(onDismiss)
    }
}

@Composable
private fun DialogCard(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .background(EdgeGray, RoundedCornerShape(12.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Separate window", color = Color.White, fontSize = 16.sp)
        EdgeButton("Close", onDismiss)
    }
}
