package com.rmichels.phasegame

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rmichels.phasegame.audio.AudioBus
import com.rmichels.phasegame.audio.AudioEngine
import com.rmichels.phasegame.audio.DefaultAudioMix
import com.rmichels.phasegame.audio.NativeAudioEngine
import com.rmichels.phasegame.audio.SoundCatalog
import com.rmichels.phasegame.audio.activeBackingTrackMaxTier
import com.rmichels.phasegame.audio.activeBackingTrackStepDurationMs
import com.rmichels.phasegame.ui.theme.PhaseGameTheme
import kotlinx.coroutines.delay
import kotlin.math.exp
import kotlin.math.roundToInt

internal val STEP_DURATION_MS = activeBackingTrackStepDurationMs
private const val MISS_QUEUE_RAISE_SLOTS = 0.25f
internal const val MIN_PATTERN_STEPS = 2
internal const val MAX_PATTERN_STEPS = 16
private const val QUEUE_FALL_SLOTS_PER_BAR = 1.2f

internal fun shiftedRhythm(
    baseRhythm: List<Boolean>,
    phaseIndex: Int
): List<Boolean> {
    require(baseRhythm.isNotEmpty())
    return List(baseRhythm.size) { index ->
        baseRhythm[Math.floorMod(index + phaseIndex, baseRhythm.size)]
    }
}

internal fun validateBaseRhythm(rhythm: List<Boolean>): List<Boolean> {
    require(rhythm.size in MIN_PATTERN_STEPS..MAX_PATTERN_STEPS) {
        "Base rhythm must contain $MIN_PATTERN_STEPS to $MAX_PATTERN_STEPS steps."
    }
    require(rhythm.any { it }) {
        "Base rhythm must contain at least one played step."
    }
    return rhythm
}

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

private enum class AppScreen {
    TITLE,
    DESIGN,
    PLAYING,
    GAME_OVER
}

class MainActivity : ComponentActivity() {
    private lateinit var audioEngine: NativeAudioEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioEngine = NativeAudioEngine(this).also { engine ->
            engine.prepare()
            engine.registerPcm16(
                sampleId = SoundCatalog.TITLE_VOICE,
                sampleRate = AllophoneSpeechSynthesizer.SAMPLE_RATE,
                samples = AllophoneSpeechSynthesizer.renderPcm(
                    VocalBark.BEAT_PHASER
                )
            )
            DefaultAudioMix.applyTo(engine)
            engine.start()
        }
        enableEdgeToEdge()
        setContent {
            PhaseGameTheme {
                Scaffold( modifier = Modifier.fillMaxSize() ) { innerPadding ->
                    var appScreen by rememberSaveable {
                        mutableStateOf(AppScreen.TITLE)
                    }
                    var perfectModeEnabled by rememberSaveable {
                        mutableStateOf(false)
                    }
                    var draftDesign by rememberSaveable(
                        stateSaver = GameDesignSaver
                    ) {
                        mutableStateOf(GameDesign.default())
                    }
                    var committedDesign by rememberSaveable(
                        stateSaver = GameDesignSaver
                    ) {
                        mutableStateOf(GameDesign.default())
                    }
                    var hasCommittedDesign by rememberSaveable {
                        mutableStateOf(false)
                    }

                    when (appScreen) {
                        AppScreen.TITLE -> TitleScreen(
                            audioEngine = audioEngine,
                            onStart = { appScreen = AppScreen.PLAYING },
                            onOpenDesign = { appScreen = AppScreen.DESIGN },
                            perfectModeEnabled = perfectModeEnabled,
                            onTogglePerfectMode = {
                                perfectModeEnabled = !perfectModeEnabled
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                        AppScreen.DESIGN -> DesignScreen(
                            audioEngine = audioEngine,
                            design = draftDesign,
                            onDesignChange = { updatedDesign ->
                                draftDesign = updatedDesign
                            },
                            onPlay = {
                                committedDesign = draftDesign
                                hasCommittedDesign = true
                                appScreen = AppScreen.PLAYING
                            },
                            onReturnToTitle = {
                                appScreen = AppScreen.TITLE
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                        AppScreen.PLAYING -> PhaseGameScreen(
                            audioEngine = audioEngine,
                            onGameOver = { appScreen = AppScreen.GAME_OVER },
                            perfectModeEnabled = perfectModeEnabled,
                            gameDesign = committedDesign.takeIf {
                                hasCommittedDesign
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                        AppScreen.GAME_OVER -> GameOverScreen(
                            onReturnToTitle = { appScreen = AppScreen.TITLE },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::audioEngine.isInitialized) audioEngine.start()
    }

    override fun onStop() {
        if (::audioEngine.isInitialized) audioEngine.stop()
        super.onStop()
    }

    override fun onDestroy() {
        if (::audioEngine.isInitialized) audioEngine.release()
        super.onDestroy()
    }
}

@Composable
internal fun TitleScreen(
    audioEngine: AudioEngine?,
    onStart: () -> Unit,
    onOpenDesign: () -> Unit,
    perfectModeEnabled: Boolean,
    onTogglePerfectMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val voiceSession = remember(audioEngine) {
        audioEngine?.createSession()
    }

    DisposableEffect(audioEngine, voiceSession) {
        if (voiceSession != null) {
            audioEngine?.playImmediate(
                sampleId = SoundCatalog.TITLE_VOICE,
                bus = AudioBus.VOICE,
                sessionId = voiceSession,
                priority = 1
            )
        }

        onDispose {
            if (voiceSession != null) audioEngine?.cancelSession(voiceSession)
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Beat Phaser",
            fontFamily = FontFamily.Cursive,
            fontWeight = FontWeight.Bold,
            fontSize = 52.sp,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onStart) {
            Text("Start")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onOpenDesign) {
            Text("Design")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onTogglePerfectMode) {
            Text(
                if (perfectModeEnabled) {
                    "Perfect Mode: On"
                } else {
                    "Perfect Mode: Off"
                }
            )
        }
    }
}

@Composable
fun GameOverScreen(
    onReturnToTitle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Game Over",
            fontFamily = FontFamily.Cursive,
            fontWeight = FontWeight.Bold,
            fontSize = 52.sp,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onReturnToTitle) {
            Text("Return to Title")
        }
    }
}

@Composable
internal fun PhaseGameScreen(
    audioEngine: AudioEngine?,
    onGameOver: () -> Unit,
    perfectModeEnabled: Boolean,
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
    val maximumBackingTier = activeBackingTrackMaxTier
    val rhythmClock = remember {
        RhythmClock(
            stepDurationMs = STEP_DURATION_MS,
            stepsPerBar = baseRhythm.size
        )
    }
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
    val baseAudioSession = remember(audioEngine) {
        audioEngine?.createSession()
    }
    val percussionAudioSession = remember(audioEngine) {
        audioEngine?.createSession()
    }
    val playerAudioSession = remember(audioEngine) {
        audioEngine?.createSession()
    }
    val audioConductor = remember(
        audioEngine,
        baseAudioSession,
        percussionAudioSession,
        baseRhythm,
        gameDesign,
        phaseMelody
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
                phaseMelody = phaseMelody
            )
        } else {
            null
        }
    }
    var playerProgress by remember { mutableFloatStateOf(0f) }
    var introCurrentSlot by remember { mutableFloatStateOf(0f) }
    var isGameplayActive by remember { mutableStateOf(false) }
    var isQueueDropping by remember { mutableStateOf(true) }
    var queueWasRaisedByMiss by remember { mutableStateOf(false) }
    var startCueText by remember { mutableStateOf<String?>(null) }
    val patternQueue = remember { mutableStateListOf<QueuedPatternBar>() }
    val isMetronomePlaying = true
    val isAudioLoaded = audioEngine?.isReady == true
    var tapJudgment by remember { mutableStateOf<TapJudgment?>(null) }
    var activeLayerCount by remember { mutableIntStateOf(0) }
    var lastProcessedAbsoluteBar by remember { mutableLongStateOf(0L) }
    var nextQueuedBar by remember {
        mutableLongStateOf(MAX_PATTERN_QUEUE_ITEMS.toLong())
    }
    val currentAudioPhase =
        patternQueue.firstOrNull()?.phaseIndex ?: 0
    val barPerformances = remember {
        mutableMapOf<Long, BarPerformance>()
    }
    val activeRhythmByClockBar = remember {
        mutableMapOf<Long, List<Boolean>>()
    }

    fun processCompletedBarsThrough(targetAbsoluteBar: Long) {
        if (targetAbsoluteBar <= lastProcessedAbsoluteBar) return

        for (
            completedBar in lastProcessedAbsoluteBar until targetAbsoluteBar
        ) {
            val activePattern = patternQueue.firstOrNull() ?: break
            activeRhythmByClockBar.putIfAbsent(
                completedBar,
                activePattern.rhythm
            )
            val transition = advanceGameplayQueue(
                patternQueue = patternQueue,
                nextQueuedBar = nextQueuedBar,
                performance = barPerformances.remove(completedBar),
                currentLayerCount = activeLayerCount,
                maximumLayerCount = maximumBackingTier,
                rhythmClock = rhythmClock,
                baseRhythm = baseRhythm
            )
            activeLayerCount = transition.nextLayerCount

            if (transition.completedWithoutMisses) {
                patternQueue.clear()
                patternQueue.addAll(transition.patternQueue)
                nextQueuedBar = transition.nextQueuedBar
                introCurrentSlot =
                    (introCurrentSlot - 1f).coerceAtLeast(0f)
                isQueueDropping = true
                queueWasRaisedByMiss = false
            }

            patternQueue.firstOrNull()?.let { nextActivePattern ->
                activeRhythmByClockBar[completedBar + 1L] =
                    nextActivePattern.rhythm
            }
        }
        lastProcessedAbsoluteBar = targetAbsoluteBar
        activeRhythmByClockBar.keys.removeAll { clockBar ->
            clockBar < targetAbsoluteBar - 2L
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
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(
        audioEngine,
        baseAudioSession,
        percussionAudioSession,
        playerAudioSession
    ) {
        onDispose {
            audioConductor?.release()
            if (baseAudioSession != null) {
                audioEngine?.cancelSession(baseAudioSession)
            }
            if (percussionAudioSession != null) {
                audioEngine?.cancelSession(percussionAudioSession)
            }
            if (playerAudioSession != null) {
                audioEngine?.cancelSession(playerAudioSession)
            }
        }
    }

    LaunchedEffect(activeLayerCount, audioConductor) {
        audioConductor?.setActiveLayerCount(activeLayerCount)
    }

    LaunchedEffect(currentAudioPhase, phaseMelody) {
        phaseMelody.selectPhase(currentAudioPhase)
    }

    LaunchedEffect(
        isGameplayActive,
        isMetronomePlaying,
        isAudioLoaded,
        isLifecycleStarted,
        audioConductor
    ) {
        if (
            isGameplayActive &&
            isMetronomePlaying &&
            isAudioLoaded &&
            isLifecycleStarted
        ) {
            audioConductor?.start(rhythmClock.startTimeMs)
        } else {
            audioConductor?.stop()
        }
    }

    LaunchedEffect(isAudioLoaded) {
        if (isAudioLoaded && patternQueue.isEmpty()) {
            repeat(MAX_PATTERN_QUEUE_ITEMS) { absoluteBar ->
                val queuedPhase =
                    rhythmClock.phaseIndexForBar(absoluteBar.toLong())
                patternQueue.add(
                    QueuedPatternBar(
                        absoluteBarIndex = absoluteBar.toLong(),
                        phaseIndex = queuedPhase,
                        rhythm = shiftedRhythm(baseRhythm, queuedPhase)
                    )
                )
            }
            activeRhythmByClockBar[0L] = patternQueue.first().rhythm
            nextQueuedBar = MAX_PATTERN_QUEUE_ITEMS.toLong()
        }
    }

    LaunchedEffect(
        isGameplayActive,
        isMetronomePlaying,
        isAudioLoaded,
        isLifecycleStarted
    ) {
        if (
            !isGameplayActive ||
            !isMetronomePlaying ||
            !isAudioLoaded ||
            !isLifecycleStarted
        ) {
            playerProgress = 0f
            return@LaunchedEffect
        }

        var previousStepProgress =
            rhythmClock.elapsedMs().toFloat() / rhythmClock.stepDurationMs

        while (
            isGameplayActive &&
            isMetronomePlaying &&
            isAudioLoaded &&
            isLifecycleStarted
        ) {
            val nowMs = SystemClock.elapsedRealtime()
            val elapsedMs = rhythmClock.elapsedMs(nowMs)
            val absoluteBar = rhythmClock.absoluteBarIndex(nowMs)
            val newStepProgress =
                elapsedMs.toFloat() / rhythmClock.stepDurationMs
            if (isQueueDropping) {
                val elapsedSteps =
                    (newStepProgress - previousStepProgress).coerceAtLeast(0f)
                introCurrentSlot = minOf(
                    (MAX_PATTERN_QUEUE_ITEMS - 1).toFloat(),
                    introCurrentSlot +
                        elapsedSteps *
                        regularQueueSlotsPerStep(baseRhythm.size)
                )
            }
            previousStepProgress = newStepProgress

            // Process transitions before rendering the new bar. Pointer input
            // calls the same function, so a tap cannot observe the old queue.
            processCompletedBarsThrough(absoluteBar)

            playerProgress = rhythmClock.progressThroughBar(nowMs)
            delay(16L)
        }
    }

    LaunchedEffect(isGameplayActive) {
        if (isGameplayActive) {
            startCueText = "Go!"
            delay(650L)
            startCueText = null
        }
    }

    val handlePlayerPress = press@{
        if (!isAudioLoaded || !isMetronomePlaying) {
            return@press
        }

        if (!isGameplayActive || !rhythmClock.isStarted) {
            if (playerAudioSession != null) {
                audioEngine?.playImmediate(
                    sampleId = SoundCatalog.PLAYER_PERFECT,
                    bus = AudioBus.PLAYER,
                    sessionId = playerAudioSession,
                    playbackRate =
                        phaseMelody.playbackRateForBaseStep(0),
                    priority = 3
                )
            }
            return@press
        }

        run {
            val tapTimeMs = SystemClock.elapsedRealtime()
            processCompletedBarsThrough(
                rhythmClock.absoluteBarIndex(tapTimeMs)
            )
            val compensatedTapTimeMs =
                tapTimeMs - INPUT_COMPENSATION_MS
            val nearestExpectedHit = findNearestExpectedHit(
                tapTimeMs = compensatedTapTimeMs,
                rhythmClock = rhythmClock,
                targetRhythmForBar = { clockBar ->
                    activeRhythmByClockBar[clockBar]
                        ?: patternQueue.firstOrNull()?.rhythm
                        ?: baseRhythm
                }
            )
            val measuredJudgment =
                TapJudgment.fromDistance(nearestExpectedHit.distanceMs)
            val judgment = if (
                perfectModeEnabled &&
                measuredJudgment != TapJudgment.MISS
            ) {
                TapJudgment.PERFECT
            } else {
                measuredJudgment
            }
            tapJudgment = judgment

            // A set prevents duplicate taps from satisfying multiple expected notes.
            val performance = barPerformances.getOrPut(
                nearestExpectedHit.absoluteBarIndex
            ) {
                BarPerformance()
            }
            if (judgment == TapJudgment.MISS) {
                performance.hadMiss = true
                activeLayerCount = 0
                audioConductor?.cancelFuturePercussion()
                introCurrentSlot =
                    (introCurrentSlot - MISS_QUEUE_RAISE_SLOTS)
                        .coerceAtLeast(0f)
                isQueueDropping = false
                queueWasRaisedByMiss = true
            } else {
                performance.successfulHitIndices.add(nearestExpectedHit.stepIndex)
            }

            val currentPhase =
                patternQueue.firstOrNull()?.phaseIndex ?: 0
            val designedPlayerPitches = if (
                judgment != TapJudgment.MISS
            ) {
                gameDesign?.playerNotes(
                    currentPhase,
                    nearestExpectedHit.stepIndex
                )
            } else {
                null
            }
            phaseMelody.selectPhase(currentPhase)
            val playbackRate = when {
                judgment == TapJudgment.MISS -> 1f
                designedPlayerPitches.isNullOrEmpty() ->
                    phaseMelody.playbackRateForPlayerStep(
                        nearestExpectedHit.stepIndex,
                        currentPhase
                    )
                judgment == TapJudgment.GOOD ->
                    DESIGN_GOOD_PLAYBACK_RATE
                judgment == TapJudgment.CLOSE ->
                    DESIGN_CLOSE_PLAYBACK_RATE
                else -> 1f
            }
            if (playerAudioSession != null) {
                if (designedPlayerPitches.isNullOrEmpty()) {
                    val sampleId = when (judgment) {
                        TapJudgment.PERFECT -> SoundCatalog.PLAYER_PERFECT
                        TapJudgment.GOOD -> SoundCatalog.PLAYER_GOOD
                        TapJudgment.CLOSE -> SoundCatalog.PLAYER_CLOSE
                        TapJudgment.MISS -> SoundCatalog.PLAYER_MISS
                    }
                    audioEngine?.playImmediate(
                        sampleId = sampleId,
                        bus = AudioBus.PLAYER,
                        sessionId = playerAudioSession,
                        playbackRate = if (judgment == TapJudgment.MISS) {
                            1f
                        } else {
                            playbackRate
                        },
                        priority = 3
                    )
                } else {
                    designedPlayerPitches.forEach { pitch ->
                        audioEngine?.playImmediate(
                            sampleId = SoundCatalog.designPlayer[pitch],
                            bus = AudioBus.PLAYER,
                            sessionId = playerAudioSession,
                            playbackRate = playbackRate,
                            priority = 3
                        )
                    }
                }
            }
        }
    }
    val currentHandlePlayerPress by rememberUpdatedState(handlePlayerPress)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        // Keep the entire 24 dp row plus a 4 dp gap above the input area.
        val playerTravelDistance = maxHeight * 0.6f - 28.dp
        val playAreaMidpoint = maxHeight * 0.3f
        val cursorX =
            rhythmCursorOffsetDp(playerProgress, baseRhythm.size).dp
        val exponentialFade = (
            (1f - exp(-5f * playerProgress)) /
                (1f - exp(-5f))
            ).coerceIn(0f, 1f)
        val cursorColor = lerp(
            Color(0xFFFFD600),
            Color.Gray,
            exponentialFade
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = cursorX)
                .width(3.dp)
                .fillMaxHeight(0.6f)
                .background(cursorColor)
        )
        val queueSlotSpacing =
            playerTravelDistance / (MAX_PATTERN_QUEUE_ITEMS - 1)
        val midpointSlot = playAreaMidpoint / queueSlotSpacing
        val lineClearanceSlots = 14.dp / queueSlotSpacing
        val newestQueueIndex = patternQueue.lastIndex

        LaunchedEffect(
            isAudioLoaded,
            isLifecycleStarted,
            midpointSlot,
            lineClearanceSlots
        ) {
            if (!isAudioLoaded || !isLifecycleStarted || isGameplayActive) {
                return@LaunchedEffect
            }

            val fastSlotsPerStep =
                startupQueueSlotsPerStep(
                    patternStepCount = baseRhythm.size,
                    queueItemCount = MAX_PATTERN_QUEUE_ITEMS
                )
            val slowSlotsPerStep =
                regularQueueSlotsPerStep(baseRhythm.size)
            val targetSlot =
                midpointSlot + 1f + lineClearanceSlots
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
            rhythmClock.scheduleStart(
                SystemClock.elapsedRealtime() + introDurationMs
            )
            var previousUpdateMs = SystemClock.elapsedRealtime()

            while (!isGameplayActive && introCurrentSlot < targetSlot) {
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

                if (introCurrentSlot >= midpointSlot) {
                    val countdownProgress = (
                        (introCurrentSlot - midpointSlot) /
                            (targetSlot - midpointSlot)
                        ).coerceIn(0f, 1f)
                    startCueText = when {
                        countdownProgress < 0.25f -> "Ready"
                        countdownProgress < 0.50f -> "2"
                        countdownProgress < 0.75f -> "3"
                        else -> "4"
                    }
                }
                delay(16L)
            }

            introCurrentSlot = targetSlot
            val remainingMs =
                rhythmClock.startTimeMs - SystemClock.elapsedRealtime()
            if (remainingMs > 0L) {
                delay(remainingMs)
            }
            isGameplayActive = true
        }

        LaunchedEffect(
            isGameplayActive,
            queueWasRaisedByMiss,
            introCurrentSlot,
            midpointSlot,
            lineClearanceSlots
        ) {
            if (
                isGameplayActive &&
                queueWasRaisedByMiss &&
                introCurrentSlot <= midpointSlot + lineClearanceSlots
            ) {
                onGameOver()
            }
        }

        val sheetOffsetSlots =
            introCurrentSlot - newestQueueIndex

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
                            y = (
                                queueSlotSpacing.toPx() * slotFromTop
                                ).roundToInt()
                        )
                    }
                    .alpha(rowOpacity)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(2f)
                .fillMaxWidth()
                .height(56.dp)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            RhythmDots(rhythm = baseRhythm)
        }
        TapArea(
            judgmentLabel = tapJudgment?.label,
            onPress = handlePlayerPress,
            modifier = Modifier.align(Alignment.BottomCenter)
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
    }
}

@Preview(showBackground = true)
@Composable
fun PhaseGameScreenPreview() {
    PhaseGameTheme {
        PhaseGameScreen(
            audioEngine = null,
            onGameOver = {},
            perfectModeEnabled = false,
            gameDesign = null
        )
    }
}
