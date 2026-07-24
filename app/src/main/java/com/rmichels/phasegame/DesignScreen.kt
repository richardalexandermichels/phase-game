package com.rmichels.phasegame

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class DesignEditor {
    BASE,
    PLAYER
}

private val basePitchLabels = listOf(
    "D2", "E2", "F♯2", "A2", "B2", "D3",
    "E3", "F♯3", "A3", "B3", "D4", "E4"
)

private val playerPitchLabels = listOf(
    "D3", "E3", "F♯3", "A3", "B3", "D4",
    "E4", "F♯4", "A4", "B4", "D5", "E5"
)

@Composable
internal fun DesignScreen(
    design: GameDesign,
    onDesignChange: (GameDesign) -> Unit,
    onPlay: () -> Unit,
    onReturnToTitle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editor by rememberSaveable { mutableStateOf(DesignEditor.BASE) }
    var phaseIndex by rememberSaveable { mutableIntStateOf(0) }
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(design.stepCount) {
        phaseIndex = phaseIndex.coerceIn(0, design.stepCount - 1)
        pageIndex = 0
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Pattern Designer",
            fontFamily = FontFamily.Cursive,
            fontWeight = FontWeight.Bold,
            fontSize = 34.sp,
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    onDesignChange(design.resized(design.stepCount - 1))
                },
                enabled = design.stepCount > MIN_PATTERN_STEPS
            ) {
                Text("−")
            }
            Text("${design.stepCount} steps", fontSize = 20.sp)
            OutlinedButton(
                onClick = {
                    onDesignChange(design.resized(design.stepCount + 1))
                },
                enabled = design.stepCount < MAX_PATTERN_STEPS
            ) {
                Text("+")
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (editor == DesignEditor.BASE) {
                Button(onClick = {}) { Text("Base") }
                OutlinedButton(
                    onClick = {
                        editor = DesignEditor.PLAYER
                        pageIndex = 0
                    }
                ) {
                    Text("Player")
                }
            } else {
                OutlinedButton(
                    onClick = {
                        editor = DesignEditor.BASE
                        pageIndex = 0
                    }
                ) {
                    Text("Base")
                }
                Button(onClick = {}) { Text("Player") }
            }
        }

        if (editor == DesignEditor.PLAYER) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        phaseIndex = Math.floorMod(
                            phaseIndex - 1,
                            design.stepCount
                        )
                        pageIndex = 0
                    }
                ) {
                    Text("Previous")
                }
                Text("Phase ${phaseIndex + 1} of ${design.stepCount}")
                OutlinedButton(
                    onClick = {
                        phaseIndex = (phaseIndex + 1) % design.stepCount
                        pageIndex = 0
                    }
                ) {
                    Text("Next")
                }
            }
        } else {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap a pitch to enable a step",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 6.dp)
        ) {
            val labelWidth = 42.dp
            val availableGridWidth = maxWidth - labelWidth
            val pages = matrixPageRanges(
                stepCount = design.stepCount,
                availableWidthDp = availableGridWidth.value
            )
            val visiblePageIndex = pageIndex.coerceIn(0, pages.lastIndex)
            val visibleColumns = pages[visiblePageIndex]
            val matrixHeight = maxHeight - 56.dp

            LaunchedEffect(pages.size) {
                pageIndex = pageIndex.coerceIn(0, pages.lastIndex)
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ToneMatrix(
                    design = design,
                    editor = editor,
                    phaseIndex = phaseIndex,
                    visibleColumns = visibleColumns,
                    availableWidth = availableGridWidth,
                    availableHeight = matrixHeight,
                    onDesignChange = onDesignChange
                )

                if (pages.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { pageIndex-- },
                            enabled = visiblePageIndex > 0
                        ) {
                            Text("Previous Page")
                        }
                        Text("${visiblePageIndex + 1} / ${pages.size}")
                        OutlinedButton(
                            onClick = { pageIndex++ },
                            enabled = visiblePageIndex < pages.lastIndex
                        ) {
                            Text("Next Page")
                        }
                    }
                }
            }
        }

        if (!design.hasPlayableBase) {
            Text(
                text = "Select at least one base note to play.",
                color = MaterialTheme.colorScheme.error
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = onReturnToTitle) {
                Text("Back")
            }
            Button(
                onClick = onPlay,
                enabled = design.hasPlayableBase
            ) {
                Text("Play")
            }
        }
    }
}

@Composable
private fun ToneMatrix(
    design: GameDesign,
    editor: DesignEditor,
    phaseIndex: Int,
    visibleColumns: IntRange,
    availableWidth: Dp,
    availableHeight: Dp,
    onDesignChange: (GameDesign) -> Unit
) {
    val columnCount = visibleColumns.count().coerceAtLeast(1)
    val cellSize = minOf(
        availableWidth / columnCount,
        availableHeight / (DESIGN_PITCH_COUNT + 1),
        42.dp
    )
    val enabledColumns = if (editor == DesignEditor.BASE) {
        List(design.stepCount) { true }
    } else {
        design.enabledPlayerColumns(phaseIndex)
    }
    val pitchLabels = if (editor == DesignEditor.BASE) {
        basePitchLabels
    } else {
        playerPitchLabels
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.width(42.dp))
            visibleColumns.forEach { step ->
                Box(
                    modifier = Modifier.size(cellSize),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (step + 1).toString(),
                        fontSize = 11.sp
                    )
                }
            }
        }

        for (pitch in (DESIGN_PITCH_COUNT - 1) downTo 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(cellSize),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = pitchLabels[pitch],
                        fontSize = 11.sp,
                        modifier = Modifier.padding(end = 5.dp)
                    )
                }

                visibleColumns.forEach { step ->
                    val enabled = enabledColumns[step]
                    val selectedPitch = when (editor) {
                        DesignEditor.BASE -> design.baseNotes[step]
                        DesignEditor.PLAYER ->
                            design.playerNotesByPhase[phaseIndex][step]
                    }
                    val isSelected = selectedPitch == pitch
                    val backgroundColor = when {
                        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(
                            alpha = 0.35f
                        )
                        isSelected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val borderColor = when {
                        !enabled -> Color.Transparent
                        isSelected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline
                    }

                    Box(
                        modifier = Modifier
                            .size(cellSize)
                            .padding(1.dp)
                            .background(backgroundColor)
                            .border(1.dp, borderColor)
                            .clickable(enabled = enabled) {
                                val nextPitch =
                                    if (isSelected) null else pitch
                                val updatedDesign = when (editor) {
                                    DesignEditor.BASE ->
                                        design.withBaseNote(step, nextPitch)
                                    DesignEditor.PLAYER ->
                                        design.withPlayerNote(
                                            phaseIndex,
                                            step,
                                            nextPitch
                                        )
                                }
                                onDesignChange(updatedDesign)
                            }
                    )
                }
            }
        }
    }
}
