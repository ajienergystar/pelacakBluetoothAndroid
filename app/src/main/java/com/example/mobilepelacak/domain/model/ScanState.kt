package com.example.mobilepelacak.domain.model

enum class BluetoothStatus {
    ON,
    OFF,
    TURNING_ON,
    TURNING_OFF,
    UNSUPPORTED;

    val isOn: Boolean get() = this == ON
}

/** Kondisi yang membuat pemindaian tidak bisa berjalan, ditampilkan ke user secara jelas. */
sealed interface ScanError {
    val message: String

    data object PermissionDenied : ScanError {
        override val message = "Izin Bluetooth belum diberikan. Aplikasi butuh izin untuk memindai perangkat sekitar."
    }

    data object BluetoothDisabled : ScanError {
        override val message = "Bluetooth sedang nonaktif. Pemindaian akan otomatis berlanjut setelah Bluetooth dinyalakan."
    }

    data object LocationDisabled : ScanError {
        override val message = "Layanan lokasi nonaktif. Android 11 ke bawah membutuhkan lokasi aktif agar hasil scan BLE muncul."
    }

    data object BleUnsupported : ScanError {
        override val message = "Perangkat ini tidak mendukung Bluetooth Low Energy."
    }

    data class Throttled(val retryInMillis: Long) : ScanError {
        override val message: String
            get() = "Android membatasi maksimal 5 kali start scan per 30 detik. Scan akan dimulai otomatis dalam ${(retryInMillis + 999) / 1000} detik."
    }

    data class ScanFailed(val errorCode: Int) : ScanError {
        override val message: String
            get() = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "Scan sudah berjalan."
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED ->
                    "Gagal mendaftarkan scanner ke stack Bluetooth. Coba matikan lalu nyalakan Bluetooth."
                SCAN_FAILED_INTERNAL_ERROR -> "Terjadi kesalahan internal pada stack Bluetooth."
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "Fitur scan BLE ini tidak didukung perangkat."
                SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "Resource hardware Bluetooth sedang penuh. Coba lagi beberapa saat."
                SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> "Scan terlalu sering. Tunggu beberapa detik lalu coba lagi."
                else -> "Scan gagal (kode $errorCode)."
            }
    }

    data class Unexpected(val cause: String?) : ScanError {
        override val message: String get() = "Terjadi kesalahan: ${cause ?: "tidak diketahui"}"
    }

    companion object {
        // Mengikuti konstanta android.bluetooth.le.ScanCallback, diduplikasi agar domain tetap bebas Android SDK.
        const val SCAN_FAILED_ALREADY_STARTED = 1
        const val SCAN_FAILED_APPLICATION_REGISTRATION_FAILED = 2
        const val SCAN_FAILED_INTERNAL_ERROR = 3
        const val SCAN_FAILED_FEATURE_UNSUPPORTED = 4
        const val SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES = 5
        const val SCAN_FAILED_SCANNING_TOO_FREQUENTLY = 6
    }
}

data class ScanState(
    /** Scanner hardware sedang aktif menerima advertising. */
    val isScanning: Boolean = false,
    /** User ingin scan berjalan (bisa true walau [isScanning] false, mis. sedang di background / Bluetooth mati). */
    val isScanRequested: Boolean = false,
    val isPausedInBackground: Boolean = false,
    val bluetoothStatus: BluetoothStatus = BluetoothStatus.OFF,
    val devices: Map<String, BleDevice> = emptyMap(),
    val error: ScanError? = null,
    val sessionStartedAt: Long? = null,
)
