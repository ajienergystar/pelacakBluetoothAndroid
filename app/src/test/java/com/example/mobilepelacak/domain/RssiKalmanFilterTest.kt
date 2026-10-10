package com.example.mobilepelacak.domain

import com.example.mobilepelacak.domain.signal.RssiKalmanFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RssiKalmanFilterTest {

    @Test
    fun `nilai pertama dikembalikan apa adanya`() {
        val filter = RssiKalmanFilter()
        assertEquals(-60.0, filter.update(-60), 0.0)
    }

    @Test
    fun `filter meredam noise`() {
        val filter = RssiKalmanFilter()
        val noisy = listOf(-60, -68, -55, -66, -57, -64, -58, -67, -56, -63)
        val output = noisy.map { filter.update(it) }
        val rawSpread = noisy.max() - noisy.min()
        val filteredSpread = output.drop(1).max() - output.drop(1).min()
        assertTrue("filteredSpread=$filteredSpread rawSpread=$rawSpread", filteredSpread < rawSpread / 2.0)
    }

    @Test
    fun `filter konvergen ke nilai baru setelah perubahan`() {
        val filter = RssiKalmanFilter()
        repeat(20) { filter.update(-80) }
        var last = 0.0
        repeat(30) { last = filter.update(-50) }
        assertTrue("last=$last", abs(last - (-50.0)) < 1.0)
    }

    @Test
    fun `reset memulai ulang filter`() {
        val filter = RssiKalmanFilter()
        repeat(10) { filter.update(-80) }
        filter.reset()
        assertEquals(-40.0, filter.update(-40), 0.0)
    }
}
