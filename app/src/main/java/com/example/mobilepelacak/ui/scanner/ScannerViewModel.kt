package com.example.mobilepelacak.ui.scanner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobilepelacak.domain.model.BleDevice
import com.example.mobilepelacak.domain.model.BluetoothStatus
import com.example.mobilepelacak.domain.model.DeviceFilter
import com.example.mobilepelacak.domain.model.ScanError
import com.example.mobilepelacak.domain.model.filterAndSort
import com.example.mobilepelacak.domain.repository.ScanRepository
import com.example.mobilepelacak.domain.repository.TimeProvider
import com.example.mobilepelacak.ui.common.tickerFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ScannerUiState(
    val devices: List<BleDevice> = emptyList(),
    val visibleDeviceCount: Int = 0,
    val activeDeviceCount: Int = 0,
    val now: Long = 0L,
    val isScanning: Boolean = false,
    val isScanRequested: Boolean = false,
    val isPausedInBackground: Boolean = false,
    val bluetoothStatus: BluetoothStatus = BluetoothStatus.ON,
    val error: ScanError? = null,
    val filter: DeviceFilter = DeviceFilter(),
)

private data class ScanControl(
    val isScanning: Boolean,
    val isScanRequested: Boolean,
    val isPausedInBackground: Boolean,
    val bluetoothStatus: BluetoothStatus,
    val error: ScanError?,
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
    private val savedStateHandle: SavedStateHandle,
    timeProvider: TimeProvider,
) : ViewModel() {

    // Disimpan di SavedStateHandle agar filter bertahan saat rotasi & process death.
    private val filter = combine(
        savedStateHandle.getStateFlow(KEY_QUERY, ""),
        savedStateHandle.getStateFlow(KEY_MIN_RSSI, NO_THRESHOLD),
        savedStateHandle.getStateFlow(KEY_NAMED_ONLY, false),
    ) { query, minRssi, namedOnly ->
        DeviceFilter(query = query, minRssi = minRssi.takeIf { it != NO_THRESHOLD }, namedOnly = namedOnly)
    }

    private val control = scanRepository.state
        .map { ScanControl(it.isScanning, it.isScanRequested, it.isPausedInBackground, it.bluetoothStatus, it.error) }
        .distinctUntilChanged()

    // Daftar perangkat di-sample tiap 500 ms supaya urutan list tidak "loncat-loncat" pada setiap
    // paket advertising, sementara status kontrol (Start/Stop, error) tetap langsung ter-update.
    @OptIn(FlowPreview::class)
    private val devices = scanRepository.state
        .map { it.devices }
        .distinctUntilChanged()
        .sample(LIST_REFRESH_MS)

    val uiState: StateFlow<ScannerUiState> = combine(
        control,
        devices,
        filter,
        tickerFlow(1_000L, timeProvider),
    ) { control, devices, filter, now ->
        val all = devices.values.toList()
        ScannerUiState(
            devices = all.filterAndSort(filter, now),
            visibleDeviceCount = all.count { it.millisSinceSeen(now) <= BleDevice.HIDE_FROM_LIST_AFTER_MS },
            activeDeviceCount = all.count { !it.isLost(now) },
            now = now,
            isScanning = control.isScanning,
            isScanRequested = control.isScanRequested,
            isPausedInBackground = control.isPausedInBackground,
            bluetoothStatus = control.bluetoothStatus,
            error = control.error,
            filter = filter,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScannerUiState(bluetoothStatus = scanRepository.state.value.bluetoothStatus),
    )

    fun onQueryChange(query: String) {
        savedStateHandle[KEY_QUERY] = query
    }

    fun onMinRssiChange(minRssi: Int?) {
        savedStateHandle[KEY_MIN_RSSI] = minRssi ?: NO_THRESHOLD
    }

    fun onNamedOnlyChange(namedOnly: Boolean) {
        savedStateHandle[KEY_NAMED_ONLY] = namedOnly
    }

    fun clearFilters() {
        onQueryChange("")
        onMinRssiChange(null)
        onNamedOnlyChange(false)
    }

    fun startScan() = scanRepository.startScan()
    fun stopScan() = scanRepository.stopScan()
    fun clearDevices() = scanRepository.clearDevices()
    fun dismissError() = scanRepository.dismissError()
    fun refreshEnvironment() = scanRepository.refreshEnvironment()

    private companion object {
        const val KEY_QUERY = "query"
        const val KEY_MIN_RSSI = "min_rssi"
        const val KEY_NAMED_ONLY = "named_only"
        const val NO_THRESHOLD = Int.MIN_VALUE
        const val LIST_REFRESH_MS = 500L
    }
}
