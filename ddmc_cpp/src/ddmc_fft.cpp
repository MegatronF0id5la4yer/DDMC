#include "ddmc_fft.hpp"
#include <cmath>
#include <algorithm>
#include <cstring>

namespace ddmc {

static constexpr float PI = 3.14159265358979323846f;

FftAnalyzer::FftAnalyzer()
    : m_sampleRing(FFT_SIZE, 0.0f),
      m_windowedBuffer(FFT_SIZE, 0.0f),
      m_fftComplex(FFT_SIZE, std::complex<float>(0.0f, 0.0f)) {
    setPerfMode(MODE_BALANCED);
}

void FftAnalyzer::setPerfMode(PerfMode mode) {
    m_perfMode = mode;
    m_data.bandCount = (mode == MODE_POTATO) ? 22 : 44;
}

void FftAnalyzer::feedAudioSamples(const float* samples, size_t sampleCount, uint32_t channels) {
    if (!samples || sampleCount == 0 || channels == 0) return;

    std::lock_guard<std::mutex> lock(m_mutex);
    for (size_t i = 0; i < sampleCount; i += channels) {
        float mono = 0.0f;
        for (uint32_t c = 0; c < channels; ++c) {
            mono += samples[i + c];
        }
        mono /= static_cast<float>(channels);

        m_sampleRing[m_ringWritePos] = mono;
        m_ringWritePos = (m_ringWritePos + 1) % FFT_SIZE;
    }
}

void FftAnalyzer::applyHannWindow(std::vector<float>& buffer) {
    const size_t n = buffer.size();
    for (size_t i = 0; i < n; ++i) {
        float multiplier = 0.5f * (1.0f - std::cos(2.0f * PI * static_cast<float>(i) / static_cast<float>(n - 1)));
        buffer[i] *= multiplier;
    }
}

void FftAnalyzer::computeFft(const std::vector<float>& input, std::vector<std::complex<float>>& output) {
    size_t n = input.size();
    for (size_t i = 0; i < n; ++i) {
        output[i] = std::complex<float>(input[i], 0.0f);
    }

    // Bit-reversal permutation
    size_t j = 0;
    for (size_t i = 0; i < n - 1; ++i) {
        if (i < j) {
            std::swap(output[i], output[j]);
        }
        size_t k = n / 2;
        while (k <= j) {
            j -= k;
            k /= 2;
        }
        j += k;
    }

    // Cooley-Tukey Radix-2 iterative FFT
    for (size_t len = 2; len <= n; len <<= 1) {
        float angle = -2.0f * PI / static_cast<float>(len);
        std::complex<float> wlen(std::cos(angle), std::sin(angle));
        for (size_t i = 0; i < n; i += len) {
            std::complex<float> w(1.0f, 0.0f);
            for (size_t k = 0; k < len / 2; ++k) {
                std::complex<float> u = output[i + k];
                std::complex<float> v = output[i + k + len / 2] * w;
                output[i + k] = u + v;
                output[i + k + len / 2] = u - v;
                w *= wlen;
            }
        }
    }
}

void FftAnalyzer::process(float dtSec) {
    // Unroll circular buffer into contiguous windowed buffer
    {
        std::lock_guard<std::mutex> lock(m_mutex);
        for (size_t i = 0; i < FFT_SIZE; ++i) {
            size_t readIdx = (m_ringWritePos + i) % FFT_SIZE;
            m_windowedBuffer[i] = m_sampleRing[readIdx];
        }
    }

    // Capture 128 points for oscilloscope display
    for (size_t i = 0; i < 128; ++i) {
        size_t idx = (i * FFT_SIZE) / 128;
        m_data.waveform[i] = m_windowedBuffer[idx];
    }

    applyHannWindow(m_windowedBuffer);
    computeFft(m_windowedBuffer, m_fftComplex);

    // Compute raw magnitudes
    for (size_t i = 0; i < HALF_FFT; ++i) {
        m_rawMagnitudes[i] = std::abs(m_fftComplex[i]) / static_cast<float>(HALF_FFT);
    }

    // Calculate logarithmic frequency bands matching Android VisualizerView
    const int bands = m_data.bandCount;
    float bassSum = 0.0f;
    float vocalSum = 0.0f;

    for (int i = 0; i < bands; ++i) {
        float ratio = static_cast<float>(i) / static_cast<float>(bands);
        int binStart = 1 + static_cast<int>(ratio * ratio * (HALF_FFT * 0.65f));
        binStart = std::max(1, std::min(binStart, static_cast<int>(HALF_FFT) - 2));
        int binEnd = std::min(binStart + 2, static_cast<int>(HALF_FFT) - 1);

        float sum = 0.0f;
        int count = 0;
        for (int b = binStart; b <= binEnd; ++b) {
            sum += m_rawMagnitudes[b];
            count++;
        }
        float avg = (count > 0) ? (sum / static_cast<float>(count)) : 0.0f;

        // Logarithmic dB boost
        float boost = 1.0f + ratio * 1.35f;
        float targetMag = std::log10(1.0f + avg * boost * 28.0f) / 1.5f;
        targetMag = std::max(0.04f, std::min(targetMag, 1.0f));

        // Attack & decay smoothing
        float prev = m_data.magnitudes[i];
        if (targetMag > prev) {
            m_data.magnitudes[i] = prev * 0.35f + targetMag * 0.65f;
        } else {
            m_data.magnitudes[i] = prev * 0.78f + targetMag * 0.22f;
        }

        // Peak cap physics (floating cap with gravity drop)
        if (m_data.magnitudes[i] >= m_data.peakCaps[i]) {
            m_data.peakCaps[i] = m_data.magnitudes[i];
        } else {
            m_data.peakCaps[i] = std::max(0.04f, m_data.peakCaps[i] - 0.015f);
        }

        if (i < 4) bassSum += m_data.magnitudes[i];
        if (i >= 3 && i <= 10) vocalSum += m_data.magnitudes[i];
    }

    m_data.bassEnergy = bassSum / 4.0f;
    m_data.vocalEnergy = vocalSum / 8.0f;

    // Dynamic beat detector with rolling variance
    float avgBeat = 0.0f;
    for (int k = 0; k < 16; ++k) avgBeat += m_beatHistory[k];
    avgBeat /= 16.0f;

    m_data.isBeat = (m_data.bassEnergy > avgBeat * 1.4f) && (m_data.bassEnergy > 0.35f);
    m_beatHistory[m_beatHistIdx] = m_data.bassEnergy;
    m_beatHistIdx = (m_beatHistIdx + 1) % 16;
}

} // namespace ddmc
