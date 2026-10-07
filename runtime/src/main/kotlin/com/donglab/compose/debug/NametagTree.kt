package com.donglab.compose.debug

import android.graphics.PixelFormat
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.tooling.CompositionGroup
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.node.InteroperableComposeUiNode

/** How deep to look into an AndroidView for an opaque background. */
private const val MAX_COVER_DEPTH = 6

/**
 * Where a composition is shown: its ComposeView, the visible part of it and the view's origin,
 * clip and origin in window coordinates.
 *
 * @property covers parts hidden by views drawn on top of the ComposeView (a top bar over a list),
 * in the host view's own coordinates
 */
internal class HostView(
    val view: View,
    val clip: LabelRect,
    val originX: Float,
    val originY: Float,
    val covers: List<LabelRect> = emptyList(),
) {
    /** [clip] in the host view's own coordinates (= compose root coordinates). */
    val localClip: LabelRect = clip.offset(-originX, -originY)

    /** Compose root coordinates, looked up once per cycle. */
    var rootCoordinates: LayoutCoordinates? = null
}

/** Group key of the [__debugComposableName] group, learned from the first tag found. */
private var markerKey: Any? = null

/** Unreadable compositions are skipped and retried; logged only once to avoid noise. */
private var loggedSkippedComposition = false

/** Set when this Compose version has no interop node API — AndroidViews then cover nothing. */
private var interopUnavailable = false

/**
 * Labeled composables of one window, read from the slot table the same way Layout Inspector does.
 *
 * - a group whose direct child holds a [NameTag] is the composable with that name
 * - its bounds are the union of the first layout nodes found under it (window coordinates)
 * - top-level names of a subcomposition (LazyRow item, Scaffold slot …) are linked to the
 *   labeled group that owns the host layout node, so wrapper chains can span compositions
 * - subcompositions under one host whose first name repeats (`ShortcutItem` × N) are list items;
 *   one-off slots (Scaffold content) and pages that fill the host (pager) are not
 * - an AndroidView drawn after a composable (a Fragment page pushed over a Compose screen) covers it:
 *   the covered part is cut from its bounds, and a fully covered composable gets no label
 *
 * The slot table walk is the expensive part, so it is cached per composition and redone only when
 * that composition changed (a name tag entered or left, or a cached node got detached).
 * Bounds are recomputed from the cached nodes on every cycle ([begin] → [step] …) and, for shown
 * labels, on every frame through [follow].
 *
 * A composition that cannot be read right now (for example while another writer holds its slot
 * table) is skipped for this cycle and retried on the next one; it never fails the whole scan.
 *
 * Main thread only, outside of composition.
 */
internal class NametagTree {

    private class CompositionScan(
        val named: List<NamedGroup>,
        val roots: List<Pair<NamedGroup, LayoutInfo>>,
        val nodes: List<LayoutInfo>,
        val ownedNodes: List<LayoutInfo>,
        val interopNodes: List<LayoutInfo>,
    ) {
        fun hasDetachedNode(): Boolean = named.any { named -> named.nodes.any { !it.isAttached } }
    }

    /** An AndroidView and the opaque parts it paints (window coordinates). */
    private class Cover(val node: LayoutInfo, val host: View, val rects: List<LabelRect>) {
        /** Ancestor → its child on the way down to [node]. */
        val path = HashMap<LayoutInfo, LayoutInfo>().also { path ->
            var child = node
            var parent = node.parentInfo
            while (parent != null) {
                path[parent] = child
                child = parent
                parent = parent.parentInfo
            }
        }
    }

    private val scans = LinkedHashMap<CompositionData, CompositionScan>()
    private val dirty = HashSet<CompositionData>()
    private val nodeOwners = HashMap<LayoutInfo, NamedGroup>()

    /** Composition order of every walked node: scan id in the high bits, position in the low bits. */
    private val nodeOrder = HashMap<LayoutInfo, Long>()
    private var nextScanId = 0

    fun invalidate(data: CompositionData) {
        dirty += data
    }

    fun remove(data: CompositionData) {
        scans.remove(data)?.let(::forget)
        dirty -= data
        hosts.remove(data) // 진행 중인 사이클이 사라진 composition 을 다시 읽지 않도록
    }

    /**
     * Current bounds of [group] in its host view's coordinates: clipped like the scan did and with
     * covered parts cut out. Cheap enough to run on every frame for the shown labels.
     */
    fun follow(group: NamedGroup): LabelRect? {
        val host = group.host ?: return null
        var bounds = group.nodes.shownBounds(host) ?: return null
        for (cover in group.covers) bounds = bounds.minus(cover) ?: return null
        return bounds
    }

    // ── 한 번의 갱신을 여러 프레임에 나눠 실행하는 사이클 ──────────────

    private enum class Phase { Rescan, Bounds, Link }

    private var phase = Phase.Link
    private val hosts = LinkedHashMap<CompositionData, HostView?>()
    private var boundsQueue: List<Pair<CompositionData, CompositionScan>> = emptyList()
    private var boundsIndex = 0

    /**
     * Starts a refresh cycle; drive it with [step] until it returns the groups.
     *
     * @param hosts host view of each composition, `null` when the view is hidden or off screen
     */
    fun begin(hosts: Map<CompositionData, HostView?>) {
        scans.keys.filter { it !in hosts }.forEach(::remove)
        dirty.retainAll(hosts.keys)
        this.hosts.clear()
        this.hosts.putAll(hosts)
        phase = Phase.Rescan
    }

    /**
     * Runs the current cycle until [budgetNanos] is spent.
     *
     * Work is split per composition (slot table walk, bounds) and linking gets a step of its own,
     * so one step stays short and the cycle can span several frames. Returns the labeled groups
     * when the cycle is complete, `null` when more steps are needed.
     */
    fun step(budgetNanos: Long): List<NamedGroup>? {
        val deadline = System.nanoTime() + budgetNanos

        if (phase == Phase.Rescan) {
            val walked = rescanned
            val done = traceSection("Nametag:rescan") { rescanUntil(deadline) }
            if (!done) return null
            boundsQueue = scans.entries.map { it.key to it.value }
            boundsIndex = 0
            phase = Phase.Bounds
            // 이번 단계에서 slot table 을 읽었다면 영역 계산은 다음 프레임 뒤로 — 한 단계가 길어지지 않도록
            if (rescanned != walked) return null
        }

        if (phase == Phase.Bounds) {
            measured = HashMap()
            try {
                traceSection("Nametag:bounds") {
                    while (boundsIndex < boundsQueue.size && System.nanoTime() < deadline) {
                        val (data, scan) = boundsQueue[boundsIndex++]
                        val host = hosts[data]
                        for (group in scan.named) {
                            group.host = host
                            group.covers = emptyList()
                            group.bounds = host?.let { group.nodes.shownBounds(it)?.offset(it.originX, it.originY) }
                        }
                    }
                }
            } finally {
                measured = null
            }
            if (boundsIndex < boundsQueue.size) return null
            phase = Phase.Link
            return null
        }

        return traceSection("Nametag:link") {
            linkSubcompositionRoots(hosts)
            // 사이클 도중 다시 읽힌 composition 은 영역이 아직 없으므로 다음 사이클에 나온다
            scans.values.flatMap { it.named }
        }
    }

    /**
     * Cuts what AndroidViews drawn on top hide from the groups [step] returned. Runs as its own step:
     * it reads the AndroidViews' current place, not the composables', so it needs no same-frame bounds.
     */
    fun cutCovers() = cutCoveredParts()

    /** Re-walks new, changed and stale compositions until [deadline]. Returns `true` when none is left. */
    private fun rescanUntil(deadline: Long): Boolean {
        for (data in hosts.keys) {
            val scan = scans[data]
            if (scan == null || data in dirty || scan.hasDetachedNode()) {
                if (System.nanoTime() >= deadline) return false
                if (rescan(data)) dirty -= data
            }
        }
        return true
    }

    /** Node bounds taken during one bounds step — a wrapper chain shares its first node, so each is measured once. */
    private var measured: HashMap<LayoutInfo, LabelRect?>? = null

    /** Number of slot table walks so far — tells [step] whether the current step already did one. */
    private var rescanned = 0

    /** Re-walks one composition. Returns `false` when it could not be read this time. */
    private fun rescan(data: CompositionData): Boolean {
        rescanned++
        scans.remove(data)?.let(::forget)
        val walk = Walk(nextScanId++)
        return try {
            for (group in data.compositionGroups) walk.visit(group, owner = null)
            scans[data] = CompositionScan(walk.named, walk.roots, walk.nodes, walk.ownedNodes, walk.interopNodes)
            true
        } catch (t: Throwable) {
            // 다른 쪽이 slot table 을 쓰는 중이거나 읽을 수 없는 상태 — 이번 사이클만 건너뜀
            walk.ownedNodes.forEach(nodeOwners::remove)
            walk.nodes.forEach(nodeOrder::remove)
            if (!loggedSkippedComposition) {
                loggedSkippedComposition = true
                try {
                    android.util.Log.d(TAG, "Skipped a composition that could not be read; will retry", t)
                } catch (_: Throwable) {
                }
            }
            false
        }
    }

    private fun forget(scan: CompositionScan) {
        scan.ownedNodes.forEach(nodeOwners::remove)
        scan.nodes.forEach(nodeOrder::remove)
    }

    private inner class Walk(private val scanId: Int) {
        val named = ArrayList<NamedGroup>()
        val roots = ArrayList<Pair<NamedGroup, LayoutInfo>>()
        val nodes = ArrayList<LayoutInfo>()
        val ownedNodes = ArrayList<LayoutInfo>()
        val interopNodes = ArrayList<LayoutInfo>()

        /** Returns the top-level layout nodes of [group]'s subtree. */
        fun visit(group: CompositionGroup, owner: NamedGroup?): List<LayoutInfo> {
            val self = group.compositionGroups.firstNotNullOfOrNull { it.nameTag() }
                ?.let { tag -> NamedGroup(tag.name, owner).also(named::add) }
            val current = self ?: owner

            val node = group.node as? LayoutInfo
            val nodes = if (node != null) {
                nodeOrder[node] = (scanId.toLong() shl 32) or this.nodes.size.toLong()
                this.nodes += node
                if (node.interopViewOrNull() != null) interopNodes += node
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
                if (owner == null) nodes.firstOrNull()?.let { roots += self to it }
            }
            return nodes
        }
    }

    private fun linkSubcompositionRoots(hosts: Map<CompositionData, HostView?>) {
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
            markListItems(hostNode, items, viewOfHostNode[hostNode])
        }
    }

    private fun markListItems(hostNode: LayoutInfo, items: List<NamedGroup>, view: HostView?) {
        val repeats = items.groupingBy { it.name }.eachCount().values.any { it >= 2 }
        if (!repeats) return
        val hostBounds = view?.let { listOf(hostNode).visibleBounds(it) }
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

    // ── AndroidView 가 위에 그리는 부분 ─────────────────────────

    /**
     * Cuts the parts covered by an AndroidView drawn later in the same ComposeView.
     *
     * Labels are painted on top of their ComposeView, so a Fragment page shown through an
     * AndroidView over a Compose screen (same ComposeView) would otherwise show the labels of the
     * screen behind it. Composables that contain the AndroidView keep their label.
     */
    private fun cutCoveredParts() {
        val covers = ArrayList<Cover>()
        for ((data, scan) in scans) {
            val host = hosts[data] ?: continue
            for (node in scan.interopNodes) {
                coverOf(node, host.view)?.let(covers::add)
            }
        }
        if (covers.isEmpty()) return

        for ((data, scan) in scans) {
            val host = hosts[data] ?: continue
            for (group in scan.named) {
                var visible: LabelRect? = group.bounds ?: continue
                val first = group.nodes.firstOrNull() ?: continue
                var cut: ArrayList<LabelRect>? = null
                for (cover in covers) {
                    if (cover.host !== host.view || !cover.isDrawnOver(first)) continue
                    for (rect in cover.rects) {
                        val current = visible ?: break
                        if (!rect.intersects(current)) continue
                        val list = cut ?: ArrayList<LabelRect>().also { cut = it }
                        list += rect.offset(-host.originX, -host.originY)
                        visible = current.minus(rect)
                    }
                }
                group.covers = cut ?: continue
                group.bounds = visible
            }
        }
    }

    /** `true` when this cover's AndroidView is drawn after [node] — after it among their nearest common ancestor's children. */
    private fun Cover.isDrawnOver(node: LayoutInfo): Boolean {
        if (node in path) return false // AndroidView 을 감싸는 Composable — 가려지는 게 아니라 그 화면 자체
        var child = node
        var parent = node.parentInfo
        while (parent != null) {
            val coverSide = path[parent]
            if (coverSide != null) return isPlacedAfter(coverSide, child)
            child = parent
            parent = parent.parentInfo
        }
        return false
    }

    /** Composition order of two siblings; siblings from different compositions (lazy items) can't be compared. */
    private fun isPlacedAfter(a: LayoutInfo, b: LayoutInfo): Boolean {
        val orderA = nodeOrder[a] ?: return false
        val orderB = nodeOrder[b] ?: return false
        if (orderA ushr 32 != orderB ushr 32) return false
        return orderA > orderB
    }

    /** The opaque parts an AndroidView paints, `null` when it is hidden or (as far as we can tell) see-through. */
    private fun coverOf(node: LayoutInfo, host: View): Cover? {
        return try {
            if (!node.isAttached || !node.isPlaced) return null
            val view = node.interopViewOrNull() ?: return null
            if (!view.isShown) return null
            val rects = ArrayList<LabelRect>()
            view.collectOpaqueRects(rects)
            if (rects.isEmpty()) null else Cover(node, host, rects)
        } catch (_: Throwable) {
            null
        }
    }


    // ── 영역 ───────────────────────────────────────────────

    private fun CompositionGroup.nameTag(): NameTag? {
        val known = markerKey
        // source information 이 켜져 있으면 data 를 여는 비용이 크다 — 마커 그룹 key 로 먼저 거른다
        if (known != null && key != known) return null
        val tag = data.firstOrNull { it is NameTag } as NameTag? ?: return null
        if (known == null) markerKey = key
        return tag
    }

    /** [localBounds] shifted to window coordinates. */
    private fun List<LayoutInfo>.visibleBounds(host: HostView): LabelRect? =
        localBounds(host)?.offset(host.originX, host.originY)

    /** [localBounds] without the parts views on top of the ComposeView hide (host coordinates). */
    private fun List<LayoutInfo>.shownBounds(host: HostView): LabelRect? {
        var bounds = localBounds(host) ?: return null
        for (cover in host.covers) bounds = bounds.minus(cover) ?: return null
        return bounds
    }

    /**
     * Union of the nodes' bounds in host view coordinates, clipped by their clipping ancestors and
     * by the visible part of the host view.
     *
     * `boundsInWindow()` recomputes the view's window position on every call (4× per node), so the
     * bounds are taken in compose root coordinates, which are the host view's own coordinates.
     */
    private fun List<LayoutInfo>.localBounds(host: HostView): LabelRect? {
        var union: LabelRect? = null
        for (node in this) {
            val bounds = node.clippedBounds(host) ?: continue
            union = union?.union(bounds) ?: bounds
        }
        return union
    }

    /** One node's bounds in host coordinates, clipped like [localBounds]; memoized while measuring. */
    private fun LayoutInfo.clippedBounds(host: HostView): LabelRect? {
        val cache = measured
        if (cache != null && cache.containsKey(this)) return cache[this]
        val bounds = measureClipped(host)
        cache?.put(this, bounds)
        return bounds
    }

    private fun LayoutInfo.measureClipped(host: HostView): LabelRect? {
        val rect = try {
            if (!isAttached || !isPlaced) return null
            val coordinates = coordinates
            if (!coordinates.isAttached) return null
            val root = host.rootCoordinates?.takeIf { it.isAttached }
                ?: coordinates.findRootCoordinates().also { host.rootCoordinates = it }
            root.localBoundingBoxOf(coordinates)
        } catch (_: Throwable) {
            // 사이클 사이에 떨어져 나간 노드 · 다른 루트의 노드 — 이번에는 영역 없음
            return null
        }
        val clip = host.localClip
        val left = maxOf(rect.left, clip.left)
        val top = maxOf(rect.top, clip.top)
        val right = minOf(rect.right, clip.right)
        val bottom = minOf(rect.bottom, clip.bottom)
        if (right <= left || bottom <= top) return null
        return LabelRect(left, top, right, bottom)
    }
}

private val coverLocation = IntArray(2)

/**
 * Collects the parts of this view that hide what is drawn below it (window coordinates).
 *
 * Only views with an opaque background (or a video surface) count — a see-through container
 * still shows what is below, so it is searched for opaque children instead.
 */
internal fun View.collectOpaqueRects(out: MutableList<LabelRect>, depth: Int = 0) {
    if (visibility != View.VISIBLE || alpha < 1f || width == 0 || height == 0) return
    @Suppress("DEPRECATION")
    val opaque = isOpaque || background?.opacity == PixelFormat.OPAQUE || this is SurfaceView || this is TextureView
    if (opaque) {
        getLocationInWindow(coverLocation)
        out += LabelRect(
            coverLocation[0].toFloat(),
            coverLocation[1].toFloat(),
            (coverLocation[0] + width).toFloat(),
            (coverLocation[1] + height).toFloat(),
        )
        return
    }
    if (this is ViewGroup && depth < MAX_COVER_DEPTH) {
        for (i in 0 until childCount) getChildAt(i).collectOpaqueRects(out, depth + 1)
    }
}

/** The Android View held by an AndroidView node, `null` for plain Compose nodes. */
@OptIn(InternalComposeUiApi::class)
private fun LayoutInfo.interopViewOrNull(): View? {
    if (interopUnavailable) return null
    return try {
        (this as? InteroperableComposeUiNode)?.getInteropView()
    } catch (_: LinkageError) {
        // 이 API 가 없는 Compose 버전 — 가림 처리만 끄고 나머지는 그대로
        interopUnavailable = true
        null
    } catch (_: Throwable) {
        null
    }
}
