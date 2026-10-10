package com.example.mobilepelacak.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.example.mobilepelacak.domain.model.BluetoothStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/** Izin runtime yang dibutuhkan untuk scan BLE, berbeda per versi Android. */
object BlePermissions {
    val required: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun hasAll(context: Context): Boolean = required.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

// adapter.state / isEnabled dilindungi izin BLUETOOTH (≤ API 30) yang sudah dideklarasikan di manifest.
@SuppressLint("MissingPermission")
@Singleton
class AndroidBleEnvironment @Inject constructor(
    @ApplicationContext private val context: Context,
) : BleEnvironment {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(BluetoothManager::class.java)

    override fun isBleSupported(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) &&
            bluetoothManager?.adapter != null

    override fun hasScanPermissions(): Boolean = BlePermissions.hasAll(context)

    override fun currentBluetoothStatus(): BluetoothStatus {
        val adapter = bluetoothManager?.adapter ?: return BluetoothStatus.UNSUPPORTED
        return mapState(adapter.state)
    }

    override fun observeBluetoothStatus(): Flow<BluetoothStatus> = callbackFlow {
        trySend(currentBluetoothStatus())
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    // Baca ulang dari adapter (bukan hanya dari extra) sebagai sumber kebenaran.
                    trySend(currentBluetoothStatus())
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            // ACTION_STATE_CHANGED adalah protected broadcast (hanya bisa dikirim sistem).
            // RECEIVER_NOT_EXPORTED via ContextCompat di Android < 13 memakai permission internal
            // yang bisa memblokir broadcast dari proses Bluetooth sistem, jadi pakai EXPORTED.
            ContextCompat.RECEIVER_EXPORTED,
        )
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    override fun isLocationRequired(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S

    override fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(LocationManager::class.java) ?: return false
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    private fun mapState(state: Int): BluetoothStatus = when (state) {
        BluetoothAdapter.STATE_ON -> BluetoothStatus.ON
        BluetoothAdapter.STATE_TURNING_ON -> BluetoothStatus.TURNING_ON
        BluetoothAdapter.STATE_TURNING_OFF -> BluetoothStatus.TURNING_OFF
        else -> BluetoothStatus.OFF
    }
}
