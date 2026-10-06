package com.donglab.compose.debug

import kotlin.math.abs

/** Rounding slack when comparing two layout bounds (px). */
internal const val SAME_BOUNDS_TOLERANCE_PX = 1.5f

/** How many labels deep to show inside a list item (item itself = 0). */
private const val MAX_DEPTH_IN_LIST_ITEM = 1

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
)

internal data class LabelTarget(val name: String, val bounds: LabelRect)

internal data class LabelSize(val width: Float, val height: Float)

internal data class PlacedLabel(val name: String, val rect: LabelRect)

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
        .map { LabelTarget(it.name, it.bounds!!) }
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
 * Places labels without overlap, biggest area first.
 *
 * Each label tries the four inner corners of its bounds (top-left → top-right → bottom-left →
 * bottom-right) and is dropped when all of them collide with an already placed label.
 */
internal fun placeLabels(
    targets: List<LabelTarget>,
    viewWidth: Float,
    viewHeight: Float,
    measure: (String) -> LabelSize,
): List<PlacedLabel> {
    val placed = ArrayList<PlacedLabel>()
    for (target in targets.sortedByDescending { it.bounds.area }) {
        val size = measure(target.name)
        val spot = cornerCandidates(target.bounds, size)
            .map { it.clampInto(viewWidth, viewHeight) }
            .firstOrNull { candidate -> placed.none { it.rect.intersects(candidate) } }
            ?: continue
        placed += PlacedLabel(target.name, spot)
    }
    return placed
}

private fun cornerCandidates(bounds: LabelRect, size: LabelSize): List<LabelRect> {
    val left = bounds.left
    val right = bounds.right - size.width
    val top = bounds.top
    val bottom = bounds.bottom - size.height
    return listOf(left to top, right to top, left to bottom, right to bottom)
        .map { (x, y) -> LabelRect(x, y, x + size.width, y + size.height) }
}

private fun LabelRect.clampInto(viewWidth: Float, viewHeight: Float): LabelRect {
    val dx = when {
        right > viewWidth -> viewWidth - right
        left < 0f -> -left
        else -> 0f
    }
    val dy = when {
        bottom > viewHeight -> viewHeight - bottom
        top < 0f -> -top
        else -> 0f
    }
    return offset(dx, dy)
}
