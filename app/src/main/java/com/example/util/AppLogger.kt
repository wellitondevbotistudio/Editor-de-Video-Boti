package com.example.util

import android.util.Log

/**
 * Utilitário central de telemetria, diagnóstico e logs para o editor Boti.
 * Fornece logs estruturados de pipeline de mídia, sincronização de áudio, renderização e Room.
 */
object AppLogger {
    private const val TAG = "BotiVideoEditor"

    fun d(subtag: String, message: String) {
        Log.d("$TAG:$subtag", message)
    }

    fun i(subtag: String, message: String) {
        Log.i("$TAG:$subtag", message)
    }

    fun w(subtag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w("$TAG:$subtag", message, throwable)
        } else {
            Log.w("$TAG:$subtag", message)
        }
    }

    fun e(subtag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e("$TAG:$subtag", message, throwable)
        } else {
            Log.e("$TAG:$subtag", message)
        }
    }

    fun logMediaImport(uri: String, success: Boolean, details: String) {
        if (success) {
            i("MediaImport", "Importada com sucesso: uri=$uri | $details")
        } else {
            e("MediaImport", "Falha na importação: uri=$uri | $details")
        }
    }

    fun logAudioSync(playheadMs: Long, trackCount: Int, driftMs: Long) {
        d("AudioSync", "Playhead: ${playheadMs}ms, faixas: $trackCount, drift: ${driftMs}ms")
    }

    fun logPerformance(operation: String, durationMs: Long, thresholdMs: Long = 16L) {
        if (durationMs > thresholdMs) {
            w("PerfOptimization", "OP LENTA: '$operation' levou ${durationMs}ms (limiar: ${thresholdMs}ms)")
        } else {
            d("PerfOptimization", "OP OK: '$operation' levou ${durationMs}ms")
        }
    }
}
