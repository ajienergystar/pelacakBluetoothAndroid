package com.example.mobilepelacak.data

import com.example.mobilepelacak.data.ble.Advertisement
import com.example.mobilepelacak.data.ble.BleEnvironment
import com.example.mobilepelacak.data.ble.BleScanException
import com.example.mobilepelacak.data.ble.BleScanner
import com.example.mobilepelacak.data.local.DeviceDao
import com.example.mobilepelacak.data.local.DeviceEntity
import com.example.mobilepelacak.data.repository.DefaultScanRepository
import com.example.mobilepelacak.domain.model.BluetoothStatus
import com.example.mobilepelacak.domain.model.ScanError
import com.example.mobilepelacak.domain.repository.TimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultScanRepositoryTest {

    // ---------------------------------------------------------------- Fakes

    private class FakeScanner : BleScanner {
        val advertisements = MutableSharedFlow<Advertisement>(extraBufferCapacity = 64)
        var failure: ScanError? = null
        var activeCollectors = 0
        var startCount = 0

        override fun scan(): Flow<Advertisement> = flow {
            failure?.let { throw BleScanException(it) }
            startCount++
            activeCollectors++
            emitAll(advertisements)
        }.onCompletion { if (failure == null) activeCollectors-- }
    }

    private class FakeEnvironment : BleEnvironment {
        val bluetooth = MutableStateFlow(BluetoothStatus.ON)
        var permissions = true
        var locationRequired = false
        var locationEnabled = true

        override fun isBleSupported() = true
        override fun hasScanPermissions() = permissions
        override fun currentBluetoothStatus() = bluetooth.value
        override fun observeBluetoothStatus(): Flow<BluetoothStatus> = bluetooth
        override fun isLocationRequired() = locationRequired
        override fun isLocationEnabled() = locationEnabled
    }

    private class FakeDao : DeviceDao() {
        val rows = MutableStateFlow<Map<String, DeviceEntity>>(emptyMap())
        override fun observeAll() = rows.map { it.values.sortedByDescending { e -> e.lastSeenAt } }
        override fun observe(address: String) = rows.map { it[address] }
        override suspend fun get(address: String) = rows.value[address]
        override suspend fun insert(entity: DeviceEntity) {
            rows.value = rows.value + (entity.address to entity)
        }
        override suspend fun update(entity: DeviceEntity) = insert(entity)
        override suspend fun delete(address: String) {
            rows.value = rows.value - address
        }
        override suspend fun clear() {
            rows.value = emptyMap()
        }
    }

    private val scanner = FakeScanner()
    private val environment = FakeEnvironment()
    private val dao = FakeDao()

    private fun TestScope.createRepository() = DefaultScanRepository(
        scanner = scanner,
        environment = environment,
        dao = dao,
        timeProvider = TimeProvider { testScheduler.currentTime },
        scope = backgroundScope,
    )

    private fun TestScope.ad(address: String, rssi: Int, name: String? = null) = Advertisement(
        address = address,
        name = name,
        rssi = rssi,
        txPower = null,
        isConnectable = true,
        manufacturerId = null,
        serviceUuids = emptyList(),
        timestamp = testScheduler.currentTime,
    )

    // ---------------------------------------------------------------- Tests

    @Test
    fun `start scan menerima advertising dan menyimpan ke database`() = runTest {
        val repo = createRepository()
        repo.startScan()
        runCurrent()
        assertTrue(repo.state.value.isScanning)

        scanner.advertisements.emit(ad("AA:00:00:00:00:01", -55, "Sensor"))
        scanner.advertisements.emit(ad("AA:00:00:00:00:01", -57))
        runCurrent()

        val device = repo.state.value.devices["AA:00:00:00:00:01"]
        assertNotNull(device)
        assertEquals("Sensor", device!!.name) // nama tidak hilang walau paket berikutnya tanpa nama
        assertEquals(-57, device.rssi)
        assertEquals(2, device.advertisementCount)

        advanceTimeBy(3_500)
        runCurrent()
        val saved = dao.rows.value["AA:00:00:00:00:01"]
        assertNotNull(saved)
        assertEquals(-55, saved!!.bestRssi)
        assertEquals(1, saved.sessionCount)
    }

    @Test
    fun `stop scan menghentikan scanner dan flush riwayat`() = runTest {
        val repo = createRepository()
        repo.startScan()
        runCurrent()
        scanner.advertisements.emit(ad("AA:00:00:00:00:02", -70))
        runCurrent()

        repo.stopScan()
        runCurrent()

        assertFalse(repo.state.value.isScanning)
        assertFalse(repo.state.value.isScanRequested)
        assertEquals(0, scanner.activeCollectors)
        assertNotNull(dao.rows.value["AA:00:00:00:00:02"])
    }

    @Test
    fun `bluetooth dimatikan lalu dinyalakan melanjutkan scan otomatis`() = runTest {
        val repo = createRepository()
        repo.startScan()
        runCurrent()

        environment.bluetooth.value = BluetoothStatus.OFF
        runCurrent()
        assertFalse(repo.state.value.isScanning)
        assertEquals(ScanError.BluetoothDisabled, repo.state.value.error)
        assertTrue(repo.state.value.isScanRequested)

        environment.bluetooth.value = BluetoothStatus.ON
        runCurrent()
        assertTrue(repo.state.value.isScanning)
        assertNull(repo.state.value.error)
    }

    @Test
    fun `izin ditolak menampilkan error dan tidak memulai scan`() = runTest {
        environment.permissions = false
        val repo = createRepository()
        repo.startScan()
        runCurrent()

        assertEquals(ScanError.PermissionDenied, repo.state.value.error)
        assertFalse(repo.state.value.isScanning)
        assertFalse(repo.state.value.isScanRequested)
        assertEquals(0, scanner.startCount)
    }

    @Test
    fun `lokasi mati di android lama menampilkan error`() = runTest {
        environment.locationRequired = true
        environment.locationEnabled = false
        val repo = createRepository()
        repo.startScan()
        runCurrent()

        assertEquals(ScanError.LocationDisabled, repo.state.value.error)

        environment.locationEnabled = true
        repo.refreshEnvironment()
        runCurrent()
        assertNull(repo.state.value.error)
        assertTrue(repo.state.value.isScanning)
    }

    @Test
    fun `scan gagal dari stack bluetooth dipetakan ke error`() = runTest {
        scanner.failure = ScanError.ScanFailed(ScanError.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED)
        val repo = createRepository()
        repo.startScan()
        runCurrent()

        val error = repo.state.value.error
        assertTrue(error is ScanError.ScanFailed)
        assertFalse(repo.state.value.isScanning)
    }

    @Test
    fun `app ke background menjeda scan dan lanjut saat kembali`() = runTest {
        val repo = createRepository()
        repo.startScan()
        runCurrent()

        repo.onAppBackground()
        runCurrent()
        assertFalse(repo.state.value.isScanning)
        assertTrue(repo.state.value.isPausedInBackground)
        assertEquals(0, scanner.activeCollectors)

        repo.onAppForeground()
        runCurrent()
        assertTrue(repo.state.value.isScanning)
        assertFalse(repo.state.value.isPausedInBackground)
    }

    @Test
    fun `start berulang lebih dari 5x dalam 30 detik ditunda`() = runTest {
        val repo = createRepository()
        repeat(5) {
            repo.startScan()
            runCurrent()
            repo.stopScan()
            runCurrent()
        }
        repo.startScan()
        runCurrent()

        assertTrue(repo.state.value.error is ScanError.Throttled)
        assertFalse(repo.state.value.isScanning)

        advanceTimeBy(31_000)
        runCurrent()
        assertTrue(repo.state.value.isScanning)
        assertNull(repo.state.value.error)
    }

    @Test
    fun `perangkat yang muncul lagi di sesi baru menambah session count`() = runTest {
        val repo = createRepository()
        repo.startScan()
        runCurrent()
        scanner.advertisements.emit(ad("AA:00:00:00:00:03", -60))
        runCurrent()
        repo.stopScan()
        runCurrent()

        repo.startScan()
        runCurrent()
        scanner.advertisements.emit(ad("AA:00:00:00:00:03", -50))
        runCurrent()
        repo.stopScan()
        runCurrent()

        val saved = dao.rows.value["AA:00:00:00:00:03"]!!
        assertEquals(2, saved.sessionCount)
        assertEquals(-50, saved.bestRssi)
        assertEquals(-50, saved.lastRssi)
    }
}
