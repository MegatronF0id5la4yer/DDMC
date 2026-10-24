# DDMC Android Application

This directory contains the Android application source code for **DDMC (Doki Doki Music Companion)**.

---

## Architecture Overview

- **Core Player**: Media3 ExoPlayer with a custom `SonoraAudioProcessor` in the audio rendering pipeline.
- **Native C++ Engine**: Interfaced via JNI (`native_bridge.cpp`) communicating with `ddmc_cpp/` for Cooley-Tukey Radix-2 FFT and Doki kinematics.
- **Audio Routing**: Software decoding ensures PCM buffers are directly captured for FFT spectrum analysis, completely avoiding hardware offload silencing and permission bugs.
- **UI Architecture**: Single-activity architecture (`MainActivity.kt`) featuring pure Kotlin dynamic layout inflation, zero XML layout bloat, and hardware-accelerated Canvas rendering.

---

## Product Flavors

The project defines two flavors under the `edition` flavor dimension in `app/build.gradle.kts`:

### 1. `standard`
- **Application ID**: `com.sonora.music`
- **App Name**: `DDMC`
- **Troll Features**: Disabled (`IS_TROLL_EDITION = false`)
- **Use Case**: Official production build.

### 2. `troll`
- **Application ID**: `com.sonora.music.troll`
- **App Name**: `DDMC Version Troll`
- **Troll Features**: Enabled (`IS_TROLL_EDITION = true`)
- **Use Case**: Satirical version with comic Doomentio / NostraCoins paywall interceptor (`FakePaywall.kt`).

Because of unique application IDs, both flavors can be installed simultaneously on the same Android device.

---

## Build Tasks

### Build Standard Debug APK:
```bash
./gradlew assembleStandardDebug
```
Output: `app/build/outputs/apk/standard/debug/app-standard-debug.apk`

### Build Troll Debug APK:
```bash
./gradlew assembleTrollDebug
```
Output: `app/build/outputs/apk/troll/debug/app-troll-debug.apk`

### Build Standard Release APK:
```bash
./gradlew assembleStandardRelease
```

---

## Native C++ NDK Setup

The native library `libddmc_native.so` is compiled via CMake:
- **Source**: `app/src/main/cpp/native_bridge.cpp` linked against `../../../../../ddmc_cpp/src/*.cpp`.
- **Target ABI**: `arm64-v8a` (with 16KB ELF page size alignment: `-Wl,-z,max-page-size=16384` for Android 15+ compatibility).
- **NDK Version**: Tested on NDK 25c and newer.
