package com.donglab.compose.debug

import android.os.Handler
import android.os.Looper
import android.util.Log

internal const val TAG = "ComposableNametag"

/**
 * Keeps the debug overlay from ever crashing or burdening the host app.
 *
 * - every library entry point (registration, frame callbacks, drawing) runs inside [guard]
 * - failures are logged and swallowed, never rethrown into the app
 * - after [MAX_FAILURES] failures in a row the overlay turns itself off for the rest of the process
 *
 * Main thread only, except [broken] which is read from composition.
 */
internal object NametagSafety {

    const val MAX_FAILURES = 3

    /** Set once repeated failures turned the overlay off. Markers stop doing anything. */
    @Volatile
    var broken = false
        private set

    private var failures = 0

    /** Hook for turning everything off when [broken] flips; the registry installs it. */
    var onBroken: (() -> Unit)? = null

    fun failed(where: String, error: Throwable) {
        failures++
        log { Log.w(TAG, "$where failed ($failures/$MAX_FAILURES)", error) }
        if (failures < MAX_FAILURES || broken) return

        broken = true
        log { Log.w(TAG, "Nametag overlay disabled after repeated failures") }
        try {
            onBroken?.invoke()
        } catch (_: Throwable) {
            // 끄는 도중의 실패도 앱으로 내보내지 않는다
        }
    }

    /** A full scan cycle went through; failures only count when they happen in a row. */
    fun succeeded() {
        failures = 0
    }

    inline fun guard(where: String, block: () -> Unit) {
        if (broken) return
        try {
            block()
        } catch (t: Throwable) {
            failed(where, t)
        }
    }

    // 로그 출력 자체가 실패해도(로컬 JVM 테스트 등) 앱으로 퍼지지 않게
    private inline fun log(write: () -> Unit) {
        try {
            write()
        } catch (_: Throwable) {
        }
    }

    internal fun resetForTest() {
        broken = false
        failures = 0
        onBroken = null
    }
}

private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

/** Runs [block] guarded on the main thread — directly when already there, posted otherwise. */
internal fun runGuardedOnMain(where: String, block: () -> Unit) {
    if (Looper.myLooper() === Looper.getMainLooper()) {
        NametagSafety.guard(where, block)
    } else {
        try {
            mainHandler.post { NametagSafety.guard(where, block) }
        } catch (t: Throwable) {
            NametagSafety.failed(where, t)
        }
    }
}
