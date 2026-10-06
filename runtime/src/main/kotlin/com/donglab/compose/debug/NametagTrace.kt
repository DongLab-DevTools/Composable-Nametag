package com.donglab.compose.debug

import android.os.Trace

/** Systrace / Perfetto section. No-op unless tracing is enabled for the app. */
internal inline fun <T> traceSection(name: String, block: () -> T): T {
    Trace.beginSection(name)
    try {
        return block()
    } finally {
        Trace.endSection()
    }
}
