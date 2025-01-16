//MainActivity.kt
package com.example.smartblindscontroller

import android.os.Bundle
import android.content.Context
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.app.TimePickerDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var bluetoothManager: BluetoothManager

    private fun checkBluetoothPermissions(): Boolean {
        val permissions = listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val hasPermissions = permissions.all { permission ->
            ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasPermissions) {
            ActivityCompat.requestPermissions(
                this,
                permissions.toTypedArray(),
                1
            )
        }

        return hasPermissions
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() &&
                grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // Initialize bluetooth manager only after permissions are granted
                bluetoothManager = BluetoothManager(this)
                // Force a recompose to update the UI
                setContent {
                    MaterialTheme {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            SmartBlindsApp(this, sharedPreferences, bluetoothManager)
                        }
                    }
                }
            } else {
                Toast.makeText(
                    this,
                    "Bluetooth permissions are required for this app",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize shared preferences first
        sharedPreferences = getSharedPreferences("SmartBlindsPrefs", Context.MODE_PRIVATE)

        // Only initialize BluetoothManager if permissions are granted
        if (checkBluetoothPermissions()) {
            bluetoothManager = BluetoothManager(this)
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var errorMessage by remember { mutableStateOf<String?>(null) }

                    // Add a check for bluetoothManager initialization
                    if (!::bluetoothManager.isInitialized) {
                        Text(
                            "Waiting for Bluetooth permissions...",
                            modifier = Modifier.padding(16.dp)
                        )
                        return@Surface
                    }

                    LaunchedEffect(Unit) {
                        bluetoothManager.errorState.collect { error ->
                            errorMessage = error
                        }
                    }

                    errorMessage?.let { error ->
                        Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
                        errorMessage = null
                    }

                    SmartBlindsApp(this, sharedPreferences, bluetoothManager)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothManager.disconnect()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartBlindsApp(
    activity: ComponentActivity,
    sharedPreferences: SharedPreferences,
    bluetoothManager: BluetoothManager
) {
    var openTime by rememberSaveable { mutableStateOf(sharedPreferences.getString("openTime", "07:00") ?: "07:00") }
    var closeTime by rememberSaveable { mutableStateOf(sharedPreferences.getString("closeTime", "20:00") ?: "20:00") }
    var openLuxText by rememberSaveable { mutableStateOf(sharedPreferences.getString("openLux", "50000") ?: "50000") }
    var closeLuxText by rememberSaveable { mutableStateOf(sharedPreferences.getString("closeLux", "10000") ?: "10000") }
    var openMode by rememberSaveable { mutableStateOf(sharedPreferences.getString("openMode", "TIME") ?: "TIME") }
    var closeMode by rememberSaveable { mutableStateOf(sharedPreferences.getString("closeMode", "TIME") ?: "TIME") }
    var isConnected by remember { mutableStateOf(false) }

    // State variables for sensor readings
    var currentTime by remember { mutableStateOf("--:--") }
    var currentLux by remember { mutableStateOf("0") }
    var currentBlindsStatus by remember { mutableStateOf("--") }
    var isRefreshing by remember { mutableStateOf(false) }

    // Time sync state variables
    var deviceTimeSync by remember { mutableStateOf(false) }
    var lastSyncTime by remember {
        mutableStateOf(sharedPreferences.getString("lastSyncTime", "Never") ?: "Never")
    }

    LaunchedEffect(isConnected) {
        if (isConnected) {
            bluetoothManager.requestSettings()
                .onSuccess { settings ->
                    openTime = settings.openTime
                    closeTime = settings.closeTime
                    openLuxText = settings.openLux
                    closeLuxText = settings.closeLux
                    openMode = settings.openMode
                    closeMode = settings.closeMode

                    // Save to SharedPreferences
                    sharedPreferences.edit().apply {
                        putString("openTime", settings.openTime)
                        putString("closeTime", settings.closeTime)
                        putString("openLux", settings.openLux)
                        putString("closeLux", settings.closeLux)
                        putString("openMode", settings.openMode)
                        putString("closeMode", settings.closeMode)
                    }.apply()
                }
                .onFailure { error ->
                    Toast.makeText(
                        activity,
                        "Failed to load settings: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    // Collect Bluetooth connection state
    LaunchedEffect(Unit) {
        bluetoothManager.connectionState.collect { connected ->
            isConnected = connected
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Bluetooth Connection Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Bluetooth Connection",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isConnected) "Connected" else "Disconnected",
                        color = if (isConnected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                    FilledTonalButton(
                        onClick = {
                            activity.lifecycleScope.launch {
                                if (!isConnected) {
                                    val result = bluetoothManager.connectToESP32()
                                    if (!result) {
                                        Toast.makeText(activity, "Failed to connect", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    bluetoothManager.disconnect()
                                }
                            }
                        }
                    ) {
                        Text(if (isConnected) "Disconnect" else "Connect")
                    }
                }
            }
        }

        // Current Readings Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Current Readings",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Device Time")
                        Text(
                            currentTime,
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Light Level")
                        Text(
                            "$currentLux lux",
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Blinds Status")
                        Text(
                            currentBlindsStatus,
                            style = MaterialTheme.typography.headlineSmall,
                            color = when (currentBlindsStatus) {
                                "OPEN" -> MaterialTheme.colorScheme.primary
                                "CLOSED" -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
                FilledTonalButton(
                    onClick = {
                        activity.lifecycleScope.launch {
                            isRefreshing = true
                            try {
                                bluetoothManager.requestStatus()
                                    .onSuccess { status ->
                                        currentTime = status.time
                                        currentLux = String.format("%.1f", status.lux)
                                        currentBlindsStatus = status.blinds
                                    }
                                    .onFailure { error ->
                                        Toast.makeText(
                                            activity,
                                            "Failed to refresh: ${error.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            } finally {
                                isRefreshing = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRefreshing && isConnected
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Refresh Readings")
                    }
                }
            }
        }


        // Time Sync Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Time Synchronization",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Last synced:")
                        Text(
                            lastSyncTime,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            activity.lifecycleScope.launch {
                                when (val result = bluetoothManager.syncTime()) {
                                    is BluetoothManager.BluetoothResult.Error -> {
                                        Toast.makeText(activity, result.message, Toast.LENGTH_SHORT).show()
                                    }
                                    is BluetoothManager.BluetoothResult.Success -> {
                                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                        val newLastSyncTime = sdf.format(Date())
                                        lastSyncTime = newLastSyncTime
                                        sharedPreferences.edit().putString("lastSyncTime", newLastSyncTime).apply()
                                        deviceTimeSync = true
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Sync Device Time")
                    }
                }
                if (deviceTimeSync) {
                    Text(
                        "Time synchronized successfully!",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // Manual Controls
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Manual Control",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            activity.lifecycleScope.launch {
                                when (val result = bluetoothManager.sendCommand(BluetoothManager.MANUAL_TURN_ON)) {
                                    is BluetoothManager.BluetoothResult.Error -> {
                                        Toast.makeText(activity, result.message, Toast.LENGTH_SHORT).show()
                                    }
                                    is BluetoothManager.BluetoothResult.Success -> {
                                        // Command successful
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("OPEN")
                    }

                    FilledTonalButton(
                        onClick = {
                            activity.lifecycleScope.launch {
                                when (val result = bluetoothManager.sendCommand(BluetoothManager.MANUAL_TURN_OFF)) {
                                    is BluetoothManager.BluetoothResult.Error -> {
                                        Toast.makeText(activity, result.message, Toast.LENGTH_SHORT).show()
                                    }
                                    is BluetoothManager.BluetoothResult.Success -> {
                                        // Command successful
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("CLOSE")
                    }
                }
            }
        }

        // Time Settings
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Schedule",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open at")
                    FilledTonalButton(
                        onClick = {
                            val currentTime = openTime.split(":")
                            TimePickerDialog(
                                activity,
                                { _, hour, minute ->
                                    openTime = String.format("%02d:%02d", hour, minute)
                                },
                                currentTime[0].toInt(),
                                currentTime[1].toInt(),
                                true
                            ).show()
                        }
                    ) {
                        Text(openTime)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close at")
                    FilledTonalButton(
                        onClick = {
                            val currentTime = closeTime.split(":")
                            TimePickerDialog(
                                activity,
                                { _, hour, minute ->
                                    closeTime = String.format("%02d:%02d", hour, minute)
                                },
                                currentTime[0].toInt(),
                                currentTime[1].toInt(),
                                true
                            ).show()
                        }
                    ) {
                        Text(closeTime)
                    }
                }
            }
        }

        // Light Level Settings
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Light Sensitivity",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open above:")
                    OutlinedTextField(
                        value = openLuxText,
                        onValueChange = { openLuxText = validateLux(it) },
                        modifier = Modifier.width(120.dp),
                        suffix = { Text("lux") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close below:")
                    OutlinedTextField(
                        value = closeLuxText,
                        onValueChange = { closeLuxText = validateLux(it) },
                        modifier = Modifier.width(120.dp),
                        suffix = { Text("lux") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }
        }

        // Control Mode
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Trigger Mode",
                    style = MaterialTheme.typography.titleMedium
                )
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Opening trigger:", modifier = Modifier.padding(bottom = 4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = openMode == "TIME",
                            onClick = { openMode = "TIME" }
                        )
                        Text("Time", modifier = Modifier.padding(start = 4.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(
                            selected = openMode == "LIGHT",
                            onClick = { openMode = "LIGHT" }
                        )
                        Text("Light level", modifier = Modifier.padding(start = 4.dp))
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Closing trigger:", modifier = Modifier.padding(bottom = 4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = closeMode == "TIME",
                            onClick = { closeMode = "TIME" }
                        )
                        Text("Time", modifier = Modifier.padding(start = 4.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(
                            selected = closeMode == "LIGHT",
                            onClick = { closeMode = "LIGHT" }
                        )
                        Text("Light level", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }

        // Update Button Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        activity.lifecycleScope.launch {
                            val settings = mapOf(
                                "openTime" to openTime,
                                "closeTime" to closeTime,
                                "openLux" to openLuxText,
                                "closeLux" to closeLuxText,
                                "openMode" to openMode,
                                "closeMode" to closeMode
                            )

                            when (val result = bluetoothManager.sendSettings(settings)) {
                                is BluetoothManager.BluetoothResult.Error -> {
                                    Toast.makeText(activity, result.message, Toast.LENGTH_SHORT).show()
                                }
                                is BluetoothManager.BluetoothResult.Success -> {
                                    // Save to SharedPreferences after successful update
                                    sharedPreferences.edit().apply {
                                        putString("openTime", openTime)
                                        putString("closeTime", closeTime)
                                        putString("openLux", openLuxText)
                                        putString("closeLux", closeLuxText)
                                        putString("openMode", openMode)
                                        putString("closeMode", closeMode)
                                    }.apply()
                                    Toast.makeText(activity, "Settings updated successfully", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Update Settings")
                }
            }
        }

        // Add some padding at the bottom for better scrolling
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// Validation function for lux input
fun validateLux(input: String): String {
    return input.filter { it.isDigit() }.let {
        when {
            it.isEmpty() -> "0"
            it.toLongOrNull() ?: 0 > 100000 -> "100000"
            else -> it
        }
    }
}

