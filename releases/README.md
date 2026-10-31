# DDMC Official Releases

This directory stores the official distribution APK for DDMC.

---

## Official Package

### `DDMC.apk`
- **Flavor**: `standard`
- **Application ID**: `com.cavifax.bagresuko`
- **App Name**: `DDMC`
- **Version**: `v2.1.0`
- **Target OS**: Android 8.0+ (API 26+)
- **Description**: The official clean release of DDMC powered by the Cavifax, Chris Chan, Aimep3, and Bagresuko engine suite. Features:
  - **Storage Video Player Section**: Dedicated video tab in library (`LibMode.VIDEOS`) automatically scanning local storage (MP4, MKV, WebM, AVI, MOV) with full ExoPlayer video surface playback.
  - **Default Media Player**: System intent filter support for `android.intent.action.VIEW` for both audio and video streams/files (`content://` and `file://`).
  - **Interactive Doki Companion Modes**: Procedural 2D sprite beat-synced dance and full 3D HD Blender 5.2 Satchely anime models with customizable dance speed (25% to 200%) and stage position controls (X/Y drag offsets).
  - **Expressive 3D Choreography**: Vibrant idol dance routine with high-range arm rotations (-16° to +44° shoulder, -20° to +32° elbow), marked dynamic leg pops (35° knee-pop, 22° thigh rotation), deep hip bounces, and flawless custom posture for Yuri with natural clasped arms and zero mesh tearing.
  - **Visualizer Engine**: 10 real-time visualizers with animation speed controls (20% to 200%) and sensitivity slider.
- **Download**: [Download DDMC.apk (v2.1.0)](https://github.com/MegatronF0id5la4yer/DDMC/releases/download/v2.1.0/DDMC.apk)

---

## Local Versioned Archive (`apks/`)

Every compiled version is preserved locally in the `apks/` directory (e.g. `apks/DDMC_v2.0.7.apk`, `apks/DDMC_v2.0.8.apk`, `apks/DDMC_v2.0.9.apk`, `apks/DDMC_v2.1.0.apk`). The `apks/` directory is kept offline and excluded from Git.

---

## Installation via ADB

To install DDMC on a connected Android device:

```bash
adb install -r DDMC.apk
```
