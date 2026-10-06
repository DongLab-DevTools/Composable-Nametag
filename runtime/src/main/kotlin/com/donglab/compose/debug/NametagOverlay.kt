package com.donglab.compose.debug

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.Choreographer
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.runtime.tooling.CompositionData
import kotlin.math.absoluteValue

private const val TAG = "ComposableNametag"
private const val MIN_SCAN_INTERVAL_MS = 100L
private const val MAX_SCAN_INTERVAL_MS = 800L
private const val STEP_BUDGET_NANOS = 2_000_000L

/** Tracks which compositions carry name tags, per window root view. Main thread only. */
internal object NametagRegistry {

    private class Composition(val view: View) {
        var tagCount = 0
    }

    private class Root(val overlay: NametagOverlay) {
        val compositions = LinkedHashMap<CompositionData, Composition>()
    }

    private val roots = HashMap<View, Root>()

    /** A name tag entered [data], hosted by [view] (the ComposeView) inside the window of [rootView]. */
    fun register(rootView: View, data: CompositionData, view: View) {
        val root = roots.getOrPut(rootView) {
            Root(NametagOverlay(rootView)).also { it.overlay.attach() }
        }
        root.compositions.getOrPut(data) { Composition(view) }.tagCount++
        root.overlay.onCompositionChanged(data)
    }

    fun unregister(rootView: View, data: CompositionData) {
        val root = roots[rootView] ?: return
        val composition = root.compositions[data] ?: return
        composition.tagCount--
        if (composition.tagCount > 0) {
            root.overlay.onCompositionChanged(data)
            return
        }

        root.compositions.remove(data)
        if (root.compositions.isEmpty()) {
            root.overlay.detach()
            roots.remove(rootView)
        } else {
            root.overlay.onCompositionRemoved(data)
        }
    }

    fun viewsOf(rootView: View): Map<CompositionData, View> =
        roots[rootView]?.compositions?.mapValues { it.value.view }.orEmpty()
}

/**
 * Paints the labels of one window on top of everything, via the root view's [View.getOverlay].
 *
 * Labels live outside the app's layout tree: they never shift layout, never get clipped by a
 * parent and are never covered by a sibling. A scan runs after a frame is drawn, at most every
 * [MIN_SCAN_INTERVAL_MS]. While the result stays the same (an idle screen that only animates)
 * the interval doubles up to [MAX_SCAN_INTERVAL_MS]; a change or a name tag entering/leaving
 * resets it. The overlay is redrawn only when the result changes.
 *
 * A scan is a cycle split into short steps (about [STEP_BUDGET_NANOS] each: slot table walks and
 * bounds per composition, then selection and placement). Each step starts right after a frame
 * finishes (frame callback → post), so it uses the idle time before the next vsync instead of
 * delaying a pending frame; a cycle may span a few frames.
 *
 * Compositions whose host view is hidden or off screen (a kept-alive tab moved out of the window,
 * a hidden Fragment) are skipped, and bounds are clipped to the visible part of the host view.
 */
internal class NametagOverlay(private val root: View) : Drawable(), ViewTreeObserver.OnPreDrawListener {

    private val handler = Handler(Looper.getMainLooper())
    private val choreographer = Choreographer.getInstance()
    private val scanTask = Runnable {
        scanPending = false
        scan()
    }

    // 다음 프레임 콜백에서 post 하면 그 프레임의 traversal(측정·배치·그리기) 이 끝난 직후에 실행된다
    private val afterFrame = Choreographer.FrameCallback { handler.post(scanTask) }
    private val waitForFrame = Runnable { choreographer.postFrameCallback(afterFrame) }
    private var scanPending = false
    private var lastScanAt = 0L
    private var scanInterval = MIN_SCAN_INTERVAL_MS
    private var labels: List<PlacedLabel> = emptyList()
    private var cycleRunning = false
    private var cycleGroups: List<NamedGroup>? = null
    private val painter = LabelPainter(root.resources.displayMetrics)
    private val tree = NametagTree()
    private val windowOffset = IntArray(2)
    private val visibleRect = Rect()
    private val viewOrigin = IntArray(2)

    fun attach() {
        root.overlay.add(this)
        root.viewTreeObserver.addOnPreDrawListener(this)
        requestScan()
    }

    fun detach() {
        cancelScan()
        root.viewTreeObserver.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
        root.overlay.remove(this)
    }

    override fun onPreDraw(): Boolean {
        requestScan()
        return true
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
                Log.w(TAG, "Nametag scan failed", t)
                cycleRunning = false
                return@traceSection
            }
            // 남은 단계(또는 선택·배치) 는 다음 프레임 뒤에 이어서
            requestScan(immediately = true)
            return@traceSection
        }
        cycleGroups = null
        cycleRunning = false

        val placed = try {
            val targets = traceSection("Nametag:select") {
                selectLabelTargets(groups).map { it.copy(bounds = it.bounds.offset(cycleDx, cycleDy)) }
            }
            traceSection("Nametag:place") {
                placeLabels(targets, root.width.toFloat(), root.height.toFloat(), painter::measure)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Nametag scan failed", t)
            emptyList()
        }

        val changed = placed != labels
        scanInterval = if (changed) MIN_SCAN_INTERVAL_MS else (scanInterval * 2).coerceAtMost(MAX_SCAN_INTERVAL_MS)
        if (!changed) return@traceSection
        labels = placed
        setBounds(0, 0, root.width, root.height)
        invalidateSelf()
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
        return HostView(clip, viewOrigin[0].toFloat(), viewOrigin[1].toFloat())
    }

    override fun draw(canvas: Canvas) = traceSection("Nametag:draw") {
        labels.forEach { painter.draw(canvas, it) }
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

    fun draw(canvas: Canvas, label: PlacedLabel) {
        val rect = label.rect
        rectF.set(rect.left, rect.top, rect.right, rect.bottom)
        fillPaint.color = overlayColors[label.name.hashCode().absoluteValue.mod(overlayColors.size)]
        canvas.drawRoundRect(rectF, corner, corner, fillPaint)
        canvas.drawRoundRect(rectF, corner, corner, borderPaint)
        canvas.drawText(label.name, rect.left + paddingH, rect.top + paddingV - fontMetrics.ascent, textPaint)
    }
}
