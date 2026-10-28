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
    // Unroll circular buffer into contiguous windowed buffer & remove DC offset
    float mean = 0.0f;
    {
        std::lock_guard<std::mutex> lock(m_mutex);
        for (size_t i = 0; i < FFT_SIZE; ++i) {
            size_t readIdx = (m_ringWritePos + i) % FFT_SIZE;
            float s = m_sampleRing[readIdx];
            m_windowedBuffer[i] = s;
            mean += s;
        }
    }
    mean /= static_cast<float>(FFT_SIZE);

    for (size_t i = 0; i < FFT_SIZE; ++i) {
        m_windowedBuffer[i] -= mean;
    }

    // Capture 128 points for oscilloscope display with adaptive gain
    for (size_t i = 0; i < 128; ++i) {
        size_t idx = (i * FFT_SIZE) / 128;
        m_data.waveform[i] = std::max(-1.0f, std::min(1.0f, m_windowedBuffer[idx] * 1.45f));
    }

    applyHannWindow(m_windowedBuffer);
    computeFft(m_windowedBuffer, m_fftComplex);

    // Compute raw magnitudes (skipping DC bin 0)
    m_rawMagnitudes[0] = 0.0f;
    for (size_t i = 1; i < HALF_FFT; ++i) {
        m_rawMagnitudes[i] = std::abs(m_fftComplex[i]) / static_cast<float>(HALF_FFT);
    }

    const int bands = m_data.bandCount;
    float rawBandTilt[64] = {0.0f};
    float framePeak = 0.0f;

    // 1. Sub-bin interpolated logarithmic frequency mapping + pink-noise spectral tilt
    for (int i = 0; i < bands; ++i) {
        float r0 = static_cast<float>(i) / static_cast<float>(bands);
        float r1 = static_cast<float>(i + 1) / static_cast<float>(bands);
        float rMid = 0.5f * (r0 + r1);

        // Smooth exponential curve from bin 1.2 (~50 Hz) to bin 290 (~12.5 kHz)
        float pos0 = 1.2f + std::pow(r0, 2.05f) * 288.0f;
        float pos1 = 1.2f + std::pow(r1, 2.05f) * 288.0f;

        float bandAmp = 0.0f;
        if (pos1 - pos0 < 1.35f) {
            // Sub-bin linear interpolation for low-frequency bands so adjacent bass bars never clone
            float centerPos = 0.5f * (pos0 + pos1);
            int b0 = std::max(1, std::min(static_cast<int>(centerPos), static_cast<int>(HALF_FFT) - 2));
            float frac = centerPos - static_cast<float>(b0);
            bandAmp = m_rawMagnitudes[b0] * (1.0f - frac) + m_rawMagnitudes[b0 + 1] * frac;
        } else {
            // Contiguous RMS + Peak integration across the entire octave slice [pos0, pos1]
            int bStart = std::max(1, std::min(static_cast<int>(std::floor(pos0)), static_cast<int>(HALF_FFT) - 2));
            int bEnd = std::max(bStart, std::min(static_cast<int>(std::ceil(pos1)), static_cast<int>(HALF_FFT) - 1));
            float sqSum = 0.0f;
            float peakVal = 0.0f;
            int cnt = 0;
            for (int b = bStart; b <= bEnd; ++b) {
                float m = m_rawMagnitudes[b];
                sqSum += m * m;
                if (m > peakVal) peakVal = m;
                cnt++;
            }
            float rms = (cnt > 0) ? std::sqrt(sqSum / static_cast<float>(cnt)) : 0.0f;
            bandAmp = 0.65f * rms + 0.35f * peakVal;
        }

        // Pink-noise (+4.5 dB/octave) spectral tilt so mids, vocals, and hi-hats dance vividly alongside bass
        float tilt = 0.82f + 8.8f * std::pow(rMid, 0.76f) + 5.5f * (rMid * rMid);
        float tilted = bandAmp * tilt;
        rawBandTilt[i] = tilted;
        if (tilted > framePeak) framePeak = tilted;
    }

    // 2. Adaptive Automatic Gain Control (AGC) envelope
    if (framePeak > m_agcPeak) {
        m_agcPeak = m_agcPeak * 0.72f + framePeak * 0.28f;
    } else {
        m_agcPeak = m_agcPeak * 0.992f + framePeak * 0.008f;
    }
    float refPeak = std::max(0.022f, std::min(m_agcPeak * 1.08f, 0.48f));

    // 3. Perceptual dB curve + contrast shaping
    float shapedBands[64] = {0.0f};
    for (int i = 0; i < bands; ++i) {
        float norm = (rawBandTilt[i] / refPeak);
        // Clean noise gate
        norm = std::max(0.0f, norm - 0.025f);
        float logNorm = std::log10(1.0f + norm * 12.0f) / std::log10(13.0f);
        logNorm = std::max(0.0f, std::min(1.0f, logNorm));
        // Power contrast so beats punch sharply while quiet parts stay low
        shapedBands[i] = 0.04f + 0.94f * std::pow(logNorm, 1.24f);
    }

    // 4. 3-tap spatial smoothing across neighboring bars + temporal attack/decay
    float bassSum = 0.0f;
    float vocalSum = 0.0f;
    for (int i = 0; i < bands; ++i) {
        float left = (i > 0) ? shapedBands[i - 1] : shapedBands[i];
        float center = shapedBands[i];
        float right = (i + 1 < bands) ? shapedBands[i + 1] : shapedBands[i];
        float targetMag = (0.16f * left + 0.68f * center + 0.16f * right);
        targetMag = std::max(0.04f, std::min(targetMag, 0.98f));

        float prev = m_data.magnitudes[i];
        if (targetMag > prev) {
            // Fast punchy attack
            m_data.magnitudes[i] = prev * 0.26f + targetMag * 0.74f;
        } else {
            // Smooth musical release
            m_data.magnitudes[i] = prev * 0.81f + targetMag * 0.19f;
        }

        // Peak cap physics (floating cap with gravity drop)
        if (m_data.magnitudes[i] >= m_data.peakCaps[i]) {
            m_data.peakCaps[i] = m_data.magnitudes[i];
        } else {
            m_data.peakCaps[i] = std::max(0.04f, m_data.peakCaps[i] - 0.016f);
        }

        if (i < 5) bassSum += m_data.magnitudes[i];
        if (i >= 6 && i <= 20) vocalSum += m_data.magnitudes[i];
    }

    m_data.bassEnergy = bassSum / 5.0f;
    m_data.vocalEnergy = vocalSum / 15.0f;

    // Dynamic beat detector with rolling variance
    float avgBeat = 0.0f;
    for (int k = 0; k < 16; ++k) avgBeat += m_beatHistory[k];
    avgBeat /= 16.0f;

    m_data.isBeat = (m_data.bassEnergy > avgBeat * 1.28f) && (m_data.bassEnergy > 0.32f);
    m_beatHistory[m_beatHistIdx] = m_data.bassEnergy;
    m_beatHistIdx = (m_beatHistIdx + 1) % 16;
}

} // namespace ddmc
