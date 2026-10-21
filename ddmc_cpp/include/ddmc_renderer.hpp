#pragma once
#include "ddmc_types.hpp"
#include <string>

namespace ddmc {

class AudioEngine;
class FftAnalyzer;
class LyricsEngine;
class CompanionEngine;

class TerminalRenderer {
public:
    TerminalRenderer();
    ~TerminalRenderer();

    void initTerminal();
    void resetTerminal();

    void render(
        const AudioEngine& audio,
        const FftAnalyzer& fft,
        const LyricsEngine& lyrics,
        const CompanionEngine& companion,
        VisualizerMode vizMode,
        PerfMode perfMode
    );

    int getTerminalWidth() const { return m_termWidth; }
    int getTerminalHeight() const { return m_termHeight; }

private:
    void updateTerminalDimensions();
    void appendSpectrum(std::string& out, const SpectrumData& data, VisualizerMode mode, int width, int height);
    void appendCompanion(std::string& out, const CompanionEngine& companion, int width);
    void appendLyrics(std::string& out, const LyricsEngine& lyrics, double currentTimeSec, int width);
    void appendControls(std::string& out, const AudioEngine& audio, PerfMode perfMode, int width);

    int m_termWidth = 80;
    int m_termHeight = 24;
    std::string m_frameBuffer;
};

} // namespace ddmc
