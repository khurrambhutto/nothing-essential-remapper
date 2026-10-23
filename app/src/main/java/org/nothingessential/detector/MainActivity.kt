// SPDX-License-Identifier: GPL-3.0-only
package org.nothingessential.detector

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
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
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshNotifications() }
    var showDisclosure by rememberSaveable { mutableStateOf(false) }

    val requestNotifications = {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    }

    DetectorContent(
        state = state,
        flash = flash,
        notificationsAllowed = notificationsAllowed,
        onActionsChange = CaptureMonitor::setActionsEnabled,
        onBackgroundChange = CaptureMonitor::setBackgroundEnabled,
        onStart = CaptureMonitor::start,
        onStop = CaptureMonitor::stop,
        onClear = CaptureMonitor::clear,
        onRequestNotifications = requestNotifications,
        onOpenAccessibility = { showDisclosure = true },
    )

    if (showDisclosure) {
        AccessDisclosureDialog(
            onDismiss = { showDisclosure = false },
            onOpenSettings = {
                showDisclosure = false
                openAccessibilitySettings()
            },
        )
    }
}

@Composable
private fun DetectorContent(
    state: DetectorState,
    flash: FlashlightState,
    notificationsAllowed: Boolean,
    onActionsChange: (Boolean) -> Unit,
    onBackgroundChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenAccessibility: () -> Unit,
) {
    val canEnableActions = state.actionsEnabled ||
        (state.serviceConnected && state.unlocked && flash.available && !flash.busy)
    val canEnableBackground = state.backgroundEnabled ||
        (notificationsAllowed && state.actionsEnabled && flash.available && state.unlocked)

    Scaffold { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Essential button",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    AccessibilityRow(
                        connected = state.serviceConnected,
                        onOpenAccessibility = onOpenAccessibility,
                    )
                }
            }
            item {
                Section(title = "Actions") {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            SettingSwitch(
                                title = "Enable double press",
                                supportingText = flash.message,
                                supportingColor = if (flash.available) null else MaterialTheme.colorScheme.error,
                                checked = state.actionsEnabled,
                                enabled = canEnableActions,
                                onCheckedChange = onActionsChange,
                            )
                        }
                    }
                }
            }
            item {
                Section(title = "Background") {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SettingSwitch(
                                title = "Use in other apps",
                                supportingText = backgroundStatus(state, notificationsAllowed, flash),
                                checked = state.backgroundEnabled,
                                enabled = canEnableBackground,
                                onCheckedChange = onBackgroundChange,
                            )
                            if (!notificationsAllowed) {
                                OutlinedButton(onClick = onRequestNotifications) {
                                    Text("Allow notifications")
                                }
                            }
                        }
                    }
                }
            }
            item {
                Section(title = "Capture") {
                    CaptureCard(state, onStart, onStop, onClear)
                }
            }
            item {
                Section(title = "Latest gesture") {
                    LatestGestureCard(state.gestures.firstOrNull())
                }
            }
            if (state.gestures.size > 1) {
                item {
                    Section(title = "History") {
                        GestureHistoryCard(state.gestures.drop(1))
                    }
                }
            }
            item {
                Section(
                    title = "Hardware events",
                    trailing = {
                        Text(
                            "CMF key 0 · scan 250",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                ) {
                    HardwareEventsCard(state.observations)
                }
            }
        }
    }
}

@Composable
private fun AccessibilityRow(
    connected: Boolean,
    onOpenAccessibility: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(
                    color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    shape = CircleShape,
                ),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (connected) "Accessibility on" else "Accessibility off",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (connected) {
            TextButton(onClick = onOpenAccessibility) { Text("Settings") }
        } else {
            FilledTonalButton(onClick = onOpenAccessibility) { Text("Enable") }
        }
    }
}

@Composable
private fun Section(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            trailing?.invoke()
        }
        content()
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    supportingText: String? = null,
    supportingColor: Color? = null,
) {
    val support = supportingText
    ListItem(
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (support != null) {
                    Text(
                        support,
                        style = MaterialTheme.typography.bodyMedium,
                        color = supportingColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier.semantics {
                    contentDescription = support?.let { "$title, $it" } ?: title
                },
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

@Composable
private fun CaptureCard(
    state: DetectorState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
) {
    val active = state.isCapturing || state.backgroundEnabled
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(captureStatus(state), style = MaterialTheme.typography.titleMedium)
            Button(
                enabled = state.serviceConnected,
                onClick = { if (active) onStop() else onStart() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        state.backgroundEnabled -> "Stop background control"
                        state.isCapturing -> "Stop capture"
                        else -> "Start 15-second capture"
                    },
                )
            }
            if (!active && (state.observations.isNotEmpty() || state.gestures.isNotEmpty())) {
                TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                    Text("Clear results")
                }
            }
        }
    }
}

@Composable
private fun LatestGestureCard(gesture: GestureObservation?) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                gesture?.type?.label ?: "No gesture",
                style = MaterialTheme.typography.headlineMedium,
            )
            if (gesture != null) {
                Text(
                    "+${gesture.elapsedMs} ms",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GestureHistoryCard(gestures: List<GestureObservation>) {
    Card(Modifier.fillMaxWidth()) {
        Column {
            gestures.forEachIndexed { index, gesture ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        gesture.type.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "+${gesture.elapsedMs} ms",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (index != gestures.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun HardwareEventsCard(observations: List<KeyObservation>) {
    if (observations.isEmpty()) {
        Text(
            "No events",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Card(Modifier.fillMaxWidth()) {
        Column {
            observations.forEachIndexed { index, observation ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${observation.edge}  ${observation.keyName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            "key ${observation.keyCode} · scan ${observation.scanCode} · repeat ${observation.repeatCount}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "+${observation.elapsedMs} ms",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (index != observations.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun AccessDisclosureDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Accessibility access") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Essential button detector uses accessibility to receive hardware key events.")
                DisclosureItem(
                    "During capture, it shows key code, scan code, press or release, repeat count, and elapsed time.",
                )
                DisclosureItem(
                    "Background control recognizes only the confirmed Essential key while you use other apps. " +
                        "It needs notification permission, pauses when the screen is off or locked, " +
                        "and shows a status notification with Stop.",
                )
                DisclosureItem(
                    "With actions on, it consumes the test key in either mode and keeps consuming the " +
                        "matching release if capture ends mid-press. Double press toggles the flashlight. " +
                        "Stopping control turns off a light this app turned on.",
                )
                DisclosureItem("It does not read screen content, store files, or use the network.")
                Text("You can disable the service in Android settings at any time.")
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text("Open settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun DisclosureItem(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•")
        Text(text)
    }
}

private fun captureStatus(state: DetectorState): String = when {
    state.backgroundEnabled -> "Background control on"
    state.isCapturing -> "Listening"
    state.serviceConnected -> "Ready to capture"
    else -> "Accessibility off"
}

private fun backgroundStatus(
    state: DetectorState,
    notificationsAllowed: Boolean,
    flash: FlashlightState,
): String = when {
    state.backgroundEnabled && !state.unlocked -> "Paused, screen locked"
    state.backgroundEnabled -> "On"
    !state.serviceConnected -> "Needs accessibility"
    !notificationsAllowed -> "Needs a status notification with Stop"
    !state.actionsEnabled -> "Needs actions"
    !flash.available -> "Flashlight unavailable"
    !state.unlocked -> "Locked"
    else -> "Off"
}
