#pragma once

#include <array>
#include <atomic>
#include <cstdint>
#include <memory>
#include <vector>

#include <oboe/Oboe.h>

namespace phaseaudio {

constexpr int kMaxSamples = 64;
constexpr int kMaxVoices = 48;
constexpr int kMaxScheduledEvents = 2048;
constexpr int kCommandQueueSize = 2048;
constexpr int kCanceledSessionHistory = 256;

struct AudioCommand {
    enum class Type : int32_t { Play, CancelSession, SetBusGain };
    Type type = Type::Play;
    int32_t sampleId = 0;
    int64_t targetNanos = 0;
    int32_t bus = 0;
    int64_t sessionId = 0;
    float gain = 1.0f;
    float pan = 0.0f;
    float playbackRate = 1.0f;
    float attackMs = 0.0f;
    float releaseMs = 0.0f;
    int32_t priority = 0;
};

template <size_t Capacity>
class CommandQueue {
public:
    CommandQueue();
    bool enqueue(const AudioCommand& command);
    bool dequeue(AudioCommand& command);

private:
    struct Cell {
        std::atomic<size_t> sequence{};
        AudioCommand command{};
    };
    std::array<Cell, Capacity> cells_{};
    std::atomic<size_t> enqueuePosition_{0};
    size_t dequeuePosition_ = 0;
};

struct Sample {
    std::vector<float> frames;
    int32_t sampleRate = 0;
};

struct Voice {
    const Sample* sample = nullptr;
    double sourcePosition = 0.0;
    double sourceIncrement = 1.0;
    int64_t sessionId = 0;
    int32_t priority = 0;
    int32_t bus = 0;
    uint64_t age = 0;
    float leftGain = 1.0f;
    float rightGain = 1.0f;
    int32_t attackFrames = 0;
    int32_t releaseFrames = 0;
    bool active = false;
};

class AudioEngine final : public oboe::AudioStreamDataCallback,
                          public oboe::AudioStreamErrorCallback {
public:
    AudioEngine();
    ~AudioEngine() override;

    bool registerWav(int32_t sampleId, const uint8_t* bytes, size_t size);
    bool registerPcm16(
        int32_t sampleId,
        int32_t sampleRate,
        const int16_t* samples,
        size_t sampleCount
    );
    bool start();
    void stop();
    bool schedule(const AudioCommand& command);
    void cancelSession(int64_t sessionId);
    void setBusGain(int32_t bus, float gain);
    std::array<int64_t, 12> diagnostics() const;

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* stream,
        void* audioData,
        int32_t numFrames
    ) override;
    void onErrorAfterClose(oboe::AudioStream*, oboe::Result error) override;

private:
    bool openStream();
    void drainCommands();
    void startVoice(const AudioCommand& command, int32_t frameOffset);
    Voice& selectVoice(int32_t priority);
    void clearRuntimeState();
    bool isSessionCanceled(int64_t sessionId) const;
    static int64_t bootTimeNanos();

    std::shared_ptr<oboe::AudioStream> stream_;
    std::array<Sample, kMaxSamples> samples_{};
    CommandQueue<kCommandQueueSize> commands_;
    std::array<AudioCommand, kMaxScheduledEvents> scheduled_{};
    size_t scheduledCount_ = 0;
    std::array<Voice, kMaxVoices> voices_{};
    std::array<int64_t, kCanceledSessionHistory> canceledSessions_{};
    size_t canceledSessionCount_ = 0;
    size_t canceledSessionCursor_ = 0;
    std::array<float, 4> busGains_{1.0f, 1.0f, 1.0f, 1.0f};
    float masterGain_ = 1.0f;
    std::atomic<bool> running_{false};
    std::atomic<bool> restartRequested_{false};
    std::atomic<int64_t> droppedCommands_{0};
    std::atomic<int64_t> lateEvents_{0};
    std::atomic<int32_t> activeVoiceCount_{0};
    uint64_t nextVoiceAge_ = 1;
    int32_t sampleRate_ = 0;
    int32_t channelCount_ = 0;
    int32_t framesPerBurst_ = 0;
    int32_t bufferSizeFrames_ = 0;
};

}  // namespace phaseaudio
