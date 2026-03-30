package com.donglab.compose.debug

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Global toggle for the Compose debug name overlay.
 *
 * ```kotlin
 * ComposeDebugConfig.enabled = true  // show labels
 * ComposeDebugConfig.enabled = false // hide labels (default, zero overhead)
 * ```
 */
object ComposeDebugConfig {
    var enabled by mutableStateOf(false)
}
