package com.example.mobilepelacak.domain.signal

/**
 * Kalman filter 1-dimensi untuk meredam noise RSSI.
 *
 * RSSI BLE sangat fluktuatif (±5–10 dBm walau perangkat diam) karena multipath & interferensi.
 * Filter ini membuat zona dan estimasi jarak lebih stabil tanpa delay sebesar moving average.
 *
 * @param processNoise (Q) seberapa cepat nilai "sebenarnya" diasumsikan berubah. Lebih besar = lebih responsif.
 * @param measurementNoise (R) seberapa besar noise pengukuran. Lebih besar = lebih halus.
 */
class RssiKalmanFilter(
    private val processNoise: Double = 0.5,
    private val measurementNoise: Double = 4.0,
) {
    private var estimate: Double = 0.0
    private var errorCovariance: Double = 1.0
    private var initialized = false

    fun update(measurement: Double): Double {
        if (!initialized) {
            estimate = measurement
            errorCovariance = measurementNoise
            initialized = true
            return estimate
        }
        // Predict
        val priorCovariance = errorCovariance + processNoise
        // Update
        val gain = priorCovariance / (priorCovariance + measurementNoise)
        estimate += gain * (measurement - estimate)
        errorCovariance = (1 - gain) * priorCovariance
        return estimate
    }

    fun update(measurement: Int): Double = update(measurement.toDouble())

    fun reset() {
        initialized = false
        estimate = 0.0
        errorCovariance = 1.0
    }
}
