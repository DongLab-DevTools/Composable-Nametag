package com.donglab.compose.kcp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

// 홈 피드 형태의 샘플 — 라벨 표시 규칙을 눈으로 확인하기 위한 구조
// - LazyColumn 섹션 안의 LazyRow (반복되는 리스트 아이템)
// - 같은 영역을 감싸는 래퍼 체인 (MovieItem › Poster › AppTheme › Thumbnail)
// - 그리는 것이 없는 Effect, 안 떠 있는 Dialog
// - spacedBy 간격 비교 (라벨이 레이아웃을 바꾸지 않는지)

private val Gray400 = Color(0xFF8A8A8A)
private val Gray700 = Color(0xFF3A3A3A)

data class Shortcut(val lines: List<String>, val color: Color, val isNew: Boolean)

data class Movie(
    val color: Color,
    val isPaid: Boolean,
    val hasSubtitle: Boolean,
    val isAdult: Boolean,
)

private val shortcuts = listOf(
    Shortcut(listOf("New", "this week"), Color(0xFF7E57C2), isNew = true),
    Shortcut(listOf("Top", "charts"), Color(0xFF26A69A), isNew = true),
    Shortcut(listOf("Drama", "picks"), Color(0xFF42A5F5), isNew = false),
    Shortcut(listOf("Live", "sports"), Color(0xFFFFCA28), isNew = true),
    Shortcut(listOf("Kids", "zone"), Color(0xFFEF5350), isNew = false),
    Shortcut(listOf("Classic", "films"), Color(0xFF8D6E63), isNew = false),
).let { it + it }

private val movies = listOf(
    Movie(Color(0xFF5C6BC0), isPaid = true, hasSubtitle = true, isAdult = true),
    Movie(Color(0xFF66BB6A), isPaid = true, hasSubtitle = false, isAdult = false),
    Movie(Color(0xFFFF7043), isPaid = false, hasSubtitle = true, isAdult = false),
    Movie(Color(0xFF8D6E63), isPaid = true, hasSubtitle = false, isAdult = true),
).let { it + it + it }

private enum class SectionType { Shortcuts, Movies, Spacing }

private data class SectionSpec(val title: String, val type: SectionType)

private val sections = listOf(
    SectionSpec("Shortcuts", SectionType.Shortcuts),
    SectionSpec("Movies", SectionType.Movies),
    SectionSpec("spacedBy check", SectionType.Spacing),
)

@Composable
fun HomeFeedSample(modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier) {
        items(sections) { section ->
            Section(title = section.title) {
                when (section.type) {
                    SectionType.Shortcuts -> ShortcutRow(shortcuts)
                    SectionType.Movies -> MovieRow(movies)
                    SectionType.Spacing -> SpacingCheck()
                }
            }
        }
    }
    SettingsDialog(visible = false)
}

// ── 섹션 공통 (래퍼 체인 + Effect) ─────────────────────────

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    SectionContent(title, content)
}

@Composable
private fun SectionContent(title: String, content: @Composable () -> Unit) {
    ImpressionEffect(title)
    SectionContainer(title, content)
}

@Composable
private fun ImpressionEffect(key: String) {
    LaunchedEffect(key) { /* 노출 로그 자리 */ }
}

@Composable
private fun SectionContainer(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionHeader(title)
        content()
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        MoreButton()
    }
}

@Composable
private fun MoreButton() {
    Text(text = "More", color = Gray400, fontSize = 13.sp)
}

// ── 숏컷 줄 (아이템 안쪽이 서로 다른 영역) ─────────────────

@Composable
private fun ShortcutRow(items: List<Shortcut>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items) { ShortcutItem(it) }
    }
}

@Composable
private fun ShortcutItem(item: Shortcut) {
    Column(
        modifier = Modifier.width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ShortcutIcon(item)
        ShortcutTitle(item.lines)
    }
}

@Composable
private fun ShortcutIcon(item: Shortcut) {
    Box(modifier = Modifier.size(64.dp)) {
        IconImage(
            color = item.color,
            modifier = Modifier
                .align(Alignment.Center)
                .size(56.dp)
                .clip(CircleShape),
        )
        BadgeOverlay(isNew = item.isNew, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun IconImage(color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(color))
}

@Composable
private fun BadgeOverlay(isNew: Boolean, modifier: Modifier = Modifier) {
    if (isNew) NewBadge(modifier.offset(x = 20.dp, y = (-20).dp))
}

@Composable
private fun NewBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(Color(0xFFFF1744)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "N", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ShortcutTitle(lines: List<String>) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        lines.forEach { TitleLine(it) }
    }
}

@Composable
private fun TitleLine(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.requiredWidth(64.dp),
    )
}

// ── 영화 줄 (MovieItem › Poster › AppTheme › Thumbnail 이 같은 영역) ──

@Composable
private fun MovieRow(items: List<Movie>) {
    PosterRow {
        items(items) { MovieItem(it) }
    }
}

@Composable
private fun PosterRow(content: LazyListScope.() -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun MovieItem(item: Movie) {
    Poster(item = item, title = null)
}

@Composable
private fun Poster(item: Movie, title: String?) {
    AppTheme {
        Column(
            modifier = Modifier.clickable {},
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Thumbnail(item)
            if (title != null) {
                Text(text = title, color = Color.White, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides Color.White, content = content)
}

@Composable
private fun Thumbnail(item: Movie) {
    Box(
        modifier = Modifier
            .width(120.dp)
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(4.dp))
            .background(item.color),
    ) {
        PosterBadges(item)
    }
}

@Composable
private fun PosterBadges(item: Movie) {
    Box(modifier = Modifier.fillMaxWidth()) {
        if (item.isPaid) {
            Box(modifier = Modifier.align(Alignment.TopStart)) { PurchaseBadge() }
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (item.hasSubtitle) SubtitleBadge()
            if (item.isAdult) AgeBadge()
        }
    }
}

@Composable
private fun PurchaseBadge() {
    BadgeChip(text = "BUY", color = Color(0xFF2962FF))
}

@Composable
private fun SubtitleBadge() {
    BadgeChip(text = "CC", color = Color(0xCC000000))
}

@Composable
private fun AgeBadge() {
    BadgeChip(text = "18", color = Color(0xFFD50000))
}

@Composable
private fun BadgeChip(text: String, color: Color) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(color, RoundedCornerShape(2.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

// ── spacedBy 간격 비교 ────────────────────────────────────

@Composable
private fun SpacingCheck() {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "Reference — plain Box", color = Gray400, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(5) { Box(modifier = Modifier.size(40.dp).background(Gray700)) }
        }
        Text(text = "Compare — Composable functions", color = Gray400, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(5) { SpacingChip() }
        }
    }
}

@Composable
private fun SpacingChip() {
    Box(modifier = Modifier.size(40.dp).background(Gray700))
}

// ── 안 떠 있는 다이얼로그 ─────────────────────────────────

@Composable
private fun SettingsDialog(visible: Boolean) {
    if (!visible) return
    Dialog(onDismissRequest = {}) {
        Text(text = "Settings", color = Color.White)
    }
}
