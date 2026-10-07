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
    fun `부모를 먼저 좌상단에 놓고 같은 자리의 자식은 바로 아래에 쌓는다`() {
        val item = LabelTarget("VodBasicBandItem", LabelRect(0f, 0f, 300f, 450f), depth = 0)
        val icon = LabelTarget("TvodLabelIcon", LabelRect(0f, 0f, 60f, 30f), depth = 1)

        val placed = place(icon, item)

        assertEquals(listOf("VodBasicBandItem", "TvodLabelIcon"), placed.map { it.name })
        assertEquals(LabelRect(0f, 0f, 100f, 20f), placed[0].rect)
        assertEquals(LabelRect(0f, 20f, 100f, 40f), placed[1].rect)
    }

    @Test
    fun `부모 라벨이 아래로 밀리면 자식도 그 아래로 밀려 계층 순서가 유지된다`() {
        // 띠 라벨이 아이템 좌상단을 차지 → 아이템은 그 아래 → 아이콘은 다시 그 아래
        val band = LabelTarget("MultiBandContent", LabelRect(0f, 0f, 1000f, 500f), depth = 0)
        val item = LabelTarget("VodBasicBandItem", LabelRect(0f, 0f, 300f, 450f), depth = 1)
        val icon = LabelTarget("TvodLabelIcon", LabelRect(0f, 0f, 60f, 50f), depth = 2)

        val placed = place(icon, item, band).associate { it.name to it.rect.top }

        assertEquals(0f, placed.getValue("MultiBandContent"))
        assertEquals(20f, placed.getValue("VodBasicBandItem"))
        assertEquals(40f, placed.getValue("TvodLabelIcon"))
    }

    @Test
    fun `자식 영역이 더 커도 부모를 먼저 놓는다`() {
        // 부모 밖으로 넘친 자식 (offset · requiredSize) — 그래도 위에서부터 부모 › 자식 순서
        val parent = LabelTarget("Card", LabelRect(0f, 0f, 120f, 100f), depth = 0)
        val child = LabelTarget("Overflow", LabelRect(0f, 0f, 400f, 400f), depth = 1)

        val placed = place(child, parent)

        assertEquals(listOf("Card", "Overflow"), placed.map { it.name })
        assertEquals(20f, placed[1].rect.top)
    }

    @Test
    fun `깊이가 같으면 큰 영역부터 놓는다`() {
        val small = LabelTarget("Small", LabelRect(0f, 0f, 100f, 100f), depth = 1)
        val large = LabelTarget("Large", LabelRect(0f, 0f, 500f, 500f), depth = 1)

        val placed = place(small, large)

        assertEquals(listOf("Large", "Small"), placed.map { it.name })
    }

    @Test
    fun `쌓을 자리가 영역을 벗어나면 생략한다`() {
        val a = LabelTarget("A", LabelRect(0f, 0f, 100f, 20f))
        val b = LabelTarget("B", LabelRect(0f, 0f, 100f, 20f), depth = 1)

        val placed = place(a, b)

        assertEquals(listOf("A"), placed.map { it.name })
    }

    @Test
    fun `네 칸보다 깊게 쌓아야 하면 생략한다`() {
        // 같은 좌상단을 쓰는 라벨이 겹겹이 — 다섯 번째부터는 읽기 어려우니 생략 (배치 비용도 제한)
        val targets = (0 until 6).map { LabelTarget("L$it", LabelRect(0f, 0f, 300f, 450f), depth = it) }

        val placed = place(*targets.toTypedArray())

        assertEquals(listOf("L0", "L1", "L2", "L3", "L4"), placed.map { it.name })
    }

    @Test
    fun `화면 밖으로 나가는 라벨은 화면 안으로 당긴다`() {
        val edge = LabelTarget("Edge", LabelRect(950f, 990f, 1000f, 1000f))

        val placed = place(edge)

        assertEquals(LabelRect(900f, 980f, 1000f, 1000f), placed.single().rect)
    }

    @Test
    fun `쌓인 높이를 기억해 다음 프레임에도 영역을 따라간다`() {
        val item = LabelTarget("VodBasicBandItem", LabelRect(0f, 0f, 300f, 450f), depth = 0)
        val icon = LabelTarget("TvodLabelIcon", LabelRect(0f, 0f, 60f, 30f), depth = 1)
        val stacked = place(item, icon)[1]
        // 스크롤로 아이콘이 위로 100 옮겨짐
        val moved = LabelRect(0f, -100f + 200f, 60f, -70f + 200f)

        assertEquals(20f, stacked.offsetY)
        assertEquals(moved.top + 20f, labelTop(moved, stacked.offsetY, labelSize.height, viewHeight = 1000f))
    }

    // ── minus (위에 그려진 것에 가려진 부분 빼기) ─────────────────

    @Test
    fun `완전히 가려지면 영역이 없다`() {
        val page = LabelRect(0f, 0f, 1000f, 2000f)

        assertEquals(null, item.minus(page))
    }

    @Test
    fun `겹치지 않으면 그대로 둔다`() {
        val elsewhere = LabelRect(500f, 500f, 600f, 600f)

        assertEquals(item, item.minus(elsewhere))
    }

    @Test
    fun `일부만 가려지면 가장 넓게 보이는 쪽만 남긴다`() {
        // 아래 300 은 가려지고 위 150 만 보임
        val sheet = LabelRect(0f, 150f, 1000f, 2000f)

        assertEquals(LabelRect(0f, 0f, 300f, 150f), item.minus(sheet))
    }

    @Test
    fun `가운데가 가려지면 위아래 중 더 넓은 쪽을 남긴다`() {
        // 위 100 · 아래 250 이 보임
        val banner = LabelRect(0f, 100f, 1000f, 200f)

        assertEquals(LabelRect(0f, 200f, 300f, 450f), item.minus(banner))
    }
}
