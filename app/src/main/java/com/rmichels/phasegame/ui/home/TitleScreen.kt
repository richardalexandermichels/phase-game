package com.rmichels.phasegame.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rmichels.phasegame.audio.AudioBus
import com.rmichels.phasegame.audio.AudioEngine
import com.rmichels.phasegame.audio.SoundCatalog
import com.rmichels.phasegame.music.GameInstrument

@Composable
internal fun TitleScreen(
    audioEngine: AudioEngine,
    onStart: () -> Unit,
    onOpenDesign: () -> Unit,
    baseInstrument: GameInstrument,
    onSelectBaseInstrument: (GameInstrument) -> Unit,
    playerInstrument: GameInstrument,
    onSelectPlayerInstrument: (GameInstrument) -> Unit,
    perfectModeEnabled: Boolean,
    onTogglePerfectMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val voiceSession = remember(audioEngine) { audioEngine.createSession() }

    DisposableEffect(audioEngine, voiceSession) {
        audioEngine.playImmediate(
            sampleId = SoundCatalog.TITLE_VOICE,
            bus = AudioBus.VOICE,
            sessionId = voiceSession,
            priority = 1
        )
        onDispose { audioEngine.cancelSession(voiceSession) }
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
        Spacer(modifier = Modifier.height(24.dp))
        InstrumentSelector(
            label = "Left hand (Base)",
            selectedInstrument = baseInstrument,
            onSelectInstrument = onSelectBaseInstrument
        )
        Spacer(modifier = Modifier.height(12.dp))
        InstrumentSelector(
            label = "Right hand (Player)",
            selectedInstrument = playerInstrument,
            onSelectInstrument = onSelectPlayerInstrument
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onStart) { Text("Start") }
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onOpenDesign) { Text("Design") }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onTogglePerfectMode) {
            Text(if (perfectModeEnabled) "Perfect Mode: On" else "Perfect Mode: Off")
        }
    }
}

@Composable
private fun InstrumentSelector(
    label: String,
    selectedInstrument: GameInstrument,
    onSelectInstrument: (GameInstrument) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(selectedInstrument.displayName)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                GameInstrument.entries.forEach { instrument ->
                    DropdownMenuItem(
                        text = { Text(instrument.displayName) },
                        onClick = {
                            onSelectInstrument(instrument)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
