package com.donglab.compose.debug

import org.junit.Assert.assertEquals
import org.junit.Test

class NametagRulesTest {

    private val item = LabelRect(0f, 0f, 300f, 450f)

    // ── selectLabelTargets ────────────────────────────────

    @Test
    fun `같은 영역 체인은 바깥부터 이름을 이어 붙여 라벨 하나로 만든다`() {
        val movieItem = NamedGroup("MovieItem", parent = null, bounds = item)
        val poster = NamedGroup("Poster", parent = movieItem, bounds = item)
        val theme = NamedGroup("AppTheme", parent = poster, bounds = item)
        val thumbnail = NamedGroup("Thumbnail", parent = theme, bounds = item)

        val targets = selectLabelTargets(listOf(movieItem, poster, theme, thumbnail))

        assertEquals(listOf("MovieItem › Poster › AppTheme › Thumbnail"), targets.map { it.name })
        assertEquals(movieItem, targets.single().source)
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

        assertEquals(listOf("Outer › Inner"), targets.map { it.name })
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

        assertEquals(listOf("MovieItem › Thumbnail", "PosterBadges"), targets.map { it.name })
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

    @Test
    fun `중간 래퍼가 숨겨지면 체인이 끊겨 두 라벨이 된다`() {
        val outer = NamedGroup("Outer", parent = null, bounds = item)
        val hidden = NamedGroup("HiddenWrapper", parent = outer, bounds = null)
        val inner = NamedGroup("Inner", parent = hidden, bounds = item)

        val targets = selectLabelTargets(listOf(outer, hidden, inner))

        assertEquals(listOf("Outer", "Inner"), targets.map { it.name })
    }

    @Test
    fun `같은 깊이 형제는 세로 막대로 잇는다`() {
        // Box 안에 같은 영역으로 겹친 두 자식
        val box = NamedGroup("Box", parent = null, bounds = item)
        val image = NamedGroup("Image", parent = box, bounds = item)
        val scrim = NamedGroup("Scrim", parent = box, bounds = item)

        val targets = selectLabelTargets(listOf(box, image, scrim))

        assertEquals(listOf("Box › Image | Scrim"), targets.map { it.name })
    }

    @Test
    fun `체인 안에 리스트 아이템이 있으면 머리를 리스트 아이템으로 센다`() {
        // ItemWrapper 와 MovieItem(리스트 아이템)이 같은 영역 — 바깥 리스트 깊이로 세면 2단계라 빠짐
        val section = NamedGroup("Section", parent = null, bounds = LabelRect(0f, 0f, 1000f, 600f), isListItem = true)
        val row = NamedGroup("MovieRow", parent = section, bounds = LabelRect(0f, 50f, 1000f, 600f))
        val wrapper = NamedGroup("ItemWrapper", parent = row, bounds = LabelRect(0f, 60f, 300f, 510f))
        val movieItem = NamedGroup("MovieItem", parent = wrapper, bounds = LabelRect(0f, 60f, 300f, 510f), isListItem = true)

        val targets = selectLabelTargets(listOf(section, row, wrapper, movieItem))

        assertEquals(listOf("Section", "MovieRow", "ItemWrapper › MovieItem"), targets.map { it.name })
    }

    @Test
    fun `리스트 아이템 안 페이저의 페이지는 멈춰 있어도 리스트 아이템으로 센다`() {
        // 롤링 배너 — 밴드(리스트 아이템) › 페이저 › 페이지. 페이지가 페이저를 꽉 채워도 사라지면 안 됨
        val band = NamedGroup("BannerBand", parent = null, bounds = LabelRect(0f, 0f, 1000f, 500f), isListItem = true)
        val pager = NamedGroup("RollingBanner", parent = band, bounds = LabelRect(0f, 50f, 1000f, 500f))
        val page = NamedGroup("RollingBannerPage", parent = pager, bounds = LabelRect(0f, 50f, 1000f, 500f), isPage = true)
        val image = NamedGroup("RollingBannerImage", parent = page, bounds = LabelRect(0f, 50f, 1000f, 400f))

        val targets = selectLabelTargets(listOf(band, pager, page, image))

        assertEquals(listOf("BannerBand", "RollingBanner › RollingBannerPage", "RollingBannerImage"), targets.map { it.name })
    }

    @Test
    fun `리스트 밖 페이저의 페이지는 리스트 아이템으로 세지 않는다`() {
        // 홈 탭 페이저 — 화면을 채운 페이지 안의 내용을 리스트 깊이로 제한하지 않음
        val pager = NamedGroup("HomePager", parent = null, bounds = LabelRect(0f, 0f, 1000f, 2000f))
        val page = NamedGroup("TabPage", parent = pager, bounds = LabelRect(0f, 0f, 1000f, 2000f), isPage = true)
        val header = NamedGroup("Header", parent = page, bounds = LabelRect(0f, 0f, 1000f, 100f))
        val title = NamedGroup("HeaderTitle", parent = header, bounds = LabelRect(0f, 0f, 500f, 100f))

        val targets = selectLabelTargets(listOf(pager, page, header, title))

        assertEquals(listOf("HomePager › TabPage", "Header", "HeaderTitle"), targets.map { it.name })
    }

    // ── fitChainName ──────────────────────────────────────

    private val band = listOf("MultiBandContentInternal", "VodBasicBand", "TvingRowBand", "BandContainer")

    // 한 글자 10px
    private fun fit(levels: List<String>, maxWidth: Float) = fitChainName(levels, maxWidth) { it.length * 10f }

    @Test
    fun `한도 안이면 체인 이름을 그대로 쓴다`() {
        assertEquals("MultiBandContentInternal › VodBasicBand › TvingRowBand › BandContainer", fit(band, 1000f))
    }

    @Test
    fun `한도를 넘으면 바깥쪽 가운데 단계부터 줄이고 양 끝은 남긴다`() {
        assertEquals("MultiBandContentInternal › … › TvingRowBand › BandContainer", fit(band, 600f))
        assertEquals("MultiBandContentInternal › … › BandContainer", fit(band, 450f))
    }

    @Test
    fun `양 끝만으로도 넘치면 가장 바깥 이름 하나만 쓴다`() {
        // 포스터처럼 좁은 영역 — 옆 아이템 라벨을 밀어내지 않도록 1.0.2 처럼 바깥 이름만
        assertEquals("MultiBandContentInternal", fit(band, 300f))
        assertEquals("MultiBandContentInternal", fit(listOf("MultiBandContentInternal"), 100f))
    }

    // ── layoutChain (세로 → 가로 → 바깥 이름) ─────────────────────────

    // 한 글자 10px, 한 줄 20px
    private fun layout(levels: List<String>, width: Float, height: Float) =
        layoutChain(levels, width, height, lineHeight = 20f) { it.length * 10f }

    private val poster = listOf("VodBasicBandItem", "VerticalThumbnail", "TdsTheme", "Thumbnail", "ThumbnailLabel")

    @Test
    fun `한 줄에 다 들어가면 세로로 쌓을 수 있어도 가로로 잇는다`() {
        // 넓은 밴드 — 세로로도 들어가지만 가로가 먼저
        assertEquals(listOf(poster.joinToString(" › ")), layout(poster, width = 900f, height = 1000f))
    }

    @Test
    fun `한 줄에 안 들어가고 세로로 쌓아도 영역의 절반 이하면 한 줄씩 쌓는다`() {
        // 5줄 = 100px ≤ 높이 200 의 1/2 · 가장 긴 이름 170px ≤ 폭 230
        assertEquals(poster, layout(poster, width = 230f, height = 200f))
    }

    @Test
    fun `세로로 쌓으면 절반을 넘게 가리면 가운데를 줄여 한 줄로 잇는다`() {
        // 5줄 100px > 높이 150 의 1/2 · 가운데를 줄이면 폭 450 안에 들어감
        assertEquals(listOf("VodBasicBandItem › … › ThumbnailLabel"), layout(poster, width = 400f, height = 150f))
    }

    @Test
    fun `세로도 가로도 안 되면 바깥 이름 하나만 쓴다`() {
        assertEquals(listOf("VodBasicBandItem"), layout(poster, width = 230f, height = 150f))
    }

    @Test
    fun `이름 하나짜리 체인은 그대로 쓴다`() {
        assertEquals(listOf("Poster"), layout(listOf("Poster"), width = 10f, height = 10f))
    }

    // ── placeLabels ───────────────────────────────────────

    private val labelSize = LabelSize(width = 100f, height = 20f)

    private fun place(vararg targets: LabelTarget) =
        placeLabels(targets.toList()) { labelSize }

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
    fun `화면 끝에 걸린 영역도 라벨을 안쪽으로 당기지 않는다`() {
        // 스크롤로 화면 끝에 걸려도 라벨은 아이템에 붙은 채 같이 움직인다 (넘친 부분은 그릴 때 잘림)
        val edge = LabelTarget("Edge", LabelRect(950f, 990f, 1000f, 1000f))

        val placed = place(edge)

        assertEquals(LabelRect(950f, 990f, 1050f, 1010f), placed.single().rect)
    }

    @Test
    fun `쌓인 높이를 기억해 다음 프레임에도 영역을 따라간다`() {
        val item = LabelTarget("VodBasicBandItem", LabelRect(0f, 0f, 300f, 450f), depth = 0)
        val icon = LabelTarget("TvodLabelIcon", LabelRect(0f, 0f, 60f, 30f), depth = 1)
        val stacked = place(item, icon)[1]
        // 스크롤로 아이콘이 위로 100 옮겨짐
        val moved = LabelRect(0f, -100f + 200f, 60f, -70f + 200f)

        assertEquals(20f, stacked.offsetY)
        assertEquals(moved.top + 20f, labelTop(moved, stacked.offsetY))
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
