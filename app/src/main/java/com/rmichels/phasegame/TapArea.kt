package com.rmichels.phasegame

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

@Composable
internal fun TapArea(
    judgmentLabel: String?,
    onPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnPress by rememberUpdatedState(onPress)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.4f)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    currentOnPress()
                    waitForUpOrCancellation()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(judgmentLabel ?: "Tap")
    }
}