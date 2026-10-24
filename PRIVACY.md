# Privacy Policy for DDMC

DDMC (Doki Doki Music Companion) is designed from the ground up as a fully offline, local-first music player and audio analysis environment.

## 1. Zero External Telemetry and Analytics
DDMC does not collect, record, or transmit any user data, device identifiers, listening history, or usage statistics. There are no background telemetry services, tracking beacons, advertising networks, or analytics SDKs embedded in the application.

## 2. Local-Only Audio and Metadata Processing
All audio playback, digital signal processing (DSP), Fast Fourier Transform (FFT) spectrum calculations, lyrics parsing, and cover rendering are performed entirely on the local device hardware. No audio signals or file information leave your device.

## 3. Storage and Permissions
- **Audio Access (`READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE`)**: Used solely by the local media scanner to discover and index audio files stored on your device storage.
- **Microphone / Record Audio (`RECORD_AUDIO`)**: Optional legacy fallback for Android OS audio visualizer sessions. DDMC v2.0.5+ processes audio directly from the internal software decoder pipeline without accessing external microphone input.
- **Network Access**: Used exclusively if the user manually checks for application updates through the built-in GitHub OTA updater. No personal or device data is transmitted during update checks.

## 4. No Cloud Accounts or Remote Storage
DDMC requires no account registration, login credentials, or cloud synchronization services. Your playlists, favorites, custom equalizer presets, and application settings remain exclusively in local private app storage (`SharedPreferences` and local SQLite databases).

## 5. Contact
Inquiries regarding privacy or the codebase may be submitted directly via GitHub:
https://github.com/MegatronF0id5la4yer/DDMC
