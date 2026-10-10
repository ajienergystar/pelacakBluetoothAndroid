package com.example.mobilepelacak.ui.radar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobilepelacak.domain.model.BleDevice
import com.example.mobilepelacak.domain.model.BluetoothStatus
import com.example.mobilepelacak.domain.model.HistoryDevice
import com.example.mobilepelacak.domain.model.ScanError
import com.example.mobilepelacak.domain.model.SignalZone
import com.example.mobilepelacak.domain.repository.HistoryRepository
import com.example.mobilepelacak.domain.repository.ScanRepository
import com.example.mobilepelacak.domain.repository.TimeProvider
import com.example.mobilepelacak.domain.signal.DistanceEstimator
import com.example.mobilepelacak.domain.signal.SignalAnalyzer
import com.example.mobilepelacak.domain.signal.SignalStability
import com.example.mobilepelacak.domain.signal.SignalTrend
import com.example.mobilepelacak.ui.common.tickerFlow
import com.example.mobilepelacak.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class RadarUiState(
    val address: String,
    val device: BleDevice? = null,
    val history: HistoryDevice? = null,
    val now: Long = 0L,
    val isScanning: Boolean = false,
    val isScanRequested: Boolean = false,
    val bluetoothStatus: BluetoothStatus = BluetoothStatus.ON,
    val error: ScanError? = null,
) {
    val zone: SignalZone get() = device?.zoneAt(now) ?: SignalZone.LOST
    val isLost: Boolean get() = zone == SignalZone.LOST
    val name: String get() = device?.displayName ?: history?.displayName ?: BleDevice.UNKNOWN_NAME
    val hasName: Boolean get() = device?.hasName == true || !history?.name.isNullOrBlank()

    val distanceMeters: Double?
        get() = device?.takeIf { !isLost }?.let { DistanceEstimator.Default.estimateMeters(it.smoothedRssi) }

    val samples get() = device?.rssiHistory.orEmpty()
    val stability: SignalStability get() = SignalAnalyzer.stability(samples)
    val standardDeviation: Double? get() = SignalAnalyzer.standardDeviation(samples)
    val trend: SignalTrend get() = if (isLost) SignalTrend.UNKNOWN else SignalAnalyzer.trend(samples)
    val advertisementRate: Double? get() = SignalAnalyzer.advertisementRate(samples)
    val lastSeenAt: Long? get() = device?.lastSeenAt ?: history?.lastSeenAt
}

@HiltViewModel
class RadarViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val scanRepository: ScanRepository,
    historyRepository: HistoryRepository,
    timeProvider: TimeProvider,
) : ViewModel() {

    val address: String = checkNotNull(savedStateHandle[Routes.ARG_ADDRESS]) {
        "Radar membutuhkan argumen address"
    }

    val uiState: StateFlow<RadarUiState> = combine(
        scanRepository.state,
        historyRepository.observeDevice(address),
        tickerFlow(TICK_MS, timeProvider),
    ) { scan, history, now ->
        RadarUiState(
            address = address,
            device = scan.devices[address],
            history = history,
            now = now,
            isScanning = scan.isScanning,
            isScanRequested = scan.isScanRequested,
            bluetoothStatus = scan.bluetoothStatus,
            error = scan.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RadarUiState(address = address),
    )

    fun startScan() = scanRepository.startScan()
    fun stopScan() = scanRepository.stopScan()
    fun dismissError() = scanRepository.dismissError()
    fun refreshEnvironment() = scanRepository.refreshEnvironment()

    private companion object {
        const val TICK_MS = 500L
    }
}
