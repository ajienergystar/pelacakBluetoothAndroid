package com.example.mobilepelacak.domain.signal

import kotlin.math.pow

/**
 * Estimasi jarak dengan model log-distance path loss:
 *
 *     d = 10 ^ ((P - RSSI) / (10 * n))
 *
 * - `P` = RSSI terukur pada jarak 1 meter (measured power)
 * - `n` = path-loss exponent (2 = ruang bebas, 2.7–4 = dalam ruangan)
 *
 * Nilai default (P = -32 dBm, n = 3.8) dikalibrasi agar hasil numerik konsisten dengan
 * tabel kategori di spesifikasi: -30 dBm ≈ 0.9 m, -50 dBm ≈ 3 m, -70 dBm ≈ 10 m,
 * -80 dBm ≈ 18 m, -90 dBm ≈ 34 m. Jarak dari RSSI hanyalah perkiraan kasar karena
 * sangat dipengaruhi daya pancar, antena, halangan, dan pantulan sinyal.
 */
class DistanceEstimator(
    private val measuredPower: Int = DEFAULT_MEASURED_POWER,
    private val pathLossExponent: Double = DEFAULT_PATH_LOSS_EXPONENT,
) {
    init {
        require(pathLossExponent > 0) { "pathLossExponent harus > 0" }
    }

    fun estimateMeters(rssi: Double): Double =
        10.0.pow((measuredPower - rssi) / (10 * pathLossExponent))

    fun estimateMeters(rssi: Int): Double = estimateMeters(rssi.toDouble())

    companion object {
        const val DEFAULT_MEASURED_POWER = -32
        const val DEFAULT_PATH_LOSS_EXPONENT = 3.8

        val Default = DistanceEstimator()

        /** Format jarak untuk UI, mis. "0.8 m", "4.2 m", "27 m". */
        fun format(meters: Double): String = when {
            meters < 10 -> "%.1f m".format(java.util.Locale.US, meters)
            meters < 100 -> "%.0f m".format(java.util.Locale.US, meters)
            else -> "> 100 m"
        }
    }
}
