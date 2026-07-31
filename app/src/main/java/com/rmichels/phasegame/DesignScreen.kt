package com.rmichels.phasegame

import android.os.SystemClock
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rmichels.phasegame.audio.AudioBus
import com.rmichels.phasegame.audio.AudioEngine
import com.rmichels.phasegame.audio.AudioEvent
import com.rmichels.phasegame.audio.AudioSessionId
import com.rmichels.phasegame.audio.SoundCatalog
import kotlin.math.roundToInt

private enum class DesignEditor {
    BASE,
    PLAYER
}

@Composable
internal fun DesignScreen(
    audioEngine: AudioEngine,
    design: GameDesign,
    baseInstrument: GameInstrument,
    playerInstrument: GameInstrument,
    onDesignChange: (GameDesign) -> Unit,
    onPlay: () -> Unit,
    onReturnToTitle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editor by rememberSaveable { mutableStateOf(DesignEditor.BASE) }
    var phaseIndex by rememberSaveable { mutableIntStateOf(0) }
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    var scaleRoot by rememberSaveable { mutableIntStateOf(0) }
    var scaleIndex by rememberSaveable {
        mutableIntStateOf(PianoScalePreset.CHROMATIC.ordinal)
    }
    val scale = PianoScalePreset.values()[
        scaleIndex.coerceIn(0, PianoScalePreset.values().lastIndex)
    ]
    val lowMidi = if (editor == DesignEditor.BASE) {
        BASE_PIANO_LOW_MIDI
    } else {
        PLAYER_PIANO_LOW_MIDI
    }
    val visiblePitches = pitchIndicesForScale(
        lowMidi = lowMidi,
        rootPitchClass = scaleRoot,
        scale = scale
    )
    val auditionSession = remember(audioEngine) {
        audioEngine.createSession()
    }
    var previewSession by remember {
        mutableStateOf<AudioSessionId?>(null)
    }
    val isPreviewAudioReady = audioEngine.isReady
    val baseSamples = SoundCatalog.baseFor(baseInstrument)
    val playerSamples = SoundCatalog.playerFor(playerInstrument)
    val previewPhaseMelody = remember(design.stepCount) {
        PopRockPhaseMelody(design.stepCount)
    }

    DisposableEffect(audioEngine, auditionSession) {
        onDispose {
            previewSession?.let(audioEngine::cancelSession)
            audioEngine.cancelSession(auditionSession)
        }
    }

    fun auditionPitch(pitch: Int) {
        if (!isPreviewAudioReady) return
        val sampleId = if (editor == DesignEditor.BASE) {
            baseSamples[pitch]
        } else {
            playerSamples[pitch]
        }
        audioEngine.playImmediate(
            sampleId = sampleId,
            bus = if (editor == DesignEditor.BASE) {
                AudioBus.BASE
            } else {
                AudioBus.PLAYER
            },
            sessionId = auditionSession,
            priority = 2
        )
    }

    fun previewCurrentBar() {
        if (!isPreviewAudioReady) return
        previewSession?.let(audioEngine::cancelSession)
        val session = audioEngine.createSession()
        previewSession = session
        val startNanos = SystemClock.elapsedRealtimeNanos() + 30_000_000L
        previewPhaseMelody.selectPhase(phaseIndex)
        for (step in 0 until design.stepCount) {
            val targetNanos =
                startNanos + step * STEP_DURATION_MS * 1_000_000L
            when (editor) {
                DesignEditor.BASE -> {
                    design.baseNotes[step].forEach { pitch ->
                        audioEngine.schedule(
                            AudioEvent(
                                sampleId = baseSamples[pitch],
                                targetElapsedRealtimeNanos = targetNanos,
                                bus = AudioBus.BASE,
                                sessionId = session,
                                priority = 1
                            )
                        )
                    }
                }
                DesignEditor.PLAYER -> {
                    if (design.enabledPlayerColumns(phaseIndex)[step]) {
                        val pitches =
                            design.playerNotesByPhase[phaseIndex][step]
                        if (pitches.isEmpty()) {
                            audioEngine.schedule(
                                AudioEvent(
                                    sampleId = playerSamples[
                                        previewPhaseMelody
                                            .playerPitchIndexForStep(step)
                                    ],
                                    targetElapsedRealtimeNanos = targetNanos,
                                    bus = AudioBus.PLAYER,
                                    sessionId = session,
                                    priority = 2
                                )
                            )
                        } else {
                            pitches.forEach { pitch ->
                                audioEngine.schedule(
                                    AudioEvent(
                                        sampleId =
                                            playerSamples[pitch],
                                        targetElapsedRealtimeNanos =
                                            targetNanos,
                                        bus = AudioBus.PLAYER,
                                        sessionId = session,
                                        priority = 2
                                    )
                                )
                            }
                        }
                    }
                }
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PitchSelector(
                label = "Key",
                selected = PIANO_PITCH_CLASS_LABELS[scaleRoot],
                options = PIANO_PITCH_CLASS_LABELS,
                enabled = scale != PianoScalePreset.CHROMATIC,
                modifier = Modifier.weight(1f),
                onSelected = { scaleRoot = it }
            )
            PitchSelector(
                label = "Scale",
                selected = scale.displayName,
                options = PianoScalePreset.values().map { it.displayName },
                modifier = Modifier.weight(1f),
                onSelected = { scaleIndex = it }
            )
        }
        Text(
            text = "Scale filters rows; drag note labels to scroll. Existing notes are preserved.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

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
                text = "Tap up to $DESIGN_MAX_CHORD_SIZE pitches per step",
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
                    visiblePitches = visiblePitches,
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
    visiblePitches: List<Int>,
    availableWidth: Dp,
    availableHeight: Dp,
    onDesignChange: (GameDesign) -> Unit,
    onAuditionPitch: (Int) -> Unit
) {
    val columnCount = visibleColumns.count().coerceAtLeast(1)
    val cellSize = minOf(
        availableWidth / columnCount,
        36.dp
    )
    val enabledColumns = if (editor == DesignEditor.BASE) {
        List(design.stepCount) { true }
    } else {
        design.enabledPlayerColumns(phaseIndex)
    }
    val lowMidi = if (editor == DesignEditor.BASE) {
        BASE_PIANO_LOW_MIDI
    } else {
        PLAYER_PIANO_LOW_MIDI
    }

    Column(
        modifier = Modifier
            .height(availableHeight)
            .verticalScroll(rememberScrollState()),
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

        for (pitch in visiblePitches.asReversed()) {
            val startingPitchPosition = visiblePitches.indexOf(pitch)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(cellSize),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = pianoNoteLabel(lowMidi + pitch),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(end = 5.dp)
                    )
                }

                visibleColumns.forEach { step ->
                    val enabled = enabledColumns[step]
                    val selectedPitches = when (editor) {
                        DesignEditor.BASE -> design.baseNotes[step]
                        DesignEditor.PLAYER ->
                            design.playerNotesByPhase[phaseIndex][step]
                    }
                    val isSelected = pitch in selectedPitches
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
                                visiblePitches,
                                visibleColumns,
                                cellSize
                            ) {
                                if (!enabled) {
                                    return@pointerInput
                                }
                                var accumulatedDragX = 0f
                                var accumulatedDragY = 0f
                                var previousStep = step
                                var previousPitchPosition =
                                    startingPitchPosition
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
                                        val pitchPositionOffset = -(
                                            accumulatedDragY /
                                                cellSize.toPx()
                                            ).roundToInt()
                                        val targetPitchPosition =
                                            (startingPitchPosition +
                                                pitchPositionOffset).coerceIn(
                                                0,
                                                visiblePitches.lastIndex
                                            )
                                        val targetPitch =
                                            visiblePitches[targetPitchPosition]

                                        if (
                                            targetStep == previousStep &&
                                            targetPitchPosition ==
                                            previousPitchPosition
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
                                            val interpolatedPitchPosition = (
                                                previousPitchPosition +
                                                    (targetPitchPosition -
                                                        previousPitchPosition) *
                                                    progress
                                                ).roundToInt().coerceIn(
                                                0,
                                                visiblePitches.lastIndex
                                            )
                                            val interpolatedPitch =
                                                visiblePitches[
                                                    interpolatedPitchPosition
                                                ]
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
                                        previousPitchPosition =
                                            targetPitchPosition
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
                                val updatedDesign = when (editor) {
                                    DesignEditor.BASE ->
                                        design.toggledBasePitch(step, pitch)
                                    DesignEditor.PLAYER ->
                                        design.toggledPlayerPitch(
                                            phaseIndex,
                                            step,
                                            pitch
                                        )
                                }
                                onDesignChange(updatedDesign)
                                if (
                                    !isSelected &&
                                    selectedPitches.size <
                                        DESIGN_MAX_CHORD_SIZE
                                ) {
                                    onAuditionPitch(pitch)
                                }
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun PitchSelector(
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (Int) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("$label: $selected")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(index)
                        expanded = false
                    }
                )
            }
        }
    }
}
