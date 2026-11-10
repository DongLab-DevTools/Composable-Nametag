package com.donglab.compose.debug

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderNode
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Choreographer
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.runtime.tooling.CompositionData
import kotlin.math.absoluteValue

private const val MIN_SCAN_INTERVAL_MS = 100L
private const val MAX_SCAN_INTERVAL_MS = 800L
private const val STEP_BUDGET_NANOS = 2_000_000L

/** Draws in a row without any label moving before positions are checked less often. */
private const val STILL_DRAWS_BEFORE_SPARSE = 8

/** Rounding slack when telling whether a composable is cut on a side (px). */
private const val CUT_SLACK_PX = 0.5f

/** While still, positions are checked on every Nth draw (≈ 30 times a second at 120 Hz). */
private const val SPARSE_FOLLOW_EVERY = 4

/**
 * Tracks which compositions carry name tags, per window root view.
 *
 * Called from composition effects; always hops to the main thread and never throws into the app.
 */
internal object NametagRegistry {

    private class Composition(val view: View) {
        var tagCount = 0
    }

    private class Root(val overlay: NametagOverlay) {
        val compositions = LinkedHashMap<CompositionData, Composition>()
    }

    private val roots = HashMap<View, Root>()

    init {
        NametagSafety.onBroken = ::clear
    }

    /** A name tag entered [data], hosted by [view] (the ComposeView) inside the window of [rootView]. */
    fun register(rootView: View, data: CompositionData, view: View) = runGuardedOnMain("register") {
        // attach 가 실패하면 map 에 남기지 않는다
        val root = roots[rootView] ?: Root(NametagOverlay(rootView)).also {
            it.overlay.attach()
            roots[rootView] = it
        }
        root.compositions.getOrPut(data) { Composition(view) }.tagCount++
        root.overlay.onCompositionChanged(data)
    }

    fun unregister(rootView: View, data: CompositionData) = runGuardedOnMain("unregister") {
        val root = roots[rootView] ?: return@runGuardedOnMain
        val composition = root.compositions[data] ?: return@runGuardedOnMain
        composition.tagCount--
        if (composition.tagCount > 0) {
            root.overlay.onCompositionChanged(data)
            return@runGuardedOnMain
        }

        root.compositions.remove(data)
        if (root.compositions.isEmpty()) {
            forget(rootView)
        } else {
            root.overlay.onCompositionRemoved(data)
        }
    }

    fun viewsOf(rootView: View): Map<CompositionData, View> =
        roots[rootView]?.compositions?.mapValues { it.value.view }.orEmpty()

    /** Drops a window — its last composition left, or its root view left the screen. */
    fun forget(rootView: View) {
        roots.remove(rootView)?.overlay?.detach()
    }

    /** Turns every overlay off (repeated failures). */
    fun clear() {
        val overlays = roots.values.map { it.overlay }
        roots.clear()
        overlays.forEach { it.detach() }
    }
}

/**
 * Finds and places the labels of one window; each ComposeView paints its own through [HostLabels].
 *
 * Labels live outside the app's layout tree, so they never shift layout. They are painted on the
 * ComposeView's [View.getOverlay], right after its content: a view stacked over that ComposeView
 * covers them like it covers the content. A scan runs after a frame is drawn, at most every
 * [MIN_SCAN_INTERVAL_MS]. While the result stays the same (an idle screen that only animates)
 * the interval doubles up to [MAX_SCAN_INTERVAL_MS]; a change or a name tag entering/leaving
 * resets it. Labels are repainted only when the result changes or the ComposeView redraws
 * (scrolling), and then follow their composables in the same frame.
 *
 * A scan is a cycle split into short steps (about [STEP_BUDGET_NANOS] each: slot table walks and
 * bounds per composition, then linking, covers, selection and placement — one step each).
 * Each step starts right after a frame finishes (frame callback → post), so it uses the idle time
 * before the next vsync instead of delaying a pending frame; a cycle may span a few frames.
 *
 * Compositions whose host view is hidden or off screen (a kept-alive tab moved out of the window,
 * a hidden Fragment) are skipped, and bounds are clipped to the visible part of the host view.
 *
 * Every callback the system invokes (pre-draw, frame, handler, draw, view detach) runs through
 * [NametagSafety] so that nothing thrown here reaches the host app.
 */
internal class NametagOverlay(private val root: View) : ViewTreeObserver.OnPreDrawListener {

    private val handler = Handler(Looper.getMainLooper())
    private val choreographer = Choreographer.getInstance()
    private val scanTask = Runnable {
        scanPending = false
        NametagSafety.guard("scan") { scan() }
    }

    // 다음 프레임 콜백에서 post 하면 그 프레임의 traversal(측정·배치·그리기) 이 끝난 직후에 실행된다
    private val afterFrame = Choreographer.FrameCallback {
        NametagSafety.guard("frame") { handler.post(scanTask) }
    }
    private val waitForFrame = Runnable {
        NametagSafety.guard("frame") { choreographer.postFrameCallback(afterFrame) }
    }

    // 창이 사라졌는데 등록 해제가 오지 않은 경우에도 View 를 붙잡고 있지 않도록
    private val rootAttachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) {
            NametagSafety.guard("detach") { NametagRegistry.forget(root) }
        }
    }
    private var scanPending = false
    private var lastScanAt = 0L
    private var scanInterval = MIN_SCAN_INTERVAL_MS
    private var labels: List<PlacedLabel> = emptyList()
    private var cycleRunning = false
    private var cycleGroups: List<NamedGroup>? = null
    private var coversCut = false
    private var cycleTargets: List<LabelTarget>? = null
    private val painter = LabelPainter(root.resources.displayMetrics)
    private val tree = NametagTree()
    private val hostLabels = HashMap<View, HostLabels>()
    private val windowOffset = IntArray(2)
    private val visibleRect = Rect()
    private val viewOrigin = IntArray(2)

    fun attach() {
        try {
            root.viewTreeObserver.addOnPreDrawListener(this)
            root.addOnAttachStateChangeListener(rootAttachListener)
            requestScan()
        } catch (t: Throwable) {
            detach()
            throw t
        }
    }

    /** Removes everything this overlay hooked into. Each step is independent and never throws. */
    fun detach() {
        quietly { cancelScan() }
        quietly { root.viewTreeObserver.takeIf { it.isAlive }?.removeOnPreDrawListener(this) }
        quietly { root.removeOnAttachStateChangeListener(rootAttachListener) }
        hostLabels.forEach { (view, labels) ->
            quietly { view.overlay.remove(labels) }
            quietly { labels.release() }
        }
        hostLabels.clear()
        cycleRunning = false
        cycleGroups = null
        cycleTargets = null
        coversCut = false
        labels = emptyList()
    }

    override fun onPreDraw(): Boolean {
        NametagSafety.guard("preDraw") { requestScan() }
        return true // 그리기를 막지 않는다
    }

    fun onCompositionChanged(data: CompositionData) {
        tree.invalidate(data)
        resetScanInterval()
    }

    fun onCompositionRemoved(data: CompositionData) {
        tree.remove(data)
        resetScanInterval()
    }

    private fun resetScanInterval() {
        // 진행 중인 사이클은 그대로 이어가고, 바뀐 composition 은 다음 사이클에 반영
        if (cycleRunning) {
            scanInterval = MIN_SCAN_INTERVAL_MS
            return
        }
        if (scanInterval != MIN_SCAN_INTERVAL_MS) {
            scanInterval = MIN_SCAN_INTERVAL_MS
            cancelScan()
        }
        requestScan()
    }

    private fun requestScan(immediately: Boolean = false) {
        if (cycleRunning && !immediately) return
        if (scanPending) return
        scanPending = true
        val wait = if (immediately) 0L else lastScanAt + scanInterval - SystemClock.uptimeMillis()
        handler.postDelayed(waitForFrame, wait.coerceAtLeast(0L))
    }

    private fun cancelScan() {
        handler.removeCallbacks(waitForFrame)
        choreographer.removeFrameCallback(afterFrame)
        handler.removeCallbacks(scanTask)
        scanPending = false
    }

    /** Runs one step of the current scan cycle, starting a new cycle when none is running. */
    private fun scan() = traceSection("Nametag:scan") {
        val groups = cycleGroups
        if (groups == null) {
            try {
                if (!cycleRunning) beginCycle()
                cycleGroups = tree.step(STEP_BUDGET_NANOS)
            } catch (t: Throwable) {
                // 디버그 도구가 앱을 죽이지 않도록 — 이번 사이클만 건너뜀
                cycleRunning = false
                NametagSafety.failed("scan", t)
                return@traceSection
            }
            // 남은 단계(가림 → 선택 → 배치) 는 다음 프레임 뒤에 이어서 — 그 사이 움직인 만큼은 그릴 때 따라잡는다
            requestScan(immediately = true)
            return@traceSection
        }
        if (!coversCut) {
            // 가림 계산은 따로 한 단계 — 선택 · 배치와 묶으면 한 단계가 길어진다
            try {
                traceSection("Nametag:covers") { tree.cutCovers() }
            } catch (t: Throwable) {
                cycleGroups = null
                cycleRunning = false
                NametagSafety.failed("scan", t)
                return@traceSection
            }
            coversCut = true
            requestScan(immediately = true)
            return@traceSection
        }
        val targets = cycleTargets
        if (targets == null) {
            // 선택과 배치도 각각 한 단계씩
            try {
                cycleTargets = traceSection("Nametag:select") {
                    selectLabelTargets(groups).map { target ->
                        // 체인 이름이 라벨이 붙는 영역보다 길면 가운데를 줄임 — 옆 아이템 라벨을 밀어내지 않도록
                        val name = fitChainName(target.levels, target.bounds.width) { painter.measure(it).width }
                        target.copy(name = name, bounds = target.bounds.offset(cycleDx, cycleDy))
                    }
                }
            } catch (t: Throwable) {
                cycleGroups = null
                coversCut = false
                cycleRunning = false
                NametagSafety.failed("scan", t)
                return@traceSection
            }
            requestScan(immediately = true)
            return@traceSection
        }
        cycleGroups = null
        cycleTargets = null
        coversCut = false
        cycleRunning = false

        val placed = try {
            traceSection("Nametag:place") {
                placeLabels(targets, painter::measure)
            }
        } catch (t: Throwable) {
            NametagSafety.failed("scan", t)
            return@traceSection
        }
        NametagSafety.succeeded()

        val changed = placed != labels
        scanInterval = if (changed) MIN_SCAN_INTERVAL_MS else (scanInterval * 2).coerceAtMost(MAX_SCAN_INTERVAL_MS)
        if (!changed) return@traceSection
        labels = placed
        show(placed)
        logSelection(groups, targets, placed)
    }

    /**
     * Why names are shown or left out — only with `adb shell setprop log.tag.ComposableNametag DEBUG`.
     * Logged when the result changes, so a still screen logs once.
     */
    private fun logSelection(groups: List<NamedGroup>, targets: List<LabelTarget>, placed: List<PlacedLabel>) {
        val enabled = try {
            android.util.Log.isLoggable(TAG, android.util.Log.DEBUG)
        } catch (_: Throwable) {
            false
        }
        if (!enabled) return
        try {
            val visible = groups.count { it.bounds != null }
            val inChains = targets.sumOf { it.levels.size }
            val placedNames = placed.mapTo(HashSet()) { it.name }
            val noRoom = targets.filter { it.name !in placedNames }.map { it.name }
            android.util.Log.d(
                TAG,
                "labels: visible=$visible → chains=${targets.size} (names in chains $inChains, " +
                    "list depth / hidden skipped ${visible - inChains}) → placed=${placed.size}, no room=${noRoom.size}",
            )
            for (target in targets) {
                if (target.levels.size > 1) android.util.Log.d(TAG, "  chain: ${target.levels.joinToString(CHAIN_SEPARATOR)} → ${target.name}")
            }
            for (name in noRoom) android.util.Log.d(TAG, "  no room: $name")
        } catch (_: Throwable) {
        }
    }

    /** Hands each ComposeView its labels; views left without labels drop their painter. */
    private fun show(placed: List<PlacedLabel>) {
        val byView = placed.groupBy { it.source?.host?.view }
        val gone = hostLabels.keys.filter { it !in byView }
        for (view in gone) {
            hostLabels.remove(view)?.let { quietly { view.overlay.remove(it) }; quietly { it.release() } }
        }
        for ((view, labels) in byView) {
            if (view == null) continue
            val painterOfView = hostLabels[view] ?: HostLabels(view, tree, painter).also {
                view.overlay.add(it)
                hostLabels[view] = it
            }
            painterOfView.show(labels)
        }
    }

    private var cycleDx = 0f
    private var cycleDy = 0f

    private fun beginCycle() {
        lastScanAt = SystemClock.uptimeMillis()
        root.getLocationInWindow(windowOffset)
        cycleDx = -windowOffset[0].toFloat()
        cycleDy = -windowOffset[1].toFloat()
        val hosts = traceSection("Nametag:hosts") {
            val hostViews = HashMap<View, HostView?>()
            NametagRegistry.viewsOf(root).mapValues { (_, view) ->
                hostViews.getOrPut(view) { view.toHostView() }
            }
        }
        tree.begin(hosts)
        cycleRunning = true
    }

    /** Visible part and origin of this view in window coordinates, `null` when hidden or fully off screen. */
    private fun View.toHostView(): HostView? {
        if (!isShown || !getGlobalVisibleRect(visibleRect)) return null
        getLocationInWindow(viewOrigin)
        // global = root view 좌표 → window 좌표
        val clip = LabelRect(
            (visibleRect.left + windowOffset[0]).toFloat(),
            (visibleRect.top + windowOffset[1]).toFloat(),
            (visibleRect.right + windowOffset[0]).toFloat(),
            (visibleRect.bottom + windowOffset[1]).toFloat(),
        )
        return HostView(this, clip, viewOrigin[0].toFloat(), viewOrigin[1].toFloat())
    }
}

/**
 * Paints the labels of one ComposeView on its overlay.
 *
 * Positions are taken again from the layout nodes when the ComposeView redraws: it redraws after
 * its own layout pass (a scroll moves items → relayout → redraw), so labels move with the content
 * in the same frame instead of waiting for the next scan.
 *
 * Some screens redraw every frame while nothing moves (a running animation). To keep that cheap:
 * - the painted labels are kept in a [RenderNode] (API 29+) and replayed until a position changes;
 *   a RenderNode that missed a frame loses its content, so it is re-recorded when empty
 * - after [STILL_DRAWS_BEFORE_SPARSE] draws without movement, positions are checked only every
 *   [SPARSE_FOLLOW_EVERY]th draw; the first movement goes back to checking every draw
 */
private class HostLabels(
    private val view: View,
    private val tree: NametagTree,
    private val painter: LabelPainter,
) : Drawable() {

    private var labels: List<PlacedLabel> = emptyList()
    private var followed: List<Followed?>? = null
    private var stillDraws = 0
    private var drawsSinceFollow = 0
    private val cache: RenderNode? = if (Build.VERSION.SDK_INT >= 29) RenderNode("Nametag") else null
    private var cacheValid = false

    fun show(labels: List<PlacedLabel>) {
        this.labels = labels
        followed = null
        stillDraws = 0
        setBounds(0, 0, view.width, view.height)
        invalidateSelf()
    }

    fun release() {
        if (Build.VERSION.SDK_INT >= 29) cache?.discardDisplayList()
    }

    override fun draw(canvas: Canvas) = NametagSafety.guard("draw") {
        traceSection("Nametag:draw") {
            if (shouldFollow()) follow()
            val current = followed ?: return@traceSection
            if (Build.VERSION.SDK_INT >= 29 && cache != null && canvas.isHardwareAccelerated) {
                // 한 프레임이라도 그려지지 않으면 시스템이 RenderNode 내용을 버린다 — 그때는 다시 그림
                if (!cacheValid || !cache.hasDisplayList() || cache.width != view.width || cache.height != view.height) {
                    cache.setPosition(0, 0, view.width, view.height)
                    val recording = cache.beginRecording()
                    try {
                        paint(recording, current)
                    } finally {
                        cache.endRecording()
                    }
                    cacheValid = true
                }
                canvas.drawRenderNode(cache)
            } else {
                paint(canvas, current)
            }
        }
    }

    private fun shouldFollow(): Boolean {
        if (followed == null || stillDraws < STILL_DRAWS_BEFORE_SPARSE) return true
        return ++drawsSinceFollow >= SPARSE_FOLLOW_EVERY
    }

    private fun follow() = traceSection("Nametag:follow") {
        drawsSinceFollow = 0
        val now = labels.map { label -> label.source?.let(tree::follow) }
        if (now == followed) {
            stillDraws++
        } else {
            stillDraws = 0
            followed = now
            cacheValid = false
        }
    }

    private fun paint(canvas: Canvas, positions: List<Followed?>) {
        for (i in labels.indices) {
            val at = positions.getOrNull(i) ?: continue
            val label = labels[i]
            val area = label.source?.host?.localClip ?: continue
            painter.draw(canvas, label.name, at, label.offsetY, label.source?.covers.orEmpty(), area)
        }
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

private val overlayColors = intArrayOf(
    0xDDE91E63.toInt(),
    0xDD2196F3.toInt(),
    0xDD4CAF50.toInt(),
    0xDDFF9800.toInt(),
    0xDD9C27B0.toInt(),
    0xDD00BCD4.toInt(),
    0xDDFF5722.toInt(),
    0xDD607D8B.toInt(),
)

/** Same look as the previous in-layout label: 7sp bold monospace on a hashed color chip. */
private class LabelPainter(metrics: DisplayMetrics) {

    private val paddingH = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, metrics)
    private val paddingV = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, metrics)
    private val corner = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, metrics)

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 7f, metrics)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 0.5f, metrics)
        color = 0x80FFFFFF.toInt()
    }
    private val fontMetrics = textPaint.fontMetrics
    private val textHeight = fontMetrics.descent - fontMetrics.ascent
    private val rectF = RectF()
    private val sizes = HashMap<String, LabelSize>()

    // 스캔마다 같은 이름을 다시 재지 않도록 캐시
    fun measure(name: String): LabelSize = sizes.getOrPut(name) {
        LabelSize(textPaint.measureText(name) + paddingH * 2, textHeight + paddingV * 2)
    }

    /**
     * Draws [name] at the top-left of the full bounds, [offsetY] down (stacked).
     *
     * Never draws a cut label:
     * - on a side where the composable itself is cut (screen edge, edge of a scrolling list), a label
     *   reaching past that edge is skipped instead of being sliced
     * - a label of a composable that is not cut but wider than it (a button at the screen edge) is
     *   pulled back inside [area], the visible part of the host view
     * A label over a part [covers] hide is skipped too.
     */
    fun draw(canvas: Canvas, name: String, at: Followed, offsetY: Float, covers: List<LabelRect>, area: LabelRect) {
        val size = measure(name)
        val full = at.full
        val visible = at.visible
        var left = labelLeft(full)
        var top = labelTop(full, offsetY)
        // 잘리지 않은 쪽으로 화면 밖에 넘치면 안쪽으로 당김
        if (left + size.width > area.right && visible.right >= full.right - CUT_SLACK_PX) left = maxOf(area.left, area.right - size.width)
        if (top + size.height > area.bottom && visible.bottom >= full.bottom - CUT_SLACK_PX) top = maxOf(area.top, area.bottom - size.height)
        val right = left + size.width
        val bottom = top + size.height
        if (visible.left > full.left + CUT_SLACK_PX && left < visible.left) return
        if (visible.top > full.top + CUT_SLACK_PX && top < visible.top) return
        if (visible.right < full.right - CUT_SLACK_PX && right > visible.right) return
        if (visible.bottom < full.bottom - CUT_SLACK_PX && bottom > visible.bottom) return
        for (cover in covers) {
            if (left < cover.right && cover.left < right && top < cover.bottom && cover.top < bottom) return
        }
        drawChip(canvas, name, left, top, size)
    }

    private fun drawChip(canvas: Canvas, name: String, left: Float, top: Float, size: LabelSize) {
        rectF.set(left, top, left + size.width, top + size.height)
        fillPaint.color = overlayColors[name.hashCode().absoluteValue.mod(overlayColors.size)]
        canvas.drawRoundRect(rectF, corner, corner, fillPaint)
        canvas.drawRoundRect(rectF, corner, corner, borderPaint)
        canvas.drawText(name, left + paddingH, top + paddingV - fontMetrics.ascent, textPaint)
    }
}

private inline fun quietly(block: () -> Unit) {
    try {
        block()
    } catch (_: Throwable) {
    }
}
