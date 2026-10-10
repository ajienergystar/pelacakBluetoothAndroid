package com.example.mobilepelacak.domain.model

/**
 * Kriteria filter daftar perangkat.
 *
 * @property query dicocokkan (case-insensitive) dengan nama perangkat atau MAC address.
 *   Pencarian MAC boleh tanpa titik dua, mis. "a4c1" cocok dengan "A4:C1:38:...".
 * @property minRssi ambang batas sinyal minimum (dBm), null = tanpa batas.
 * @property namedOnly hanya tampilkan perangkat yang memiliki nama.
 */
data class DeviceFilter(
    val query: String = "",
    val minRssi: Int? = null,
    val namedOnly: Boolean = false,
) {
    val isActive: Boolean get() = query.isNotBlank() || minRssi != null || namedOnly

    fun matches(device: BleDevice): Boolean {
        if (namedOnly && !device.hasName) return false
        if (minRssi != null && device.signalRssi < minRssi) return false
        val q = query.trim()
        if (q.isEmpty()) return true
        val nameMatch = device.name?.contains(q, ignoreCase = true) == true
        val addressMatch = device.address.contains(q, ignoreCase = true) ||
            device.address.replace(":", "").contains(q.replace(":", ""), ignoreCase = true)
        return nameMatch || addressMatch
    }

    companion object {
        /** Pilihan cepat ambang batas RSSI di UI. */
        val RSSI_PRESETS = listOf(-60, -70, -80, -90)
    }
}

/**
 * Terapkan filter lalu urutkan: perangkat aktif dengan sinyal terkuat (RSSI tertinggi) di atas,
 * perangkat yang sinyalnya hilang di bawah. Perangkat yang sudah lama hilang disembunyikan.
 */
fun List<BleDevice>.filterAndSort(filter: DeviceFilter, now: Long): List<BleDevice> =
    asSequence()
        .filter { it.millisSinceSeen(now) <= BleDevice.HIDE_FROM_LIST_AFTER_MS }
        .filter(filter::matches)
        .sortedWith(
            compareBy<BleDevice> { it.isLost(now) }
                .thenByDescending { it.smoothedRssi }
                .thenBy { it.address }
        )
        .toList()
