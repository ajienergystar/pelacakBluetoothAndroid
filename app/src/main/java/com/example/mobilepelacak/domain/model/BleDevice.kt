package com.example.mobilepelacak.domain.model

import kotlin.math.roundToInt

/** Satu sampel RSSI yang diterima dari paket advertising. */
data class RssiSample(
    val timestamp: Long,
    val rssi: Int,
    val smoothedRssi: Double,
)

/**
 * Representasi perangkat BLE yang sedang/baru saja terdeteksi pada sesi scan aktif.
 *
 * @property rssi nilai RSSI mentah (raw dBm) dari paket advertising terakhir.
 * @property smoothedRssi RSSI setelah difilter (Kalman) untuk meredam fluktuasi.
 */
data class BleDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val smoothedRssi: Double,
    val txPower: Int?,
    val isConnectable: Boolean?,
    val manufacturerId: Int?,
    val serviceUuids: List<String>,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val advertisementCount: Int,
    val rssiHistory: List<RssiSample>,
) {
    /** RSSI yang dipakai untuk pengurutan, filter, zona & estimasi jarak. */
    val signalRssi: Int get() = smoothedRssi.roundToInt()

    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: UNKNOWN_NAME

    val hasName: Boolean get() = !name.isNullOrBlank()

    fun millisSinceSeen(now: Long): Long = (now - lastSeenAt).coerceAtLeast(0)

    fun isLost(now: Long): Boolean = millisSinceSeen(now) > LOST_TIMEOUT_MS

    /** Zona saat ini. Perangkat yang tidak mengirim advertising > [LOST_TIMEOUT_MS] dianggap LOST. */
    fun zoneAt(now: Long): SignalZone =
        if (isLost(now)) SignalZone.LOST else SignalZone.fromRssi(signalRssi)

    companion object {
        const val UNKNOWN_NAME = "Perangkat tanpa nama"

        /** Tanpa paket advertising selama ini, perangkat dianggap hilang/terputus. */
        const val LOST_TIMEOUT_MS = 6_000L

        /** Perangkat yang hilang lebih lama dari ini disembunyikan dari daftar scan. */
        const val HIDE_FROM_LIST_AFTER_MS = 30_000L

        /** Jumlah sampel RSSI yang disimpan per perangkat (untuk grafik & statistik). */
        const val MAX_HISTORY = 90
    }
}
