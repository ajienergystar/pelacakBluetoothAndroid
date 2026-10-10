package com.example.mobilepelacak.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Scope aplikasi untuk sesi scan BLE. Hidup selama proses aplikasi hidup sehingga scan
 * tidak terputus oleh rotasi layar / pergantian screen. Memakai dispatcher single-thread
 * (limitedParallelism(1)) agar state repository terkonfinasi di satu thread.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ScanScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Provides
    @Singleton
    @ScanScope
    fun provideScanScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
}
