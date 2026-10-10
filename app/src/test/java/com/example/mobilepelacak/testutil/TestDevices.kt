package com.example.mobilepelacak.testutil

import com.example.mobilepelacak.domain.model.BleDevice
import com.example.mobilepelacak.domain.model.RssiSample

fun testDevice(
    address: String = "AA:BB:CC:DD:EE:FF",
    name: String? = "Device",
    rssi: Int = -60,
    smoothedRssi: Double = rssi.toDouble(),
    lastSeenAt: Long = 0L,
    history: List<RssiSample> = emptyList(),
) = BleDevice(
    address = address,
    name = name,
    rssi = rssi,
    smoothedRssi = smoothedRssi,
    txPower = null,
    isConnectable = true,
    manufacturerId = null,
    serviceUuids = emptyList(),
    firstSeenAt = 0L,
    lastSeenAt = lastSeenAt,
    advertisementCount = 1,
    rssiHistory = history,
)
