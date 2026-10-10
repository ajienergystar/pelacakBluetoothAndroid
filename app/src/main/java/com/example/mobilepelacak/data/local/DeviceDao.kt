package com.example.mobilepelacak.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlin.math.max

@Dao
abstract class DeviceDao {

    @Query("SELECT * FROM devices ORDER BY last_seen_at DESC")
    abstract fun observeAll(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE address = :address LIMIT 1")
    abstract fun observe(address: String): Flow<DeviceEntity?>

    @Query("SELECT * FROM devices WHERE address = :address LIMIT 1")
    abstract suspend fun get(address: String): DeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(entity: DeviceEntity)

    @Update
    abstract suspend fun update(entity: DeviceEntity)

    @Query("DELETE FROM devices WHERE address = :address")
    abstract suspend fun delete(address: String)

    @Query("DELETE FROM devices")
    abstract suspend fun clear()

    /**
     * Upsert yang menggabungkan data lama: nama tidak ditimpa null, first_seen dipertahankan,
     * best_rssi diambil yang tertinggi, session_count bertambah sekali per sesi scan.
     * (SQLite bawaan Android < 11 belum mendukung `ON CONFLICT DO UPDATE`, jadi dilakukan manual.)
     */
    @Transaction
    open suspend fun upsertSightings(sightings: List<DeviceSighting>) {
        for (s in sightings) {
            val existing = get(s.address)
            if (existing == null) {
                insert(
                    DeviceEntity(
                        address = s.address,
                        name = s.name,
                        lastRssi = s.rssi,
                        bestRssi = s.bestRssi,
                        firstSeenAt = s.firstSeenAt,
                        lastSeenAt = s.lastSeenAt,
                        sessionCount = 1,
                        manufacturerId = s.manufacturerId,
                    )
                )
            } else {
                update(
                    existing.copy(
                        name = s.name?.takeIf { it.isNotBlank() } ?: existing.name,
                        lastRssi = s.rssi,
                        bestRssi = max(existing.bestRssi, s.bestRssi),
                        firstSeenAt = minOf(existing.firstSeenAt, s.firstSeenAt),
                        lastSeenAt = max(existing.lastSeenAt, s.lastSeenAt),
                        sessionCount = existing.sessionCount + if (s.isNewSession) 1 else 0,
                        manufacturerId = s.manufacturerId ?: existing.manufacturerId,
                    )
                )
            }
        }
    }
}
