package com.example.mobilepelacak.domain

import com.example.mobilepelacak.domain.model.SignalZone
import com.example.mobilepelacak.domain.signal.DistanceEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DistanceEstimatorTest {

    private val estimator = DistanceEstimator.Default

    @Test
    fun `jarak bertambah ketika rssi melemah`() {
        val distances = (-20 downTo -100 step 5).map { estimator.estimateMeters(it) }
        distances.zipWithNext().forEach { (a, b) -> assertTrue("$a < $b", a < b) }
    }

    @Test
    fun `estimasi konsisten dengan rentang jarak tiap zona`() {
        // Titik tengah tiap zona harus jatuh di rentang jarak tabel spesifikasi.
        assertTrue(estimator.estimateMeters(-20) < 1.0)                   // Sangat Kuat: < 1 m
        assertTrue(estimator.estimateMeters(-40) in 1.0..3.0)             // Kuat: 1 – 3 m
        assertTrue(estimator.estimateMeters(-60) in 3.0..10.0)            // Cukup: 3 – 10 m
        assertTrue(estimator.estimateMeters(-75) in 10.0..20.0)           // Lemah: 10 – 20 m
        assertTrue(estimator.estimateMeters(-85) > 20.0)                  // Sangat lemah: > 20 m
    }

    @Test
    fun `batas zona menghasilkan jarak di batas tabel`() {
        assertEquals(3.0, estimator.estimateMeters(-50), 0.1)
        assertEquals(10.0, estimator.estimateMeters(-70), 0.1)
        assertEquals(SignalZone.STRONG, SignalZone.fromRssi(-50))
    }

    @Test
    fun `rssi sama dengan measured power menghasilkan 1 meter`() {
        val e = DistanceEstimator(measuredPower = -59, pathLossExponent = 2.0)
        assertEquals(1.0, e.estimateMeters(-59), 1e-9)
        assertEquals(10.0, e.estimateMeters(-79), 1e-9)
    }

    @Test
    fun `format jarak`() {
        assertEquals("0.8 m", DistanceEstimator.format(0.8))
        assertEquals("27 m", DistanceEstimator.format(27.3))
        assertEquals("> 100 m", DistanceEstimator.format(150.0))
    }
}
