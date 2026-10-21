#pragma once
#include <string>
#include <vector>
#include <cstdint>

namespace ddmc {

enum PerfMode {
    MODE_POTATO = 0,   // 30 FPS, 22 FFT bands, minimal CPU and RAM footprint
    MODE_BALANCED = 1, // 60 FPS, 44 FFT bands, smooth animation
    MODE_ULTRA = 2     // 120 FPS, 44 FFT bands, maximum fidelity
};

enum VisualizerMode {
    VIZ_MIRROR_SPECTRUM = 0,
    VIZ_DUAL_OSCILLOSCOPE = 1,
    VIZ_RADIAL_PULSE = 2,
    VIZ_HYBRID_STUDIO = 3
};

enum CompanionState {
    COMPANION_STAND,
    COMPANION_WALK,
    COMPANION_RUN,
    COMPANION_SIT,
    COMPANION_HAPPY_JUMP,
    COMPANION_DANCE,
    COMPANION_CLIMB
};

enum DokiCharacter {
    DOKI_NATSUKI = 0,
    DOKI_SAYORI = 1,
    DOKI_YURI = 2,
    DOKI_MONIKA = 3,
    DOKI_BOCCHI = 4
};

struct TrackInfo {
    std::string filePath;
    std::string title;
    std::string artist;
    std::string album;
    double durationSec = 0.0;
    uint32_t sampleRate = 44100;
    uint32_t channels = 2;
};

struct LyricLine {
    double timestampSec = 0.0;
    std::string text;
};

struct SpectrumData {
    int bandCount = 44;
    float magnitudes[44] = {0.0f};
    float peakCaps[44] = {0.0f};
    float waveform[128] = {0.0f};
    float vocalEnergy = 0.0f;
    float bassEnergy = 0.0f;
    bool isBeat = false;
};

} // namespace ddmc
