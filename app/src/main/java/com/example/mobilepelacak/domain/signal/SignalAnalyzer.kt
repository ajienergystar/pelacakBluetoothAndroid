package com.example.mobilepelacak.domain.signal

import com.example.mobilepelacak.domain.model.RssiSample
import kotlin.math.sqrt

enum class SignalStability(val label: String) {
    STABLE("Stabil"),
    MODERATE("Cukup stabil"),
    UNSTABLE("Fluktuatif"),
    UNKNOWN("Belum cukup data"),
}

enum class SignalTrend(val label: String) {
    APPROACHING("Mendekat"),
    STEADY("Stabil"),
    RECEDING("Menjauh"),
    UNKNOWN("–"),
}

/** Statistik sinyal untuk layar pelacakan (stabilitas & arah pergerakan). */
object SignalAnalyzer {

    private const val STABILITY_WINDOW = 10
    private const val TREND_WINDOW = 5
    private const val TREND_THRESHOLD_DB = 2.0

    /** Standar deviasi RSSI mentah pada [STABILITY_WINDOW] sampel terakhir. */
    fun standardDeviation(samples: List<RssiSample>): Double? {
        val window = samples.takeLast(STABILITY_WINDOW)
        if (window.size < 3) return null
        val mean = window.sumOf { it.rssi }.toDouble() / window.size
        val variance = window.sumOf { (it.rssi - mean) * (it.rssi - mean) } / window.size
        return sqrt(variance)
    }

    fun stability(samples: List<RssiSample>): SignalStability {
        val sd = standardDeviation(samples) ?: return SignalStability.UNKNOWN
        return when {
            sd < 3.0 -> SignalStability.STABLE
            sd < 6.0 -> SignalStability.MODERATE
            else -> SignalStability.UNSTABLE
        }
    }

    /**
     * Membandingkan rata-rata RSSI ter-filter pada [TREND_WINDOW] sampel terakhir dengan
     * [TREND_WINDOW] sampel sebelumnya. RSSI naik = mendekat.
     */
    fun trend(samples: List<RssiSample>): SignalTrend {
        if (samples.size < TREND_WINDOW * 2) return SignalTrend.UNKNOWN
        val recent = samples.takeLast(TREND_WINDOW).map { it.smoothedRssi }.average()
        val previous = samples.takeLast(TREND_WINDOW * 2).take(TREND_WINDOW).map { it.smoothedRssi }.average()
        val delta = recent - previous
        return when {
            delta > TREND_THRESHOLD_DB -> SignalTrend.APPROACHING
            delta < -TREND_THRESHOLD_DB -> SignalTrend.RECEDING
            else -> SignalTrend.STEADY
        }
    }

    /** Laju paket advertising per detik pada sampel yang ada. */
    fun advertisementRate(samples: List<RssiSample>): Double? {
        if (samples.size < 2) return null
        val spanMs = samples.last().timestamp - samples.first().timestamp
        if (spanMs <= 0) return null
        return (samples.size - 1) * 1000.0 / spanMs
    }
}
