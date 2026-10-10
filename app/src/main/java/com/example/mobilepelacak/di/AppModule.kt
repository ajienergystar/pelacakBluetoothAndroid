package com.example.mobilepelacak.di

import android.content.Context
import androidx.room.Room
import com.example.mobilepelacak.data.ble.AndroidBleEnvironment
import com.example.mobilepelacak.data.ble.AndroidBleScanner
import com.example.mobilepelacak.data.ble.BleEnvironment
import com.example.mobilepelacak.data.ble.BleScanner
import com.example.mobilepelacak.data.local.AppDatabase
import com.example.mobilepelacak.data.local.DeviceDao
import com.example.mobilepelacak.data.repository.DefaultScanRepository
import com.example.mobilepelacak.data.repository.RoomHistoryRepository
import com.example.mobilepelacak.domain.repository.HistoryRepository
import com.example.mobilepelacak.domain.repository.ScanRepository
import com.example.mobilepelacak.domain.repository.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideDeviceDao(db: AppDatabase): DeviceDao = db.deviceDao()

    @Provides
    @Singleton
    fun provideTimeProvider(): TimeProvider = TimeProvider { System.currentTimeMillis() }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingModule {

    @Binds
    abstract fun bindBleScanner(impl: AndroidBleScanner): BleScanner

    @Binds
    abstract fun bindBleEnvironment(impl: AndroidBleEnvironment): BleEnvironment

    @Binds
    abstract fun bindScanRepository(impl: DefaultScanRepository): ScanRepository

    @Binds
    abstract fun bindHistoryRepository(impl: RoomHistoryRepository): HistoryRepository
}
