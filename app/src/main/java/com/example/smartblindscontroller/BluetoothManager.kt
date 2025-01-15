// BluetoothManager.kt
package com.example.smartblindscontroller

import java.util.Calendar
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.util.*

class BluetoothManager(private val context: Context) {
    private val TAG = "BluetoothManager"
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private val ESP32_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState

    sealed class BluetoothResult {
        object Success : BluetoothResult()
        data class Error(val message: String) : BluetoothResult()
    }

    companion object {
        const val MANUAL_TURN_ON = 1
        const val MANUAL_TURN_OFF = 2
        const val UPDATE_SETTINGS = 3
        const val MANUAL_SYNC_TIME = 4
    }

    private fun hasRequiredPermissions(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) ==
                    PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) ==
                    PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean {
        if (!hasRequiredPermissions()) {
            _errorState.value = "Missing Bluetooth permissions"
            return false
        }
        return try {
            bluetoothAdapter?.isEnabled == true
        } catch (e: SecurityException) {
            _errorState.value = "Missing Bluetooth permissions"
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun findESP32Device(deviceName: String): BluetoothDevice? {
        if (!hasRequiredPermissions()) {
            _errorState.value = "Missing Bluetooth permissions"
            return null
        }

        return try {
            val pairedDevices = bluetoothAdapter?.bondedDevices

            pairedDevices?.forEach { device ->
                try {
                    Log.d(TAG, "Paired device: '${device.name}' with address ${device.address}")
                } catch (e: SecurityException) {
                    Log.e(TAG, "Security exception while accessing device name", e)
                }
            }

            pairedDevices?.find { device ->
                try {
                    val deviceFound = device.name?.equals(deviceName, ignoreCase = true) == true
                    if (deviceFound) {
                        Log.d(TAG, "Found matching device: ${device.name}")
                    }
                    deviceFound
                } catch (e: SecurityException) {
                    false
                }
            }
        } catch (e: SecurityException) {
            _errorState.value = "Missing Bluetooth permissions"
            null
        }
    }

    @Suppress("DEPRECATION")
    suspend fun connectToESP32(deviceName: String = "ESP32test"): Boolean {
        Log.d(TAG, "Starting connection attempt to $deviceName")

        if (!hasRequiredPermissions()) {
            _errorState.value = "Missing Bluetooth permissions"
            return false
        }

        if (!isBluetoothSupported()) {
            Log.e(TAG, "Bluetooth not supported")
            _errorState.value = "Bluetooth not supported on this device"
            return false
        }

        if (!isBluetoothEnabled()) {
            Log.e(TAG, "Bluetooth not enabled")
            _errorState.value = "Bluetooth is not enabled"
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                disconnect()  // Ensure clean state
                Log.d(TAG, "Searching for ESP32 device...")

                val esp32Device = findESP32Device(deviceName) ?: run {
                    Log.e(TAG, "ESP32 device not found in paired devices")
                    _errorState.value = "ESP32 device not found in paired devices"
                    return@withContext false
                }

                try {
                    Log.d(TAG, "Found device: ${esp32Device.name} (${esp32Device.address})")
                } catch (e: SecurityException) {
                    Log.d(TAG, "Found device with address: ${esp32Device.address}")
                }

                try {
                    // Try insecure connection first
                    Log.d(TAG, "Attempting insecure connection...")
                    bluetoothSocket = esp32Device.createInsecureRfcommSocketToServiceRecord(ESP32_UUID)
                    bluetoothSocket?.connect()
                } catch (e: Exception) {
                    Log.e(TAG, "Insecure connection failed, trying secure connection", e)
                    try {
                        // Try secure connection
                        bluetoothSocket = esp32Device.createRfcommSocketToServiceRecord(ESP32_UUID)
                        bluetoothSocket?.connect()
                    } catch (e: Exception) {
                        Log.e(TAG, "Secure connection failed, trying fallback method", e)
                        // Try fallback method
                        try {
                            val method = esp32Device.javaClass.getMethod("createRfcommSocket", Int::class.java)
                            bluetoothSocket = method.invoke(esp32Device, 1) as BluetoothSocket
                            bluetoothSocket?.connect()
                        } catch (e2: Exception) {
                            Log.e(TAG, "Fallback connection failed", e2)
                            _errorState.value = "Connection failed: ${e2.message}"
                            disconnect()
                            return@withContext false
                        }
                    }
                }

                _connectionState.value = bluetoothSocket?.isConnected == true
                if (_connectionState.value) {
                    _errorState.value = null
                    Log.d(TAG, "Connection established successfully")
                } else {
                    Log.e(TAG, "Socket connected but connection state check failed")
                }
                return@withContext _connectionState.value

            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception during connection", e)
                _errorState.value = "Missing Bluetooth permissions"
                disconnect()
                false
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during connection", e)
                e.printStackTrace()
                _errorState.value = "Connection error: ${e.message}"
                disconnect()
                false
            }
        }
    }

    @Suppress("DEPRECATION")
    suspend fun sendCommand(command: Int): BluetoothResult {
        if (!hasRequiredPermissions()) {
            _errorState.value = "Missing Bluetooth permissions"
            return BluetoothResult.Error("Missing Bluetooth permissions")
        }

        return withContext(Dispatchers.IO) {
            try {
                if (!_connectionState.value) {
                    _errorState.value = "Not connected to device"
                    return@withContext BluetoothResult.Error("Not connected to device")
                }

                val commandString = "$command\n"
                Log.d(TAG, "Sending command: $command")

                // Clear any pending input first
                while (bluetoothSocket?.inputStream?.available() ?: 0 > 0) {
                    bluetoothSocket?.inputStream?.skip(bluetoothSocket?.inputStream?.available()?.toLong() ?: 0)
                }

                // Send command
                bluetoothSocket?.outputStream?.write(commandString.toByteArray())
                bluetoothSocket?.outputStream?.flush()

                // Wait for response with timeout
                val buffer = ByteArray(1024)
                var response = ""

                withTimeout(5000) {
                    // Wait a bit for the response to start arriving
                    delay(100)

                    // Read until we get a complete response
                    while (!response.contains("OK") && !response.contains("ERROR")) {
                        if (bluetoothSocket?.inputStream?.available() ?: 0 > 0) {
                            val bytes = bluetoothSocket?.inputStream?.read(buffer)
                            if (bytes != null && bytes > 0) {
                                response += String(buffer, 0, bytes)
                            }
                        }
                        // Small delay to prevent tight polling
                        delay(50)
                    }
                }

                Log.d(TAG, "Received response: $response")

                if (!response.contains("OK")) {
                    throw IOException("Invalid response: $response")
                }

                BluetoothResult.Success

            } catch (e: SecurityException) {
                _errorState.value = "Missing Bluetooth permissions"
                _connectionState.value = false
                BluetoothResult.Error(e.message ?: "Security error")
            } catch (e: IOException) {
                e.printStackTrace()
                _errorState.value = "Command failed: ${e.message}"
                _connectionState.value = false
                // Only attempt reconnect if we've actually lost connection
                if (bluetoothSocket?.isConnected != true) {
                    launch {
                        connectToESP32()
                    }
                }
                BluetoothResult.Error(e.message ?: "IO error")
            } catch (e: Exception) {
                e.printStackTrace()
                _errorState.value = "Unexpected error: ${e.message}"
                BluetoothResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    @Suppress("DEPRECATION")
    suspend fun sendSettings(settings: Map<String, String>): BluetoothResult {
        if (!hasRequiredPermissions()) {
            _errorState.value = "Missing Bluetooth permissions"
            return BluetoothResult.Error("Missing Bluetooth permissions")
        }

        return withContext(Dispatchers.IO) {
            try {
                if (!_connectionState.value) {
                    _errorState.value = "Not connected to device"
                    return@withContext BluetoothResult.Error("Not connected to device")
                }

                // Format settings string according to ESP32 format
                val settingsString = StringBuilder().apply {
                    append("$UPDATE_SETTINGS|")
                    settings.forEach { (key, value) ->
                        append("$key:$value|")
                    }
                    append("\n")
                }.toString()

                Log.d(TAG, "Sending settings: $settingsString")

                // Clear any pending input first
                while (bluetoothSocket?.inputStream?.available() ?: 0 > 0) {
                    bluetoothSocket?.inputStream?.skip(bluetoothSocket?.inputStream?.available()?.toLong() ?: 0)
                }

                bluetoothSocket?.outputStream?.write(settingsString.toByteArray())
                bluetoothSocket?.outputStream?.flush()

                // Wait for response with timeout
                val buffer = ByteArray(1024)
                var response = ""

                withTimeout(5000) {
                    // Wait a bit for the response to start arriving
                    delay(100)

                    // Read until we get a complete response
                    while (!response.contains("OK") && !response.contains("ERROR")) {
                        if (bluetoothSocket?.inputStream?.available() ?: 0 > 0) {
                            val bytes = bluetoothSocket?.inputStream?.read(buffer)
                            if (bytes != null && bytes > 0) {
                                response += String(buffer, 0, bytes)
                            }
                        }
                        // Small delay to prevent tight polling
                        delay(50)
                    }
                }

                Log.d(TAG, "Received response: $response")

                if (!response.contains("OK")) {
                    throw IOException("Invalid response: $response")
                }

                BluetoothResult.Success
            } catch (e: Exception) {
                e.printStackTrace()
                _errorState.value = "Settings update failed: ${e.message}"
                _connectionState.value = false
                BluetoothResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    @Suppress("DEPRECATION")
    suspend fun syncTime(): BluetoothResult {
        if (!hasRequiredPermissions()) {
            _errorState.value = "Missing Bluetooth permissions"
            return BluetoothResult.Error("Missing Bluetooth permissions")
        }

        return withContext(Dispatchers.IO) {
            try {
                if (!_connectionState.value) {
                    _errorState.value = "Not connected to device"
                    return@withContext BluetoothResult.Error("Not connected to device")
                }

                // Get current time using Calendar API for better compatibility
                val calendar = Calendar.getInstance()
                val timeString = StringBuilder().apply {
                    append("$MANUAL_SYNC_TIME|")
                    append(String.format("%04d-%02d-%02d %02d:%02d:%02d",
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH) + 1, // Calendar months are 0-based
                        calendar.get(Calendar.DAY_OF_MONTH),
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        calendar.get(Calendar.SECOND)
                    ))
                    append("|\n")
                }.toString()

                Log.d(TAG, "Sending time sync: $timeString")

                // Clear any pending input
                while (bluetoothSocket?.inputStream?.available() ?: 0 > 0) {
                    bluetoothSocket?.inputStream?.skip(bluetoothSocket?.inputStream?.available()?.toLong() ?: 0)
                }

                bluetoothSocket?.outputStream?.write(timeString.toByteArray())
                bluetoothSocket?.outputStream?.flush()

                // Wait for response with timeout
                val buffer = ByteArray(1024)
                var response = ""

                withTimeout(5000) {
                    delay(100)
                    while (!response.contains("OK") && !response.contains("ERROR")) {
                        if (bluetoothSocket?.inputStream?.available() ?: 0 > 0) {
                            val bytes = bluetoothSocket?.inputStream?.read(buffer)
                            if (bytes != null && bytes > 0) {
                                response += String(buffer, 0, bytes)
                            }
                        }
                        delay(50)
                    }
                }

                Log.d(TAG, "Received response: $response")

                if (!response.contains("OK")) {
                    throw IOException("Invalid response: $response")
                }

                BluetoothResult.Success
            } catch (e: Exception) {
                e.printStackTrace()
                _errorState.value = "Time sync failed: ${e.message}"
                _connectionState.value = false
                BluetoothResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun disconnect() {
        try {
            bluetoothSocket?.close()
        } catch (e: SecurityException) {
            _errorState.value = "Missing Bluetooth permissions"
        } catch (e: IOException) {
            e.printStackTrace()
            _errorState.value = "Disconnect error: ${e.message}"
        } finally {
            bluetoothSocket = null
            _connectionState.value = false
        }
    }
}