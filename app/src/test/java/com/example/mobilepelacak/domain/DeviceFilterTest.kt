package com.example.mobilepelacak.domain

import com.example.mobilepelacak.domain.model.BleDevice
import com.example.mobilepelacak.domain.model.DeviceFilter
import com.example.mobilepelacak.domain.model.filterAndSort
import com.example.mobilepelacak.testutil.testDevice
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceFilterTest {

    private val now = 100_000L

    private val devices = listOf(
        testDevice(address = "11:11:11:11:11:11", name = "Mi Band 7", rssi = -75, lastSeenAt = now),
        testDevice(address = "22:22:22:22:22:22", name = null, rssi = -45, lastSeenAt = now),
        testDevice(address = "A4:C1:38:00:00:01", name = "Galaxy Buds", rssi = -60, lastSeenAt = now),
        testDevice(address = "33:33:33:33:33:33", name = "JBL Flip", rssi = -88, lastSeenAt = now),
    )

    @Test
    fun `urut berdasarkan rssi tertinggi`() {
        val result = devices.filterAndSort(DeviceFilter(), now)
        assertEquals(listOf(-45, -60, -75, -88), result.map { it.signalRssi })
    }

    @Test
    fun `cari berdasarkan nama tidak case sensitive`() {
        val result = devices.filterAndSort(DeviceFilter(query = "galaxy"), now)
        assertEquals(listOf("Galaxy Buds"), result.map { it.name })
    }

    @Test
    fun `cari berdasarkan mac dengan atau tanpa titik dua`() {
        assertEquals(1, devices.filterAndSort(DeviceFilter(query = "a4:c1"), now).size)
        assertEquals(1, devices.filterAndSort(DeviceFilter(query = "A4C138"), now).size)
    }

    @Test
    fun `ambang batas rssi minimum`() {
        val result = devices.filterAndSort(DeviceFilter(minRssi = -80), now)
        assertEquals(listOf(-45, -60, -75), result.map { it.signalRssi })
    }

    @Test
    fun `hanya perangkat bernama`() {
        val result = devices.filterAndSort(DeviceFilter(namedOnly = true), now)
        assertEquals(3, result.size)
        assertEquals(true, result.all { it.hasName })
    }

    @Test
    fun `filter digabung`() {
        val result = devices.filterAndSort(DeviceFilter(query = "band", minRssi = -80, namedOnly = true), now)
        assertEquals(listOf("Mi Band 7"), result.map { it.name })
    }

    @Test
    fun `perangkat hilang diletakkan di bawah dan disembunyikan setelah 30 detik`() {
        val lost = testDevice(address = "44:44:44:44:44:44", rssi = -30, lastSeenAt = now - 10_000)
        val gone = testDevice(address = "55:55:55:55:55:55", rssi = -30, lastSeenAt = now - 40_000)
        val result = (devices + lost + gone).filterAndSort(DeviceFilter(), now)
        assertEquals("44:44:44:44:44:44", result.last().address)
        assertEquals(false, result.any { it.address == "55:55:55:55:55:55" })
    }

    @Test
    fun `zona LOST setelah timeout`() {
        val d = testDevice(rssi = -40, lastSeenAt = now - BleDevice.LOST_TIMEOUT_MS - 1)
        assertEquals(true, d.isLost(now))
        assertEquals(com.example.mobilepelacak.domain.model.SignalZone.LOST, d.zoneAt(now))
    }
}
