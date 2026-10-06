package com.donglab.compose.debug

import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.tooling.CompositionGroup
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.layout.findRootCoordinates

/**
 * Where a composition is shown: the visible part of its ComposeView and the view's origin,
 * both in window coordinates.
 */
internal class HostView(val clip: LabelRect, val originX: Float, val originY: Float)

/** Group key of the [__debugComposableName] group, learned from the first tag found. */
private var markerKey: Any? = null

/**
 * Labeled composables of one window, read from the slot table the same way Layout Inspector does.
 *
 * - a group whose direct child holds a [NameTag] is the composable with that name
 * - its bounds are the union of the first layout nodes found under it (window coordinates)
 * - top-level names of a subcomposition (LazyRow item, Scaffold slot …) are linked to the
 *   labeled group that owns the host layout node, so wrapper chains can span compositions
 * - subcompositions under one host whose first name repeats (`ShortcutItem` × N) are list items;
 *   one-off slots (Scaffold content) and pages that fill the host (pager) are not
 *
 * The slot table walk is the expensive part, so it is cached per composition and redone only when
 * that composition changed (a name tag entered or left, or a cached node got detached).
 * Bounds are recomputed from the cached nodes on every [refresh].
 *
 * Main thread only, outside of composition.
 */
internal class NametagTree {

    private class Named(val group: NamedGroup) {
        var nodes: List<LayoutInfo> = emptyList()
    }

    private class CompositionScan(
        val named: List<Named>,
        val roots: List<Pair<NamedGroup, LayoutInfo>>,
        val ownedNodes: List<LayoutInfo>,
    ) {
        fun hasDetachedNode(): Boolean = named.any { named -> named.nodes.any { !it.isAttached } }
    }

    private val scans = LinkedHashMap<CompositionData, CompositionScan>()
    private val dirty = HashSet<CompositionData>()
    private val nodeOwners = HashMap<LayoutInfo, NamedGroup>()

    /** Number of compositions whose slot table was walked by the last [refresh]. */
    var lastRescanCount = 0
        private set

    fun invalidate(data: CompositionData) {
        dirty += data
    }

    fun remove(data: CompositionData) {
        scans.remove(data)?.let(::forget)
        dirty -= data
    }

    /**
     * @param hosts host view of each composition, `null` when the view is hidden or off screen
     */
    fun refresh(hosts: Map<CompositionData, HostView?>): List<NamedGroup> {
        scans.keys.filter { it !in hosts }.forEach(::remove)

        lastRescanCount = 0
        for (data in hosts.keys) {
            val scan = scans[data]
            if (scan == null || data in dirty || scan.hasDetachedNode()) {
                rescan(data)
                lastRescanCount++
            }
        }
        dirty.clear()

        val groups = ArrayList<NamedGroup>()
        val roots = HashMap<HostView, LayoutCoordinates>()
        for ((data, scan) in scans) {
            val host = hosts[data]
            for (named in scan.named) {
                named.group.bounds = host?.let { named.nodes.visibleBounds(it, roots) }
                groups += named.group
            }
        }
        linkSubcompositionRoots(hosts, roots)
        return groups
    }

    private fun rescan(data: CompositionData) {
        scans.remove(data)?.let(::forget)
        val walk = Walk()
        for (group in data.compositionGroups) walk.visit(group, owner = null)
        scans[data] = CompositionScan(walk.named, walk.roots, walk.ownedNodes)
    }

    private fun forget(scan: CompositionScan) {
        scan.ownedNodes.forEach(nodeOwners::remove)
    }

    private inner class Walk {
        val named = ArrayList<Named>()
        val roots = ArrayList<Pair<NamedGroup, LayoutInfo>>()
        val ownedNodes = ArrayList<LayoutInfo>()

        /** Returns the top-level layout nodes of [group]'s subtree. */
        fun visit(group: CompositionGroup, owner: NamedGroup?): List<LayoutInfo> {
            val self = group.compositionGroups.firstNotNullOfOrNull { it.nameTag() }
                ?.let { tag -> Named(NamedGroup(tag.name, owner)).also(named::add) }
            val current = self?.group ?: owner

            val node = group.node as? LayoutInfo
            val nodes = if (node != null) {
                if (current != null) {
                    nodeOwners[node] = current
                    ownedNodes += node
                }
                group.compositionGroups.forEach { visit(it, current) }
                listOf(node)
            } else {
                group.compositionGroups.flatMap { visit(it, current) }
            }

            if (self != null) {
                self.nodes = nodes
                if (owner == null) nodes.firstOrNull()?.let { roots += self.group to it }
            }
            return nodes
        }
    }

    private fun linkSubcompositionRoots(
        hosts: Map<CompositionData, HostView?>,
        roots: MutableMap<HostView, LayoutCoordinates>,
    ) {
        val itemsByHostNode = HashMap<LayoutInfo, MutableList<NamedGroup>>()
        val viewOfHostNode = HashMap<LayoutInfo, HostView>()
        for ((data, scan) in scans) {
            val view = hosts[data]
            for ((group, firstNode) in scan.roots) {
                group.isListItem = false
                val hostNode = hostOf(firstNode.parentInfo) ?: continue
                group.parent = nodeOwners[hostNode]
                itemsByHostNode.getOrPut(hostNode) { ArrayList() } += group
                if (view != null) viewOfHostNode[hostNode] = view
            }
        }
        for ((hostNode, items) in itemsByHostNode) {
            markListItems(hostNode, items, viewOfHostNode[hostNode], roots)
        }
    }

    private fun markListItems(
        hostNode: LayoutInfo,
        items: List<NamedGroup>,
        view: HostView?,
        roots: MutableMap<HostView, LayoutCoordinates>,
    ) {
        val repeats = items.groupingBy { it.name }.eachCount().values.any { it >= 2 }
        if (!repeats) return
        val hostBounds = view?.let { listOf(hostNode).visibleBounds(it, roots) }
        for (item in items) {
            val bounds = item.bounds ?: continue
            item.isListItem = hostBounds == null || !bounds.isCloseTo(hostBounds, SAME_BOUNDS_TOLERANCE_PX)
        }
    }

    /** Nearest ancestor node owned by a labeled group — the node hosting the subcomposition. */
    private fun hostOf(start: LayoutInfo?): LayoutInfo? {
        var info = start
        while (info != null) {
            if (info in nodeOwners) return info
            info = info.parentInfo
        }
        return null
    }

    private fun CompositionGroup.nameTag(): NameTag? {
        val known = markerKey
        // source information 이 켜져 있으면 data 를 여는 비용이 크다 — 마커 그룹 key 로 먼저 거른다
        if (known != null && key != known) return null
        val tag = data.firstOrNull { it is NameTag } as NameTag? ?: return null
        if (known == null) markerKey = key
        return tag
    }

    /**
     * Union of the nodes' bounds in window coordinates, clipped by their clipping ancestors and by
     * the visible part of the host view.
     *
     * `boundsInWindow()` recomputes the view's window position on every call (4× per node), so the
     * bounds are taken in compose root coordinates and shifted by the host view origin once.
     */
    private fun List<LayoutInfo>.visibleBounds(
        host: HostView,
        roots: MutableMap<HostView, LayoutCoordinates>,
    ): LabelRect? {
        var union: LabelRect? = null
        for (node in this) {
            if (!node.isAttached || !node.isPlaced) continue
            val coordinates = node.coordinates
            if (!coordinates.isAttached) continue
            val root = roots.getOrPut(host) { coordinates.findRootCoordinates() }
            val rect = root.localBoundingBoxOf(coordinates)
            val clip = host.clip
            val left = maxOf(rect.left + host.originX, clip.left)
            val top = maxOf(rect.top + host.originY, clip.top)
            val right = minOf(rect.right + host.originX, clip.right)
            val bottom = minOf(rect.bottom + host.originY, clip.bottom)
            if (right <= left || bottom <= top) continue
            val bounds = LabelRect(left, top, right, bottom)
            union = union?.union(bounds) ?: bounds
        }
        return union
    }
}
