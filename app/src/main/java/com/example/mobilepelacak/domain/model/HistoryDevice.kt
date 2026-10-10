package com.example.mobilepelacak.domain.model

/** Perangkat yang pernah terdeteksi, dibaca dari database lokal. */
data class HistoryDevice(
    val address: String,
    val name: String?,
    val lastRssi: Int,
    val bestRssi: Int,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val sessionCount: Int,
    val manufacturerId: Int?,
) {
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: BleDevice.UNKNOWN_NAME
}

/** Nama vendor untuk Bluetooth SIG Company Identifier yang umum ditemui. */
object Manufacturers {
    private val known = mapOf(
        0x0006 to "Microsoft",
        0x000F to "Broadcom",
        0x004C to "Apple",
        0x0059 to "Nordic Semiconductor",
        0x0075 to "Samsung",
        0x0087 to "Garmin",
        0x00E0 to "Google",
        0x0157 to "Huami (Amazfit)",
        0x02E5 to "Espressif",
        0x038F to "Xiaomi",
        0x0499 to "Ruuvi",
        0x0822 to "Tile",
    )

    fun nameOf(id: Int?): String? = id?.let { known[it] ?: "0x%04X".format(it) }
}
