package com.rmichels.phasegame.ui.game

internal data class RhythmIndicator(
    val queuedBarOffset: Int,
    val stepIndex: Int,
    val stepsUntilHit: Float
)

internal fun futureRhythmIndicators(
    queuedRhythms: List<List<Boolean>>,
    currentStepPosition: Float
): List<RhythmIndicator> {
    if (queuedRhythms.isEmpty()) {
        return emptyList()
    }

    val stepsPerBar = queuedRhythms.first().size
    require(stepsPerBar > 0)
    require(queuedRhythms.all { it.size == stepsPerBar })

    return queuedRhythms.flatMapIndexed { barOffset, rhythm ->
        rhythm.mapIndexedNotNull { stepIndex, isPlayed ->
            if (!isPlayed) {
                null
            } else {
                val scheduledStep =
                    barOffset * stepsPerBar + stepIndex

                val stepsUntilHit =
                    scheduledStep - currentStepPosition

                if (stepsUntilHit < 0f) {
                    null
                } else {
                    RhythmIndicator(
                        queuedBarOffset = barOffset,
                        stepIndex = stepIndex,
                        stepsUntilHit = stepsUntilHit
                    )
                }
            }
        }
    }
}
