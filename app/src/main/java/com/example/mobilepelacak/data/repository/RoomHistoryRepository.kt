package com.example.mobilepelacak.data.repository

import com.example.mobilepelacak.data.local.DeviceDao
import com.example.mobilepelacak.data.local.toDomain
import com.example.mobilepelacak.domain.model.HistoryDevice
import com.example.mobilepelacak.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomHistoryRepository @Inject constructor(
    private val dao: DeviceDao,
) : HistoryRepository {

    override fun observeHistory(): Flow<List<HistoryDevice>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeDevice(address: String): Flow<HistoryDevice?> =
        dao.observe(address).map { it?.toDomain() }

    override suspend fun delete(address: String) = dao.delete(address)

    override suspend fun clear() = dao.clear()
}
