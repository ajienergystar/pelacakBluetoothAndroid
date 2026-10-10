package com.example.mobilepelacak.domain

import com.example.mobilepelacak.domain.model.RssiSample
import com.example.mobilepelacak.domain.signal.SignalAnalyzer
import com.example.mobilepelacak.domain.signal.SignalStability
import com.example.mobilepelacak.domain.signal.SignalTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SignalAnalyzerTest {

    private fun samples(vararg smoothed: Double, raw: List<Int>? = null) =
        smoothed.mapIndexed { i, s -> RssiSample(timestamp = i * 100L, rssi = raw?.get(i) ?: s.toInt(), smoothedRssi = s) }

    @Test
    fun `trend mendekat saat rssi naik`() {
        val s = samples(-80.0, -79.0, -78.0, -77.0, -76.0, -70.0, -68.0, -66.0, -64.0, -62.0)
        assertEquals(SignalTrend.APPROACHING, SignalAnalyzer.trend(s))
    }

    @Test
    fun `trend menjauh saat rssi turun`() {
        val s = samples(-50.0, -51.0, -52.0, -53.0, -54.0, -60.0, -62.0, -64.0, -66.0, -68.0)
        assertEquals(SignalTrend.RECEDING, SignalAnalyzer.trend(s))
    }

    @Test
    fun `trend unknown jika data kurang`() {
        assertEquals(SignalTrend.UNKNOWN, SignalAnalyzer.trend(samples(-50.0, -51.0)))
    }

    @Test
    fun `stabilitas berdasarkan standar deviasi`() {
        val stable = samples(-60.0, -60.0, -61.0, -60.0, -59.0)
        assertEquals(SignalStability.STABLE, SignalAnalyzer.stability(stable))

        val noisy = samples(-50.0, -70.0, -48.0, -72.0, -51.0, -69.0)
        assertEquals(SignalStability.UNSTABLE, SignalAnalyzer.stability(noisy))
    }

    @Test
    fun `laju paket advertising`() {
        val s = samples(-60.0, -60.0, -60.0, -60.0, -60.0) // 5 sampel dalam 400 ms
        val rate = SignalAnalyzer.advertisementRate(s)
        assertNotNull(rate)
        assertEquals(10.0, rate!!, 0.01)
    }
}
