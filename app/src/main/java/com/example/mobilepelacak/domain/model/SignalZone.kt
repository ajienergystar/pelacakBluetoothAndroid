package com.example.mobilepelacak.domain.model

/**
 * Kategori kedekatan berdasarkan RSSI, mengikuti tabel pada spesifikasi:
 *
 * | RSSI (dBm)   | Kategori                    | Perkiraan jarak |
 * |--------------|-----------------------------|-----------------|
 * | -10 s/d -30  | Sangat Kuat (Sangat Dekat)  | < 1 m           |
 * | -30 s/d -50  | Kuat (Dekat)                | 1 – 3 m         |
 * | -50 s/d -70  | Cukup / Baik                | 3 – 10 m        |
 * | -70 s/d -80  | Lemah                       | 10 – 20 m       |
 * | -80 s/d -90  | Sangat Lemah / Putus-putus  | > 20 m          |
 * | < -90        | Sinyal Hilang (Lost)        | Di luar jangkauan |
 *
 * Batas atas tiap kategori bersifat inklusif (mis. -30 dBm masuk "Sangat Kuat").
 */
enum class SignalZone(
    val label: String,
    val proximityLabel: String,
    val distanceLabel: String,
    /** RSSI minimum (inklusif) agar masuk zona ini. */
    val minRssi: Int,
) {
    VERY_STRONG("Sangat Kuat", "Sangat Dekat", "< 1 m", -30),
    STRONG("Kuat", "Dekat", "1 – 3 m", -50),
    GOOD("Cukup / Baik", "Sedang", "3 – 10 m", -70),
    WEAK("Lemah", "Jauh", "10 – 20 m", -80),
    VERY_WEAK("Sangat Lemah", "Putus-putus", "> 20 m", -90),
    LOST("Sinyal Hilang", "Di luar jangkauan", "Terputus", Int.MIN_VALUE);

    /** Jumlah bar sinyal (0..5) untuk indikator visual. */
    val bars: Int get() = if (this == LOST) 0 else 5 - ordinal

    /** true jika zona ini lebih dekat dibanding [other]. */
    fun isCloserThan(other: SignalZone): Boolean = ordinal < other.ordinal

    companion object {
        fun fromRssi(rssi: Int): SignalZone = entries.first { rssi >= it.minRssi }
    }
}
