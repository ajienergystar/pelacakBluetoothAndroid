package com.example.mobilepelacak.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.mobilepelacak.domain.model.HistoryDevice

@Entity(
    tableName = "devices",
    indices = [Index(value = ["last_seen_at"])],
)
data class DeviceEntity(
    @PrimaryKey
    @ColumnInfo(name = "address") val address: String,
    @ColumnInfo(name = "name") val name: String?,
    @ColumnInfo(name = "last_rssi") val lastRssi: Int,
    @ColumnInfo(name = "best_rssi") val bestRssi: Int,
    @ColumnInfo(name = "first_seen_at") val firstSeenAt: Long,
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Long,
    /** Berapa sesi scan perangkat ini pernah terdeteksi. */
    @ColumnInfo(name = "session_count") val sessionCount: Int,
    @ColumnInfo(name = "manufacturer_id") val manufacturerId: Int?,
)

/** Data satu kali "flush" perangkat dari sesi scan ke database. */
data class DeviceSighting(
    val address: String,
    val name: String?,
    val rssi: Int,
    val bestRssi: Int,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val manufacturerId: Int?,
    /** true jika ini penulisan pertama perangkat tsb di sesi scan saat ini. */
    val isNewSession: Boolean,
)

fun DeviceEntity.toDomain() = HistoryDevice(
    address = address,
    name = name,
    lastRssi = lastRssi,
    bestRssi = bestRssi,
    firstSeenAt = firstSeenAt,
    lastSeenAt = lastSeenAt,
    sessionCount = sessionCount,
    manufacturerId = manufacturerId,
)
