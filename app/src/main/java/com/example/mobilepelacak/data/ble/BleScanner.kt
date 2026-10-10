package com.example.mobilepelacak.data.ble

import com.example.mobilepelacak.domain.model.ScanError
import kotlinx.coroutines.flow.Flow

/** Satu paket advertising yang diterima scanner. */
data class Advertisement(
    val address: String,
    val name: String?,
    val rssi: Int,
    val txPower: Int?,
    val isConnectable: Boolean?,
    val manufacturerId: Int?,
    val serviceUuids: List<String>,
    val timestamp: Long,
)

/** Exception yang membawa [ScanError] agar mudah dipetakan ke UI. */
class BleScanException(val error: ScanError) : RuntimeException(error.message)

interface BleScanner {
    /**
     * Cold flow: scan dimulai saat flow di-collect dan otomatis berhenti saat collector dibatalkan.
     * Error (izin, Bluetooth mati, scan gagal) dilempar sebagai [BleScanException].
     */
    fun scan(): Flow<Advertisement>
}
