package com.rmichels.phasegame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

@Composable
internal fun PatternQueueDisplay(
    patternQueue: List<QueuedPatternBar>,
    currentSlot: Float,
    queueSlotSpacing: Dp,
    playAreaMidpoint: Dp,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        val newestQueueIndex = patternQueue.lastIndex
        val sheetOffsetSlots = currentSlot - newestQueueIndex
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = playAreaMidpoint)
                .zIndex(1f)
                .fillMaxWidth()
                .height(4.dp)
                .background(MaterialTheme.colorScheme.error)
        )
        patternQueue.forEachIndexed { index, queuedBar ->
            val slotFromTop =
                newestQueueIndex - index + sheetOffsetSlots
            val distanceFraction =
                index.toFloat() / (patternQueue.size - 1).coerceAtLeast(1)
            val rowOpacity =
                1f - (kotlin.math.sqrt(distanceFraction) * 0.8f)

            RhythmDots(
                rhythm = queuedBar.rhythm,
                playedColor = if (index == 0) {
                    Color(0xFF00BCD4)
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset {
                        IntOffset(
                            x = 0,
                            y = (queueSlotSpacing.toPx() * slotFromTop)
                                .roundToInt()
                        )
                    }
                    .alpha(rowOpacity)
            )
        }
    }
}