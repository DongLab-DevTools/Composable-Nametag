package com.donglab.compose.debug

import org.junit.Assert.assertEquals
import org.junit.Test

class NametagRulesTest {

    private val item = LabelRect(0f, 0f, 300f, 450f)

    // ── selectLabelTargets ────────────────────────────────

    @Test
    fun `같은 영역 체인은 가장 바깥쪽 이름만 남긴다`() {
        val movieItem = NamedGroup("MovieItem", parent = null, bounds = item)
        val poster = NamedGroup("Poster", parent = movieItem, bounds = item)
        val theme = NamedGroup("AppTheme", parent = poster, bounds = item)
        val thumbnail = NamedGroup("Thumbnail", parent = theme, bounds = item)

        val targets = selectLabelTargets(listOf(movieItem, poster, theme, thumbnail))

        assertEquals(listOf("MovieItem"), targets.map { it.name })
    }

    @Test
    fun `영역이 다르면 안쪽도 따로 남긴다`() {
        val movieItem = NamedGroup("MovieItem", parent = null, bounds = item)
        val label = NamedGroup("PosterBadges", parent = movieItem, bounds = LabelRect(0f, 0f, 300f, 40f))

        val targets = selectLabelTargets(listOf(movieItem, label))

        assertEquals(listOf("MovieItem", "PosterBadges"), targets.map { it.name })
    }

    @Test
    fun `그린 영역이 없으면 제외한다`() {
        val section = NamedGroup("SectionContent", parent = null, bounds = item)
        val effect = NamedGroup("ImpressionEffect", parent = section, bounds = null)

        val targets = selectLabelTargets(listOf(section, effect))

        assertEquals(listOf("SectionContent"), targets.map { it.name })
    }

    @Test
    fun `반올림 오차 이내면 같은 영역으로 본다`() {
        val outer = NamedGroup("Outer", parent = null, bounds = item)
        val inner = NamedGroup("Inner", parent = outer, bounds = item.offset(1f, 1f))

        val targets = selectLabelTargets(listOf(outer, inner))

        assertEquals(listOf("Outer"), targets.map { it.name })
    }

    @Test
    fun `리스트 아이템 안에서는 1단계까지만 남긴다`() {
        val shortcutItem = NamedGroup("ShortcutItem", parent = null, bounds = LabelRect(0f, 0f, 64f, 110f), isListItem = true)
        val title = NamedGroup("ShortcutTitle", parent = shortcutItem, bounds = LabelRect(0f, 70f, 64f, 110f))
        val line = NamedGroup("TitleLine", parent = title, bounds = LabelRect(0f, 70f, 64f, 90f))

        val targets = selectLabelTargets(listOf(shortcutItem, title, line))

        assertEquals(listOf("ShortcutItem", "ShortcutTitle"), targets.map { it.name })
    }

    @Test
    fun `숨겨진 래퍼는 단계로 세지 않는다`() {
        val movieItem = NamedGroup("MovieItem", parent = null, bounds = item, isListItem = true)
        val thumbnail = NamedGroup("Thumbnail", parent = movieItem, bounds = item)
        val label = NamedGroup("PosterBadges", parent = thumbnail, bounds = LabelRect(0f, 0f, 300f, 40f))
        val badge = NamedGroup("PurchaseBadge", parent = label, bounds = LabelRect(0f, 0f, 40f, 20f))

        val targets = selectLabelTargets(listOf(movieItem, thumbnail, label, badge))

        assertEquals(listOf("MovieItem", "PosterBadges"), targets.map { it.name })
    }

    @Test
    fun `안쪽 리스트 아이템에서 단계를 다시 센다`() {
        val section = NamedGroup("Section", parent = null, bounds = LabelRect(0f, 0f, 1000f, 400f), isListItem = true)
        val row = NamedGroup("ShortcutRow", parent = section, bounds = LabelRect(0f, 50f, 1000f, 400f))
        val shortcutItem = NamedGroup("ShortcutItem", parent = row, bounds = LabelRect(0f, 60f, 64f, 170f), isListItem = true)
        val icon = NamedGroup("ShortcutIcon", parent = shortcutItem, bounds = LabelRect(0f, 60f, 64f, 124f))

        val targets = selectLabelTargets(listOf(section, row, shortcutItem, icon))

        assertEquals(listOf("Section", "ShortcutRow", "ShortcutItem", "ShortcutIcon"), targets.map { it.name })
    }

    @Test
    fun `리스트 밖은 단계 제한이 없다`() {
        val screen = NamedGroup("Screen", parent = null, bounds = LabelRect(0f, 0f, 1000f, 1000f))
        val header = NamedGroup("Header", parent = screen, bounds = LabelRect(0f, 0f, 1000f, 100f))
        val title = NamedGroup("HeaderTitle", parent = header, bounds = LabelRect(0f, 0f, 500f, 100f))
        val icon = NamedGroup("HeaderIcon", parent = title, bounds = LabelRect(0f, 0f, 40f, 40f))

        val targets = selectLabelTargets(listOf(screen, header, title, icon))

        assertEquals(listOf("Screen", "Header", "HeaderTitle", "HeaderIcon"), targets.map { it.name })
    }

    // ── placeLabels ───────────────────────────────────────

    private val labelSize = LabelSize(width = 100f, height = 20f)

    private fun place(vararg targets: LabelTarget) =
        placeLabels(targets.toList(), viewWidth = 1000f, viewHeight = 1000f) { labelSize }

    @Test
    fun `큰 영역이 먼저 좌상단을 차지하고 작은 영역은 다음 모서리로 간다`() {
        val row = LabelTarget("PosterRow", LabelRect(0f, 0f, 900f, 500f))
        val movieItem = LabelTarget("MovieItem", LabelRect(0f, 0f, 300f, 450f))

        val placed = place(movieItem, row)

        assertEquals(listOf("PosterRow", "MovieItem"), placed.map { it.name })
        assertEquals(LabelRect(0f, 0f, 100f, 20f), placed[0].rect)
        assertEquals(LabelRect(200f, 0f, 300f, 20f), placed[1].rect)
    }

    @Test
    fun `네 모서리가 모두 막히면 생략한다`() {
        val a = LabelTarget("A", LabelRect(0f, 0f, 100f, 20f))
        val b = LabelTarget("B", LabelRect(0f, 0f, 100f, 20f))

        val placed = place(a, b)

        assertEquals(listOf("A"), placed.map { it.name })
    }

    @Test
    fun `화면 밖으로 나가는 라벨은 화면 안으로 당긴다`() {
        val edge = LabelTarget("Edge", LabelRect(950f, 990f, 1000f, 1000f))

        val placed = place(edge)

        assertEquals(LabelRect(900f, 980f, 1000f, 1000f), placed.single().rect)
    }
}
