#pragma once
#include "ddmc_types.hpp"
#include <vector>
#include <complex>

namespace ddmc {

class FftAnalyzer {
public:
    FftAnalyzer();
    ~FftAnalyzer() = default;

    void setPerfMode(PerfMode mode);
    PerfMode getPerfMode() const { return m_perfMode; }

    // Feeds PCM samples (mono or stereo interleaved float buffer, [-1.0f, 1.0f])
    void feedAudioSamples(const float* samples, size_t sampleCount, uint32_t channels);

    // Computes FFT and updates spectrum bands
    void process(float dtSec);

    // Retrieves current spectrum snapshot
    const SpectrumData& getSpectrum() const { return m_data; }

private:
    void computeFft(const std::vector<float>& input, std::vector<std::complex<float>>& output);
    void applyHannWindow(std::vector<float>& buffer);

    PerfMode m_perfMode = MODE_BALANCED;
    SpectrumData m_data;

    static constexpr size_t FFT_SIZE = 1024;
    static constexpr size_t HALF_FFT = FFT_SIZE / 2;

    std::vector<float> m_sampleRing;
    size_t m_ringWritePos = 0;
    std::vector<float> m_windowedBuffer;
    std::vector<std::complex<float>> m_fftComplex;
    float m_rawMagnitudes[HALF_FFT] = {0.0f};

    float m_beatHistory[16] = {0.0f};
    int m_beatHistIdx = 0;
};

} // namespace ddmc
