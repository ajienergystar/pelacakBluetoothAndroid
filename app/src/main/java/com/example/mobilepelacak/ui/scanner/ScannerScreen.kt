package com.example.mobilepelacak.ui.scanner

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mobilepelacak.domain.model.BleDevice
import com.example.mobilepelacak.domain.model.DeviceFilter
import com.example.mobilepelacak.domain.model.SignalZone
import com.example.mobilepelacak.domain.signal.DistanceEstimator
import com.example.mobilepelacak.ui.common.formatRelative
import com.example.mobilepelacak.ui.components.BleActions
import com.example.mobilepelacak.ui.components.ScanStatusBanner
import com.example.mobilepelacak.ui.components.SignalBars
import com.example.mobilepelacak.ui.components.ZoneChip
import com.example.mobilepelacak.ui.components.color
import com.example.mobilepelacak.ui.components.rememberBleActions

@Composable
fun ScannerRoute(
    onDeviceClick: (String) -> Unit,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = rememberBleActions(
        onStartScan = viewModel::startScan,
        onEnvironmentChanged = viewModel::refreshEnvironment,
    )
    // Kembali dari Settings (izin / lokasi / Bluetooth) → cek ulang kondisi.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshEnvironment() }

    ScannerScreen(
        state = state,
        actions = actions,
        onStop = viewModel::stopScan,
        onQueryChange = viewModel::onQueryChange,
        onMinRssiChange = viewModel::onMinRssiChange,
        onNamedOnlyChange = viewModel::onNamedOnlyChange,
        onClearFilters = viewModel::clearFilters,
        onClearDevices = viewModel::clearDevices,
        onDismissError = viewModel::dismissError,
        onDeviceClick = onDeviceClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    state: ScannerUiState,
    actions: BleActions,
    onStop: () -> Unit,
    onQueryChange: (String) -> Unit,
    onMinRssiChange: (Int?) -> Unit,
    onNamedOnlyChange: (Boolean) -> Unit,
    onClearFilters: () -> Unit,
    onClearDevices: () -> Unit,
    onDismissError: () -> Unit,
    onDeviceClick: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Pemindai BLE", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = statusSubtitle(state),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onClearDevices, enabled = state.visibleDeviceCount > 0) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Bersihkan daftar")
                    }
                },
            )
        },
        floatingActionButton = {
            val scanActive = state.isScanRequested
            ExtendedFloatingActionButton(
                onClick = { if (scanActive) onStop() else actions.startScanWithPermission() },
                icon = {
                    Icon(
                        if (scanActive) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = null,
                    )
                },
                text = { Text(if (scanActive) "Stop" else "Start") },
                containerColor = if (scanActive) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.isScanning) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Spacer(Modifier.height(4.dp))
            }

            ScanStatusBanner(
                error = state.error,
                bluetoothStatus = state.bluetoothStatus,
                actions = actions,
                onRetry = actions.startScanWithPermission,
                onDismiss = onDismissError,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            FilterSection(
                filter = state.filter,
                onQueryChange = onQueryChange,
                onMinRssiChange = onMinRssiChange,
                onNamedOnlyChange = onNamedOnlyChange,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (state.filter.isActive) {
                        "${state.devices.size} dari ${state.visibleDeviceCount} perangkat (terfilter)"
                    } else {
                        "${state.visibleDeviceCount} perangkat • ${state.activeDeviceCount} aktif"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (state.filter.isActive) {
                    TextButton(onClick = onClearFilters) { Text("Reset filter") }
                }
            }

            if (state.devices.isEmpty()) {
                EmptyState(state)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.devices, key = { it.address }) { device ->
                        DeviceRow(
                            device = device,
                            now = state.now,
                            onClick = { onDeviceClick(device.address) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

private fun statusSubtitle(state: ScannerUiState): String = when {
    state.isScanning -> "Memindai perangkat di sekitar…"
    state.isScanRequested && state.isPausedInBackground -> "Dijeda (aplikasi di background)"
    state.isScanRequested -> "Menunggu Bluetooth / izin…"
    else -> "Pemindaian berhenti"
}

@Composable
private fun FilterSection(
    filter: DeviceFilter,
    onQueryChange: (String) -> Unit,
    onMinRssiChange: (Int?) -> Unit,
    onNamedOnlyChange: (Boolean) -> Unit,
) {
    OutlinedTextField(
        value = filter.query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        placeholder = { Text("Cari nama atau MAC address") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (filter.query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Hapus pencarian")
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = filter.minRssi == null,
            onClick = { onMinRssiChange(null) },
            label = { Text("Semua sinyal") },
        )
        DeviceFilter.RSSI_PRESETS.forEach { threshold ->
            FilterChip(
                selected = filter.minRssi == threshold,
                onClick = { onMinRssiChange(if (filter.minRssi == threshold) null else threshold) },
                label = { Text("≥ $threshold dBm") },
            )
        }
        FilterChip(
            selected = filter.namedOnly,
            onClick = { onNamedOnlyChange(!filter.namedOnly) },
            label = { Text("Bernama saja") },
        )
    }
}

@Composable
private fun DeviceRow(
    device: BleDevice,
    now: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = device.zoneAt(now)
    val lost = zone == SignalZone.LOST
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (lost) 0.55f else 1f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SignalBars(zone, Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = device.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontStyle = if (device.hasName) FontStyle.Normal else FontStyle.Italic,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ZoneChip(zone)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (lost) {
                            "Terakhir ${formatRelative(now, device.lastSeenAt)}"
                        } else {
                            "≈ ${DistanceEstimator.format(DistanceEstimator.Default.estimateMeters(device.smoothedRssi))} (${zone.distanceLabel})"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${device.rssi}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = zone.color(),
                )
                Text(
                    text = "dBm (raw)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(state: ScannerUiState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                state.isScanning && state.visibleDeviceCount == 0 -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Mencari perangkat BLE di sekitar…", textAlign = TextAlign.Center)
                }
                state.visibleDeviceCount > 0 -> {
                    Text(
                        "Tidak ada perangkat yang cocok dengan filter.",
                        textAlign = TextAlign.Center,
                    )
                }
                else -> {
                    Text(
                        "Belum ada perangkat",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tekan tombol Start untuk mulai memindai sinyal advertising Bluetooth Low Energy di sekitar Anda.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
