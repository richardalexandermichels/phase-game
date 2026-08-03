package com.rmichels.phasegame.ui.game

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rmichels.phasegame.audio.AudioBus
import com.rmichels.phasegame.audio.AudioEngine
import com.rmichels.phasegame.audio.GameAudioConductor
import com.rmichels.phasegame.audio.SoundCatalog
import com.rmichels.phasegame.audio.activeBackingTrackMaxTier
import com.rmichels.phasegame.audio.activeBackingTrackStepDurationMs
import com.rmichels.phasegame.design.GameDesign
import com.rmichels.phasegame.gameplay.GameplaySession
import com.rmichels.phasegame.gameplay.GameplaySnapshot
import com.rmichels.phasegame.gameplay.SessionAdvance
import com.rmichels.phasegame.gameplay.TapJudgment
import com.rmichels.phasegame.gameplay.validateBaseRhythm
import com.rmichels.phasegame.music.GameInstrument
import com.rmichels.phasegame.music.PLAYER_CLOSE_GAIN
import com.rmichels.phasegame.music.PLAYER_GOOD_GAIN
import com.rmichels.phasegame.music.PopRockPhaseMelody
import com.rmichels.phasegame.ui.theme.PhaseGameTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val MISS_QUEUE_RAISE_SLOTS = 0.25f
private const val QUEUE_FALL_SLOTS_PER_BAR = 1.2f
private const val START_CUE_DURATION_MS = 650L

internal fun startupQueueSlotsPerStep(
    patternStepCount: Int,
    queueItemCount: Int
): Float {
    require(patternStepCount > 0)
    require(queueItemCount > 1)
    return (queueItemCount - 1).toFloat() / patternStepCount
}

internal fun regularQueueSlotsPerStep(patternStepCount: Int): Float {
    require(patternStepCount > 0)
    return QUEUE_FALL_SLOTS_PER_BAR / patternStepCount
}

/**
 * Compose coordinator for one game run. Rules are delegated to
 * [GameplaySession], sample scheduling to [GameAudioConductor], and drawing to
 * the stateless components in this package.
 */
@Composable
internal fun PhaseGameScreen(
    audioEngine: AudioEngine?,
    onReturnToTitle: () -> Unit,
    perfectModeEnabled: Boolean,
    baseInstrument: GameInstrument,
    playerInstrument: GameInstrument,
    gameDesign: GameDesign?,
    modifier: Modifier = Modifier
) {
    val baseRhythm = remember(gameDesign) {
        gameDesign?.baseRhythm ?: validateBaseRhythm(
            listOf(
                true, false, true, false,
                true, true, false, true,
                false, true, false, true,
                true, false, true, false
            )
        )
    }
    val session = remember(baseRhythm) {
        GameplaySession(
            baseRhythm = baseRhythm,
            stepDurationMs = activeBackingTrackStepDurationMs,
            maximumLayerCount = activeBackingTrackMaxTier
        )
    }
    var gameplaySnapshot by remember(session) {
        mutableStateOf(session.snapshot())
    }
    val rhythmClock = session.clock
    val lifecycleOwner = LocalLifecycleOwner.current
    var isLifecycleStarted by remember(lifecycleOwner) {
        mutableStateOf(
            lifecycleOwner.lifecycle.currentState.isAtLeast(
                Lifecycle.State.STARTED
            )
        )
    }
    val phaseMelody = remember(baseRhythm.size) {
        PopRockPhaseMelody(baseRhythm.size)
    }
    val playerSamples = SoundCatalog.playerFor(playerInstrument)
    val phaseVisualThemes = remember(baseRhythm.size) {
        resolvePhaseVisualThemes(baseRhythm.size)
    }
    val baseAudioSession = remember(audioEngine) { audioEngine?.createSession() }
    val percussionAudioSession = remember(audioEngine) {
        audioEngine?.createSession()
    }
    val playerAudioSession = remember(audioEngine) { audioEngine?.createSession() }
    val audioConductor = remember(
        audioEngine,
        baseAudioSession,
        percussionAudioSession,
        baseRhythm,
        gameDesign,
        phaseMelody,
        baseInstrument
    ) {
        if (
            audioEngine != null &&
            baseAudioSession != null &&
            percussionAudioSession != null
        ) {
            GameAudioConductor(
                audioEngine = audioEngine,
                baseSession = baseAudioSession,
                percussionSession = percussionAudioSession,
                baseRhythm = baseRhythm,
                gameDesign = gameDesign,
                phaseMelody = phaseMelody,
                baseInstrument = baseInstrument,
                stepDurationMs = rhythmClock.stepDurationMs
            )
        } else {
            null
        }
    }

    var playerProgress by remember { mutableFloatStateOf(0f) }
    var introIndicatorStepPosition by remember { mutableStateOf<Float?>(null) }
    var introCurrentSlot by remember { mutableFloatStateOf(0f) }
    var isGameplayActive by remember { mutableStateOf(false) }
    var isQueueDropping by remember { mutableStateOf(true) }
    var phaseTransitionMissCount by remember { mutableIntStateOf(0) }
    val phaseTransitionRecoil = remember { Animatable(0f) }
    var startCueText by remember { mutableStateOf<String?>(null) }
    var tapJudgment by remember { mutableStateOf<TapJudgment?>(null) }
    var gameplayMenuExpanded by remember { mutableStateOf(false) }
    val isAudioLoaded = audioEngine?.isReady == true

    fun applyAdvance(advance: SessionAdvance) {
        if (advance.completedBarCount == 0) return
        if (advance.successfulBarCount > 0) {
            introCurrentSlot = (
                introCurrentSlot - advance.successfulBarCount
            ).coerceAtLeast(0f)
            isQueueDropping = true
        }
        gameplaySnapshot = session.snapshot()
    }

    LaunchedEffect(phaseTransitionMissCount) {
        if (phaseTransitionMissCount > 0) {
            phaseTransitionRecoil.snapTo(0.45f)
            phaseTransitionRecoil.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    DisposableEffect(lifecycleOwner, rhythmClock) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    rhythmClock.resume()
                    isLifecycleStarted = true
                }
                Lifecycle.Event.ON_STOP -> {
                    rhythmClock.pause()
                    isLifecycleStarted = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(
        audioEngine,
        baseAudioSession,
        percussionAudioSession,
        playerAudioSession
    ) {
        onDispose {
            audioConductor?.release()
            baseAudioSession?.let { audioEngine?.cancelSession(it) }
            percussionAudioSession?.let { audioEngine?.cancelSession(it) }
            playerAudioSession?.let { audioEngine?.cancelSession(it) }
        }
    }

    LaunchedEffect(gameplaySnapshot.activeLayerCount, audioConductor) {
        audioConductor?.setActiveLayerCount(gameplaySnapshot.activeLayerCount)
    }
    LaunchedEffect(gameplaySnapshot.currentPhaseIndex, phaseMelody) {
        phaseMelody.selectPhase(gameplaySnapshot.currentPhaseIndex)
    }
    LaunchedEffect(
        isGameplayActive,
        isAudioLoaded,
        isLifecycleStarted,
        audioConductor
    ) {
        if (isGameplayActive && isAudioLoaded && isLifecycleStarted) {
            audioConductor?.start(rhythmClock.startTimeMs)
        } else {
            audioConductor?.stop()
        }
    }

    LaunchedEffect(isGameplayActive, isAudioLoaded, isLifecycleStarted) {
        if (!isGameplayActive || !isAudioLoaded || !isLifecycleStarted) {
            playerProgress = 0f
            return@LaunchedEffect
        }

        var previousStepProgress =
            rhythmClock.elapsedMs().toFloat() / rhythmClock.stepDurationMs
        while (isGameplayActive && isAudioLoaded && isLifecycleStarted) {
            withFrameNanos {
                val nowMs = SystemClock.elapsedRealtime()
                val newStepProgress =
                    rhythmClock.elapsedMs(nowMs).toFloat() /
                        rhythmClock.stepDurationMs
                if (isQueueDropping) {
                    val elapsedSteps =
                        (newStepProgress - previousStepProgress).coerceAtLeast(0f)
                    introCurrentSlot = minOf(
                        (gameplaySnapshot.patternQueue.size - 1).toFloat(),
                        introCurrentSlot + elapsedSteps *
                            regularQueueSlotsPerStep(baseRhythm.size)
                    )
                }
                previousStepProgress = newStepProgress
                applyAdvance(session.advanceTo(nowMs))
                playerProgress = rhythmClock.progressThroughBar(nowMs)
            }
        }
    }

    LaunchedEffect(isGameplayActive) {
        if (isGameplayActive) {
            startCueText = "Go!"
            delay(START_CUE_DURATION_MS)
            startCueText = null
        }
    }

    fun playPlayerSample(sampleId: com.rmichels.phasegame.audio.AudioSampleId, gain: Float = 1f) {
        if (playerAudioSession != null) {
            audioEngine?.playImmediate(
                sampleId = sampleId,
                bus = AudioBus.PLAYER,
                sessionId = playerAudioSession,
                gain = gain,
                priority = 3
            )
        }
    }

    val handlePlayerPress = press@{
        if (!isAudioLoaded) return@press
        if (!isGameplayActive || !rhythmClock.isStarted) {
            playPlayerSample(
                playerSamples[phaseMelody.playerPitchIndexForStep(0)]
            )
            return@press
        }

        val result = session.judgeTap(
            tapTimeMs = SystemClock.elapsedRealtime(),
            perfectModeEnabled = perfectModeEnabled
        )
        applyAdvance(result.advance)
        tapJudgment = result.judgment
        if (result.judgment == TapJudgment.MISS) {
            phaseTransitionMissCount++
            audioConductor?.cancelFuturePercussion()
            introCurrentSlot =
                (introCurrentSlot - MISS_QUEUE_RAISE_SLOTS).coerceAtLeast(0f)
            isQueueDropping = false
        }
        gameplaySnapshot = session.snapshot()

        val judgmentGain = when (result.judgment) {
            TapJudgment.GOOD -> PLAYER_GOOD_GAIN
            TapJudgment.CLOSE -> PLAYER_CLOSE_GAIN
            else -> 1f
        }
        val designedPitches = if (result.judgment != TapJudgment.MISS) {
            gameDesign?.playerNotes(result.phaseIndex, result.stepIndex)
        } else {
            null
        }
        phaseMelody.selectPhase(result.phaseIndex)
        if (designedPitches.isNullOrEmpty()) {
            val sampleId = if (result.judgment == TapJudgment.MISS) {
                SoundCatalog.PLAYER_MISS
            } else {
                playerSamples[phaseMelody.playerPitchIndexForStep(result.stepIndex)]
            }
            playPlayerSample(sampleId, judgmentGain)
        } else {
            designedPitches.forEach { pitch ->
                playPlayerSample(playerSamples[pitch], judgmentGain)
            }
        }
    }

    GameScene(
        snapshot = gameplaySnapshot,
        baseRhythm = baseRhythm,
        phaseVisualThemes = phaseVisualThemes,
        playerProgress = playerProgress,
        isGameplayActive = isGameplayActive,
        introIndicatorStepPosition = introIndicatorStepPosition,
        phaseTransitionRecoil = phaseTransitionRecoil.value,
        startCueText = startCueText,
        tapJudgment = tapJudgment,
        gameplayMenuExpanded = gameplayMenuExpanded,
        onGameplayMenuExpandedChange = { gameplayMenuExpanded = it },
        onReturnToTitle = onReturnToTitle,
        onPress = handlePlayerPress,
        modifier = modifier,
        introEnabled = isAudioLoaded && isLifecycleStarted && !isGameplayActive,
        introEffect = { midpointSlot, lineClearanceSlots ->
            if (!isAudioLoaded || !isLifecycleStarted || isGameplayActive) {
                return@GameScene
            }
            introIndicatorStepPosition = null
            val fastSlotsPerStep = startupQueueSlotsPerStep(
                patternStepCount = baseRhythm.size,
                queueItemCount = gameplaySnapshot.patternQueue.size
            )
            val slowSlotsPerStep = regularQueueSlotsPerStep(baseRhythm.size)
            val targetSlot = midpointSlot + 1f + lineClearanceSlots
            val remainingIntroSteps = if (introCurrentSlot < midpointSlot) {
                (midpointSlot - introCurrentSlot) / fastSlotsPerStep +
                    (targetSlot - midpointSlot) / slowSlotsPerStep
            } else {
                (targetSlot - introCurrentSlot).coerceAtLeast(0f) /
                    slowSlotsPerStep
            }
            val introDurationMs =
                (remainingIntroSteps * rhythmClock.stepDurationMs)
                    .roundToInt()
                    .toLong()
            rhythmClock.scheduleStart(SystemClock.elapsedRealtime() + introDurationMs)
            audioConductor?.start(rhythmClock.startTimeMs)
            var previousUpdateMs = SystemClock.elapsedRealtime()

            while (!isGameplayActive && introCurrentSlot < targetSlot) {
                withFrameNanos {
                    val nowMs = SystemClock.elapsedRealtime()
                    val elapsedSteps =
                        (nowMs - previousUpdateMs).coerceAtLeast(0L).toFloat() /
                            rhythmClock.stepDurationMs
                    previousUpdateMs = nowMs
                    introCurrentSlot = if (introCurrentSlot < midpointSlot) {
                        minOf(
                            midpointSlot,
                            introCurrentSlot + elapsedSteps * fastSlotsPerStep
                        )
                    } else {
                        introCurrentSlot + elapsedSteps * slowSlotsPerStep
                    }.coerceAtMost(targetSlot)

                    val remainingStartMs =
                        (rhythmClock.startTimeMs - nowMs).coerceAtLeast(0L)
                    introIndicatorStepPosition =
                        -remainingStartMs.toFloat() / rhythmClock.stepDurationMs
                    if (introCurrentSlot >= midpointSlot) {
                        val progress = (
                            (introCurrentSlot - midpointSlot) /
                                (targetSlot - midpointSlot)
                        ).coerceIn(0f, 1f)
                        startCueText = when {
                            progress < 0.25f -> "Ready"
                            progress < 0.50f -> "2"
                            progress < 0.75f -> "3"
                            else -> "4"
                        }
                    }
                }
            }
            introCurrentSlot = targetSlot
            val remainingMs = rhythmClock.startTimeMs - SystemClock.elapsedRealtime()
            if (remainingMs > 0L) delay(remainingMs)
            introIndicatorStepPosition = null
            isGameplayActive = true
        }
    )
}

@Composable
private fun GameScene(
    snapshot: GameplaySnapshot,
    baseRhythm: List<Boolean>,
    phaseVisualThemes: List<PhaseVisualTheme>,
    playerProgress: Float,
    isGameplayActive: Boolean,
    introIndicatorStepPosition: Float?,
    phaseTransitionRecoil: Float,
    startCueText: String?,
    tapJudgment: TapJudgment?,
    gameplayMenuExpanded: Boolean,
    onGameplayMenuExpandedChange: (Boolean) -> Unit,
    onReturnToTitle: () -> Unit,
    onPress: () -> Unit,
    modifier: Modifier,
    introEnabled: Boolean,
    introEffect: suspend (midpointSlot: Float, lineClearanceSlots: Float) -> Unit
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().clipToBounds()
    ) {
        val playerTravelDistance = maxHeight * 0.6f - 28.dp
        val playAreaMidpoint = maxHeight * 0.3f
        val queueSlotSpacing =
            playerTravelDistance / (snapshot.patternQueue.size - 1)
        val midpointSlot = playAreaMidpoint / queueSlotSpacing
        val lineClearanceSlots = 14.dp / queueSlotSpacing

        val currentIntroEffect by rememberUpdatedState(introEffect)
        LaunchedEffect(introEnabled, midpointSlot, lineClearanceSlots) {
            if (introEnabled) {
                currentIntroEffect(midpointSlot, lineClearanceSlots)
            }
        }

        RhythmPolygon(
            rhythm = snapshot.patternQueue.firstOrNull()?.rhythm ?: baseRhythm,
            queuedPatterns = snapshot.patternQueue,
            phaseVisualThemes = phaseVisualThemes,
            barProgress = playerProgress,
            isGameplayActive = isGameplayActive,
            introIndicatorStepPosition = introIndicatorStepPosition,
            phaseTransitionRecoil = phaseTransitionRecoil,
            repeatCurrentPatternNextBar = snapshot.repeatCurrentPatternNextBar,
            modifier = Modifier.fillMaxSize().zIndex(3f)
        )
        TapArea(
            judgmentLabel = tapJudgment?.label,
            onPress = { onPress() },
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(4f)
                .fillMaxWidth()
                .fillMaxHeight(0.6f),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = startCueText != null,
                enter = fadeIn() + scaleIn(initialScale = 0.65f),
                exit = fadeOut()
            ) {
                Text(
                    text = startCueText.orEmpty(),
                    fontFamily = FontFamily.Cursive,
                    fontWeight = FontWeight.Bold,
                    fontSize = 64.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Box(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).zIndex(10f)
        ) {
            TextButton(onClick = { onGameplayMenuExpandedChange(true) }) {
                Text("Menu", style = MaterialTheme.typography.labelSmall)
            }
            DropdownMenu(
                expanded = gameplayMenuExpanded,
                onDismissRequest = { onGameplayMenuExpandedChange(false) }
            ) {
                DropdownMenuItem(
                    text = { Text("Back to start screen") },
                    onClick = {
                        onGameplayMenuExpandedChange(false)
                        onReturnToTitle()
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PhaseGameScreenPreview() {
    PhaseGameTheme {
        PhaseGameScreen(
            audioEngine = null,
            onReturnToTitle = {},
            perfectModeEnabled = false,
            baseInstrument = GameInstrument.PIANO,
            playerInstrument = GameInstrument.PIANO,
            gameDesign = null
        )
    }
}
