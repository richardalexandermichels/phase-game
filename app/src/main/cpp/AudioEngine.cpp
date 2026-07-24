#include "AudioEngine.h"

#include <algorithm>
#include <cmath>
#include <cstring>
#include <ctime>
#include <limits>

namespace phaseaudio {

namespace {
constexpr int64_t kLateDropNanos = 40'000'000;

uint16_t readU16(const uint8_t* data) {
    return static_cast<uint16_t>(data[0]) |
           (static_cast<uint16_t>(data[1]) << 8);
}

uint32_t readU32(const uint8_t* data) {
    return static_cast<uint32_t>(data[0]) |
           (static_cast<uint32_t>(data[1]) << 8) |
           (static_cast<uint32_t>(data[2]) << 16) |
           (static_cast<uint32_t>(data[3]) << 24);
}
}  // namespace

template <size_t Capacity>
CommandQueue<Capacity>::CommandQueue() {
    for (size_t i = 0; i < Capacity; ++i) cells_[i].sequence.store(i);
}

template <size_t Capacity>
bool CommandQueue<Capacity>::enqueue(const AudioCommand& command) {
    size_t position = enqueuePosition_.load(std::memory_order_relaxed);
    for (;;) {
        Cell& cell = cells_[position % Capacity];
        const size_t sequence = cell.sequence.load(std::memory_order_acquire);
        const intptr_t difference =
            static_cast<intptr_t>(sequence) - static_cast<intptr_t>(position);
        if (difference == 0) {
            if (enqueuePosition_.compare_exchange_weak(
                    position, position + 1, std::memory_order_relaxed)) {
                cell.command = command;
                cell.sequence.store(position + 1, std::memory_order_release);
                return true;
            }
        } else if (difference < 0) {
            return false;
        } else {
            position = enqueuePosition_.load(std::memory_order_relaxed);
        }
    }
}

template <size_t Capacity>
bool CommandQueue<Capacity>::dequeue(AudioCommand& command) {
    Cell& cell = cells_[dequeuePosition_ % Capacity];
    const size_t sequence = cell.sequence.load(std::memory_order_acquire);
    const intptr_t difference = static_cast<intptr_t>(sequence) -
                                static_cast<intptr_t>(dequeuePosition_ + 1);
    if (difference != 0) return false;
    command = cell.command;
    cell.sequence.store(
        dequeuePosition_ + Capacity,
        std::memory_order_release
    );
    ++dequeuePosition_;
    return true;
}

template class CommandQueue<kCommandQueueSize>;

AudioEngine::AudioEngine() = default;
AudioEngine::~AudioEngine() { stop(); }

bool AudioEngine::registerWav(
    int32_t sampleId,
    const uint8_t* bytes,
    size_t size
) {
    if (running_.load() || sampleId <= 0 || sampleId >= kMaxSamples ||
        bytes == nullptr || size < 44 ||
        std::memcmp(bytes, "RIFF", 4) != 0 ||
        std::memcmp(bytes + 8, "WAVE", 4) != 0) {
        return false;
    }

    uint16_t format = 0;
    uint16_t channels = 0;
    uint16_t bits = 0;
    uint32_t sampleRate = 0;
    const uint8_t* pcm = nullptr;
    size_t pcmBytes = 0;
    size_t cursor = 12;
    while (cursor + 8 <= size) {
        const uint8_t* chunk = bytes + cursor;
        const uint32_t chunkSize = readU32(chunk + 4);
        const size_t dataStart = cursor + 8;
        if (dataStart + chunkSize > size) return false;
        if (std::memcmp(chunk, "fmt ", 4) == 0 && chunkSize >= 16) {
            format = readU16(bytes + dataStart);
            channels = readU16(bytes + dataStart + 2);
            sampleRate = readU32(bytes + dataStart + 4);
            bits = readU16(bytes + dataStart + 14);
        } else if (std::memcmp(chunk, "data", 4) == 0) {
            pcm = bytes + dataStart;
            pcmBytes = chunkSize;
        }
        cursor = dataStart + chunkSize + (chunkSize & 1U);
    }
    if (format != 1 || channels != 1 || bits != 16 || sampleRate == 0 ||
        pcm == nullptr || pcmBytes % 2 != 0) {
        return false;
    }

    std::vector<int16_t> decoded(pcmBytes / 2);
    for (size_t i = 0; i < decoded.size(); ++i) {
        decoded[i] = static_cast<int16_t>(readU16(pcm + i * 2));
    }
    return registerPcm16(
        sampleId,
        static_cast<int32_t>(sampleRate),
        decoded.data(),
        decoded.size()
    );
}

bool AudioEngine::registerPcm16(
    int32_t sampleId,
    int32_t sampleRate,
    const int16_t* samples,
    size_t sampleCount
) {
    if (running_.load() || sampleId <= 0 || sampleId >= kMaxSamples ||
        sampleRate <= 0 || samples == nullptr || sampleCount == 0) {
        return false;
    }
    Sample& destination = samples_[sampleId];
    destination.sampleRate = sampleRate;
    destination.frames.resize(sampleCount);
    std::transform(
        samples,
        samples + sampleCount,
        destination.frames.begin(),
        [](int16_t value) { return value / 32768.0f; }
    );
    return true;
}

bool AudioEngine::openStream() {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setUsage(oboe::Usage::Game)
        ->setContentType(oboe::ContentType::Sonification)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(2)
        ->setDataCallback(this)
        ->setErrorCallback(this);

    if (builder.openStream(stream_) != oboe::Result::OK || !stream_) {
        return false;
    }
    sampleRate_ = stream_->getSampleRate();
    channelCount_ = stream_->getChannelCount();
    framesPerBurst_ = stream_->getFramesPerBurst();
    const auto sizeResult = stream_->setBufferSizeInFrames(framesPerBurst_ * 2);
    bufferSizeFrames_ = sizeResult ? sizeResult.value() :
        stream_->getBufferSizeInFrames();
    return sampleRate_ > 0 && channelCount_ > 0;
}

bool AudioEngine::start() {
    if (running_.load()) return true;
    if (!stream_ && !openStream()) return false;
    clearRuntimeState();
    running_.store(true);
    const oboe::Result result = stream_->requestStart();
    if (result != oboe::Result::OK) running_.store(false);
    return result == oboe::Result::OK;
}

void AudioEngine::stop() {
    running_.store(false);
    if (stream_) {
        stream_->requestStop();
        stream_->close();
        stream_.reset();
    }
    clearRuntimeState();
    AudioCommand discarded;
    while (commands_.dequeue(discarded)) {
    }
}

bool AudioEngine::schedule(const AudioCommand& command) {
    if (!commands_.enqueue(command)) {
        droppedCommands_.fetch_add(1);
        return false;
    }
    return true;
}

void AudioEngine::cancelSession(int64_t sessionId) {
    AudioCommand command{};
    command.type = AudioCommand::Type::CancelSession;
    command.sessionId = sessionId;
    schedule(command);
}

void AudioEngine::setBusGain(int32_t bus, float gain) {
    AudioCommand command{};
    command.type = AudioCommand::Type::SetBusGain;
    command.bus = bus;
    command.gain = std::max(0.0f, gain);
    schedule(command);
}

void AudioEngine::clearRuntimeState() {
    scheduledCount_ = 0;
    canceledSessionCount_ = 0;
    canceledSessionCursor_ = 0;
    for (Voice& voice : voices_) voice.active = false;
    activeVoiceCount_.store(0);
}

bool AudioEngine::isSessionCanceled(int64_t sessionId) const {
    return std::find(
        canceledSessions_.begin(),
        canceledSessions_.begin() +
            static_cast<std::ptrdiff_t>(canceledSessionCount_),
        sessionId
    ) != canceledSessions_.begin() +
        static_cast<std::ptrdiff_t>(canceledSessionCount_);
}

void AudioEngine::drainCommands() {
    AudioCommand command;
    while (commands_.dequeue(command)) {
        if (command.type == AudioCommand::Type::SetBusGain) {
            if (command.bus < 0) {
                masterGain_ = command.gain;
            } else if (command.bus < static_cast<int32_t>(busGains_.size())) {
                busGains_[command.bus] = command.gain;
            }
        } else if (command.type == AudioCommand::Type::CancelSession) {
            canceledSessions_[canceledSessionCursor_] = command.sessionId;
            canceledSessionCursor_ =
                (canceledSessionCursor_ + 1) % canceledSessions_.size();
            canceledSessionCount_ = std::min(
                canceledSessionCount_ + 1,
                canceledSessions_.size()
            );
            for (size_t i = 0; i < scheduledCount_;) {
                if (scheduled_[i].sessionId == command.sessionId) {
                    scheduled_[i] = scheduled_[--scheduledCount_];
                } else {
                    ++i;
                }
            }
            for (Voice& voice : voices_) {
                if (voice.active && voice.sessionId == command.sessionId) {
                    voice.active = false;
                }
            }
        } else if (
            !isSessionCanceled(command.sessionId) &&
            scheduledCount_ < scheduled_.size()
        ) {
            scheduled_[scheduledCount_++] = command;
        } else {
            droppedCommands_.fetch_add(1);
        }
    }
}

Voice& AudioEngine::selectVoice(int32_t priority) {
    for (Voice& voice : voices_) if (!voice.active) return voice;
    return *std::min_element(
        voices_.begin(),
        voices_.end(),
        [priority](const Voice& left, const Voice& right) {
            const bool leftProtected = left.priority > priority;
            const bool rightProtected = right.priority > priority;
            if (leftProtected != rightProtected) return !leftProtected;
            if (left.priority != right.priority) {
                return left.priority < right.priority;
            }
            return left.age < right.age;
        }
    );
}

void AudioEngine::startVoice(
    const AudioCommand& command,
    int32_t frameOffset
) {
    if (command.sampleId <= 0 || command.sampleId >= kMaxSamples) return;
    const Sample& sample = samples_[command.sampleId];
    if (sample.frames.empty()) return;
    Voice& voice = selectVoice(command.priority);
    const float pan = std::clamp(command.pan, -1.0f, 1.0f);
    voice.sample = &sample;
    voice.sourcePosition = -static_cast<double>(frameOffset) *
                           sample.sampleRate * command.playbackRate /
                           sampleRate_;
    voice.sourceIncrement =
        static_cast<double>(sample.sampleRate) * command.playbackRate /
        sampleRate_;
    voice.sessionId = command.sessionId;
    voice.priority = command.priority;
    voice.bus = command.bus;
    voice.age = nextVoiceAge_++;
    voice.leftGain = command.gain * (pan <= 0.0f ? 1.0f : 1.0f - pan);
    voice.rightGain = command.gain * (pan >= 0.0f ? 1.0f : 1.0f + pan);
    voice.attackFrames =
        static_cast<int32_t>(command.attackMs * sampleRate_ / 1000.0f);
    voice.releaseFrames =
        static_cast<int32_t>(command.releaseMs * sampleRate_ / 1000.0f);
    voice.active = true;
}

int64_t AudioEngine::bootTimeNanos() {
    timespec time{};
    clock_gettime(CLOCK_BOOTTIME, &time);
    return static_cast<int64_t>(time.tv_sec) * 1'000'000'000LL + time.tv_nsec;
}

oboe::DataCallbackResult AudioEngine::onAudioReady(
    oboe::AudioStream*,
    void* audioData,
    int32_t numFrames
) {
    auto* output = static_cast<float*>(audioData);
    std::fill(output, output + numFrames * channelCount_, 0.0f);
    if (!running_.load()) return oboe::DataCallbackResult::Continue;

    drainCommands();
    const int64_t blockStart = bootTimeNanos();
    const int64_t blockDuration =
        static_cast<int64_t>(numFrames) * 1'000'000'000LL / sampleRate_;
    const int64_t blockEnd = blockStart + blockDuration;
    for (size_t i = 0; i < scheduledCount_;) {
        const AudioCommand command = scheduled_[i];
        if (command.targetNanos <= blockEnd) {
            if (command.targetNanos < blockStart - kLateDropNanos) {
                lateEvents_.fetch_add(1);
            } else {
                const int32_t offset = static_cast<int32_t>(std::clamp<int64_t>(
                    (command.targetNanos - blockStart) * sampleRate_ /
                        1'000'000'000LL,
                    0,
                    numFrames - 1
                ));
                startVoice(command, offset);
            }
            scheduled_[i] = scheduled_[--scheduledCount_];
        } else {
            ++i;
        }
    }

    int32_t active = 0;
    for (Voice& voice : voices_) {
        if (!voice.active || voice.sample == nullptr) continue;
        const Sample& sample = *voice.sample;
        for (int32_t frame = 0; frame < numFrames; ++frame) {
            if (voice.sourcePosition < 0.0) {
                voice.sourcePosition += voice.sourceIncrement;
                continue;
            }
            const size_t index = static_cast<size_t>(voice.sourcePosition);
            if (index >= sample.frames.size()) {
                voice.active = false;
                break;
            }
            const size_t next = std::min(index + 1, sample.frames.size() - 1);
            const float fraction =
                static_cast<float>(voice.sourcePosition - index);
            float value = sample.frames[index] +
                (sample.frames[next] - sample.frames[index]) * fraction;
            if (voice.attackFrames > 0 &&
                voice.sourcePosition < voice.attackFrames) {
                value *= static_cast<float>(
                    voice.sourcePosition / voice.attackFrames
                );
            }
            if (voice.releaseFrames > 0) {
                const double remaining =
                    sample.frames.size() - voice.sourcePosition;
                if (remaining < voice.releaseFrames) {
                    value *= static_cast<float>(remaining / voice.releaseFrames);
                }
            }
            const float busGain =
                voice.bus >= 0 &&
                voice.bus < static_cast<int32_t>(busGains_.size())
                    ? busGains_[voice.bus]
                    : 1.0f;
            value *= busGain * masterGain_;
            output[frame * channelCount_] += value * voice.leftGain;
            if (channelCount_ > 1) {
                output[frame * channelCount_ + 1] += value * voice.rightGain;
            }
            voice.sourcePosition += voice.sourceIncrement;
        }
        if (voice.active) ++active;
    }
    for (int32_t i = 0; i < numFrames * channelCount_; ++i) {
        output[i] = std::tanh(output[i]);
    }
    activeVoiceCount_.store(active);
    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream*, oboe::Result) {
    const bool shouldRestart = running_.exchange(false);
    stream_.reset();
    restartRequested_.store(shouldRestart);
    if (shouldRestart) {
        restartRequested_.store(false);
        start();
    }
}

std::array<int64_t, 12> AudioEngine::diagnostics() const {
    int32_t xRuns = 0;
    int64_t latencyMicros = -1;
    if (stream_) {
        const auto result = stream_->getXRunCount();
        if (result) xRuns = result.value();
        const auto latency = stream_->calculateLatencyMillis();
        if (latency) {
            latencyMicros =
                static_cast<int64_t>(latency.value() * 1'000.0);
        }
    }
    return {
        sampleRate_,
        channelCount_,
        framesPerBurst_,
        bufferSizeFrames_,
        stream_ ? static_cast<int64_t>(stream_->getAudioApi()) : 0,
        stream_ ? static_cast<int64_t>(stream_->getSharingMode()) : 0,
        stream_ ? static_cast<int64_t>(stream_->getPerformanceMode()) : 0,
        latencyMicros,
        xRuns,
        activeVoiceCount_.load(),
        droppedCommands_.load(),
        lateEvents_.load()
    };
}

}  // namespace phaseaudio
