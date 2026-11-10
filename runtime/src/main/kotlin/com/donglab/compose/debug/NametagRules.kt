package com.donglab.compose.debug

import androidx.compose.ui.layout.LayoutInfo
import kotlin.math.abs

/** Rounding slack when comparing two layout bounds (px). */
internal const val SAME_BOUNDS_TOLERANCE_PX = 1.5f

/** How many labels deep to show inside a list item (item itself = 0). */
private const val MAX_DEPTH_IN_LIST_ITEM = 1

/** How many times a label may be pushed below another before it is dropped — keeps stacks readable and placement cheap. */
internal const val MAX_STACK_STEPS = 4

internal data class LabelRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val area: Float get() = width * height

    fun contains(other: LabelRect): Boolean =
        left <= other.left && top <= other.top && other.right <= right && other.bottom <= bottom

    fun intersects(other: LabelRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    fun isCloseTo(other: LabelRect, tolerance: Float): Boolean =
        abs(left - other.left) <= tolerance &&
            abs(top - other.top) <= tolerance &&
            abs(right - other.right) <= tolerance &&
            abs(bottom - other.bottom) <= tolerance

    fun offset(dx: Float, dy: Float): LabelRect = LabelRect(left + dx, top + dy, right + dx, bottom + dy)

    fun union(other: LabelRect): LabelRect = LabelRect(
        minOf(left, other.left),
        minOf(top, other.top),
        maxOf(right, other.right),
        maxOf(bottom, other.bottom),
    )

    /**
     * What stays visible once [cover] is drawn on top: `this` when they don't overlap, `null` when
     * fully covered, otherwise the biggest uncovered strip (above, below, left or right of [cover]).
     */
    fun minus(cover: LabelRect): LabelRect? {
        if (!intersects(cover)) return this
        var best: LabelRect? = null
        for (strip in arrayOf(
            LabelRect(left, top, right, minOf(bottom, cover.top)),
            LabelRect(left, maxOf(top, cover.bottom), right, bottom),
            LabelRect(left, top, minOf(right, cover.left), bottom),
            LabelRect(maxOf(left, cover.right), top, right, bottom),
        )) {
            if (strip.width <= 0f || strip.height <= 0f) continue
            if (best == null || strip.area > best.area) best = strip
        }
        return best
    }
}

/**
 * A composable found in the slot table.
 *
 * @property parent nearest labeled ancestor (may live in a parent composition)
 * @property bounds full layout bounds of what this composable drew (not clipped, so scrolling doesn't
 * change them), `null` when no part of it is visible
 * @property isListItem first name of a repeated list item composition (LazyRow / LazyColumn item)
 * @property isPage first name of a repeated composition that fills its host (a pager page)
 */
internal class NamedGroup(
    val name: String,
    var parent: NamedGroup?,
    var bounds: LabelRect? = null,
    var isListItem: Boolean = false,
    var isPage: Boolean = false,
) {
    /** Top-level layout nodes under this composable — re-measured every frame so the label sticks to them. */
    var nodes: List<LayoutInfo> = emptyList()

    /** Where this composable is shown, `null` while its host view is hidden. */
    var host: HostView? = null

    /** Parts of the host drawn on top of this composable (host view coordinates), e.g. a page in an AndroidView. */
    var covers: List<LabelRect> = emptyList()

    /** The part of [bounds] actually visible (clipped by scrolling containers and the host view). */
    var visibleBounds: LabelRect? = null
}

/** @property depth labeled ancestors above it — parents are placed before their children */
/** Between names of a same-bounds chain, outer to inner. */
internal const val CHAIN_SEPARATOR = " › "

/** Between names at the same depth of a chain (siblings stacked on the same bounds). */
internal const val SIBLING_SEPARATOR = " | "

/** Stands for the levels dropped from a chain name that is too long. */
internal const val CHAIN_ELLIPSIS = "…"

/**
 * @property depth labeled ancestors above it — parents are placed before their children
 * @property levels the chain behind [name], outer to inner (one entry per depth)
 */
internal data class LabelTarget(
    val name: String,
    val bounds: LabelRect,
    val source: NamedGroup? = null,
    val depth: Int = 0,
    val levels: List<String> = listOf(name),
)

internal data class LabelSize(val width: Float, val height: Float)

/**
 * @property offsetY how far below the top of its bounds the label sits (stacked under other labels)
 * @property offsetX where the label starts from the left of its bounds (negative when right-aligned)
 * @property source the composable, followed between scans
 */
internal data class PlacedLabel(
    val name: String,
    val rect: LabelRect,
    val offsetY: Float = 0f,
    val source: NamedGroup? = null,
    val offsetX: Float = 0f,
)

/**
 * Picks the composables worth labeling.
 *
 * - nothing visible (effects, hidden dialogs, early returns, clipped out) → skipped
 * - a wrapper chain with the same bounds (`MovieItem › Poster › AppTheme › Thumbnail`) becomes one
 *   label on the outermost composable, named after the whole chain, outer to inner — the specific
 *   name is often an inner one. Names at the same depth (siblings on the same bounds) join with `|`.
 * - inside a list item, only labels up to [maxDepthInListItem] levels deep are kept
 *   (`ShortcutItem` › `ShortcutTitle` kept, › `TitleLine` skipped); a chain holding a list item
 *   counts as that list item
 */
internal fun selectLabelTargets(
    groups: List<NamedGroup>,
    tolerance: Float = SAME_BOUNDS_TOLERANCE_PX,
    maxDepthInListItem: Int = MAX_DEPTH_IN_LIST_ITEM,
): List<LabelTarget> {
    // 같은 영역 체인은 바깥 그룹(머리) 하나에 모음 — 순회 순서상 부모가 먼저 온다고 가정하지 않음
    val chains = LinkedHashMap<NamedGroup, MutableList<NamedGroup>>()
    for (group in groups) {
        if (group.bounds == null) continue
        var head = group
        while (head.hasSameBoundsAsParent(tolerance)) head = head.parent!!
        chains.getOrPut(head) { ArrayList() } += group
    }
    val heads = chains.keys
    val targets = ArrayList<LabelTarget>(chains.size)
    for ((head, chain) in chains) {
        if (depthInListItem(head, heads, chain.any { it.startsListItem() }) > maxDepthInListItem) continue
        val levels = chainLevels(head, chain)
        targets += LabelTarget(levels.joinToString(CHAIN_SEPARATOR), head.bounds!!, head, depthOf(head), levels)
    }
    return targets
}

private fun NamedGroup.hasSameBoundsAsParent(tolerance: Float): Boolean {
    val bounds = bounds ?: return false
    val parentBounds = parent?.bounds ?: return false
    return bounds.isCloseTo(parentBounds, tolerance)
}

/** Names of a chain by distance from its [head], outer to inner; same distance joined with `|`. */
private fun chainLevels(head: NamedGroup, chain: List<NamedGroup>): List<String> {
    if (chain.size == 1) return listOf(head.name)
    val byLevel = sortedMapOf<Int, MutableList<String>>()
    for (group in chain) {
        var level = 0
        var ancestor: NamedGroup? = group
        while (ancestor != null && ancestor !== head) {
            level++
            ancestor = ancestor.parent
        }
        byLevel.getOrPut(level) { ArrayList() } += group.name
    }
    return byLevel.values.map { it.joinToString(SIBLING_SEPARATOR) }
}

/** Share of an item's height a vertical stack of chain names may cover. */
internal const val MAX_STACK_COVER_RATIO = 1f / 2f

/**
 * How a chain's names are shown on an item of [width] × [height]: one entry per label line.
 *
 * 1. one line `A › B › C`, when the whole chain fits the width
 * 2. otherwise stacked one name per line, when every name fits the width and the stack covers at
 *    most [MAX_STACK_COVER_RATIO] of the height
 * 3. otherwise one line with the middle shortened by `…` (see [fitChainName]), or the outermost
 *    name only when even that doesn't fit
 */
internal fun layoutChain(
    levels: List<String>,
    width: Float,
    height: Float,
    lineHeight: Float,
    textWidth: (String) -> Float,
): List<String> {
    if (levels.size <= 1) return levels
    val full = levels.joinToString(CHAIN_SEPARATOR)
    if (textWidth(full) <= width) return listOf(full)
    val stackFits = levels.size - 1 <= MAX_STACK_STEPS &&
        levels.size * lineHeight <= height * MAX_STACK_COVER_RATIO &&
        levels.all { textWidth(it) <= width }
    if (stackFits) return levels
    return listOf(fitChainName(levels, width, textWidth))
}

/**
 * The chain name, shortened to fit [maxWidth] when needed.
 *
 * Drops levels from the outer middle first (inner names are usually the specific ones) and puts
 * `…` in their place, keeping the outermost and innermost levels. When even that doesn't fit, only
 * the outermost name is used — a narrow item (a poster) then reads like before instead of pushing
 * its neighbors' labels down.
 */
internal fun fitChainName(levels: List<String>, maxWidth: Float, width: (String) -> Float): String {
    val full = levels.joinToString(CHAIN_SEPARATOR)
    if (width(full) <= maxWidth) return full
    if (levels.size <= 2) return levels.first()
    for (keepFrom in 2 until levels.size) {
        val name = (listOf(levels.first(), CHAIN_ELLIPSIS) + levels.subList(keepFrom, levels.size)).joinToString(CHAIN_SEPARATOR)
        if (width(name) <= maxWidth) return name
    }
    return levels.first()
}

/** Labeled ancestors of [group], shown or not. */
private fun depthOf(group: NamedGroup): Int {
    var depth = 0
    var ancestor = group.parent
    while (ancestor != null) {
        depth++
        ancestor = ancestor.parent
    }
    return depth
}

/** Shown labels between [group] and its nearest list item. The item itself and anything outside a list = 0. */
private fun depthInListItem(group: NamedGroup, shown: Set<NamedGroup>, holdsListItem: Boolean = false): Int {
    if (group.startsListItem() || holdsListItem) return 0
    var depth = 0
    var ancestor = group.parent
    while (ancestor != null) {
        if (ancestor in shown) depth++
        if (ancestor.startsListItem()) return depth
        ancestor = ancestor.parent
    }
    return 0
}

/**
 * Where list depth counting restarts: a list item, or a pager page inside a list item (a rolling
 * banner in a feed row). A settled page fills its pager, so it isn't a list item by bounds; without
 * this its contents would count from the row and disappear whenever the pager stops.
 * A pager outside any list (screen tabs) doesn't restart counting.
 */
private fun NamedGroup.startsListItem(): Boolean {
    if (isListItem) return true
    if (!isPage) return false
    var ancestor = parent
    while (ancestor != null) {
        if (ancestor.isListItem) return true
        ancestor = ancestor.parent
    }
    return false
}

/**
 * Places labels without overlap at the top-left of their bounds, parents before children.
 *
 * A label whose spot is taken goes right below the label in the way, so a label pushed down pushes
 * the labels of its children down with it and the hierarchy reads top to bottom
 * (`MultiBandContent` › `VodBasicBandItem` › `TvodLabelIcon`). A label that would start below its
 * bounds or sit more than [MAX_STACK_STEPS] labels down is dropped. Same depth → biggest area first. Runs on every scan, so the loop works on
 * primitives instead of allocating candidate rects.
 */
internal fun placeLabels(
    targets: List<LabelTarget>,
    measure: (String) -> LabelSize,
): List<PlacedLabel> {
    val sorted = targets.sortedWith(compareBy<LabelTarget> { it.depth }.thenByDescending { it.bounds.area })
    val placedRects = FloatArray(sorted.size * 4)
    val placedTargets = arrayOfNulls<LabelTarget>(sorted.size)
    var placedCount = 0
    val placed = ArrayList<PlacedLabel>(sorted.size)

    for (target in sorted) {
        val size = measure(target.name)
        val bounds = target.bounds
        // 왼쪽 정렬 → 오른쪽 정렬 — 아이템 오른쪽 끝의 작은 컴포넌트(배지)는 왼쪽 정렬이면 라벨이 옆 아이템으로 넘침.
        // 감싸는 아이템 안에 가로로 들어가는 자리를 먼저 쓰고, 둘 다 넘치면 넘치는 자리라도 씀
        val container = nearestContainer(target, placedTargets, placedCount)
        val anchors = if (size.width > bounds.width) floatArrayOf(bounds.left, bounds.right - size.width) else floatArrayOf(bounds.left)
        val inside = anchors.filter { container == null || (it >= container.left && it + size.width <= container.right) }
        for (left in inside + anchors.filter { it !in inside }) {
            val top = stackedTop(target, left, size, container, placedRects, placedCount) ?: continue
            val index = placedCount * 4
            placedRects[index] = left
            placedRects[index + 1] = top
            placedRects[index + 2] = left + size.width
            placedRects[index + 3] = top + size.height
            placedTargets[placedCount] = target
            placedCount++
            placed += PlacedLabel(
                target.name,
                LabelRect(left, top, left + size.width, top + size.height),
                top - bounds.top,
                target.source,
                left - bounds.left,
            )
            break
        }
    }
    return placed
}

/**
 * Top of [target]'s label at [left]: its bounds' top, pushed right below the placed labels in the
 * way. It may be pushed down to the bottom of its own bounds or of its nearest labeled [container]
 * (a small badge's label then stays inside its poster). `null` when pushed further or more than
 * [MAX_STACK_STEPS] times.
 */
private fun stackedTop(
    target: LabelTarget,
    left: Float,
    size: LabelSize,
    container: LabelRect?,
    placedRects: FloatArray,
    placedCount: Int,
): Float? {
    val bounds = target.bounds
    var top = labelTop(bounds, 0f)
    val limit = maxOf(bounds.bottom, container?.bottom ?: bounds.bottom)
    var steps = 0
    while (true) {
        var below = -1f
        for (i in 0 until placedCount) {
            val index = i * 4
            if (left < placedRects[index + 2] && placedRects[index] < left + size.width &&
                top < placedRects[index + 3] && placedRects[index + 1] < top + size.height
            ) {
                below = maxOf(below, placedRects[index + 3])
            }
        }
        if (below < 0f) return top
        // 막은 라벨 바로 아래로 — 컨테이너 밖으로 나가거나 너무 깊게 쌓이면 생략
        top = below
        if (++steps > MAX_STACK_STEPS || top >= limit) return null
    }
}

/** Bounds of the smallest placed label's composable that contains [target] (its nearest labeled ancestor). */
private fun nearestContainer(target: LabelTarget, placedTargets: Array<LabelTarget?>, placedCount: Int): LabelRect? {
    var nearest: LabelRect? = null
    for (i in 0 until placedCount) {
        val other = placedTargets[i]!!
        if (other.depth >= target.depth || !other.bounds.contains(target.bounds)) continue
        if (nearest == null || other.bounds.area < nearest.area) nearest = other.bounds
    }
    return nearest
}

/** Top edge of a label stacked [offsetY] below the top of [bounds]. */
internal fun labelTop(bounds: LabelRect, offsetY: Float): Float = bounds.top + offsetY
