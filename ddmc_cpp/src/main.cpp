#include "ddmc_types.hpp"
#include "ddmc_audio.hpp"
#include "ddmc_fft.hpp"
#include "ddmc_lyrics.hpp"
#include "ddmc_companion.hpp"
#include "ddmc_renderer.hpp"

#include <iostream>
#include <chrono>
#include <thread>
#include <filesystem>
#include <csignal>

#ifdef _WIN32
#include <conio.h>
#else
#include <termios.h>
#include <unistd.h>
#include <fcntl.h>
#endif

namespace fs = std::filesystem;

static std::atomic<bool> g_running{true};

void signalHandler(int signum) {
    (void)signum;
    g_running = false;
}

#ifndef _WIN32
static struct termios orig_termios;
void disableRawMode() {
    tcsetattr(STDIN_FILENO, TCSAFLUSH, &orig_termios);
}
void enableRawMode() {
    tcgetattr(STDIN_FILENO, &orig_termios);
    atexit(disableRawMode);
    struct termios raw = orig_termios;
    raw.c_lflag &= ~(ECHO | ICANON);
    tcsetattr(STDIN_FILENO, TCSAFLUSH, &raw);
}
int kbhit() {
    struct timeval tv = {0, 0};
    fd_set fds;
    FD_ZERO(&fds);
    FD_SET(STDIN_FILENO, &fds);
    return select(STDIN_FILENO + 1, &fds, NULL, NULL, &tv) > 0;
}
int getch() {
    int r;
    unsigned char c;
    if ((r = read(STDIN_FILENO, &c, sizeof(c))) < 0) return r;
    return c;
}
#endif

int main(int argc, char* argv[]) {
    std::signal(SIGINT, signalHandler);
    std::signal(SIGTERM, signalHandler);

#ifndef _WIN32
    enableRawMode();
#endif

    std::string trackPath = "";
    ddmc::PerfMode perfMode = ddmc::MODE_BALANCED;

    // Parse CLI arguments
    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        if (arg == "--potato") {
            perfMode = ddmc::MODE_POTATO;
        } else if (arg == "--balanced") {
            perfMode = ddmc::MODE_BALANCED;
        } else if (arg == "--ultra") {
            perfMode = ddmc::MODE_ULTRA;
        } else if (trackPath.empty() && arg[0] != '-') {
            trackPath = arg;
        }
    }

    // Auto-search for first audio file if none provided
    if (trackPath.empty()) {
        try {
            for (const auto& entry : fs::directory_iterator(".")) {
                if (entry.is_regular_file()) {
                    std::string ext = entry.path().extension().string();
                    std::transform(ext.begin(), ext.end(), ext.begin(), ::tolower);
                    if (ext == ".mp3" || ext == ".flac" || ext == ".wav" || ext == ".ogg") {
                        trackPath = entry.path().string();
                        break;
                    }
                }
            }
        } catch (...) {}
    }

    // Initialize subsystems
    ddmc::AudioEngine audio;
    if (!audio.init()) {
        std::cerr << "Error: Could not initialize miniaudio playback engine." << std::endl;
        return 1;
    }

    ddmc::FftAnalyzer fft;
    fft.setPerfMode(perfMode);
    audio.attachFftAnalyzer(&fft);

    ddmc::LyricsEngine lyrics;
    ddmc::CompanionEngine companion;
    companion.setPerfMode(perfMode);

    ddmc::TerminalRenderer renderer;
    ddmc::VisualizerMode vizMode = ddmc::VIZ_MIRROR_SPECTRUM;

    if (!trackPath.empty()) {
        if (audio.loadFile(trackPath)) {
            lyrics.loadFromAdjacent(trackPath);
            audio.play();
        }
    }

    auto lastTime = std::chrono::steady_clock::now();

    // Main event and rendering loop
    while (g_running) {
        auto now = std::chrono::steady_clock::now();
        float dtSec = std::chrono::duration<float>(now - lastTime).count();
        lastTime = now;

        // Process keyboard inputs non-blockingly
#ifdef _WIN32
        while (_kbhit()) {
            int key = _getch();
            if (key == 0 || key == 224) { // Arrow keys prefix on Windows
                int arrow = _getch();
                if (arrow == 72) audio.setVolume(audio.getVolume() + 0.05f); // Up
                else if (arrow == 80) audio.setVolume(audio.getVolume() - 0.05f); // Down
                else if (arrow == 75) audio.seekRelative(-5.0); // Left
                else if (arrow == 77) audio.seekRelative(5.0);  // Right
            } else {
#else
        while (kbhit()) {
            int key = getch();
            if (key == 27) { // Escape or arrow sequence on Unix
                if (kbhit()) {
                    int c2 = getch();
                    if (c2 == '[') {
                        int arrow = getch();
                        if (arrow == 'A') audio.setVolume(audio.getVolume() + 0.05f); // Up
                        else if (arrow == 'B') audio.setVolume(audio.getVolume() - 0.05f); // Down
                        else if (arrow == 'D') audio.seekRelative(-5.0); // Left
                        else if (arrow == 'C') audio.seekRelative(5.0);  // Right
                    }
                } else {
                    g_running = false;
                }
            } else {
#endif
                if (key == ' ' || key == 'k') {
                    audio.togglePlayPause();
                } else if (key == 'q' || key == 'Q' || key == 27) {
                    g_running = false;
                } else if (key == 'm' || key == 'M') {
                    vizMode = static_cast<ddmc::VisualizerMode>((vizMode + 1) % 4);
                } else if (key == 'p' || key == 'P') {
                    perfMode = static_cast<ddmc::PerfMode>((perfMode + 1) % 3);
                    fft.setPerfMode(perfMode);
                    companion.setPerfMode(perfMode);
                } else if (key == 'd' || key == 'D') {
                    int nextDoki = (static_cast<int>(companion.getCharacter()) + 1) % 5;
                    companion.setCharacter(static_cast<ddmc::DokiCharacter>(nextDoki));
                } else if (key == 'j' || key == 'J') {
                    companion.triggerAction("jump");
                } else if (key == 'c' || key == 'C') {
                    companion.triggerAction("dance");
                } else if (key == '+' || key == '=') {
                    audio.setVolume(audio.getVolume() + 0.05f);
                } else if (key == '-' || key == '_') {
                    audio.setVolume(audio.getVolume() - 0.05f);
                }
            }
        }

        // Process DSP FFT spectrum
        fft.process(dtSec);

        // Update Companion physics and beat reactivity
        const auto& spectrum = fft.getSpectrum();
        companion.update(dtSec, spectrum.bassEnergy, spectrum.isBeat, renderer.getTerminalWidth(), 6);

        // Render current frame
        renderer.render(audio, fft, lyrics, companion, vizMode, perfMode);

        // Frame rate limiter
        int targetFps = (perfMode == ddmc::MODE_POTATO) ? 30 : (perfMode == ddmc::MODE_ULTRA ? 120 : 60);
        int frameIntervalMs = 1000 / targetFps;

        auto renderDuration = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - now).count();
        if (renderDuration < frameIntervalMs) {
            std::this_thread::sleep_for(std::chrono::milliseconds(frameIntervalMs - renderDuration));
        }
    }

    renderer.resetTerminal();
    audio.shutdown();
    std::cout << "\nDDMC C++ Engine exited cleanly.\n" << std::endl;
    return 0;
}
