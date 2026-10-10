package com.example.mobilepelacak.domain

import com.example.mobilepelacak.domain.model.SignalZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalZoneTest {

    @Test
    fun `rssi dipetakan sesuai tabel spesifikasi`() {
        val expected = mapOf(
            -10 to SignalZone.VERY_STRONG,
            -30 to SignalZone.VERY_STRONG,
            -31 to SignalZone.STRONG,
            -50 to SignalZone.STRONG,
            -51 to SignalZone.GOOD,
            -70 to SignalZone.GOOD,
            -71 to SignalZone.WEAK,
            -80 to SignalZone.WEAK,
            -81 to SignalZone.VERY_WEAK,
            -90 to SignalZone.VERY_WEAK,
            -91 to SignalZone.LOST,
            -127 to SignalZone.LOST,
        )
        expected.forEach { (rssi, zone) ->
            assertEquals("RSSI $rssi", zone, SignalZone.fromRssi(rssi))
        }
    }

    @Test
    fun `rssi di atas -10 tetap sangat kuat`() {
        assertEquals(SignalZone.VERY_STRONG, SignalZone.fromRssi(0))
    }

    @Test
    fun `jumlah bar menurun seiring melemahnya sinyal`() {
        val bars = SignalZone.entries.map { it.bars }
        assertEquals(listOf(5, 4, 3, 2, 1, 0), bars)
    }

    @Test
    fun `isCloserThan membandingkan kedekatan`() {
        assertTrue(SignalZone.STRONG.isCloserThan(SignalZone.WEAK))
        assertTrue(!SignalZone.WEAK.isCloserThan(SignalZone.STRONG))
    }
}
