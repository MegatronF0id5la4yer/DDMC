#include <jni.h>
#include <string>
#include <vector>
#include <memory>
#include <algorithm>
#include <android/log.h>
#include "ddmc_fft.hpp"
#include "ddmc_companion.hpp"
#include "ddmc_types.hpp"

#define LOG_TAG "DDMC_Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::unique_ptr<ddmc::FftAnalyzer> s_analyzer;
static std::unique_ptr<ddmc::CompanionEngine> s_companion;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_cavifax_bagresuko_DdmcNative_nativeInit(JNIEnv* env, jclass clazz, jint perfMode) {
    try {
        s_analyzer = std::make_unique<ddmc::FftAnalyzer>();
        s_analyzer->setPerfMode(static_cast<ddmc::PerfMode>(perfMode));

        s_companion = std::make_unique<ddmc::CompanionEngine>();
        s_companion->setCharacter(ddmc::DOKI_MONIKA);

        LOGI("DDMC Native C++ engine initialized successfully with perfMode=%d", perfMode);
        return JNI_TRUE;
    } catch (...) {
        LOGE("Failed to initialize DDMC Native C++ engine");
        return JNI_FALSE;
    }
}

JNIEXPORT void JNICALL
Java_com_cavifax_bagresuko_DdmcNative_nativeSetPerfMode(JNIEnv* env, jclass clazz, jint perfMode) {
    if (s_analyzer) {
        s_analyzer->setPerfMode(static_cast<ddmc::PerfMode>(perfMode));
    }
    if (s_companion) {
        s_companion->setPerfMode(static_cast<ddmc::PerfMode>(perfMode));
    }
}

JNIEXPORT void JNICALL
Java_com_cavifax_bagresuko_DdmcNative_nativeSetVisualizerSensitivity(JNIEnv* env, jclass clazz, jfloat sensitivity) {
    if (s_analyzer) {
        s_analyzer->setVisualizerSensitivity(static_cast<float>(sensitivity));
    }
}

JNIEXPORT void JNICALL
Java_com_cavifax_bagresuko_DdmcNative_nativeFeedAudioSamples(
    JNIEnv* env, jclass clazz, jfloatArray samples, jint count, jint channels) {
    if (!s_analyzer || !samples || count <= 0 || channels <= 0) return;

    jfloat* pSamples = env->GetFloatArrayElements(samples, nullptr);
    if (pSamples) {
        s_analyzer->feedAudioSamples(pSamples, static_cast<size_t>(count), static_cast<uint32_t>(channels));
        env->ReleaseFloatArrayElements(samples, pSamples, JNI_ABORT);
    }
}

JNIEXPORT void JNICALL
Java_com_cavifax_bagresuko_DdmcNative_nativeFeedFftBytes(
    JNIEnv* env, jclass clazz, jbyteArray rawFft, jint count) {
    if (!s_analyzer || !rawFft || count < 8) return;

    jbyte* pBytes = env->GetByteArrayElements(rawFft, nullptr);
    if (pBytes) {
        // Synthesize float buffer from raw Android visualizer FFT bytes
        std::vector<float> pcmBuffer(count);
        for (int i = 0; i < count; ++i) {
            pcmBuffer[i] = static_cast<float>(pBytes[i]) / 128.0f;
        }
        s_analyzer->feedAudioSamples(pcmBuffer.data(), pcmBuffer.size(), 1);
        env->ReleaseByteArrayElements(rawFft, pBytes, JNI_ABORT);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_cavifax_bagresuko_DdmcNative_nativeProcessFft(
    JNIEnv* env, jclass clazz,
    jfloat dtSec,
    jfloatArray outMagnitudes,
    jfloatArray outPeakCaps,
    jfloatArray outWaveform) {
    if (!s_analyzer) return JNI_FALSE;

    s_analyzer->process(dtSec);
    const auto& spec = s_analyzer->getSpectrum();

    if (outMagnitudes) {
        jsize len = env->GetArrayLength(outMagnitudes);
        int copyCount = std::min(static_cast<int>(len), spec.bandCount);
        env->SetFloatArrayRegion(outMagnitudes, 0, copyCount, spec.magnitudes);
    }

    if (outPeakCaps) {
        jsize len = env->GetArrayLength(outPeakCaps);
        int copyCount = std::min(static_cast<int>(len), spec.bandCount);
        env->SetFloatArrayRegion(outPeakCaps, 0, copyCount, spec.peakCaps);
    }

    if (outWaveform) {
        jsize len = env->GetArrayLength(outWaveform);
        int copyCount = std::min(static_cast<int>(len), 128);
        env->SetFloatArrayRegion(outWaveform, 0, copyCount, spec.waveform);
    }

    return JNI_TRUE;
}

JNIEXPORT jfloat JNICALL
Java_com_cavifax_bagresuko_DdmcNative_nativeGetVocalEnergy(JNIEnv* env, jclass clazz) {
    return s_analyzer ? s_analyzer->getSpectrum().vocalEnergy : 0.0f;
}

JNIEXPORT jfloat JNICALL
Java_com_cavifax_bagresuko_DdmcNative_nativeGetBassEnergy(JNIEnv* env, jclass clazz) {
    return s_analyzer ? s_analyzer->getSpectrum().bassEnergy : 0.0f;
}

JNIEXPORT jboolean JNICALL
Java_com_cavifax_bagresuko_DdmcNative_nativeIsBeat(JNIEnv* env, jclass clazz) {
    return (s_analyzer && s_analyzer->getSpectrum().isBeat) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_cavifax_bagresuko_DdmcNative_nativeUpdateCompanion(
    JNIEnv* env, jclass clazz,
    jfloat dtSec, jfloat energy, jboolean isBeat,
    jfloatArray outData) {
    if (!s_companion) return;

    s_companion->update(dtSec, energy, isBeat == JNI_TRUE, 80, 24);

    if (outData && env->GetArrayLength(outData) >= 4) {
        float buffer[4];
        buffer[0] = s_companion->getX();
        buffer[1] = s_companion->getY();
        buffer[2] = static_cast<float>(s_companion->getState());
        buffer[3] = s_companion->isFacingRight() ? 1.0f : 0.0f;
        env->SetFloatArrayRegion(outData, 0, 4, buffer);
    }
}

JNIEXPORT void JNICALL
Java_com_cavifax_bagresuko_DdmcNative_nativeTriggerJump(JNIEnv* env, jclass clazz) {
    if (s_companion) s_companion->triggerAction("jump");
}

JNIEXPORT void JNICALL
Java_com_cavifax_bagresuko_DdmcNative_nativeTriggerDance(JNIEnv* env, jclass clazz) {
    if (s_companion) s_companion->triggerAction("dance");
}

JNIEXPORT void JNICALL
Java_com_cavifax_bagresuko_DdmcNative_nativeSetCompanionCharacter(JNIEnv* env, jclass clazz, jint charId) {
    if (s_companion) {
        s_companion->setCharacter(static_cast<ddmc::DokiCharacter>(charId));
    }
}

} // extern "C"
