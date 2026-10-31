# DDMC Official Releases

This directory stores the official distribution APK for DDMC.

---

## Official Package

### `DDMC.apk`
- **Flavor**: `standard`
- **Application ID**: `com.cavifax.bagresuko`
- **App Name**: `DDMC`
- **Version**: `v2.1.2`
- **Target OS**: Android 8.0+ (API 26+)
- **Description**: The official clean release of DDMC powered by the Cavifax, Chris Chan, Aimep3, and Bagresuko engine suite. Features:
  - **Zero Black Bars Adaptive Video (Sin Recortar)**: Contenedor y superficie adaptativa de video que ajustan sus dimensiones proporcionalmente a la relación de aspecto nativa del video (`effW : effH`), eliminando por completo las franjas negras sin recortar bordes de la imagen. Shutter y fondos transparentes.
  - **Inmersión Total Edge-to-Edge**: Ocultación permanente de la barra de notificaciones superior y de la barra de botones/gestos de Android (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`), aprovechando el 100% de la pantalla sin interferencias visuales.
  - **Kalcifer Lyrics Engine & LRCLIB**: Búsqueda multi-ruta de archivos compañeros `.lrc` y `.txt` en la misma carpeta del audio y en rutas comunes, insensible a mayúsculas/minúsculas (`.LRC`), persistencia por clave dual y sincronización automática en vivo con LRCLIB.
  - **Keystore RSA Permanente**: Firma digital permanente (`ddmc.keystore`) para actualizaciones sin desinstalar ni perder cookies o datos.
  - **CookieBackupEngine**: Persistencia y restauración de cookies de sesión para WebView y YouTube.
  - **Interactive Doki Companions & 3D HD Dance**: 7 atuendos Non-Chibi 3D HD y 6 personajes Chibi con coreografía de baile expresiva y rotación amplia de brazos/piernas.
- **Download**: [Download DDMC.apk (v2.1.2)](https://github.com/MegatronF0id5la4yer/DDMC/releases/download/v2.1.2/DDMC.apk)

---

## Local Versioned Archive (`apks/`)

Every compiled version is preserved locally in the `apks/` directory (e.g. `apks/DDMC_v2.0.7.apk`, `apks/DDMC_v2.0.8.apk`, `apks/DDMC_v2.0.9.apk`, `apks/DDMC_v2.1.0.apk`, `apks/DDMC_v2.1.1.apk`, `apks/DDMC_v2.1.2.apk`). The `apks/` directory is kept offline and excluded from Git.

---

## Installation via ADB

To install DDMC on a connected Android device:

```bash
adb install -r DDMC.apk
```
