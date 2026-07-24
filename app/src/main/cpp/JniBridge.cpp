#include "AudioEngine.h"

#include <jni.h>
#include <memory>

using phaseaudio::AudioCommand;
using phaseaudio::AudioEngine;

namespace {
AudioEngine* engine(jlong handle) {
    return reinterpret_cast<AudioEngine*>(handle);
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_rmichels_phasegame_audio_NativeAudioEngine_nativeCreate(
    JNIEnv*,
    jobject
) {
    return reinterpret_cast<jlong>(new AudioEngine());
}

extern "C" JNIEXPORT void JNICALL
Java_com_rmichels_phasegame_audio_NativeAudioEngine_nativeDestroy(
    JNIEnv*,
    jobject,
    jlong handle
) {
    delete engine(handle);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_rmichels_phasegame_audio_NativeAudioEngine_nativeRegisterWav(
    JNIEnv* env,
    jobject,
    jlong handle,
    jint sampleId,
    jbyteArray bytes
) {
    const jsize size = env->GetArrayLength(bytes);
    jbyte* data = env->GetByteArrayElements(bytes, nullptr);
    const bool result = engine(handle)->registerWav(
        sampleId,
        reinterpret_cast<uint8_t*>(data),
        static_cast<size_t>(size)
    );
    env->ReleaseByteArrayElements(bytes, data, JNI_ABORT);
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_rmichels_phasegame_audio_NativeAudioEngine_nativeRegisterPcm16(
    JNIEnv* env,
    jobject,
    jlong handle,
    jint sampleId,
    jint sampleRate,
    jshortArray samples
) {
    const jsize size = env->GetArrayLength(samples);
    jshort* data = env->GetShortArrayElements(samples, nullptr);
    const bool result = engine(handle)->registerPcm16(
        sampleId,
        sampleRate,
        reinterpret_cast<int16_t*>(data),
        static_cast<size_t>(size)
    );
    env->ReleaseShortArrayElements(samples, data, JNI_ABORT);
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_rmichels_phasegame_audio_NativeAudioEngine_nativeStart(
    JNIEnv*,
    jobject,
    jlong handle
) {
    return engine(handle)->start();
}

extern "C" JNIEXPORT void JNICALL
Java_com_rmichels_phasegame_audio_NativeAudioEngine_nativeStop(
    JNIEnv*,
    jobject,
    jlong handle
) {
    engine(handle)->stop();
}

extern "C" JNIEXPORT void JNICALL
Java_com_rmichels_phasegame_audio_NativeAudioEngine_nativeCancelSession(
    JNIEnv*,
    jobject,
    jlong handle,
    jlong sessionId
) {
    engine(handle)->cancelSession(sessionId);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_rmichels_phasegame_audio_NativeAudioEngine_nativeSchedule(
    JNIEnv*,
    jobject,
    jlong handle,
    jint sampleId,
    jlong targetNanos,
    jint bus,
    jlong sessionId,
    jfloat gain,
    jfloat pan,
    jfloat playbackRate,
    jfloat attackMs,
    jfloat releaseMs,
    jint priority
) {
    AudioCommand command{};
    command.type = AudioCommand::Type::Play;
    command.sampleId = sampleId;
    command.targetNanos = targetNanos;
    command.bus = bus;
    command.sessionId = sessionId;
    command.gain = gain;
    command.pan = pan;
    command.playbackRate = playbackRate;
    command.attackMs = attackMs;
    command.releaseMs = releaseMs;
    command.priority = priority;
    return engine(handle)->schedule(command);
}

extern "C" JNIEXPORT void JNICALL
Java_com_rmichels_phasegame_audio_NativeAudioEngine_nativeSetBusGain(
    JNIEnv*,
    jobject,
    jlong handle,
    jint bus,
    jfloat gain
) {
    engine(handle)->setBusGain(bus, gain);
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_rmichels_phasegame_audio_NativeAudioEngine_nativeDiagnostics(
    JNIEnv* env,
    jobject,
    jlong handle
) {
    const auto values = engine(handle)->diagnostics();
    jlongArray result = env->NewLongArray(values.size());
    env->SetLongArrayRegion(result, 0, values.size(), values.data());
    return result;
}
