package com.example.mobilepelacak.ui.history

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mobilepelacak.domain.model.HistoryDevice
import com.example.mobilepelacak.domain.model.Manufacturers
import com.example.mobilepelacak.domain.model.SignalZone
import com.example.mobilepelacak.ui.common.formatDateTime
import com.example.mobilepelacak.ui.common.formatRelative
import com.example.mobilepelacak.ui.components.SignalBars
import com.example.mobilepelacak.ui.components.color

@Composable
fun HistoryRoute(
    onDeviceClick: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onDelete = viewModel::delete,
        onClearAll = viewModel::clearAll,
        onDeviceClick = onDeviceClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onQueryChange: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit,
    onDeviceClick: (String) -> Unit,
) {
    var showClearDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Riwayat Perangkat", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${state.totalCount} perangkat tersimpan",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showClearDialog = true }, enabled = state.totalCount > 0) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Hapus semua riwayat")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Cari nama atau MAC address") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Hapus pencarian")
                        }
                    }
                },
                singleLine = true,
            )

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.devices.isEmpty() -> Box(
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (state.totalCount == 0) {
                            "Belum ada riwayat. Perangkat yang terdeteksi saat scan akan tersimpan otomatis di sini."
                        } else {
                            "Tidak ada riwayat yang cocok dengan pencarian."
                        },
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.devices, key = { it.address }) { device ->
                        HistoryRow(
                            device = device,
                            now = state.now,
                            onClick = { onDeviceClick(device.address) },
                            onDelete = { onDelete(device.address) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Hapus semua riwayat?") },
            text = { Text("Seluruh ${state.totalCount} perangkat di database lokal akan dihapus. Tindakan ini tidak bisa dibatalkan.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearAll()
                    showClearDialog = false
                }) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Batal") }
            },
        )
    }
}

@Composable
private fun HistoryRow(
    device: HistoryDevice,
    now: Long,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = SignalZone.fromRssi(device.lastRssi)
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SignalBars(zone, Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    device.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontStyle = if (device.name.isNullOrBlank()) FontStyle.Italic else FontStyle.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    device.address,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Terakhir ditemukan ${formatRelative(now, device.lastSeenAt)} • ${formatDateTime(device.lastSeenAt)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    buildString {
                        append("RSSI terakhir ")
                        append(device.lastRssi)
                        append(" dBm • ")
                        append(device.sessionCount)
                        append(" sesi")
                        Manufacturers.nameOf(device.manufacturerId)?.let { append(" • ").append(it) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = zone.color(),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Close, contentDescription = "Hapus ${device.displayName} dari riwayat")
            }
        }
    }
}
