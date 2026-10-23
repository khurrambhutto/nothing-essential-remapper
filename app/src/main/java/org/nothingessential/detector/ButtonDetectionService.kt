// SPDX-License-Identifier: GPL-3.0-only
package org.nothingessential.detector

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ButtonDetectionService : AccessibilityService() {
    private val consumption = KeyConsumptionGate()
    private var captureRequestsFiltering = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var flashlight: FlashlightController
    private lateinit var notification: BackgroundModeNotification
    private var receiverRegistered = false
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { CaptureMonitor.refreshEnvironment() }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        flashlight = FlashlightRuntime.get(this)
        flashlight.activate()
        notification = BackgroundModeNotification(this)
        CaptureMonitor.connected(setFiltering = { enabled ->
            captureRequestsFiltering = enabled
            if (!enabled) consumption.discardPassThroughPresses()
            updateFiltering()
        }, unlocked = {
            val keyguard = getSystemService(KeyguardManager::class.java)
            getSystemService(PowerManager::class.java).isInteractive &&
                !keyguard.isKeyguardLocked && !keyguard.isDeviceLocked
        }, notificationsAllowed = notification::allowed,
            toggle = flashlight::toggle, cleanup = flashlight::turnOffOwnedLight)
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else @Suppress("DEPRECATION") registerReceiver(screenReceiver, filter)
            receiverRegistered = true
        }
        scope.launch {
            CaptureMonitor.state.map { it.backgroundEnabled to it.unlocked }.distinctUntilChanged()
                .collect { notification.update(CaptureMonitor.state.value) }
        }
        scope.launch {
            flashlight.state.collect {
                if (!it.available && CaptureMonitor.state.value.actionsEnabled) CaptureMonitor.setActionsEnabled(false)
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        CaptureMonitor.refreshEnvironment()
        if (!CaptureMonitor.state.value.isCapturing && !CmfTestKey.matches(event.keyCode, event.scanCode)) return false
        val edge = when (event.action) {
            KeyEvent.ACTION_DOWN -> KeyEdge.DOWN
            KeyEvent.ACTION_UP -> KeyEdge.UP
            else -> return false
        }
        val isVirtual = event.device?.isVirtual != false ||
            event.flags and KeyEvent.FLAG_SOFT_KEYBOARD != 0
        val isExcluded = event.keyCode in excludedKeys
        val consume = consumption.onEdge(
            HardwareKey(event.deviceId, event.keyCode, event.scanCode), edge, event.repeatCount,
            intercept = CaptureMonitor.mayInterceptButton && !event.isPrintingKey &&
                !isVirtual && !isExcluded,
        )
        CaptureMonitor.observe(
            keyCode = event.keyCode,
            scanCode = event.scanCode,
            keyName = KeyEvent.keyCodeToString(event.keyCode),
            edge = edge,
            repeatCount = event.repeatCount,
            isTypingKey = event.isPrintingKey,
            isVirtual = isVirtual,
            isExcludedKey = isExcluded,
            isCanceled = event.isCanceled,
        )
        updateFiltering()
        return consume
    }

    private fun updateFiltering() {
        if (!captureRequestsFiltering) consumption.discardPassThroughPresses()
        val currentInfo = serviceInfo ?: return
        val requestedFlags = if (captureRequestsFiltering || consumption.hasConsumedPress)
            AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS else 0
        if (currentInfo.flags == requestedFlags && currentInfo.eventTypes == 0) return
        serviceInfo = currentInfo.apply {
            eventTypes = 0
            // Finish consuming an already claimed pair even if capture ends during a hold.
            flags = requestedFlags
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        CaptureMonitor.stop()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        CaptureMonitor.disconnected()
        consumption.reset()
        captureRequestsFiltering = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        CaptureMonitor.disconnected()
        consumption.reset()
        scope.cancel()
        if (receiverRegistered) unregisterReceiver(screenReceiver)
        if (::flashlight.isInitialized) flashlight.deactivate()
        if (::notification.isInitialized) notification.cancel()
        super.onDestroy()
    }

    private companion object {
        val excludedKeys = setOf(
            KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_APP_SWITCH, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL,
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT,
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT,
            KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT,
        )
    }
}
