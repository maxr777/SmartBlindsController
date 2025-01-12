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
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = getSharedPreferences("SmartBlindsPrefs", Context.MODE_PRIVATE)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SmartBlindsApp(this, sharedPreferences)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartBlindsApp(activity: ComponentActivity, sharedPreferences: SharedPreferences) {
    var openTime by remember { mutableStateOf("07:00") }
    var closeTime by remember { mutableStateOf("20:00") }
    var openLuxText by remember { mutableStateOf("50000") }
    var closeLuxText by remember { mutableStateOf("10000") }
    var openMode by remember { mutableStateOf("TIME") }
    var closeMode by remember { mutableStateOf("TIME") }

    // State variables for sensor readings
    var currentTime by remember { mutableStateOf("--:--") }
    var currentLux by remember { mutableStateOf("0") }

    // Time sync state variables
    var deviceTimeSync by remember { mutableStateOf(false) }
    var lastSyncTime by remember {
        mutableStateOf(sharedPreferences.getString("lastSyncTime", "Never") ?: "Never")
    }

    // Function to update sensor readings (placeholder)
    fun updateSensorReadings() {
        currentTime = getCurrentTimeFromESP32()
        currentLux = getCurrentLuxFromESP32()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                FilledTonalButton(
                    onClick = { updateSensorReadings() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Refresh Readings")
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
                            syncDeviceTime()
                            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            val newLastSyncTime = sdf.format(Date())
                            lastSyncTime = newLastSyncTime
                            sharedPreferences.edit().putString("lastSyncTime", newLastSyncTime).apply()
                            deviceTimeSync = true
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
                        onClick = { sendCommand("OPEN") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("OPEN")
                    }
                    FilledTonalButton(
                        onClick = { sendCommand("CLOSE") },
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
                        // Send all settings to ESP32
                        val settings = mapOf(
                            "openTime" to openTime,
                            "closeTime" to closeTime,
                            "openLux" to openLuxText,
                            "closeLux" to closeLuxText,
                            "openMode" to openMode,
                            "closeMode" to closeMode
                        )
                        sendSettings(settings)
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

fun syncDeviceTime() {
    val date = Date()
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    val timeString = timeFormat.format(date)
    val dateString = dateFormat.format(date)

    // Send time to ESP32
    sendTimeToESP32(timeString, dateString)
}

fun sendTimeToESP32(time: String, date: String) {
    // Implement the actual ESP32 communication here
    // This would use your preferred communication method (HTTP, MQTT, etc.)
    val command = mapOf(
        "command" to "SET_TIME",
        "time" to time,
        "date" to date
    )
    println("Sending time sync command: $command")
    // Add your actual ESP32 communication code here
}

fun sendCommand(command: String) {
    // Implement your ESP32 communication here
    println("Sending command: $command")
}

fun sendSettings(settings: Map<String, String>) {
    // Implement your ESP32 settings update here
    println("Sending settings update: $settings")
}

// Placeholder functions for ESP32 communication
fun getCurrentTimeFromESP32(): String {
    // Implement actual ESP32 time retrieval here
    return "12:34"
}

fun getCurrentLuxFromESP32(): String {
    // Implement actual ESP32 lux reading retrieval here
    return "45000"
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