package com.example.mobilepelacak.domain.repository

import com.example.mobilepelacak.domain.model.HistoryDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import com.example.mobilepelacak.domain.model.ScanState

/**
 * Sumber kebenaran tunggal (single source of truth) untuk sesi pemindaian BLE.
 * Singleton sehingga tetap hidup saat rotasi layar / pergantian screen.
 */
interface ScanRepository {
    val state: StateFlow<ScanState>

    /** Dipanggil saat user menekan Start. */
    fun startScan()

    /** Dipanggil saat user menekan Stop. */
    fun stopScan()

    /** Hapus daftar perangkat sesi saat ini (tidak menghapus riwayat database). */
    fun clearDevices()

    /** Hapus pesan error yang sedang ditampilkan. */
    fun dismissError()

    /** Cek ulang izin / status sistem (mis. setelah user kembali dari Settings). */
    fun refreshEnvironment()

    /** Lifecycle proses aplikasi: app masuk background. */
    fun onAppBackground()

    /** Lifecycle proses aplikasi: app kembali ke foreground. */
    fun onAppForeground()
}

interface HistoryRepository {
    fun observeHistory(): Flow<List<HistoryDevice>>
    fun observeDevice(address: String): Flow<HistoryDevice?>
    suspend fun delete(address: String)
    suspend fun clear()
}

/** Abstraksi waktu supaya logika berbasis waktu bisa diuji. */
fun interface TimeProvider {
    fun now(): Long
}
