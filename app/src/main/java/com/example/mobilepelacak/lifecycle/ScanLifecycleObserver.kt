package com.example.mobilepelacak.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.mobilepelacak.domain.repository.ScanRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Didaftarkan ke ProcessLifecycleOwner (bukan Activity) sehingga:
 * - Rotasi layar / config change TIDAK menghentikan scan (ProcessLifecycleOwner tidak
 *   mengirim ON_STOP untuk config change).
 * - Saat app benar-benar ke background, scan di-pause; dilanjutkan otomatis saat kembali.
 */
@Singleton
class ScanLifecycleObserver @Inject constructor(
    private val scanRepository: ScanRepository,
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        scanRepository.onAppForeground()
    }

    override fun onStop(owner: LifecycleOwner) {
        scanRepository.onAppBackground()
    }
}
