package com.example.mobilepelacak.ui.radar

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mobilepelacak.domain.model.Manufacturers
import com.example.mobilepelacak.domain.model.SignalZone
import com.example.mobilepelacak.domain.signal.DistanceEstimator
import com.example.mobilepelacak.domain.signal.SignalTrend
import com.example.mobilepelacak.ui.common.formatDateTime
import com.example.mobilepelacak.ui.common.formatRelative
import com.example.mobilepelacak.ui.components.BleActions
import com.example.mobilepelacak.ui.components.ScanStatusBanner
import com.example.mobilepelacak.ui.components.color
import com.example.mobilepelacak.ui.components.rememberBleActions
import java.util.Locale

@Composable
fun RadarRoute(
    onBack: () -> Unit,
    viewModel: RadarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = rememberBleActions(
        onStartScan = viewModel::startScan,
        onEnvironmentChanged = viewModel::refreshEnvironment,
    )
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshEnvironment() }

    RadarScreen(
        state = state,
        actions = actions,
        onBack = onBack,
        onDismissError = viewModel::dismissError,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(
    state: RadarUiState,
    actions: BleActions,
    onBack: () -> Unit,
    onDismissError: () -> Unit,
) {
    var hapticEnabled by rememberSaveable { mutableStateOf(true) }
    ZoneHapticFeedback(zone = state.zone, enabled = hapticEnabled)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            state.address,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconToggleButton(checked = hapticEnabled, onCheckedChange = { hapticEnabled = it }) {
                        Icon(
                            Icons.Filled.Vibration,
                            contentDescription = if (hapticEnabled) "Matikan getar" else "Nyalakan getar",
                            tint = if (hapticEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.isScanning) LinearProgressIndicator(Modifier.fillMaxWidth())

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ScanStatusBanner(
                    error = state.error,
                    bluetoothStatus = state.bluetoothStatus,
                    actions = actions,
                    onRetry = actions.startScanWithPermission,
                    onDismiss = onDismissError,
                )

                if (!state.isScanRequested) {
                    NotScanningCard(onStart = actions.startScanWithPermission)
                } else if (state.isLost && state.isScanning) {
                    LostSignalCard(state)
                }

                ProximityRadar(
                    zone = state.zone,
                    distanceMeters = state.distanceMeters,
                    address = state.address,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .align(Alignment.CenterHorizontally),
                )

                ZoneCard(state)
                SignalChartCard(state)
                DetailCard(state)
            }
        }
    }
}

/** Getar setiap kali target berpindah ke zona yang lebih dekat. */
@Composable
private fun ZoneHapticFeedback(zone: SignalZone, enabled: Boolean) {
    val haptic = LocalHapticFeedback.current
    val previous = remember { mutableStateOf(zone) }
    LaunchedEffect(zone) {
        if (enabled && zone != SignalZone.LOST && zone.isCloserThan(previous.value)) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        previous.value = zone
    }
}

@Composable
private fun NotScanningCard(onStart: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Pemindaian tidak aktif", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Mulai scan untuk melacak kedekatan perangkat ini secara real-time.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onStart) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text("Mulai Scan", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun LostSignalCard(state: RadarUiState) {
    val since = state.lastSeenAt?.let { formatRelative(state.now, it) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Sinyal hilang", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = buildString {
                    append(
                        if (since != null) "Tidak ada paket advertising sejak $since. " else "Perangkat belum terdeteksi pada sesi scan ini. "
                    )
                    append("Perangkat mungkin mati, terputus, atau di luar jangkauan. Terus mencari…")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ZoneCard(state: RadarUiState) {
    val zone = state.zone
    val container by animateColorAsState(zone.color().copy(alpha = 0.18f), label = "zoneContainer")
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = zone.label.uppercase(Locale.ROOT),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = zone.color(),
            )
            Text(zone.proximityLabel, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.distanceMeters?.let { "≈ ${DistanceEstimator.format(it)}" } ?: "—",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Rentang zona: ${zone.distanceLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Metric(label = "RSSI raw", value = state.device?.takeIf { !state.isLost }?.let { "${it.rssi} dBm" } ?: "—")
                Metric(
                    label = "RSSI filter",
                    value = state.device?.takeIf { !state.isLost }?.let { "%.1f dBm".format(Locale.US, it.smoothedRssi) } ?: "—",
                )
                Metric(label = "Arah", value = trendText(state.trend), valueColor = trendColor(state.trend))
            }
        }
    }
}

private fun trendText(trend: SignalTrend): String = when (trend) {
    SignalTrend.APPROACHING -> "▲ ${trend.label}"
    SignalTrend.RECEDING -> "▼ ${trend.label}"
    SignalTrend.STEADY -> "● ${trend.label}"
    SignalTrend.UNKNOWN -> trend.label
}

private fun trendColor(trend: SignalTrend): Color? = when (trend) {
    SignalTrend.APPROACHING -> SignalZone.VERY_STRONG.color()
    SignalTrend.RECEDING -> SignalZone.WEAK.color()
    else -> null
}

@Composable
private fun Metric(label: String, value: String, valueColor: Color? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SignalChartCard(state: RadarUiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Riwayat sinyal (±${state.samples.size} sampel)", style = MaterialTheme.typography.titleSmall)
            Text(
                "Titik = RSSI raw, garis = RSSI setelah Kalman filter",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            RssiChart(
                samples = state.samples,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            )
        }
    }
}

@Composable
private fun DetailCard(state: RadarUiState) {
    val device = state.device
    val history = state.history
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Detail koneksi & stabilitas", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            DetailRow(
                "Stabilitas sinyal",
                state.standardDeviation?.let { "${state.stability.label} (σ %.1f dB)".format(Locale.US, it) }
                    ?: state.stability.label,
            )
            DetailRow(
                "Paket advertising",
                state.advertisementRate?.let { "%.1f paket/detik".format(Locale.US, it) } ?: "—",
            )
            DetailRow("Terakhir terlihat", state.lastSeenAt?.let { formatRelative(state.now, it) } ?: "—")
            DetailRow("Total paket sesi ini", device?.advertisementCount?.toString() ?: "—")
            DetailRow("Tx Power", device?.txPower?.let { "$it dBm" } ?: "Tidak diiklankan")
            DetailRow(
                "Connectable",
                when (device?.isConnectable) {
                    true -> "Ya"
                    false -> "Tidak"
                    null -> "—"
                },
            )
            DetailRow(
                "Vendor",
                Manufacturers.nameOf(device?.manufacturerId ?: history?.manufacturerId) ?: "Tidak diketahui",
            )
            if (!device?.serviceUuids.isNullOrEmpty()) {
                DetailRow("Service UUID", device!!.serviceUuids.joinToString("\n"))
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            DetailRow("Pertama tercatat", history?.firstSeenAt?.let(::formatDateTime) ?: "—")
            DetailRow("RSSI terbaik", history?.bestRssi?.let { "$it dBm" } ?: "—")
            DetailRow("Terdeteksi pada", history?.sessionCount?.let { "$it sesi scan" } ?: "—")
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.55f),
        )
    }
}
