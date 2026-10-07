package com.donglab.compose.debug

import androidx.compose.ui.layout.LayoutInfo
import kotlin.math.abs

/** Rounding slack when comparing two layout bounds (px). */
internal const val SAME_BOUNDS_TOLERANCE_PX = 1.5f

/** How many labels deep to show inside a list item (item itself = 0). */
private const val MAX_DEPTH_IN_LIST_ITEM = 1

/** How many times a label may be pushed below another before it is dropped — keeps stacks readable and placement cheap. */
private const val MAX_STACK_STEPS = 4

internal data class LabelRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val area: Float get() = width * height

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
 * @property bounds what this composable actually drew, `null` when nothing is visible
 * @property isListItem first name of a repeated list item composition (LazyRow / LazyColumn item)
 */
internal class NamedGroup(
    val name: String,
    var parent: NamedGroup?,
    var bounds: LabelRect? = null,
    var isListItem: Boolean = false,
) {
    /** Top-level layout nodes under this composable — re-measured every frame so the label sticks to them. */
    var nodes: List<LayoutInfo> = emptyList()

    /** Where this composable is shown, `null` while its host view is hidden. */
    var host: HostView? = null

    /** Parts of the host drawn on top of this composable (host view coordinates), e.g. a page in an AndroidView. */
    var covers: List<LabelRect> = emptyList()
}

/** @property depth labeled ancestors above it — parents are placed before their children */
internal data class LabelTarget(
    val name: String,
    val bounds: LabelRect,
    val source: NamedGroup? = null,
    val depth: Int = 0,
)

internal data class LabelSize(val width: Float, val height: Float)

/**
 * @property offsetY how far below the top of its bounds the label sits (stacked under other labels)
 * @property source the composable, followed between scans
 */
internal data class PlacedLabel(
    val name: String,
    val rect: LabelRect,
    val offsetY: Float = 0f,
    val source: NamedGroup? = null,
)

/**
 * Picks the composables worth labeling.
 *
 * - nothing visible (effects, hidden dialogs, early returns, clipped out) → skipped
 * - same bounds as the nearest labeled ancestor → skipped, so a wrapper chain
 *   (`MovieItem › Poster › AppTheme › Thumbnail`) keeps only its outermost name
 * - inside a list item, only labels up to [maxDepthInListItem] levels deep are kept
 *   (`ShortcutItem` › `ShortcutTitle` kept, › `TitleLine` skipped)
 */
internal fun selectLabelTargets(
    groups: List<NamedGroup>,
    tolerance: Float = SAME_BOUNDS_TOLERANCE_PX,
    maxDepthInListItem: Int = MAX_DEPTH_IN_LIST_ITEM,
): List<LabelTarget> {
    val shown = groups.filter { group ->
        val bounds = group.bounds ?: return@filter false
        val parentBounds = group.parent?.bounds
        parentBounds == null || !bounds.isCloseTo(parentBounds, tolerance)
    }
    val shownSet = shown.toHashSet()
    return shown
        .filter { depthInListItem(it, shownSet) <= maxDepthInListItem }
        .map { LabelTarget(it.name, it.bounds!!, it, depthOf(it)) }
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
private fun depthInListItem(group: NamedGroup, shown: Set<NamedGroup>): Int {
    if (group.isListItem) return 0
    var depth = 0
    var ancestor = group.parent
    while (ancestor != null) {
        if (ancestor in shown) depth++
        if (ancestor.isListItem) return depth
        ancestor = ancestor.parent
    }
    return 0
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
    viewWidth: Float,
    viewHeight: Float,
    measure: (String) -> LabelSize,
): List<PlacedLabel> {
    val sorted = targets.sortedWith(compareBy<LabelTarget> { it.depth }.thenByDescending { it.bounds.area })
    val placedRects = FloatArray(sorted.size * 4)
    var placedCount = 0
    val placed = ArrayList<PlacedLabel>(sorted.size)

    for (target in sorted) {
        val size = measure(target.name)
        val bounds = target.bounds
        val left = labelLeft(bounds, size.width, viewWidth)
        var top = labelTop(bounds, 0f, size.height, viewHeight)
        var fits = true
        var steps = 0
        while (true) {
            val below = bottomOfCollisions(placedRects, placedCount, left, top, left + size.width, top + size.height)
            if (below < 0f) break
            // 막은 라벨 바로 아래로 — 영역 · 화면 밖으로 나가거나 너무 깊게 쌓이면 생략
            top = below
            if (++steps > MAX_STACK_STEPS || top >= bounds.bottom || top + size.height > viewHeight) {
                fits = false
                break
            }
        }
        if (!fits) continue

        val index = placedCount * 4
        placedRects[index] = left
        placedRects[index + 1] = top
        placedRects[index + 2] = left + size.width
        placedRects[index + 3] = top + size.height
        placedCount++
        placed += PlacedLabel(target.name, LabelRect(left, top, left + size.width, top + size.height), top - bounds.top, target.source)
    }
    return placed
}

/** Left edge of a label of [width] at the left of [bounds], kept inside the view. */
internal fun labelLeft(bounds: LabelRect, width: Float, viewWidth: Float): Float =
    clampStart(bounds.left, width, viewWidth)

/** Top edge of a label of [height] stacked [offsetY] below the top of [bounds], kept inside the view. */
internal fun labelTop(bounds: LabelRect, offsetY: Float, height: Float, viewHeight: Float): Float =
    clampStart(bounds.top + offsetY, height, viewHeight)

/** Pulls a label back into the view: overflow at the end first, then at the start. */
private fun clampStart(start: Float, length: Float, viewLength: Float): Float = when {
    start + length > viewLength -> viewLength - length
    start < 0f -> 0f
    else -> start
}

/** Lowest bottom among placed rects overlapping the given one, -1 when nothing overlaps. */
private fun bottomOfCollisions(rects: FloatArray, count: Int, left: Float, top: Float, right: Float, bottom: Float): Float {
    var lowest = -1f
    for (i in 0 until count) {
        val index = i * 4
        if (left < rects[index + 2] && rects[index] < right && top < rects[index + 3] && rects[index + 1] < bottom) {
            lowest = maxOf(lowest, rects[index + 3])
        }
    }
    return lowest
}
