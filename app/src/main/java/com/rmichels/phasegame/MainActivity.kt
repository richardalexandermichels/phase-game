package com.rmichels.phasegame

import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.rmichels.phasegame.ui.theme.PhaseGameTheme
import kotlinx.coroutines.delay
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.random.Random

private const val STEP_DURATION_MS = 250L
private const val INPUT_COMPENSATION_MS = 10L
private const val PERFECT_WINDOW_MS = 35L
private const val GOOD_WINDOW_MS = 70L
private const val CLOSE_WINDOW_MS = 120L
private const val REPETITIONS_PER_PHASE = 4L
private const val MAX_PATTERN_QUEUE_ITEMS = 11
private const val STARTUP_QUEUE_PULL_STEPS = 12f
private const val REGULAR_QUEUE_PULL_STEPS = 10f
private const val MISS_QUEUE_RAISE_SLOTS = 0.25f

/**
 * Converts one monotonic start time into bar, phase, and animation positions.
 * All rhythm systems must use this clock so their timing cannot drift apart.
 */
internal class RhythmClock(
    private val stepDurationMs: Long,
    private val stepsPerBar: Int
) {
    var startTimeMs: Long = 0L
        private set

    val barDurationMs: Long = stepDurationMs * stepsPerBar
    val isStarted: Boolean get() = startTimeMs != 0L

    fun start(nowMs: Long = SystemClock.elapsedRealtime()) {
        startTimeMs = nowMs
    }

    fun scheduleStart(startAtMs: Long) {
        startTimeMs = startAtMs
    }

    fun elapsedMs(nowMs: Long = SystemClock.elapsedRealtime()): Long =
        (nowMs - startTimeMs).coerceAtLeast(0L)

    fun progressThroughBar(nowMs: Long = SystemClock.elapsedRealtime()): Float =
        Math.floorMod(elapsedMs(nowMs), barDurationMs).toFloat() / barDurationMs

    fun positionInBarMs(nowMs: Long): Long =
        Math.floorMod(nowMs - startTimeMs, barDurationMs)

    fun absoluteBarIndex(nowMs: Long): Long =
        elapsedMs(nowMs) / barDurationMs

    fun phaseIndexForBar(
        absoluteBarIndex: Long,
        repetitionsPerPhase: Long = REPETITIONS_PER_PHASE
    ): Int =
        ((absoluteBarIndex / repetitionsPerPhase) % stepsPerBar).toInt()

    fun phaseIndex(
        nowMs: Long,
        repetitionsPerPhase: Long = REPETITIONS_PER_PHASE
    ): Int = phaseIndexForBar(absoluteBarIndex(nowMs), repetitionsPerPhase)
}

internal fun shiftedRhythm(baseRhythm: List<Boolean>, phaseIndex: Int): List<Boolean> =
    List(baseRhythm.size) { index ->
        baseRhythm[(index + phaseIndex) % baseRhythm.size]
    }

private enum class TapJudgment(val label: String) {
    PERFECT("Perfect"),
    GOOD("Good"),
    CLOSE("Close"),
    MISS("Miss");

    companion object {
        fun fromDistance(distanceMs: Long): TapJudgment = when {
            distanceMs <= PERFECT_WINDOW_MS -> PERFECT
            distanceMs <= GOOD_WINDOW_MS -> GOOD
            distanceMs <= CLOSE_WINDOW_MS -> CLOSE
            else -> MISS
        }
    }
}

private enum class LayerPattern {
    BASS_DRUM,
    SNARE,
    OPEN_HI_HAT,
    CLOSED_HI_HAT
}

private enum class AppScreen {
    TITLE,
    PLAYING,
    GAME_OVER
}

private class BarPerformance {
    val successfulHitIndices = mutableSetOf<Int>()
    var hadMiss = false
}

private data class ExpectedHit(
    val absoluteBarIndex: Long,
    val stepIndex: Int,
    val distanceMs: Long
)

private data class QueuedPatternBar(
    val absoluteBarIndex: Long,
    val rhythm: List<Boolean>
)

/**
 * Finds the closest valid player attack, including adjacent bars so taps near a
 * bar boundary are credited to the intended pattern and phase.
 */
private fun findNearestExpectedHit(
    tapTimeMs: Long,
    rhythmClock: RhythmClock,
    targetRhythm: List<Boolean>
): ExpectedHit {
    val barDurationMs = rhythmClock.barDurationMs
    val tapElapsedMs = rhythmClock.elapsedMs(tapTimeMs)
    val tapAbsoluteBar = rhythmClock.absoluteBarIndex(tapTimeMs)

    return (-1L..1L)
        .map { barOffset -> tapAbsoluteBar + barOffset }
        .filter { candidateBar -> candidateBar >= 0L }
        .flatMap { candidateBar ->
            targetRhythm.indices
                .filter { targetRhythm[it] }
                .map { hitIndex ->
                    val expectedTimeMs =
                        candidateBar * barDurationMs + hitIndex * STEP_DURATION_MS
                    ExpectedHit(
                        absoluteBarIndex = candidateBar,
                        stepIndex = hitIndex,
                        distanceMs = kotlin.math.abs(tapElapsedMs - expectedTimeMs)
                    )
                }
        }
        .minBy { it.distanceMs }
}

/**
 * Builds an evolving pop phrase from one three-note and one two-note motif.
 * The phrase repeats before one motif changes, creating hooks without becoming
 * completely predictable.
 */
private class PopMotifPitchGenerator {
    // Major-pentatonic intervals relative to each sound's generated root.
    // The upper B4 and D5 positions are omitted to keep the melody grounded.
    private val playbackRates = floatArrayOf(
        0.7492f, // A3 when the sample is D4
        0.8409f, // B3
        1.0000f, // D4 (root)
        1.1225f, // E4
        1.2599f, // F#4
        1.4983f  // A4
    )

    private var currentIndex = 2
    private var direction = if (Random.nextBoolean()) 1 else -1
    private var threeNoteMotif = generateMotif(length = 3)
    private var twoNoteMotif = generateMotif(length = 2)
    private var phrase = arrangePhrase()
    private var phraseIndex = 0
    private var completedPhraseCount = 0

    fun nextPlaybackRate(): Float {
        val rate = playbackRates[phrase[phraseIndex]]
        phraseIndex++

        if (phraseIndex == phrase.size) {
            phraseIndex = 0
            completedPhraseCount++

            // Repeat the complete hook twice, then mutate only half of its
            // musical identity while retaining the other recognizable motif.
            if (completedPhraseCount % 2 == 0) {
                if (Random.nextBoolean()) {
                    threeNoteMotif = generateMotif(length = 3)
                } else {
                    twoNoteMotif = generateMotif(length = 2)
                }
                phrase = arrangePhrase()
            }
        }

        return rate
    }

    private fun generateMotif(length: Int): List<Int> =
        List(length) {
            val noteIndex = currentIndex

            // Mostly move by one scale tone; occasional direction changes make
            // compact melodic arches rather than unrelated random pitches.
            if (Random.nextFloat() < 0.28f) {
                direction *= -1
            }
            var nextIndex = currentIndex + direction
            if (nextIndex !in playbackRates.indices) {
                direction *= -1
                nextIndex = currentIndex + direction
            }
            currentIndex = nextIndex
            noteIndex
        }

    // A–A–B–A is a compact electronic-pop hook with repetition and contrast.
    private fun arrangePhrase(): List<Int> =
        threeNoteMotif +
            threeNoteMotif +
            twoNoteMotif +
            threeNoteMotif
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhaseGameTheme {
                Scaffold( modifier = Modifier.fillMaxSize() ) { innerPadding ->
                    var appScreen by rememberSaveable {
                        mutableStateOf(AppScreen.TITLE)
                    }

                    when (appScreen) {
                        AppScreen.TITLE -> TitleScreen(
                            onStart = { appScreen = AppScreen.PLAYING },
                            modifier = Modifier.padding(innerPadding)
                        )
                        AppScreen.PLAYING -> PhaseGameScreen(
                            onGameOver = { appScreen = AppScreen.GAME_OVER },
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
}

@Composable
fun TitleScreen(
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val titleVoiceTrack = remember {
        AllophoneSpeechSynthesizer.createTrack(VocalBark.BEAT_PHASER)
    }

    DisposableEffect(titleVoiceTrack) {
        AllophoneSpeechSynthesizer.playFromStart(titleVoiceTrack)

        onDispose {
            titleVoiceTrack.release()
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
fun PhaseGameScreen(
    onGameOver: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val baseRhythm = remember {
        listOf(true, true, true, false, true, true, false, true, false, true, true, false)
    }
    val bassDrumPattern = remember {
        listOf(true, false, false, false, true, false, false, false, true, false, false, false)
    }
    val snarePattern = remember {
        listOf(false, false, true, false, false, false, true, false, false, false, true, false)
    }
    val openHiHatPattern = remember {
        listOf(false, true, false, true, false, true, false, true, false, true, false, true)
    }
    val closedHiHatPattern = remember {
        listOf(false, false, false, false, false, false, false, false, false, false, false, false)
    }
    // Change this list to control which layer patterns enter and in what order.
    val layerOrder = remember {
        listOf(
            LayerPattern.BASS_DRUM,
            LayerPattern.SNARE,
            LayerPattern.OPEN_HI_HAT
        )
    }
    val rhythmClock = remember {
        RhythmClock(STEP_DURATION_MS, baseRhythm.size)
    }
    val playerPitchGenerator = remember {
        PopMotifPitchGenerator()
    }
    val basePitchGenerator = remember {
        PopMotifPitchGenerator()
    }
    val soundPool = remember {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(audioAttributes)
            .build()
    }
    var playerProgress by remember { mutableFloatStateOf(0f) }
    var introCurrentSlot by remember { mutableFloatStateOf(0f) }
    var isGameplayActive by remember { mutableStateOf(false) }
    var isQueueDropping by remember { mutableStateOf(true) }
    var queueWasRaisedByMiss by remember { mutableStateOf(false) }
    var startCueText by remember { mutableStateOf<String?>(null) }
    val patternQueue = remember { mutableStateListOf<QueuedPatternBar>() }
    val isMetronomePlaying = true
    var baseSoundId by remember { mutableIntStateOf(0) }
    var perfectSoundId by remember { mutableIntStateOf(0) }
    var goodSoundId by remember { mutableIntStateOf(0) }
    var closeSoundId by remember { mutableIntStateOf(0) }
    var missSoundId by remember { mutableIntStateOf(0) }
    var bassDrumSoundId by remember { mutableIntStateOf(0) }
    var snareSoundId by remember { mutableIntStateOf(0) }
    var openHiHatSoundId by remember { mutableIntStateOf(0) }
    var closedHiHatSoundId by remember { mutableIntStateOf(0) }
    var loadedSoundCount by remember { mutableIntStateOf(0) }
    val isAudioLoaded = loadedSoundCount == 9
    var tapJudgment by remember { mutableStateOf<TapJudgment?>(null) }
    var activeLayerCount by remember { mutableIntStateOf(0) }
    val barPerformances = remember {
        mutableMapOf<Long, BarPerformance>()
    }

    DisposableEffect(soundPool, context) {
        soundPool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) {
                loadedSoundCount++
            }
        }
        baseSoundId = soundPool.load(context, R.raw.player_click, 1)
        perfectSoundId = soundPool.load(context, R.raw.player_perfect, 1)
        goodSoundId = soundPool.load(context, R.raw.player_good, 1)
        closeSoundId = soundPool.load(context, R.raw.player_close, 1)
        missSoundId = soundPool.load(context, R.raw.player_miss, 1)
        bassDrumSoundId = soundPool.load(context, R.raw.bass_drum, 1)
        snareSoundId = soundPool.load(context, R.raw.snare, 1)
        openHiHatSoundId = soundPool.load(context, R.raw.open_hi_hat, 1)
        closedHiHatSoundId = soundPool.load(context, R.raw.closed_hi_hat, 1)

        onDispose {
            soundPool.setOnLoadCompleteListener(null)
            soundPool.release()
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
                        rhythm = shiftedRhythm(baseRhythm, queuedPhase)
                    )
                )
            }
        }
    }

    LaunchedEffect(isGameplayActive, isMetronomePlaying, isAudioLoaded) {
        if (!isGameplayActive || !isMetronomePlaying || !isAudioLoaded) {
            playerProgress = 0f
            return@LaunchedEffect
        }

        var nextAbsoluteStep = 0L
        var currentAbsoluteBar = 0L
        var previousStepProgress = 0f
        var basePlaybackRate = 1f
        val stepsPerBasePitchSection = baseRhythm.size / 4
        var nextQueuedBar =
            (patternQueue.lastOrNull()?.absoluteBarIndex ?: -1L) + 1L

        while (isGameplayActive && isMetronomePlaying && isAudioLoaded) {
            val nowMs = SystemClock.elapsedRealtime()
            val elapsedMs = rhythmClock.elapsedMs(nowMs)
            val dueAbsoluteStep = elapsedMs / STEP_DURATION_MS
            val absoluteBar = rhythmClock.absoluteBarIndex(nowMs)
            val newStepProgress =
                elapsedMs.toFloat() / STEP_DURATION_MS
            if (isQueueDropping) {
                val elapsedSteps =
                    (newStepProgress - previousStepProgress).coerceAtLeast(0f)
                introCurrentSlot = minOf(
                    (MAX_PATTERN_QUEUE_ITEMS - 1).toFloat(),
                    introCurrentSlot +
                        elapsedSteps / REGULAR_QUEUE_PULL_STEPS
                )
            }
            previousStepProgress = newStepProgress

            // Score completed bars before scheduling the new bar's first step.
            if (absoluteBar > currentAbsoluteBar) {
                for (completedBar in currentAbsoluteBar until absoluteBar) {
                    val performance = barPerformances.remove(completedBar)
                    val completedPlayerPattern =
                        patternQueue.firstOrNull()?.rhythm ?: baseRhythm
                    val requiredHits =
                        completedPlayerPattern.count { it }
                    val completedWithoutMisses =
                        performance != null &&
                            !performance.hadMiss &&
                            performance.successfulHitIndices.size == requiredHits

                    activeLayerCount = if (completedWithoutMisses) {
                        minOf(activeLayerCount + 1, layerOrder.size)
                    } else {
                        0
                    }

                    if (completedWithoutMisses) {
                        if (patternQueue.isNotEmpty()) {
                            patternQueue.removeAt(0)
                            introCurrentSlot =
                                (introCurrentSlot - 1f).coerceAtLeast(0f)
                        }
                        if (patternQueue.size < MAX_PATTERN_QUEUE_ITEMS) {
                            val queuedPhase =
                                rhythmClock.phaseIndexForBar(nextQueuedBar)
                            patternQueue.add(
                                QueuedPatternBar(
                                    absoluteBarIndex = nextQueuedBar,
                                    rhythm = shiftedRhythm(baseRhythm, queuedPhase)
                                )
                            )
                            nextQueuedBar++
                        }
                        isQueueDropping = true
                        queueWasRaisedByMiss = false
                    }
                }
                currentAbsoluteBar = absoluteBar
            }

            // A long UI stall must not replay every stale sound in a rapid burst.
            if (dueAbsoluteStep - nextAbsoluteStep > 1L) {
                nextAbsoluteStep = dueAbsoluteStep
            }

            while (nextAbsoluteStep <= dueAbsoluteStep) {
                val stepInBar = (nextAbsoluteStep % baseRhythm.size).toInt()
                if (stepInBar % stepsPerBasePitchSection == 0) {
                    basePlaybackRate =
                        basePitchGenerator.nextPlaybackRate()
                }
                if (baseRhythm[stepInBar]) {
                    soundPool.play(
                        baseSoundId,
                        1f,
                        1f,
                        1,
                        0,
                        basePlaybackRate
                    )
                }
                val activeLayers = layerOrder.take(activeLayerCount)
                if (
                    LayerPattern.BASS_DRUM in activeLayers &&
                    bassDrumPattern[stepInBar]
                ) {
                    soundPool.play(bassDrumSoundId, 1f, 1f, 1, 0, 1f)
                }
                if (
                    LayerPattern.SNARE in activeLayers &&
                    snarePattern[stepInBar]
                ) {
                    soundPool.play(snareSoundId, 1f, 1f, 1, 0, 1f)
                }
                if (
                    LayerPattern.OPEN_HI_HAT in activeLayers &&
                    openHiHatPattern[stepInBar]
                ) {
                    soundPool.play(openHiHatSoundId, 1f, 1f, 1, 0, 1f)
                }
                if (
                    LayerPattern.CLOSED_HI_HAT in activeLayers &&
                    closedHiHatPattern[stepInBar]
                ) {
                    soundPool.play(closedHiHatSoundId, 1f, 1f, 1, 0, 1f)
                }
                nextAbsoluteStep++
            }

            playerProgress = rhythmClock.progressThroughBar(nowMs)

            val nextStepTimeMs =
                rhythmClock.startTimeMs + nextAbsoluteStep * STEP_DURATION_MS
            delay((nextStepTimeMs - nowMs).coerceIn(1L, 16L))
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
            soundPool.play(
                perfectSoundId,
                1f,
                1f,
                1,
                0,
                playerPitchGenerator.nextPlaybackRate()
            )
            return@press
        }

        run {
            val compensatedTapTimeMs =
                SystemClock.elapsedRealtime() - INPUT_COMPENSATION_MS
            val nearestExpectedHit = findNearestExpectedHit(
                tapTimeMs = compensatedTapTimeMs,
                rhythmClock = rhythmClock,
                targetRhythm =
                    patternQueue.firstOrNull()?.rhythm ?: baseRhythm
            )
            val judgment = TapJudgment.fromDistance(nearestExpectedHit.distanceMs)
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
                introCurrentSlot =
                    (introCurrentSlot - MISS_QUEUE_RAISE_SLOTS)
                        .coerceAtLeast(0f)
                isQueueDropping = false
                queueWasRaisedByMiss = true
            } else {
                performance.successfulHitIndices.add(nearestExpectedHit.stepIndex)
            }

            val playerSoundId = when (judgment) {
                TapJudgment.PERFECT -> perfectSoundId
                TapJudgment.GOOD -> goodSoundId
                TapJudgment.CLOSE -> closeSoundId
                TapJudgment.MISS -> missSoundId
            }
            val playbackRate = if (judgment == TapJudgment.MISS) {
                1f
            } else {
                playerPitchGenerator.nextPlaybackRate()
            }
            soundPool.play(
                playerSoundId,
                1f,
                1f,
                1,
                0,
                if (judgment == TapJudgment.MISS) 1f else playbackRate
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        // Keep the entire 24 dp row plus a 4 dp gap above the input area.
        val playerTravelDistance = maxHeight * 0.6f - 28.dp
        val playAreaMidpoint = maxHeight * 0.3f
        val cursorX = (-154f + 336f * playerProgress).dp
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
            midpointSlot,
            lineClearanceSlots
        ) {
            if (!isAudioLoaded || isGameplayActive) {
                return@LaunchedEffect
            }

            val fastSlotsPerStep =
                (MAX_PATTERN_QUEUE_ITEMS - 1) / STARTUP_QUEUE_PULL_STEPS
            val slowSlotsPerStep = 1f / REGULAR_QUEUE_PULL_STEPS
            val fastStepsToMidpoint = midpointSlot / fastSlotsPerStep
            val targetSlot =
                midpointSlot + 1f + lineClearanceSlots
            val slowStepsAfterMidpoint =
                (targetSlot - midpointSlot) / slowSlotsPerStep
            val totalIntroSteps =
                fastStepsToMidpoint + slowStepsAfterMidpoint
            val introDurationMs =
                (totalIntroSteps * STEP_DURATION_MS).roundToInt().toLong()
            val introStartMs = SystemClock.elapsedRealtime()
            rhythmClock.scheduleStart(introStartMs + introDurationMs)

            while (!isGameplayActive && introCurrentSlot < targetSlot) {
                val nowMs = SystemClock.elapsedRealtime()
                val stepsUntilPlayable =
                    (rhythmClock.startTimeMs - nowMs).toFloat() /
                        STEP_DURATION_MS
                val elapsedSteps =
                    (totalIntroSteps - stepsUntilPlayable)
                        .coerceIn(0f, totalIntroSteps)
                introCurrentSlot = if (elapsedSteps <= fastStepsToMidpoint) {
                    elapsedSteps * fastSlotsPerStep
                } else {
                    midpointSlot +
                        (elapsedSteps - fastStepsToMidpoint) *
                        slowSlotsPerStep
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
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.4f)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .pointerInput(isAudioLoaded) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        handlePlayerPress()
                        waitForUpOrCancellation()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(tapJudgment?.label ?: "Tap")
        }
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

@Composable
fun RhythmDots(rhythm: List<Boolean>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        rhythm.forEach { isHit ->
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(
                            if (isHit) 18.dp else 8.dp
                        )
                        .background(
                            color = if (isHit) {
                                MaterialTheme.colorScheme.primary
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

@Preview(showBackground = true)
@Composable
fun PhaseGameScreenPreview() {
    PhaseGameTheme {
        PhaseGameScreen(onGameOver = {})
    }
}
