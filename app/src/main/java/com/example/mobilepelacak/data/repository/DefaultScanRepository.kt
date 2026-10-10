package com.example.mobilepelacak.data.repository

import android.util.Log
import com.example.mobilepelacak.data.ble.Advertisement
import com.example.mobilepelacak.data.ble.BleEnvironment
import com.example.mobilepelacak.data.ble.BleScanException
import com.example.mobilepelacak.data.ble.BleScanner
import com.example.mobilepelacak.data.local.DeviceDao
import com.example.mobilepelacak.data.local.DeviceSighting
import com.example.mobilepelacak.di.ScanScope
import com.example.mobilepelacak.domain.model.BleDevice
import com.example.mobilepelacak.domain.model.BluetoothStatus
import com.example.mobilepelacak.domain.model.RssiSample
import com.example.mobilepelacak.domain.model.ScanError
import com.example.mobilepelacak.domain.model.ScanState
import com.example.mobilepelacak.domain.repository.ScanRepository
import com.example.mobilepelacak.domain.repository.TimeProvider
import com.example.mobilepelacak.domain.signal.RssiKalmanFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Implementasi [ScanRepository].
 *
 * Konkurensi: semua state internal (job, filter Kalman, buffer persistensi) hanya diakses dari
 * [scope] yang memakai dispatcher single-thread (lihat `CoroutineModule`), sehingga aman tanpa lock.
 * Method publik hanya men-*dispatch* perintah ke scope tersebut.
 */
@Singleton
class DefaultScanRepository @Inject constructor(
    private val scanner: BleScanner,
    private val environment: BleEnvironment,
    private val dao: DeviceDao,
    private val timeProvider: TimeProvider,
    @ScanScope private val scope: CoroutineScope,
) : ScanRepository {

    private val _state = MutableStateFlow(
        ScanState(bluetoothStatus = environment.currentBluetoothStatus())
    )
    override val state: StateFlow<ScanState> = _state.asStateFlow()

    // ---- State internal, hanya disentuh dari `scope` ----
    private var scanJob: Job? = null
    private var scanRequested = false
    private var inBackground = false
    private val kalmanFilters = HashMap<String, RssiKalmanFilter>()
    private val bestRssi = HashMap<String, Int>()
    private val pendingWrites = LinkedHashMap<String, BleDevice>()
    private val persistedThisSession = HashSet<String>()
    private val startTimestamps = ArrayDeque<Long>()

    init {
        scope.launch {
            environment.observeBluetoothStatus().collect { onBluetoothStatusChanged(it) }
        }
    }

    // ---------------------------------------------------------------- Public API

    override fun startScan() {
        scope.launch {
            scanRequested = true
            persistedThisSession.clear()
            _state.update {
                it.copy(isScanRequested = true, error = null, sessionStartedAt = timeProvider.now())
            }
            startHardwareScan()
        }
    }

    override fun stopScan() {
        scope.launch {
            scanRequested = false
            stopHardwareScan()
            _state.update { it.copy(isScanRequested = false, isPausedInBackground = false, error = null) }
        }
    }

    override fun clearDevices() {
        scope.launch {
            kalmanFilters.clear()
            bestRssi.clear()
            _state.update { it.copy(devices = emptyMap()) }
        }
    }

    override fun dismissError() {
        scope.launch { _state.update { it.copy(error = null) } }
    }

    override fun refreshEnvironment() {
        scope.launch {
            _state.update { it.copy(bluetoothStatus = environment.currentBluetoothStatus()) }
            val resolved = when (_state.value.error) {
                ScanError.PermissionDenied -> environment.hasScanPermissions()
                ScanError.LocationDisabled -> !environment.isLocationRequired() || environment.isLocationEnabled()
                ScanError.BluetoothDisabled -> environment.currentBluetoothStatus().isOn
                else -> false
            }
            if (resolved) _state.update { it.copy(error = null) }
            if (scanRequested) startHardwareScan()
        }
    }

    override fun onAppBackground() {
        scope.launch {
            inBackground = true
            if (scanJob?.isActive == true) {
                // Hemat baterai & patuhi batasan background Android: scan dihentikan
                // dan otomatis dilanjutkan saat app kembali ke foreground.
                stopHardwareScan()
                _state.update { it.copy(isPausedInBackground = true) }
            }
        }
    }

    override fun onAppForeground() {
        scope.launch {
            inBackground = false
            _state.update { it.copy(isPausedInBackground = false) }
            if (scanRequested) startHardwareScan()
        }
    }

    // ---------------------------------------------------------------- Internal

    private fun onBluetoothStatusChanged(status: BluetoothStatus) {
        _state.update { it.copy(bluetoothStatus = status) }
        if (!status.isOn) {
            if (scanJob?.isActive == true) stopHardwareScan()
            if (scanRequested) _state.update { it.copy(error = ScanError.BluetoothDisabled) }
        } else {
            if (_state.value.error == ScanError.BluetoothDisabled) {
                _state.update { it.copy(error = null) }
            }
            // Bluetooth dinyalakan kembali → lanjutkan scan jika user masih menginginkannya.
            if (scanRequested) startHardwareScan()
        }
    }

    private fun preflightError(): ScanError? = when {
        !environment.isBleSupported() -> ScanError.BleUnsupported
        !environment.hasScanPermissions() -> ScanError.PermissionDenied
        !environment.currentBluetoothStatus().isOn -> ScanError.BluetoothDisabled
        environment.isLocationRequired() && !environment.isLocationEnabled() -> ScanError.LocationDisabled
        else -> null
    }

    private fun startHardwareScan() {
        if (scanJob?.isActive == true) return
        if (!scanRequested || inBackground) return

        preflightError()?.let { error ->
            handleScanError(error)
            return
        }

        val waitMs = throttleDelay()
        scanJob = scope.launch {
            if (waitMs > 0) {
                _state.update { it.copy(error = ScanError.Throttled(waitMs)) }
                delay(waitMs)
                _state.update { s -> if (s.error is ScanError.Throttled) s.copy(error = null) else s }
            }
            startTimestamps.addLast(timeProvider.now())
            _state.update { it.copy(isScanning = true, isPausedInBackground = false) }

            val persistJob = launch { persistLoop() }
            try {
                scanner.scan().collect { onAdvertisement(it) }
            } catch (e: BleScanException) {
                handleScanError(e.error)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Scan error", e)
                handleScanError(ScanError.Unexpected(e.message))
            } finally {
                persistJob.cancel()
                _state.update { it.copy(isScanning = false) }
                withContext(NonCancellable) { flushPendingWrites() }
            }
        }
    }

    private fun stopHardwareScan() {
        scanJob?.cancel()
        scanJob = null
        _state.update { it.copy(isScanning = false) }
    }

    private fun handleScanError(error: ScanError) {
        // Bluetooth mati / lokasi mati: tetap "requested" agar scan otomatis berlanjut
        // setelah kondisi pulih. Error lain butuh aksi user (izin, retry), jadi request dibatalkan.
        val keepRequest = error == ScanError.BluetoothDisabled || error == ScanError.LocationDisabled
        if (!keepRequest) scanRequested = false
        _state.update {
            it.copy(
                error = error,
                isScanning = false,
                isScanRequested = if (keepRequest) it.isScanRequested else false,
            )
        }
    }

    private fun onAdvertisement(ad: Advertisement) {
        val now = ad.timestamp
        val current = _state.value.devices[ad.address]
        val filter = kalmanFilters.getOrPut(ad.address) { RssiKalmanFilter() }
        // Perangkat muncul lagi setelah hilang: mulai ulang filter agar tidak "tertarik" nilai lama.
        if (current != null && current.isLost(now)) filter.reset()
        val smoothed = filter.update(ad.rssi)

        val history = (current?.rssiHistory.orEmpty() + RssiSample(now, ad.rssi, smoothed))
            .takeLast(BleDevice.MAX_HISTORY)

        val device = BleDevice(
            address = ad.address,
            name = ad.name?.takeIf { it.isNotBlank() } ?: current?.name,
            rssi = ad.rssi,
            smoothedRssi = smoothed,
            txPower = ad.txPower ?: current?.txPower,
            isConnectable = ad.isConnectable ?: current?.isConnectable,
            manufacturerId = ad.manufacturerId ?: current?.manufacturerId,
            serviceUuids = ad.serviceUuids.ifEmpty { current?.serviceUuids.orEmpty() },
            firstSeenAt = current?.firstSeenAt ?: now,
            lastSeenAt = now,
            advertisementCount = (current?.advertisementCount ?: 0) + 1,
            rssiHistory = history,
        )
        bestRssi[ad.address] = max(bestRssi[ad.address] ?: ad.rssi, ad.rssi)
        pendingWrites[ad.address] = device
        _state.update { it.copy(devices = it.devices + (ad.address to device)) }
    }

    private suspend fun persistLoop() {
        while (true) {
            delay(PERSIST_INTERVAL_MS) // melempar CancellationException saat scan dihentikan
            flushPendingWrites()
        }
    }

    /** Tulis perangkat yang berubah ke Room secara batch (bukan per paket advertising). */
    private suspend fun flushPendingWrites() {
        if (pendingWrites.isEmpty()) return
        val batch = pendingWrites.values.map { d ->
            DeviceSighting(
                address = d.address,
                name = d.name,
                rssi = d.rssi,
                bestRssi = bestRssi[d.address] ?: d.rssi,
                firstSeenAt = d.firstSeenAt,
                lastSeenAt = d.lastSeenAt,
                manufacturerId = d.manufacturerId,
                isNewSession = persistedThisSession.add(d.address),
            )
        }
        pendingWrites.clear()
        try {
            dao.upsertSightings(batch)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menyimpan riwayat perangkat", e)
        }
    }

    /** Android memblokir app yang memanggil startScan > 5x dalam 30 detik (tanpa callback error). */
    private fun throttleDelay(): Long {
        val now = timeProvider.now()
        while (startTimestamps.isNotEmpty() && now - startTimestamps.first() > THROTTLE_WINDOW_MS) {
            startTimestamps.removeFirst()
        }
        return if (startTimestamps.size >= MAX_STARTS_PER_WINDOW) {
            startTimestamps.first() + THROTTLE_WINDOW_MS - now + THROTTLE_MARGIN_MS
        } else {
            0L
        }
    }

    private companion object {
        const val TAG = "ScanRepository"
        const val PERSIST_INTERVAL_MS = 3_000L
        const val THROTTLE_WINDOW_MS = 30_000L
        const val THROTTLE_MARGIN_MS = 500L
        const val MAX_STARTS_PER_WINDOW = 5
    }
}
