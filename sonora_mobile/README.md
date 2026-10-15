# Sonora Mobile (Android)

Port móvil de [Sonora](https://github.com/sonorahq/sonora) para Android, desarrollado con Flutter, Dart y ExoPlayer. Conserva la estética oscura minimalista, glassmorphism, el visualizador de espectro de audio en tiempo real y añade soporte para reproducción de videoclips musicales en alta definición.

---

## Características Principales

1. **Reproducción de Música y Vídeo HD**:
   - Transmisión directa desde YouTube Music y archivos locales.
   - **Modo Vídeo**: Alterna entre la portada y el videoclip oficial directamente en la pantalla de reproducción con un solo toque.
2. **Visualizador de Espectro de Audio en Tiempo Real**:
   - Renderizado con `CustomPainter` a 60 FPS.
   - Modos configurables: **Barras de frecuencia**, **Línea de onda (glow)**, **Ambos** o **Desactivado**.
   - Se muestra de fondo interactuando con la música tal como en la versión de PC.
3. **Letras Sincronizadas (Karaoke)**:
   - Desplazamiento automático en tiempo real vía LRCLIB.
   - Toca cualquier línea de la letra para saltar directamente a ese minuto y segundo de la canción.
4. **Reproducción en Segundo Plano y Controles Android**:
   - `AudioService` integrado con notificación multimedia en la barra de estado y pantalla de bloqueo.
   - Soporte para botones de auriculares bluetooth y reanudación automática.
5. **Navegación Fluida con Mini-Reproductor Persistente**:
   - Pestañas de **Inicio**, **Buscar**, **Biblioteca** (Favoritas y almacenamiento local) y **Ajustes**.
   - Mini-player flotante con barra de progreso y previsualización de espectro en miniatura.

---

## Requisitos y Compilación

### Requisitos Previos
- [Flutter SDK](https://docs.flutter.dev/get-started/install) (versión 3.0.0 o superior).
- [Android Studio](https://developer.android.com/studio) o Android SDK (API 34).
- JDK 17.

### Pasos para Ejecutar en Emulador o Celular
```bash
cd sonora_mobile
flutter pub get
flutter run
```

### Generar APK para Instalación en Android
```bash
# APK Release universal
flutter build apk --release

# O dividida por arquitectura para menor tamaño
flutter build apk --split-per-abi
```
El archivo generado se ubicará en: `sonora_mobile/build/app/outputs/flutter-apk/app-release.apk`.

