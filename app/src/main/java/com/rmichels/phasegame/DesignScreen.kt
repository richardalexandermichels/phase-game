package com.rmichels.phasegame

import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    val context = LocalContext.current
    val previewScope = rememberCoroutineScope()
    var editor by rememberSaveable { mutableStateOf(DesignEditor.BASE) }
    var phaseIndex by rememberSaveable { mutableIntStateOf(0) }
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    var previewJob by remember { mutableStateOf<Job?>(null) }
    val previewSoundPool = remember {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attributes)
            .build()
    }
    val previewBaseSoundIds = remember {
        IntArray(DESIGN_PITCH_COUNT)
    }
    val previewPlayerSoundIds = remember {
        IntArray(DESIGN_PITCH_COUNT)
    }
    var previewFallbackSoundId by remember { mutableIntStateOf(0) }
    var loadedPreviewSoundCount by remember { mutableIntStateOf(0) }
    val expectedPreviewSoundCount =
        DESIGN_BASE_SOUND_RESOURCES.size +
            DESIGN_PLAYER_SOUND_RESOURCES.size + 1
    val isPreviewAudioReady =
        loadedPreviewSoundCount == expectedPreviewSoundCount
    val previewFallbackPitchGenerator = remember {
        PopMotifPitchGenerator()
    }

    DisposableEffect(previewSoundPool, context) {
        previewSoundPool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) {
                loadedPreviewSoundCount++
            }
        }
        DESIGN_BASE_SOUND_RESOURCES.forEachIndexed { index, resourceId ->
            previewBaseSoundIds[index] =
                previewSoundPool.load(context, resourceId, 1)
        }
        DESIGN_PLAYER_SOUND_RESOURCES.forEachIndexed { index, resourceId ->
            previewPlayerSoundIds[index] =
                previewSoundPool.load(context, resourceId, 1)
        }
        previewFallbackSoundId =
            previewSoundPool.load(context, R.raw.player_perfect, 1)

        onDispose {
            previewJob?.cancel()
            previewSoundPool.setOnLoadCompleteListener(null)
            previewSoundPool.release()
        }
    }

    fun auditionPitch(pitch: Int) {
        if (!isPreviewAudioReady) return
        val soundId = if (editor == DesignEditor.BASE) {
            previewBaseSoundIds[pitch]
        } else {
            previewPlayerSoundIds[pitch]
        }
        previewSoundPool.play(soundId, 1f, 1f, 1, 0, 1f)
    }

    fun previewCurrentBar() {
        if (!isPreviewAudioReady) return
        previewJob?.cancel()
        previewJob = previewScope.launch {
            for (step in 0 until design.stepCount) {
                when (editor) {
                    DesignEditor.BASE -> {
                        design.baseNotes[step]?.let { pitch ->
                            previewSoundPool.play(
                                previewBaseSoundIds[pitch],
                                1f,
                                1f,
                                1,
                                0,
                                1f
                            )
                        }
                    }
                    DesignEditor.PLAYER -> {
                        if (design.enabledPlayerColumns(phaseIndex)[step]) {
                            val pitch =
                                design.playerNotesByPhase[phaseIndex][step]
                            if (pitch == null) {
                                previewSoundPool.play(
                                    previewFallbackSoundId,
                                    1f,
                                    1f,
                                    1,
                                    0,
                                    previewFallbackPitchGenerator
                                        .nextPlaybackRate()
                                )
                            } else {
                                previewSoundPool.play(
                                    previewPlayerSoundIds[pitch],
                                    1f,
                                    1f,
                                    1,
                                    0,
                                    1f
                                )
                            }
                        }
                    }
                }
                delay(STEP_DURATION_MS)
            }
        }
    }

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

        Button(
            onClick = ::previewCurrentBar,
            enabled = isPreviewAudioReady
        ) {
            Text("Preview Bar")
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
                    onDesignChange = onDesignChange,
                    onAuditionPitch = ::auditionPitch
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
    onDesignChange: (GameDesign) -> Unit,
    onAuditionPitch: (Int) -> Unit
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
                    val cellShape = RoundedCornerShape(4.dp)

                    fun applyPaintedNotes(notesByStep: Map<Int, Int>) {
                        var updatedDesign = design
                        notesByStep.toSortedMap().forEach {
                                (selectedStep, selectedPitch) ->
                            if (!enabledColumns[selectedStep]) {
                                return@forEach
                            }
                            updatedDesign = when (editor) {
                                DesignEditor.BASE ->
                                    updatedDesign.withBaseNote(
                                        selectedStep,
                                        selectedPitch
                                    )
                                DesignEditor.PLAYER ->
                                    updatedDesign.withPlayerNote(
                                        phaseIndex,
                                        selectedStep,
                                        selectedPitch
                                    )
                            }
                        }
                        onDesignChange(updatedDesign)
                    }

                    Box(
                        modifier = Modifier
                            .size(cellSize)
                            .padding(1.5.dp)
                            .background(backgroundColor, cellShape)
                            .pointerInput(
                                enabled,
                                step,
                                pitch,
                                visibleColumns,
                                cellSize
                            ) {
                                if (!enabled) {
                                    return@pointerInput
                                }
                                var accumulatedDragX = 0f
                                var accumulatedDragY = 0f
                                var previousStep = step
                                var previousPitch = pitch
                                val paintedNotes = mutableMapOf<Int, Int>()
                                detectDragGestures(
                                    onDragStart = {
                                        paintedNotes.clear()
                                        paintedNotes[step] = pitch
                                        applyPaintedNotes(paintedNotes)
                                        onAuditionPitch(pitch)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        accumulatedDragX += dragAmount.x
                                        accumulatedDragY += dragAmount.y
                                        val stepOffset = (
                                            accumulatedDragX /
                                                cellSize.toPx()
                                            ).roundToInt()
                                        val targetStep =
                                            (step + stepOffset).coerceIn(
                                                visibleColumns.first,
                                                visibleColumns.last
                                            )
                                        // Pitch rows run high-to-low, so an
                                        // upward drag raises the painted note.
                                        val pitchOffset = -(
                                            accumulatedDragY /
                                                cellSize.toPx()
                                            ).roundToInt()
                                        val targetPitch =
                                            (pitch + pitchOffset).coerceIn(
                                                0,
                                                DESIGN_PITCH_COUNT - 1
                                            )

                                        if (
                                            targetStep == previousStep &&
                                            targetPitch == previousPitch
                                        ) {
                                            return@detectDragGestures
                                        }

                                        val segmentSteps = if (
                                            targetStep >= previousStep
                                        ) {
                                            previousStep..targetStep
                                        } else {
                                            targetStep..previousStep
                                        }
                                        val stepDistance =
                                            targetStep - previousStep
                                        val changedPitches = mutableListOf<Int>()

                                        segmentSteps.forEach { paintedStep ->
                                            if (!enabledColumns[paintedStep]) {
                                                return@forEach
                                            }
                                            val progress = if (
                                                stepDistance == 0
                                            ) {
                                                1f
                                            } else {
                                                (paintedStep - previousStep)
                                                    .toFloat() / stepDistance
                                            }
                                            val interpolatedPitch = (
                                                previousPitch +
                                                    (targetPitch -
                                                        previousPitch) *
                                                    progress
                                                ).roundToInt().coerceIn(
                                                0,
                                                DESIGN_PITCH_COUNT - 1
                                            )
                                            if (
                                                paintedNotes[paintedStep] !=
                                                interpolatedPitch
                                            ) {
                                                paintedNotes[paintedStep] =
                                                    interpolatedPitch
                                                changedPitches.add(
                                                    interpolatedPitch
                                                )
                                            }
                                        }
                                        if (changedPitches.isNotEmpty()) {
                                            applyPaintedNotes(paintedNotes)
                                            changedPitches.forEach(
                                                onAuditionPitch
                                            )
                                        }
                                        previousStep = targetStep
                                        previousPitch = targetPitch
                                    },
                                    onDragEnd = {
                                        accumulatedDragX = 0f
                                        accumulatedDragY = 0f
                                    },
                                    onDragCancel = {
                                        accumulatedDragX = 0f
                                        accumulatedDragY = 0f
                                    }
                                )
                            }
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
                                if (nextPitch != null) {
                                    onAuditionPitch(nextPitch)
                                }
                            }
                    )
                }
            }
        }
    }
}
