package com.trapwire.racing

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID

private val BLE_RACE_SERVICE_UUID: UUID = UUID.fromString("9f6f7d78-4e2f-4f24-9c12-3e9f91f5a4b1")
private val BLE_COMMAND_CHARACTERISTIC_UUID: UUID = UUID.fromString("9f6f7d79-4e2f-4f24-9c12-3e9f91f5a4b1")
private val BLE_SNAPSHOT_CHARACTERISTIC_UUID: UUID = UUID.fromString("9f6f7d7a-4e2f-4f24-9c12-3e9f91f5a4b1")
private val BLE_RACE_SERVICE_PARCEL_UUID = ParcelUuid(BLE_RACE_SERVICE_UUID)
private const val BLE_MTU_BYTES = 517
private const val BLE_MAX_APP_PAYLOAD_BYTES = 480
private const val BLE_SCAN_TIMEOUT_MS = 15_000L
private const val BLE_SNAPSHOT_POLL_MS = 600L

private fun Context.bluetoothManager(): BluetoothManager? = getSystemService(BluetoothManager::class.java)

private fun Context.bluetoothAdapter(): BluetoothAdapter? = bluetoothManager()?.adapter

private fun Context.hasRuntimePermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

private fun Context.hasBleConnectPermission(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || hasRuntimePermission(Manifest.permission.BLUETOOTH_CONNECT)
}

private fun Context.hasBleScanPermission(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || hasRuntimePermission(Manifest.permission.BLUETOOTH_SCAN)
}

private fun Context.hasBleAdvertisePermission(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || hasRuntimePermission(Manifest.permission.BLUETOOTH_ADVERTISE)
}

private fun bleRoomServiceData(code: String): ByteArray = "TW$code".toByteArray(StandardCharsets.UTF_8)

private fun ByteArray.asBleRoomCode(): String = String(this, StandardCharsets.UTF_8).removePrefix("TW")

private fun JSONObject.toBleBytes(): ByteArray = toString().toByteArray(StandardCharsets.UTF_8)

private fun ByteArray.toJsonObjectOrNull(): JSONObject? {
    if (isEmpty()) return null
    return runCatching { JSONObject(String(this, StandardCharsets.UTF_8)) }.getOrNull()
}

@SuppressLint("MissingPermission")
class BleRaceHost(
    private val context: Context,
    private val code: String,
    private val onClientMessage: (deviceKey: String, json: JSONObject) -> Unit,
    private val onDeviceDisconnected: (deviceKey: String) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: android.bluetooth.le.BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null

    @Volatile
    private var snapshotBytes: ByteArray = ByteArray(0)

    fun updateSnapshot(json: JSONObject) {
        snapshotBytes = json.toBleBytes()
    }

    fun start() {
        val adapter = context.bluetoothAdapter()
        if (adapter == null) {
            onErrorOnMain("BLE is not available on this device.")
            return
        }
        if (!adapter.isEnabled) {
            onErrorOnMain("Bluetooth is off. Turn it on before advertising the room.")
            return
        }
        if (!context.hasBleConnectPermission() || !context.hasBleAdvertisePermission()) {
            onErrorOnMain("Bluetooth advertise/connect permission is missing.")
            return
        }

        val manager = context.bluetoothManager()
        val server = manager?.openGattServer(context, gattCallback)
        if (server == null) {
            onErrorOnMain("Could not open BLE GATT server.")
            return
        }
        gattServer = server
        server.addService(createRaceService())
        startAdvertising(adapter)
    }

    fun stop() {
        runCatching { advertiseCallback?.let { advertiser?.stopAdvertising(it) } }
        advertiseCallback = null
        advertiser = null
        runCatching { gattServer?.clearServices() }
        runCatching { gattServer?.close() }
        gattServer = null
    }

    private fun createRaceService(): BluetoothGattService {
        return BluetoothGattService(BLE_RACE_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
            addCharacteristic(
                BluetoothGattCharacteristic(
                    BLE_COMMAND_CHARACTERISTIC_UUID,
                    BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE,
                ),
            )
            addCharacteristic(
                BluetoothGattCharacteristic(
                    BLE_SNAPSHOT_CHARACTERISTIC_UUID,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ,
                ),
            )
        }
    }

    private fun startAdvertising(adapter: BluetoothAdapter) {
        val activeAdvertiser = adapter.bluetoothLeAdvertiser
        if (activeAdvertiser == null) {
            onErrorOnMain("BLE advertising is not supported on this device.")
            return
        }
        advertiser = activeAdvertiser
        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                onStatusOnMain("BLE room Trapwire-$code is advertising.")
            }

            override fun onStartFailure(errorCode: Int) {
                onErrorOnMain("BLE advertising failed: $errorCode")
            }
        }
        advertiseCallback = callback
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceData(BLE_RACE_SERVICE_PARCEL_UUID, bleRoomServiceData(code))
            .build()
        activeAdvertiser.startAdvertising(settings, data, callback)
    }

    private val gattCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                onMain { onDeviceDisconnected(device.address ?: device.toString()) }
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val server = gattServer ?: return
            if (characteristic.uuid != BLE_SNAPSHOT_CHARACTERISTIC_UUID) {
                server.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                return
            }
            val data = snapshotBytes
            val value = if (offset in 0..data.size) data.copyOfRange(offset, data.size) else ByteArray(0)
            server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            val server = gattServer ?: return
            if (characteristic.uuid != BLE_COMMAND_CHARACTERISTIC_UUID || offset != 0) {
                if (responseNeeded) server.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                return
            }
            if (responseNeeded) server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            value.toJsonObjectOrNull()?.let { json ->
                onMain { onClientMessage(device.address ?: device.toString(), json) }
            }
        }
    }

    private fun onMain(block: () -> Unit) {
        mainHandler.post(block)
    }

    private fun onStatusOnMain(message: String) = onMain { onStatus(message) }

    private fun onErrorOnMain(message: String) = onMain { onError(message) }
}

@SuppressLint("MissingPermission")
class BleRaceClient(
    private val context: Context,
    private val code: String,
    private val joinJson: JSONObject,
    private val onSnapshot: (JSONObject) -> Unit,
    private val onConnected: () -> Unit,
    private val onDisconnected: () -> Unit,
    private val onStatus: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var scanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var snapshotCharacteristic: BluetoothGattCharacteristic? = null
    private val pendingWrites = ArrayDeque<JSONObject>()
    private var ready = false
    private var closed = false

    fun start() {
        val adapter = context.bluetoothAdapter()
        if (adapter == null) {
            onErrorOnMain("BLE is not available on this device.")
            return
        }
        if (!adapter.isEnabled) {
            onErrorOnMain("Bluetooth is off. Turn it on before searching.")
            return
        }
        if (!context.hasBleConnectPermission() || !context.hasBleScanPermission()) {
            onErrorOnMain("Bluetooth scan/connect permission is missing.")
            return
        }
        val activeScanner = adapter.bluetoothLeScanner
        if (activeScanner == null) {
            onErrorOnMain("BLE scanning is not available.")
            return
        }
        scanner = activeScanner
        onStatusOnMain("Searching for BLE room Trapwire-$code...")
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceData(BLE_RACE_SERVICE_PARCEL_UUID, bleRoomServiceData(code))
                .build(),
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        activeScanner.startScan(filters, settings, scanCallback)
        mainHandler.postDelayed(scanTimeoutRunnable, BLE_SCAN_TIMEOUT_MS)
    }

    fun sendJson(json: JSONObject) {
        if (closed) return
        if (!ready) {
            pendingWrites.add(json)
            return
        }
        writeJsonNow(json)
    }

    fun close() {
        closed = true
        mainHandler.removeCallbacks(scanTimeoutRunnable)
        mainHandler.removeCallbacks(snapshotPollRunnable)
        stopScan()
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        ready = false
    }

    private fun stopScan() {
        runCatching { scanner?.stopScan(scanCallback) }
        scanner = null
        mainHandler.removeCallbacks(scanTimeoutRunnable)
    }

    private val scanTimeoutRunnable = Runnable {
        if (!closed && !ready) {
            stopScan()
            onErrorOnMain("No BLE controller found for code $code.")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val serviceData = result.scanRecord?.getServiceData(BLE_RACE_SERVICE_PARCEL_UUID) ?: return
            if (serviceData.asBleRoomCode() != code) return
            stopScan()
            onStatusOnMain("Found BLE controller. Connecting...")
            connect(result.device)
        }

        override fun onScanFailed(errorCode: Int) {
            onErrorOnMain("BLE scan failed: $errorCode")
        }
    }

    private fun connect(device: BluetoothDevice) {
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                onStatusOnMain("Discovering BLE race service...")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                ready = false
                if (!closed) onMain { onDisconnected() }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(BLE_RACE_SERVICE_UUID)
            commandCharacteristic = service?.getCharacteristic(BLE_COMMAND_CHARACTERISTIC_UUID)
            snapshotCharacteristic = service?.getCharacteristic(BLE_SNAPSHOT_CHARACTERISTIC_UUID)
            if (service == null || commandCharacteristic == null || snapshotCharacteristic == null) {
                onErrorOnMain("Controller does not expose the Trapwire BLE service.")
                close()
                return
            }
            runCatching { gatt.requestMtu(BLE_MTU_BYTES) }
            mainHandler.postDelayed({ markReady() }, 700L)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            markReady()
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            handleSnapshotRead(value, status)
        }

        @Deprecated("Deprecated in Android API 33")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            @Suppress("DEPRECATION")
            handleSnapshotRead(characteristic.value ?: ByteArray(0), status)
        }
    }

    private fun markReady() {
        if (ready || closed) return
        ready = true
        onMain { onConnected() }
        sendJson(joinJson)
        flushPendingWrites()
        mainHandler.removeCallbacks(snapshotPollRunnable)
        mainHandler.postDelayed(snapshotPollRunnable, BLE_SNAPSHOT_POLL_MS)
    }

    private fun flushPendingWrites() {
        while (pendingWrites.isNotEmpty()) writeJsonNow(pendingWrites.removeFirst())
    }

    private fun writeJsonNow(json: JSONObject) {
        val activeGatt = gatt ?: return
        val characteristic = commandCharacteristic ?: return
        val bytes = json.toBleBytes()
        if (bytes.size > BLE_MAX_APP_PAYLOAD_BYTES) {
            onErrorOnMain("BLE command is too large (${bytes.size} bytes).")
            return
        }
        @Suppress("DEPRECATION")
        characteristic.value = bytes
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        if (!activeGatt.writeCharacteristic(characteristic)) {
            onErrorOnMain("BLE write failed to start.")
        }
    }

    private fun readSnapshot() {
        val activeGatt = gatt ?: return
        val characteristic = snapshotCharacteristic ?: return
        activeGatt.readCharacteristic(characteristic)
    }

    private val snapshotPollRunnable = object : Runnable {
        override fun run() {
            if (closed || !ready) return
            readSnapshot()
            mainHandler.postDelayed(this, BLE_SNAPSHOT_POLL_MS)
        }
    }

    private fun handleSnapshotRead(value: ByteArray, status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS) return
        value.toJsonObjectOrNull()?.let { json -> onMain { onSnapshot(json) } }
    }

    private fun onMain(block: () -> Unit) {
        mainHandler.post(block)
    }

    private fun onStatusOnMain(message: String) = onMain { onStatus(message) }

    private fun onErrorOnMain(message: String) = onMain { onError(message) }
}
