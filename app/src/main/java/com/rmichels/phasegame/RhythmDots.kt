package com.rmichels.phasegame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private const val DEFAULT_RHYTHM_DOT_STRIDE_DP = 28f
private const val MAX_RHYTHM_WIDTH_DP = 336f

internal fun rhythmCursorOffsetDp(
    barProgress: Float,
    patternStepCount: Int
): Float {
    require(patternStepCount > 0)
    val dotStride = rhythmDotStrideDp(patternStepCount)
    val firstDotCenter =
        -((patternStepCount - 1) * dotStride) / 2f
    return firstDotCenter +
            patternStepCount * dotStride * barProgress
}

internal fun rhythmDotStrideDp(patternStepCount: Int): Float {
    require(patternStepCount > 0)
    return minOf(
        DEFAULT_RHYTHM_DOT_STRIDE_DP,
        MAX_RHYTHM_WIDTH_DP / patternStepCount
    )
}

@Composable
fun RhythmDots(
    rhythm: List<Boolean>,
    modifier: Modifier = Modifier,
    playedColor: Color = MaterialTheme.colorScheme.primary
) {
    val dotStride = rhythmDotStrideDp(rhythm.size)
    val playedDotSize = minOf(18f, dotStride - 4f).dp
    val silentDotSize = minOf(8f, dotStride - 4f).dp

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        rhythm.forEach { isHit ->
            Box(
                modifier = Modifier.size(dotStride.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(
                            if (isHit) playedDotSize else silentDotSize
                        )
                        .background(
                            color = if (isHit) {
                                playedColor
                            } else {
                                Color.Gray
                            },
                            shape = CircleShape
                        )
                )
            }
        }
    }
}