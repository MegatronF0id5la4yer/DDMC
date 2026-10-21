# DDMC C/C++ Engine

High-performance, pure C/C++ standalone audio player and beat-reactive Doki Doki Companion engine.

## Features

- **Pure C/C++ Audio Engine**:
  - Zero garbage collector (GC) pauses, sub-millisecond audio latency.
  - Powered by `miniaudio.h` (single-header C, zero third-party library dependencies).
  - Native hardware playback on Windows (WASAPI / DirectSound), Linux (ALSA / PulseAudio), Android (AAudio / OpenSL ES), and macOS (CoreAudio).
  - Supports MP3, WAV, FLAC, and Vorbis out of the box.

- **Real-Time DSP & Spectrum FFT Analyzer**:
  - Cooley-Tukey Radix-2 FFT with Hann windowing.
  - Logarithmic frequency binning (22 bands in Potato mode, 44 bands in Balanced/Ultra modes).
  - Floating peak caps with physical gravity decay.
  - Dual neon waveform oscilloscope display.
  - Automatic bass kick and beat drop detection.

- **Doki Doki Companion Physics Simulation**:
  - 2D kinematics state machine (`STAND`, `WALK`, `RUN`, `SIT`, `HAPPY_JUMP`, `DANCE`).
  - Characters: **Natsuki**, **Sayori**, **Yuri**, **Monika**, and **Bocchi**.
  - Dances and jumps in sync with the song's beat drops and bass energy.
  - Particle effects (musical notes, hearts, stars).

- **Synced LRC Lyrics Reader**:
  - Real-time timestamp parsing and synchronization.
  - Dynamic active line glowing and pre/post contextual lines.

- **Hardware Optimization Modes ("Modo Patata")**:
  - `Potato Mode` (30 FPS, 22 FFT bands, throttled particles, < 8MB RAM, 0% idle CPU).
  - `Balanced Mode` (60 FPS, 44 FFT bands).
  - `Ultra Mode` (120 FPS high-refresh rate mode).

---

## Building

### Prerequisites

- A C++17 compatible compiler:
  - Windows: Visual Studio 2019+ or MinGW / Clang
  - Linux: GCC 9+ or Clang 10+
  - Android: Android NDK or Termux (`pkg install clang cmake make`)

### Build with CMake

```bash
mkdir build
cd build
cmake ..
cmake --build . --config Release
```

### Build with Make

```bash
make
```

---

## Running

```bash
# Play a track with default settings (auto-detects .lrc file if present):
./ddmc song.mp3

# Run directly in Potato mode (optimized for older / budget hardware):
./ddmc song.flac --potato

# Run in Ultra 120 FPS mode:
./ddmc song.mp3 --ultra
```

---

## Keyboard Controls

| Key | Action |
|---|---|
| `Space` | Play / Pause |
| `Left` / `Right` Arrow | Seek -5s / +5s |
| `Up` / `Down` Arrow | Volume Up / Down |
| `M` | Cycle Visualizer Mode (Mirror Bars, Dual Waveform, Radial Pulse, Studio Hybrid) |
| `P` | Toggle Performance Mode (Potato 30fps -> Balanced 60fps -> Ultra 120fps) |
| `D` | Switch Doki Companion (Natsuki -> Sayori -> Yuri -> Monika -> Bocchi) |
| `J` | Make Companion jump |
| `C` | Make Companion dance |
| `Q` / `Esc` | Quit cleanly |
