#include "ddmc_renderer.hpp"
#include "ddmc_audio.hpp"
#include "ddmc_fft.hpp"
#include "ddmc_lyrics.hpp"
#include "ddmc_companion.hpp"

#include <iostream>
#include <sstream>
#include <iomanip>
#include <algorithm>
#include <cmath>

#ifdef _WIN32
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#else
#include <sys/ioctl.h>
#include <unistd.h>
#endif

namespace ddmc {

TerminalRenderer::TerminalRenderer() {
    initTerminal();
}

TerminalRenderer::~TerminalRenderer() {
    resetTerminal();
}

void TerminalRenderer::initTerminal() {
#ifdef _WIN32
    HANDLE hOut = GetStdHandle(STD_OUTPUT_HANDLE);
    DWORD dwMode = 0;
    if (GetConsoleMode(hOut, &dwMode)) {
        dwMode |= ENABLE_VIRTUAL_TERMINAL_PROCESSING;
        SetConsoleMode(hOut, dwMode);
    }
    // Set UTF-8 code page
    SetConsoleOutputCP(CP_UTF8);
#endif
    // Hide cursor, clear screen, home cursor
    std::cout << "\033[?25l\033[2J\033[H" << std::flush;
}

void TerminalRenderer::resetTerminal() {
    // Show cursor, reset colors, clear
    std::cout << "\033[?25h\033[0m\033[2J\033[H" << std::flush;
}

void TerminalRenderer::updateTerminalDimensions() {
#ifdef _WIN32
    CONSOLE_SCREEN_BUFFER_INFO csbi;
    if (GetConsoleScreenBufferInfo(GetStdHandle(STD_OUTPUT_HANDLE), &csbi)) {
        m_termWidth = csbi.srWindow.Right - csbi.srWindow.Left + 1;
        m_termHeight = csbi.srWindow.Bottom - csbi.srWindow.Top + 1;
    }
#else
    struct winsize w;
    if (ioctl(STDOUT_FILENO, TIOCGWINSZ, &w) == 0) {
        m_termWidth = w.ws_col;
        m_termHeight = w.ws_row;
    }
#endif
    if (m_termWidth < 40) m_termWidth = 40;
    if (m_termHeight < 15) m_termHeight = 15;
}

static std::string formatTime(double seconds) {
    if (seconds < 0.0) seconds = 0.0;
    int total = static_cast<int>(seconds);
    int m = total / 60;
    int s = total % 60;
    char buf[16];
    std::snprintf(buf, sizeof(buf), "%02d:%02d", m, s);
    return std::string(buf);
}

void TerminalRenderer::appendSpectrum(std::string& out, const SpectrumData& data, VisualizerMode mode, int width, int height) {
    const char* blockChars[] = {" ", " ", "▂", "▃", "▄", "▅", "▆", "▇", "█"};
    int bands = std::min(data.bandCount, width / 2);
    if (bands <= 0) bands = 22;

    int vizH = std::max(4, height);

    if (mode == VIZ_DUAL_OSCILLOSCOPE) {
        // Dual Neon Waveform Oscilloscope
        out += "\033[38;2;56;189;248m"; // Sky Cyan
        for (int y = vizH - 1; y >= 0; --y) {
            float targetNorm = static_cast<float>(y) / static_cast<float>(vizH - 1);
            out += "  ";
            for (int x = 0; x < width - 4; ++x) {
                int waveIdx = (x * 128) / (width - 4);
                float sampleNorm = (data.waveform[waveIdx] + 1.0f) * 0.5f;
                if (std::abs(sampleNorm - targetNorm) < (1.0f / vizH)) {
                    out += "─";
                } else {
                    out += " ";
                }
            }
            out += "\n";
        }
    } else {
        // Mode 0 & 3: Mirror Spectrum Bars
        for (int row = vizH - 1; row >= 0; --row) {
            float rowThreshold = static_cast<float>(row) / static_cast<float>(vizH);
            out += "  ";
            for (int i = 0; i < bands; ++i) {
                float mag = data.magnitudes[i];
                float cap = data.peakCaps[i];

                if (std::abs(cap - rowThreshold) < (1.0f / vizH) && row > 0) {
                    out += "\033[38;2;254;240;138m-\033[0m "; // Peak cap pill
                } else if (mag >= rowThreshold) {
                    // Color gradient from cyan to magenta to pink
                    int r = static_cast<int>(56 + 188 * rowThreshold);
                    int g = static_cast<int>(189 - 75 * rowThreshold);
                    int b = static_cast<int>(248 - 66 * rowThreshold);
                    out += "\033[38;2;" + std::to_string(r) + ";" + std::to_string(g) + ";" + std::to_string(b) + "m█\033[0m ";
                } else {
                    out += "  ";
                }
            }
            out += "\n";
        }
    }
}

void TerminalRenderer::appendCompanion(std::string& out, const CompanionEngine& companion, int width) {
    const char* col = companion.getCharacterColor();
    auto lines = companion.getRenderLines();

    out += "\033[1m";
    out += col;
    out += "  [Companion: " + std::string(companion.getCharacterName()) + "]\033[0m\n";

    for (const auto& line : lines) {
        out += col;
        out += line;
        out += "\033[0m\n";
    }

    // Floating particles
    const auto& particles = companion.getParticles();
    if (!particles.empty()) {
        out += "   ";
        for (const auto& p : particles) {
            out += "\033[38;2;244;114;182m" + p.symbol + " \033[0m";
        }
        out += "\n";
    }
}

void TerminalRenderer::appendLyrics(std::string& out, const LyricsEngine& lyrics, double currentTimeSec, int width) {
    out += "\033[1;37m  --- Synced Lyrics ---\033[0m\n";
    if (!lyrics.hasLyrics()) {
        out += "\033[90m  (No synced .lrc file found beside audio track)\033[0m\n";
        return;
    }

    int activeIdx = lyrics.getActiveIndex(currentTimeSec);

    // Show previous line
    if (activeIdx > 0) {
        const auto* prev = lyrics.getLine(activeIdx - 1);
        if (prev) {
            out += "\033[38;2;100;116;139m    " + prev->text + "\033[0m\n";
        }
    } else {
        out += "\n";
    }

    // Active line
    if (activeIdx >= 0) {
        const auto* cur = lyrics.getLine(activeIdx);
        if (cur) {
            out += "\033[1;38;2;244;114;182m  ▶ " + cur->text + "\033[0m\n";
        }
    } else {
        out += "\033[90m  ... Instrumental Intro ...\033[0m\n";
    }

    // Next line
    if (activeIdx >= 0 && static_cast<size_t>(activeIdx + 1) < lyrics.getLineCount()) {
        const auto* next = lyrics.getLine(activeIdx + 1);
        if (next) {
            out += "\033[38;2;100;116;139m    " + next->text + "\033[0m\n";
        }
    } else {
        out += "\n";
    }
}

void TerminalRenderer::appendControls(std::string& out, const AudioEngine& audio, PerfMode perfMode, int width) {
    const auto& info = audio.getTrackInfo();
    double curSec = audio.getPositionSec();
    double durSec = audio.getDurationSec();

    // Progress Bar
    int barWidth = std::max(10, width - 26);
    float progress = (durSec > 0.0) ? static_cast<float>(curSec / durSec) : 0.0f;
    int filled = static_cast<int>(progress * barWidth);

    out += "\n  " + formatTime(curSec) + " [";
    out += "\033[38;2;56;189;248m";
    for (int i = 0; i < filled; ++i) out += "=";
    out += "\033[1;37mO\033[0m\033[90m";
    for (int i = filled + 1; i < barWidth; ++i) out += "-";
    out += "\033[0m] " + formatTime(durSec) + "\n";

    // Track details and status
    std::string playState = audio.isPlaying() ? "▶ PLAYING" : "❚❚ PAUSED";
    std::string perfLabel = (perfMode == MODE_POTATO) ? "\033[38;2;251;191;36m[POTATO 30FPS]\033[0m" :
                            (perfMode == MODE_ULTRA)  ? "\033[38;2;168;85;247m[ULTRA 120FPS]\033[0m" :
                                                        "\033[38;2;34;197;94m[BALANCED 60FPS]\033[0m";

    int volPct = static_cast<int>(audio.getVolume() * 100.0f);
    out += "  \033[1;32m" + playState + "\033[0m | Vol: " + std::to_string(volPct) + "% | " + perfLabel + "\n";

    // Hotkey Guide
    out += "\033[90m  [Space] Play/Pause | [←/→] Seek 5s | [↑/↓] Vol | [M] Viz | [P] Potato/Ultra | [D] Doki | [Q] Quit\033[0m\n";
}

void TerminalRenderer::render(
    const AudioEngine& audio,
    const FftAnalyzer& fft,
    const LyricsEngine& lyrics,
    const CompanionEngine& companion,
    VisualizerMode vizMode,
    PerfMode perfMode
) {
    updateTerminalDimensions();
    m_frameBuffer.clear();
    m_frameBuffer.reserve(4096);

    // Return to top-left corner without clear to prevent flickering
    m_frameBuffer += "\033[H";

    // Top Header
    const auto& info = audio.getTrackInfo();
    m_frameBuffer += "\033[1;38;2;244;114;182m  DDMC C++ ENGINE\033[0m \033[90m| High Performance Audio & Shimeji Stage\033[0m\n";
    m_frameBuffer += "  \033[1;37m" + (info.title.empty() ? "No Track Loaded" : info.title) + "\033[0m\n";
    if (!info.artist.empty()) {
        m_frameBuffer += "  \033[38;2;148;163;184m" + info.artist + " — " + info.album + "\033[0m\n";
    }
    m_frameBuffer += "\n";

    // Spectrum Visualizer
    appendSpectrum(m_frameBuffer, fft.getSpectrum(), vizMode, m_termWidth, 6);
    m_frameBuffer += "\n";

    // Doki Companion Stage
    appendCompanion(m_frameBuffer, companion, m_termWidth);
    m_frameBuffer += "\n";

    // Synced Lyrics
    appendLyrics(m_frameBuffer, lyrics, audio.getPositionSec(), m_termWidth);

    // Controls & Progress
    appendControls(m_frameBuffer, audio, perfMode, m_termWidth);

    // Write complete frame atomically
    std::cout.write(m_frameBuffer.data(), m_frameBuffer.size());
    std::cout.flush();
}

} // namespace ddmc
