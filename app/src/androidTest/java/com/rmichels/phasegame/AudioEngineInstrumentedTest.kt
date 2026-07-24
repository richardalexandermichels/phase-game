package com.rmichels.phasegame

import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rmichels.phasegame.audio.AudioBus
import com.rmichels.phasegame.audio.NativeAudioEngine
import com.rmichels.phasegame.audio.SoundCatalog
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioEngineInstrumentedTest {
    @Test
    fun engine_opensSchedulesAndReportsItsGrantedConfiguration() {
        val engine = NativeAudioEngine(
            ApplicationProvider.getApplicationContext()
        )
        try {
            engine.prepare()
            engine.start()
            val session = engine.createSession()
            assertTrue(
                engine.playImmediate(
                    sampleId = SoundCatalog.PLAYER_PERFECT,
                    bus = AudioBus.PLAYER,
                    sessionId = session,
                    gain = 0.05f
                )
            )
            SystemClock.sleep(100L)

            val diagnostics = engine.diagnostics()
            assertTrue(diagnostics.sampleRate > 0)
            assertTrue(diagnostics.channelCount > 0)
            assertTrue(diagnostics.framesPerBurst > 0)
            assertTrue(diagnostics.bufferSizeFrames > 0)
        } finally {
            engine.release()
        }
    }
}
