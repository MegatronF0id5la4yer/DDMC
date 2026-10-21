#define MINIAUDIO_IMPLEMENTATION
#define MA_NO_ENCODING
#include "miniaudio.h"

#include "ddmc_audio.hpp"
#include "ddmc_fft.hpp"
#include <iostream>
#include <algorithm>
#include <filesystem>

namespace ddmc {

static void maDataCallback(ma_device* pDevice, void* pOutput, const void* pInput, ma_uint32 frameCount) {
    AudioEngine* engine = static_cast<AudioEngine*>(pDevice->pUserData);
    if (engine) {
        engine->onAudioFrames(pOutput, pInput, frameCount);
    }
}

AudioEngine::AudioEngine() {
    m_decoder = new ma_decoder();
    m_device = new ma_device();
    std::memset(m_decoder, 0, sizeof(ma_decoder));
    std::memset(m_device, 0, sizeof(ma_device));
}

AudioEngine::~AudioEngine() {
    shutdown();
    delete m_decoder;
    delete m_device;
}

bool AudioEngine::init() {
    ma_device_config config = ma_device_config_init(ma_device_type_playback);
    config.playback.format = ma_format_f32;
    config.playback.channels = 2;
    config.sampleRate = 44100;
    config.dataCallback = maDataCallback;
    config.pUserData = this;

    if (ma_device_init(nullptr, &config, m_device) != MA_SUCCESS) {
        return false;
    }

    return true;
}

void AudioEngine::shutdown() {
    stop();
    if (m_isLoaded) {
        ma_decoder_uninit(m_decoder);
        m_isLoaded = false;
    }
    if (m_device->state != ma_device_state_uninitialized) {
        ma_device_uninit(m_device);
    }
}

bool AudioEngine::loadFile(const std::string& filePath) {
    stop();
    if (m_isLoaded) {
        ma_decoder_uninit(m_decoder);
        m_isLoaded = false;
    }

    ma_decoder_config config = ma_decoder_config_init(ma_format_f32, 2, 44100);
    if (ma_decoder_init_file(filePath.c_str(), &config, m_decoder) != MA_SUCCESS) {
        return false;
    }

    m_currentFilePath = filePath;
    m_isLoaded = true;

    ma_uint64 lengthFrames = 0;
    ma_decoder_get_length_in_pcm_frames(m_decoder, &lengthFrames);

    m_trackInfo.filePath = filePath;
    m_trackInfo.sampleRate = m_decoder->outputSampleRate;
    m_trackInfo.channels = m_decoder->outputChannels;
    m_trackInfo.durationSec = static_cast<double>(lengthFrames) / static_cast<double>(m_trackInfo.sampleRate);

    // Extract file stem as fallback title
    try {
        std::filesystem::path p(filePath);
        m_trackInfo.title = p.stem().string();
        m_trackInfo.artist = "Local Audio";
        m_trackInfo.album = p.parent_path().filename().string();
    } catch (...) {
        m_trackInfo.title = filePath;
    }

    return true;
}

void AudioEngine::play() {
    if (!m_isLoaded) return;
    if (m_device->state == ma_device_state_stopped) {
        ma_device_start(m_device);
    }
    m_isPlaying = true;
}

void AudioEngine::pause() {
    if (!m_isPlaying) return;
    if (m_device->state == ma_device_state_started) {
        ma_device_stop(m_device);
    }
    m_isPlaying = false;
}

void AudioEngine::togglePlayPause() {
    if (m_isPlaying) pause();
    else play();
}

void AudioEngine::stop() {
    if (m_device->state == ma_device_state_started) {
        ma_device_stop(m_device);
    }
    if (m_isLoaded) {
        ma_decoder_seek_to_pcm_frame(m_decoder, 0);
    }
    m_isPlaying = false;
}

bool AudioEngine::isPlaying() const {
    return m_isPlaying;
}

bool AudioEngine::isLoaded() const {
    return m_isLoaded;
}

void AudioEngine::setVolume(float volume) {
    m_volume = std::max(0.0f, std::min(volume, 2.0f));
    if (m_device->state != ma_device_state_uninitialized) {
        ma_device_set_master_volume(m_device, m_volume);
    }
}

float AudioEngine::getVolume() const {
    return m_volume;
}

void AudioEngine::seek(double positionSec) {
    if (!m_isLoaded) return;
    ma_uint64 targetFrame = static_cast<ma_uint64>(std::max(0.0, positionSec) * m_trackInfo.sampleRate);
    ma_decoder_seek_to_pcm_frame(m_decoder, targetFrame);
}

void AudioEngine::seekRelative(double deltaSec) {
    if (!m_isLoaded) return;
    double current = getPositionSec();
    seek(std::max(0.0, std::min(current + deltaSec, getDurationSec())));
}

double AudioEngine::getPositionSec() const {
    if (!m_isLoaded) return 0.0;
    ma_uint64 cursorFrame = 0;
    ma_decoder_get_cursor_in_pcm_frames(m_decoder, &cursorFrame);
    return static_cast<double>(cursorFrame) / static_cast<double>(m_trackInfo.sampleRate);
}

double AudioEngine::getDurationSec() const {
    return m_trackInfo.durationSec;
}

void AudioEngine::attachFftAnalyzer(FftAnalyzer* analyzer) {
    m_fftAnalyzer = analyzer;
}

void AudioEngine::onAudioFrames(void* pOutput, const void* pInput, uint32_t frameCount) {
    (void)pInput;
    if (!m_isLoaded || !m_isPlaying) {
        std::memset(pOutput, 0, frameCount * 2 * sizeof(float));
        return;
    }

    ma_uint64 framesRead = 0;
    ma_decoder_read_pcm_frames(m_decoder, pOutput, frameCount, &framesRead);

    if (framesRead < frameCount) {
        // End of stream
        float* outFloats = static_cast<float*>(pOutput);
        std::memset(outFloats + framesRead * 2, 0, (frameCount - framesRead) * 2 * sizeof(float));
        m_isPlaying = false;
    }

    // Feed samples to FFT analyzer
    if (m_fftAnalyzer && framesRead > 0) {
        m_fftAnalyzer->feedAudioSamples(static_cast<const float*>(pOutput), framesRead * 2, 2);
    }
}

} // namespace ddmc
