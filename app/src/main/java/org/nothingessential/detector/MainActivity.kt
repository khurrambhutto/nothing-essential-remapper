// SPDX-License-Identifier: GPL-3.0-only
package org.nothingessential.detector

import android.content.Intent
import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.nothingessential.detector.ui.theme.NothingEssentialTheme

class MainActivity : ComponentActivity() {
    private lateinit var flashlight: FlashlightController
    private var notificationsAllowed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        flashlight = FlashlightRuntime.get(this)
        enableEdgeToEdge()
        setContent {
            NothingEssentialTheme {
                DetectorScreen(flashlight, notificationsAllowed, ::refreshNotifications) {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        flashlight.activate()
        refreshNotifications()
        CaptureMonitor.setForeground(true)
    }

    override fun onPause() {
        CaptureMonitor.setForeground(false)
        if (!CaptureMonitor.state.value.serviceConnected) flashlight.deactivate()
        super.onPause()
    }

    private fun refreshNotifications() {
        notificationsAllowed = BackgroundModeNotification(this).allowed()
        CaptureMonitor.refreshEnvironment()
    }
}

@Composable
private fun DetectorScreen(
    flashlight: FlashlightController,
    notificationsAllowed: Boolean,
    refreshNotifications: () -> Unit,
    openAccessibilitySettings: () -> Unit,
) {
    val state by CaptureMonitor.state.collectAsState()
    val flash by flashlight.state.collectAsState()
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshNotifications()
    }
    var showDisclosure by rememberSaveable { mutableStateOf(false) }

    Scaffold { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("Essential button", style = MaterialTheme.typography.headlineLarge)
                Text("Background prototype · v0.4.1", style = MaterialTheme.typography.titleMedium)
                Text("${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE}")
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Double press → flashlight", style = MaterialTheme.typography.titleLarge)
                        Text(flash.message)
                        Text("Uses the torch API. No Camera permission, photos, or video recording.")
                        SettingSwitch(
                            title = "Enable actions",
                            checked = state.actionsEnabled,
                            enabled = state.actionsEnabled || (state.serviceConnected && state.unlocked && flash.available && !flash.busy),
                            onCheckedChange = { CaptureMonitor.setActionsEnabled(it) },
                        )
                        Text("Only double press toggles the flashlight. Single and long press display results. " +
                            "Changing this switch stops all monitoring and turns off a light this app enabled.")
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Use in other apps", style = MaterialTheme.typography.titleLarge)
                        Text(when {
                            state.backgroundEnabled && !state.unlocked -> "Paused while screen is off or locked"
                            state.backgroundEnabled -> "Background control is on"
                            else -> "Background control is off"
                        })
                        if (!notificationsAllowed) {
                            Text("Allow status notifications to enable background control and its Stop button.")
                            OutlinedButton(onClick = {
                                if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                                }
                            }) { Text("Allow status notifications") }
                        }
                        SettingSwitch(
                            title = "Use in other apps",
                            checked = state.backgroundEnabled,
                            enabled = state.backgroundEnabled || (notificationsAllowed && state.actionsEnabled && flash.available && state.unlocked),
                            onCheckedChange = { CaptureMonitor.setBackgroundEnabled(it) },
                        )
                        Text("Enable actions, then turn this on. Leave the app and double press while " +
                            "the phone is unlocked. No capture timer is needed. Only the confirmed " +
                            "Essential key is recognized; raw key logging stays off.")
                        Text("Screen off or lock pauses actions, clears unfinished gestures, and turns " +
                            "off a light this app enabled. Unlock resumes background control. " +
                            "Use Stop in the notification to disable it. Service restart or reboot leaves it off.")
                    }
                }
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
                    if (state.backgroundEnabled) "Background control enabled"
                    else if (state.isCapturing) "Listening for 15 seconds…" else "Ready to capture",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text("For a short foreground diagnostic, start capture and press the Essential button. " +
                    "Try a single press, a quick double press, or hold for 600 ms. " +
                    if (state.actionsEnabled) "Double press will toggle the flashlight during capture."
                    else "Actions are off; button events pass through normally.")
                Button(
                    enabled = state.serviceConnected,
                    onClick = { if (state.isCapturing || state.backgroundEnabled) CaptureMonitor.stop() else CaptureMonitor.start() },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Text(if (state.backgroundEnabled) "Stop background control" else if (state.isCapturing) "Stop capture" else "Start 15-second capture")
                }
                TextButton(onClick = { CaptureMonitor.clear() }) { Text("Clear results and stop") }
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
                Text("Raw capture stops after 15 seconds or when you leave this screen. " +
                    "Background control continues only when explicitly enabled, with an unlocked screen. " +
                    "Typing, virtual, volume, power, and common navigation keys are ignored. " +
                    "Events stay in memory until cleared or the process ends. " +
                    "No internet access, screen reading, or system-package changes.")
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
                    "store files, or send data. Optional background control recognizes only the " +
                    "confirmed Essential key while using other apps and pauses on screen off or lock. " +
                    "It has an ongoing status notification with Stop. " +
                    "With Enable actions on, it consumes the test button's events in either mode " +
                    "and uses double press to toggle the flashlight. A matching release is still " +
                    "consumed if capture ends while the button is held. " +
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

@Composable
private fun SettingSwitch(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        content = {
            Text(title, style = MaterialTheme.typography.titleMedium)
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = title },
                thumbContent = if (checked) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
    )
}
