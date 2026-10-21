#pragma once
#include "ddmc_types.hpp"
#include <string>
#include <functional>
#include <atomic>

// Forward declaration of miniaudio types to keep header light
struct ma_decoder;
struct ma_device;

namespace ddmc {

class FftAnalyzer;

class AudioEngine {
public:
    AudioEngine();
    ~AudioEngine();

    bool init();
    void shutdown();

    bool loadFile(const std::string& filePath);
    void play();
    void pause();
    void togglePlayPause();
    void stop();

    bool isPlaying() const;
    bool isLoaded() const;

    void setVolume(float volume); // 0.0f to 2.0f
    float getVolume() const;

    void seek(double positionSec);
    void seekRelative(double deltaSec);

    double getPositionSec() const;
    double getDurationSec() const;

    const TrackInfo& getTrackInfo() const { return m_trackInfo; }

    void attachFftAnalyzer(FftAnalyzer* analyzer);

    // Internal audio callback from miniaudio
    void onAudioFrames(void* pOutput, const void* pInput, uint32_t frameCount);

private:
    ma_decoder* m_decoder = nullptr;
    ma_device* m_device = nullptr;
    FftAnalyzer* m_fftAnalyzer = nullptr;

    std::atomic<bool> m_isLoaded{false};
    std::atomic<bool> m_isPlaying{false};
    std::atomic<float> m_volume{1.0f};

    TrackInfo m_trackInfo;
    std::string m_currentFilePath;
};

} // namespace ddmc
