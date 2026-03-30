package com.donglab.compose.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.absoluteValue

private val overlayColors = listOf(
    Color(0xDDE91E63),
    Color(0xDD2196F3),
    Color(0xDD4CAF50),
    Color(0xDDFF9800),
    Color(0xDD9C27B0),
    Color(0xDD00BCD4),
    Color(0xDDFF5722),
    Color(0xDD607D8B),
)

private const val LABEL_HEIGHT_DP = 14
private const val STAGGER_SLOTS = 6

/**
 * Debug overlay that shows the composable function name as a small label.
 *
 * Injected automatically by the Compose Debug Compiler Plugin.
 * Do NOT call manually.
 */
@Composable
fun __debugComposableName(name: String) {
    if (!ComposeDebugConfig.enabled) return

    val color = remember(name) {
        overlayColors[name.hashCode().absoluteValue.mod(overlayColors.size)]
    }

    val density = LocalDensity.current
    val yOffsetPx = remember(name) {
        val slot = name.hashCode().absoluteValue.mod(STAGGER_SLOTS)
        with(density) { (slot * LABEL_HEIGHT_DP).dp.roundToPx() }
    }

    val shape = RoundedCornerShape(2.dp)

    Box(
        modifier = Modifier
            .zIndex(Float.MAX_VALUE)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(0, 0) {
                    placeable.placeWithLayer(0, yOffsetPx) { clip = false }
                }
            },
    ) {
        Text(
            text = name,
            modifier = Modifier
                .align(Alignment.TopStart)
                .background(color, shape)
                .border(0.5.dp, Color.White.copy(alpha = 0.5f), shape)
                .padding(horizontal = 4.dp, vertical = 1.dp),
            style = TextStyle(
                color = Color.White,
                fontSize = 7.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                lineHeight = 9.sp,
            ),
            maxLines = 1,
        )
    }
}
