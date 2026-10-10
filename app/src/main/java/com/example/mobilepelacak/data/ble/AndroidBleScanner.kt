package com.example.mobilepelacak.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import com.example.mobilepelacak.domain.model.ScanError
import com.example.mobilepelacak.domain.repository.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidBleScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val environment: BleEnvironment,
    private val timeProvider: TimeProvider,
) : BleScanner {

    // Izin sudah dicek lewat BleEnvironment sebelum startScan, SecurityException tetap ditangkap
    // untuk kasus izin dicabut saat scan berjalan.
    @SuppressLint("MissingPermission")
    override fun scan(): Flow<Advertisement> = callbackFlow {
        if (!environment.isBleSupported()) throw BleScanException(ScanError.BleUnsupported)
        if (!environment.hasScanPermissions()) throw BleScanException(ScanError.PermissionDenied)

        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: throw BleScanException(ScanError.BleUnsupported)
        if (!adapter.isEnabled) throw BleScanException(ScanError.BluetoothDisabled)
        if (environment.isLocationRequired() && !environment.isLocationEnabled()) {
            throw BleScanException(ScanError.LocationDisabled)
        }

        // bluetoothLeScanner bernilai null ketika Bluetooth sedang dimatikan.
        val leScanner = adapter.bluetoothLeScanner ?: throw BleScanException(ScanError.BluetoothDisabled)

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(result.toAdvertisement())
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { trySend(it.toAdvertisement()) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "onScanFailed: $errorCode")
                close(BleScanException(ScanError.ScanFailed(errorCode)))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setReportDelay(0L)
            .build()

        try {
            leScanner.startScan(null, settings, callback)
            Log.d(TAG, "BLE scan started")
        } catch (e: SecurityException) {
            throw BleScanException(ScanError.PermissionDenied)
        } catch (e: IllegalStateException) {
            // Dilempar bila adapter dimatikan tepat saat startScan dipanggil.
            throw BleScanException(ScanError.BluetoothDisabled)
        }

        awaitClose {
            try {
                if (adapter.isEnabled) leScanner.stopScan(callback)
                Log.d(TAG, "BLE scan stopped")
            } catch (e: SecurityException) {
                Log.w(TAG, "stopScan tanpa izin", e)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "stopScan saat adapter mati", e)
            }
        }
    }.buffer(capacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    @SuppressLint("MissingPermission")
    private fun ScanResult.toAdvertisement(): Advertisement {
        val record = scanRecord
        val name = record?.deviceName?.takeIf { it.isNotBlank() } ?: try {
            // Android 12+: device.name butuh BLUETOOTH_CONNECT.
            device.name
        } catch (e: SecurityException) {
            null
        }
        val tx = when {
            txPower != ScanResult.TX_POWER_NOT_PRESENT -> txPower
            record != null && record.txPowerLevel != Int.MIN_VALUE -> record.txPowerLevel
            else -> null
        }
        val manufacturerData = record?.manufacturerSpecificData
        val manufacturerId = if (manufacturerData != null && manufacturerData.size() > 0) {
            manufacturerData.keyAt(0)
        } else {
            null
        }
        return Advertisement(
            address = device.address,
            name = name,
            rssi = rssi,
            txPower = tx,
            isConnectable = isConnectable,
            manufacturerId = manufacturerId,
            serviceUuids = record?.serviceUuids?.map { it.uuid.toString() }.orEmpty(),
            timestamp = timeProvider.now(),
        )
    }

    private companion object {
        const val TAG = "AndroidBleScanner"
    }
}
