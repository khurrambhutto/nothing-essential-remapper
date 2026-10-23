// SPDX-License-Identifier: GPL-3.0-only
package org.nothingessential.detector

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF333333),
                    background = Color(0xFFF7F7F7),
                    surface = Color(0xFFF7F7F7),
                ),
            ) {
                DetectorScreen {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        CaptureMonitor.setForeground(true)
    }

    override fun onPause() {
        CaptureMonitor.setForeground(false)
        super.onPause()
    }
}

@Composable
private fun DetectorScreen(openAccessibilitySettings: () -> Unit) {
    val state by CaptureMonitor.state.collectAsState()
    var showDisclosure by rememberSaveable { mutableStateOf(false) }

    Scaffold { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("Essential button", style = MaterialTheme.typography.headlineLarge)
                Text("Gesture prototype · v0.2.0", style = MaterialTheme.typography.titleMedium)
                Text("${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE}")
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            if (state.serviceConnected) "Service connected" else "Service not connected",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text("Enable Essential button detector in Android accessibility settings, then return here.")
                        OutlinedButton(onClick = { showDisclosure = true }) {
                            Text("Accessibility settings")
                        }
                    }
                }
            }
            item {
                Text(
                    if (state.isCapturing) "Listening for 15 seconds…" else "Ready to capture",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text("Start capture, then press and release only the physical Essential button. " +
                    "Try a single press, a quick double press, or hold for 600 ms. " +
                    "The app only displays results; it does not change what the button does.")
                Button(
                    enabled = state.serviceConnected,
                    onClick = { if (state.isCapturing) CaptureMonitor.stop() else CaptureMonitor.start() },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Text(if (state.isCapturing) "Stop capture" else "Start 15-second capture")
                }
                TextButton(onClick = { CaptureMonitor.clear() }) { Text("Clear results") }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Latest gesture", style = MaterialTheme.typography.titleMedium)
                        Text(
                            state.gestures.firstOrNull()?.type?.label ?: "No gesture yet",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text("CMF test profile: key code 0, scan code 250. " +
                            "Double press: second press starts within 300 ms of the first release. " +
                            "Long press: hold for 600 ms.")
                        Text("Single presses wait 300 ms to rule out a double press. " +
                            "Stopping capture discards unfinished gestures.")
                    }
                }
            }
            items(state.gestures) { gesture ->
                Text("${gesture.type.label}  ·  +${gesture.elapsedMs} ms")
            }
            item {
                Text("Observed hardware events", style = MaterialTheme.typography.titleMedium)
                Text("The gesture profile uses the codes observed on your CMF A001. " +
                    "Other hardware events may appear below but do not produce gestures.")
            }
            if (state.observations.isEmpty()) {
                item {
                    Text("No events yet. If nothing appears, Nothing OS may be intercepting the button, " +
                        "or another accessibility service may own key filtering. " +
                        "Essential Space has not been changed by this app.")
                }
            }
            items(state.observations) { observation ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${observation.edge}  ${observation.keyName}", fontFamily = FontFamily.Monospace)
                        Text("Key code ${observation.keyCode}  ·  Scan code ${observation.scanCode}")
                        Text("Repeat ${observation.repeatCount}  ·  +${observation.elapsedMs} ms")
                    }
                }
            }
            item {
                Text("Capture stops after 15 seconds or when you leave this screen. " +
                    "Typing, virtual, volume, power, and common navigation keys are ignored. " +
                    "Events stay in memory until cleared or the process ends. " +
                    "No internet access, screen reading, remapping, or system changes.")
            }
        }
    }

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text("Hardware key access") },
            text = {
                Text("Android accessibility access lets this prototype receive hardware key events. " +
                    "During a capture it displays non-typing key codes, scan codes, press/release, " +
                    "repeat counts, and relative times. It does not request screen content, " +
                    "store files, or send data. Filtering is requested only during a foreground capture. " +
                    "You can disable the service in Android settings at any time.")
            },
            confirmButton = {
                TextButton(onClick = { showDisclosure = false; openAccessibilitySettings() }) {
                    Text("Continue to settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosure = false }) { Text("Cancel") }
            },
        )
    }
}

