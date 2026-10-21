# DDMC (Doki Doki Music Companion)

A high-fidelity Android music player featuring beat-reactive spectrum visualizers, dynamic per-track video covers, Doki companions with real-time physics, and a standalone pure C/C++ desktop engine.

---

## Downloads and Releases

| Asset | Target | Description | Link |
|---|---|---|---|
| **DDMC Android APK (v2.0.0)** | Android 8.0+ | Full player with Performance Engine (Potato / Ultra), dynamic video covers, and launcher physics | [Download v2.0.0 APK](https://github.com/MegatronF0id5la4yer/DDMC/releases/latest) |
| **DDMC C/C++ Engine** | Windows / Linux / macOS | Standalone zero-GC CLI player with miniaudio, Radix-2 FFT, and ANSI TrueColor visualizer | [Source in `ddmc_cpp/`](./ddmc_cpp) |

All official releases and changelogs are published on [GitHub Releases](https://github.com/MegatronF0id5la4yer/DDMC/releases).

---

## Key Features

### 1. Dynamic Per-Track Video Covers and Center-Crop
- Plays looped MP4 and GIF video covers per individual track or globally.
- Dynamic matrix center-crop scaling eliminates black bars (letterboxing/pillarboxing) across vertical 9:16, 16:9, 4:3, and 1:1 aspect ratios.
- Internal storage isolation ensures smooth playback without Scoped Storage permission timeouts.

### 2. Pro Hardware Beat-Reactive Visualizers
- 5 distinct visualization modes:
  - **Mirror Spectrum Pro**: Dual-sided logarithmic frequency spectrum with floating peak pills and gravity decay.
  - **Dual Neon Oscilloscope**: Real-time waveform rendering with cubic spline interpolation.
  - **Radial 360 Pulse**: Circular pulse visualizer with beat-reactive radius modulation.
  - **Spectral Waterfall**: Time-decay heat-map spectrogram.
  - **Studio Hybrid**: Combined dual waveform and spectrum analysis.

### 3. Doki Doki Companion Engine
- Interactive companion characters: **Natsuki**, **Sayori**, **Yuri**, **Monika**, and **Bocchi**.
- Real-time 2D physics engine: gravity, wall collisions, state transitions (`STAND`, `WALK`, `RUN`, `SIT`, `HAPPY_JUMP`, `DANCE`).
- Audio energy reactivity: companions jump, dance, and celebrate in sync with music beat drops.
- Particle effects: floating musical notes, hearts, and sparkles.

### 4. Launcher-Style Motion Physics
- Tactile home-screen motion design:
  - Long-press lift: 1.05x scale elevation with haptic feedback.
  - Dynamic drag follow with 3D tilt based on gesture velocity.
  - Fluid delayed layout reflow using cubic bezier path interpolators.
  - Spring overshoot landing with tactile tick feedback.

### 5. Hardware Performance Engine (Potato Mode to Ultra)
- Preserves all visualizers, companions, lyrics, and video covers without removing any features:
  - **Potato Mode (30 FPS)**: Halves FFT calculations (22 bands), enforces 16-bit RGB_565 bitmap decoding to cut memory footprint by 50%, throttles particle spawning, and caps rendering to 30 FPS for budget/older devices.
  - **Balanced Mode (60 FPS)**: Standard high-fidelity smooth rendering.
  - **Ultra Mode (120 FPS)**: Uncapped high-refresh rate mode for flagship screens.
- Automatic hardware evaluation selects the optimal mode on first launch.

### 6. Automatic In-App OTA Self-Updater
- Checks for new releases and updates directly from the GitHub repository on application startup.
- Downloads the latest package in the background and prompts the Android package installer for one-tap updates without requiring a PC or ADB connection.

### 7. Standalone C/C++ Engine (`ddmc_cpp`)
- Independent, high-performance C/C++ desktop version:
  - Powered by `miniaudio.h` (single-header C, zero third-party library dependencies).
  - Radix-2 Cooley-Tukey FFT analyzer with Hann windowing.
  - Synced LRC lyric reader.
  - ANSI TrueColor terminal renderer with non-flickering double buffering.
  - Uses less than 10 MB RAM and zero garbage collection overhead.

---

## Building from Source

### Android App

Requirements: JDK 17, Android SDK (API 34).

```bash
cd android_app
./gradlew assembleDebug
```

The compiled APK will be generated at:
`android_app/app/build/outputs/apk/debug/app-debug.apk`

### C/C++ Desktop Engine

Requirements: C++17 compiler (GCC, Clang, or MSVC) and CMake.

```bash
cd ddmc_cpp
mkdir build
cd build
cmake ..
cmake --build . --config Release
```

Or using Make on Linux / macOS:

```bash
cd ddmc_cpp
make
```

---

## License

This project is released under the MIT License.
