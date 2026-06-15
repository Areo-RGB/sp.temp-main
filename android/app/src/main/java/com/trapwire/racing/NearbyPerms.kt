package com.trapwire.racing

import android.Manifest
import android.os.Build

fun requiredNearbyPermissions(): Array<String> {
    val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions += Manifest.permission.BLUETOOTH_SCAN
        permissions += Manifest.permission.BLUETOOTH_ADVERTISE
        permissions += Manifest.permission.BLUETOOTH_CONNECT
    } else {
        permissions += Manifest.permission.BLUETOOTH
        permissions += Manifest.permission.BLUETOOTH_ADMIN
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions += Manifest.permission.NEARBY_WIFI_DEVICES
    }

    return permissions.distinct().toTypedArray()
}
