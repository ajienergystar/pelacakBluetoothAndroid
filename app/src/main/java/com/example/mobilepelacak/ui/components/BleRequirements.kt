package com.example.mobilepelacak.ui.components

import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.mobilepelacak.data.ble.BlePermissions
import com.example.mobilepelacak.domain.model.BluetoothStatus
import com.example.mobilepelacak.domain.model.ScanError

/**
 * Aksi UI untuk memenuhi prasyarat scan: izin runtime, menyalakan Bluetooth, dan membuka Settings.
 */
class BleActions(
    val startScanWithPermission: () -> Unit,
    val enableBluetooth: () -> Unit,
    val openAppSettings: () -> Unit,
    val openLocationSettings: () -> Unit,
    val isPermissionPermanentlyDenied: Boolean,
)

@Composable
fun rememberBleActions(
    onStartScan: () -> Unit,
    onEnvironmentChanged: () -> Unit,
): BleActions {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val currentOnStartScan by rememberUpdatedState(onStartScan)
    val currentOnEnvironmentChanged by rememberUpdatedState(onEnvironmentChanged)
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.isNotEmpty() && result.values.all { it }
        permanentlyDenied = !granted && activity != null &&
            BlePermissions.required.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        // Tetap panggil start: jika izin ditolak, repository akan memunculkan error PermissionDenied
        // sehingga UI menampilkan penjelasan + tombol aksi.
        currentOnStartScan()
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { currentOnEnvironmentChanged() }

    return remember(permanentlyDenied, context) {
        BleActions(
            startScanWithPermission = {
                if (BlePermissions.hasAll(context)) {
                    currentOnStartScan()
                } else {
                    permissionLauncher.launch(BlePermissions.required)
                }
            },
            enableBluetooth = {
                if (!BlePermissions.hasAll(context)) {
                    // Android 12+: ACTION_REQUEST_ENABLE butuh BLUETOOTH_CONNECT.
                    permissionLauncher.launch(BlePermissions.required)
                } else {
                    try {
                        enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    } catch (e: SecurityException) {
                        context.safeStartActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    } catch (e: ActivityNotFoundException) {
                        context.safeStartActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    }
                }
            },
            openAppSettings = {
                context.safeStartActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    )
                )
            },
            openLocationSettings = {
                context.safeStartActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            },
            isPermissionPermanentlyDenied = permanentlyDenied,
        )
    }
}

private fun Context.safeStartActivity(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Tidak ada aplikasi Settings yang cocok; abaikan.
    }
}

/**
 * Banner status/error yang menjelaskan kondisi ke user beserta aksi untuk memperbaikinya.
 * Mengembalikan tanpa render jika tidak ada yang perlu ditampilkan.
 */
@Composable
fun ScanStatusBanner(
    error: ScanError?,
    bluetoothStatus: BluetoothStatus,
    actions: BleActions,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        error != null -> {
            val (icon, actionLabel, action) = when (error) {
                ScanError.PermissionDenied -> Triple(
                    Icons.Filled.Lock,
                    if (actions.isPermissionPermanentlyDenied) "Buka Pengaturan" else "Berikan Izin",
                    if (actions.isPermissionPermanentlyDenied) actions.openAppSettings else actions.startScanWithPermission,
                )
                ScanError.BluetoothDisabled -> Triple(Icons.Filled.BluetoothDisabled, "Nyalakan", actions.enableBluetooth)
                ScanError.LocationDisabled -> Triple(Icons.Filled.LocationOff, "Aktifkan Lokasi", actions.openLocationSettings)
                ScanError.BleUnsupported -> Triple(Icons.Filled.ErrorOutline, null, null)
                is ScanError.Throttled -> Triple(Icons.Filled.Info, null, null)
                is ScanError.ScanFailed, is ScanError.Unexpected -> Triple(Icons.Filled.ErrorOutline, "Coba Lagi", onRetry)
            }
            BannerCard(
                icon = icon,
                message = error.message,
                isError = error !is ScanError.Throttled,
                actionLabel = actionLabel,
                onAction = action,
                onDismiss = if (error is ScanError.Throttled) null else onDismiss,
                modifier = modifier,
            )
        }

        bluetoothStatus == BluetoothStatus.OFF || bluetoothStatus == BluetoothStatus.TURNING_OFF -> BannerCard(
            icon = Icons.Filled.BluetoothDisabled,
            message = "Bluetooth nonaktif. Nyalakan Bluetooth untuk mulai memindai perangkat sekitar.",
            isError = false,
            actionLabel = "Nyalakan",
            onAction = actions.enableBluetooth,
            onDismiss = null,
            modifier = modifier,
        )

        bluetoothStatus == BluetoothStatus.UNSUPPORTED -> BannerCard(
            icon = Icons.Filled.ErrorOutline,
            message = ScanError.BleUnsupported.message,
            isError = true,
            actionLabel = null,
            onAction = null,
            onDismiss = null,
            modifier = modifier,
        )
    }
}

@Composable
private fun BannerCard(
    icon: ImageVector,
    message: String,
    isError: Boolean,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = if (isError) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
    Card(modifier = modifier.fillMaxWidth(), colors = colors) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(icon, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (onDismiss != null) {
                    TextButton(onClick = onDismiss) { Text("Tutup") }
                }
                if (actionLabel != null && onAction != null) {
                    TextButton(onClick = onAction) { Text(actionLabel) }
                }
            }
        }
    }
}
