package com.example.mobilepelacak.data.ble

import com.example.mobilepelacak.domain.model.BluetoothStatus
import kotlinx.coroutines.flow.Flow

/**
 * Membungkus pengecekan kondisi sistem (izin, dukungan BLE, status Bluetooth & lokasi)
 * supaya repository tidak bergantung langsung ke API Android.
 */
interface BleEnvironment {
    fun isBleSupported(): Boolean
    fun hasScanPermissions(): Boolean
    fun currentBluetoothStatus(): BluetoothStatus
    fun observeBluetoothStatus(): Flow<BluetoothStatus>

    /** Android ≤ 11 mewajibkan Location Services aktif agar hasil scan BLE dikirim. */
    fun isLocationRequired(): Boolean
    fun isLocationEnabled(): Boolean
}
