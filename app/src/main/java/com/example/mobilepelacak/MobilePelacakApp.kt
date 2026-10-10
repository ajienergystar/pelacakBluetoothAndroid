package com.example.mobilepelacak

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.mobilepelacak.lifecycle.ScanLifecycleObserver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MobilePelacakApp : Application() {

    @Inject
    lateinit var scanLifecycleObserver: ScanLifecycleObserver

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(scanLifecycleObserver)
    }
}
