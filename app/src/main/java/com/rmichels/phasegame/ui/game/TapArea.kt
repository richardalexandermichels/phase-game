package com.rmichels.phasegame.ui.game

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

@Composable
internal fun TapArea(
    judgmentLabel: String?,
    onPress: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnPress by rememberUpdatedState(onPress)
    Box(
        modifier = modifier
//            .fillMaxWidth()
//            .fillMaxHeight(0.4f)
//            .background(MaterialTheme.colorScheme.secondaryContainer)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val downEvent =
                        awaitFirstDown(requireUnconsumed = false)
                    currentOnPress(downEvent.position)
                    waitForUpOrCancellation()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(judgmentLabel ?: "")
    }
}
