package com.rmichels.phasegame.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.rmichels.phasegame.audio.AudioEngine
import com.rmichels.phasegame.design.GameDesign
import com.rmichels.phasegame.design.GameDesignSaver
import com.rmichels.phasegame.music.GameInstrument
import com.rmichels.phasegame.ui.design.DesignScreen
import com.rmichels.phasegame.ui.game.PhaseGameScreen
import com.rmichels.phasegame.ui.home.TitleScreen

private enum class AppScreen {
    TITLE,
    DESIGN,
    PLAYING
}

/** Small navigation shell; no gameplay rules or native lifecycle code belongs here. */
@Composable
internal fun PhaseGameApp(audioEngine: AudioEngine) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        var screen by rememberSaveable { mutableStateOf(AppScreen.TITLE) }
        var perfectModeEnabled by rememberSaveable { mutableStateOf(false) }
        var baseInstrumentOrdinal by rememberSaveable {
            mutableIntStateOf(GameInstrument.PIANO.ordinal)
        }
        var playerInstrumentOrdinal by rememberSaveable {
            mutableIntStateOf(GameInstrument.PIANO.ordinal)
        }
        val baseInstrument = GameInstrument.entries[
            baseInstrumentOrdinal.coerceIn(0, GameInstrument.entries.lastIndex)
        ]
        val playerInstrument = GameInstrument.entries[
            playerInstrumentOrdinal.coerceIn(0, GameInstrument.entries.lastIndex)
        ]
        var draftDesign by rememberSaveable(stateSaver = GameDesignSaver) {
            mutableStateOf(GameDesign.default())
        }
        var committedDesign by rememberSaveable(stateSaver = GameDesignSaver) {
            mutableStateOf(GameDesign.default())
        }
        var hasCommittedDesign by rememberSaveable { mutableStateOf(false) }
        val contentModifier = Modifier.padding(innerPadding)

        when (screen) {
            AppScreen.TITLE -> TitleScreen(
                audioEngine = audioEngine,
                onStart = { screen = AppScreen.PLAYING },
                onOpenDesign = { screen = AppScreen.DESIGN },
                baseInstrument = baseInstrument,
                onSelectBaseInstrument = { baseInstrumentOrdinal = it.ordinal },
                playerInstrument = playerInstrument,
                onSelectPlayerInstrument = { playerInstrumentOrdinal = it.ordinal },
                perfectModeEnabled = perfectModeEnabled,
                onTogglePerfectMode = {
                    perfectModeEnabled = !perfectModeEnabled
                },
                modifier = contentModifier
            )

            AppScreen.DESIGN -> DesignScreen(
                audioEngine = audioEngine,
                design = draftDesign,
                baseInstrument = baseInstrument,
                playerInstrument = playerInstrument,
                onDesignChange = { draftDesign = it },
                onPlay = {
                    committedDesign = draftDesign
                    hasCommittedDesign = true
                    screen = AppScreen.PLAYING
                },
                onReturnToTitle = { screen = AppScreen.TITLE },
                modifier = contentModifier
            )

            AppScreen.PLAYING -> PhaseGameScreen(
                audioEngine = audioEngine,
                onReturnToTitle = { screen = AppScreen.TITLE },
                perfectModeEnabled = perfectModeEnabled,
                baseInstrument = baseInstrument,
                playerInstrument = playerInstrument,
                gameDesign = committedDesign.takeIf { hasCommittedDesign },
                modifier = contentModifier
            )
        }
    }
}
