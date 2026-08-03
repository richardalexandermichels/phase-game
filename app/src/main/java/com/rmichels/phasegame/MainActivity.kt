package com.rmichels.phasegame

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import com.rmichels.phasegame.app.PhaseGameApp
import com.rmichels.phasegame.audio.PhaseAudioHost
import com.rmichels.phasegame.ui.theme.PhaseGameTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Android lifecycle entry point. UI navigation and game state live below it. */
class MainActivity : ComponentActivity() {
    private lateinit var audioHost: PhaseAudioHost
    private var audioLoadState by mutableStateOf<AudioLoadState>(AudioLoadState.Loading)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioHost = PhaseAudioHost(this)
        enableEdgeToEdge()
        setContent {
            PhaseGameTheme {
                when (val state = audioLoadState) {
                    AudioLoadState.Loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                    AudioLoadState.Ready -> PhaseGameApp(
                        audioEngine = audioHost.engine
                    )
                    is AudioLoadState.Failed -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Audio could not be loaded: ${state.reason}",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        lifecycleScope.launch {
            val failure = withContext(Dispatchers.IO) {
                runCatching { audioHost.prepare() }.exceptionOrNull()
            }
            if (failure == null) {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    audioHost.start()
                }
                audioLoadState = AudioLoadState.Ready
            } else {
                audioLoadState = AudioLoadState.Failed(
                    failure.message ?: failure::class.java.simpleName
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::audioHost.isInitialized && audioHost.isPrepared) {
            audioHost.start()
        }
    }

    override fun onStop() {
        if (::audioHost.isInitialized) audioHost.stop()
        super.onStop()
    }

    override fun onDestroy() {
        if (::audioHost.isInitialized) audioHost.release()
        super.onDestroy()
    }
}

private sealed interface AudioLoadState {
    data object Loading : AudioLoadState
    data object Ready : AudioLoadState
    data class Failed(val reason: String) : AudioLoadState
}
