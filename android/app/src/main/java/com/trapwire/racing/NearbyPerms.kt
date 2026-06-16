package com.trapwire.racing

import android.Manifest
import android.os.Build

fun requiredBlePermissions(): Array<String> {
    val permissions = mutableListOf<String>()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions += Manifest.permission.BLUETOOTH_SCAN
        permissions += Manifest.permission.BLUETOOTH_ADVERTISE
        permissions += Manifest.permission.BLUETOOTH_CONNECT
    } else {
        permissions += Manifest.permission.BLUETOOTH
        permissions += Manifest.permission.BLUETOOTH_ADMIN
        permissions += Manifest.permission.ACCESS_FINE_LOCATION
    }

    return permissions.distinct().toTypedArray()
}
