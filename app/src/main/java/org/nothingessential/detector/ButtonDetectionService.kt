// SPDX-License-Identifier: GPL-3.0-only
package org.nothingessential.detector

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class ButtonDetectionService : AccessibilityService() {
    private val consumption = KeyConsumptionGate()
    private var captureRequestsFiltering = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        CaptureMonitor.connected { enabled ->
            captureRequestsFiltering = enabled
            if (!enabled) consumption.discardPassThroughPresses()
            updateFiltering()
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
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

