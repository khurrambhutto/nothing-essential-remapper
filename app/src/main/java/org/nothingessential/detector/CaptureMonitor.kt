// SPDX-License-Identifier: GPL-3.0-only
package org.nothingessential.detector

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DetectorState(
    val serviceConnected: Boolean = false,
    val isCapturing: Boolean = false,
    val observations: List<KeyObservation> = emptyList(),
    val gestures: List<GestureObservation> = emptyList(),
    val actionsEnabled: Boolean = false,
    val backgroundEnabled: Boolean = false,
    val unlocked: Boolean = false,
)

/** Main-thread runtime shared by the activity and system-bound accessibility service. */
object CaptureMonitor {
    private val handler = Handler(Looper.getMainLooper())
    private val session = CaptureSession(SystemClock::uptimeMillis)
    private val recognizer = GestureRecognizer(SystemClock::uptimeMillis)
    private val mutableState = MutableStateFlow(DetectorState())
    val state = mutableState.asStateFlow()
    private var foreground = false
    private var startedAt = 0L
    private var gestures: List<GestureObservation> = emptyList()
    private var toggleFlashlight: (() -> Unit)? = null
    private var stopFlashlight: (() -> Unit)? = null
    private var setKeyFiltering: ((Boolean) -> Unit)? = null
    private var checkUnlocked: (() -> Boolean)? = null
    private var canNotify: (() -> Boolean)? = null
    private val timeout = Runnable { stop() }
    private val gestureTick = Runnable {
        refreshEnvironment()
        if (policy.canRecognize) {
            recordGestures(recognizer.advance())
            publish()
            scheduleGestureTick()
        } else recognizer.cancel()
    }

    private val policy: MonitoringPolicy
        get() = mutableState.value.let {
            MonitoringPolicy(foreground, it.unlocked, session.isCapturing, it.backgroundEnabled, it.actionsEnabled)
        }
    val mayInterceptButton: Boolean get() = policy.canAct

    fun connected(
        setFiltering: (Boolean) -> Unit,
        unlocked: () -> Boolean,
        notificationsAllowed: () -> Boolean,
        toggle: () -> Unit,
        cleanup: () -> Unit,
    ) {
        setKeyFiltering = setFiltering
        checkUnlocked = unlocked
        canNotify = notificationsAllowed
        toggleFlashlight = toggle
        stopFlashlight = cleanup
        mutableState.value = mutableState.value.copy(serviceConnected = true)
        refreshEnvironment()
        setFiltering(false)
    }

    fun disconnected() {
        setActionsEnabled(false)
        setKeyFiltering = null
        toggleFlashlight = null
        stopFlashlight = null
        checkUnlocked = null
        canNotify = null
        mutableState.value = mutableState.value.copy(serviceConnected = false, unlocked = false)
    }

    fun refreshEnvironment() {
        val unlocked = checkUnlocked?.invoke() == true
        if (!unlocked && mutableState.value.unlocked) {
            handler.removeCallbacks(timeout)
            handler.removeCallbacks(gestureTick)
            recognizer.cancel()
            session.stop()
            stopFlashlight?.invoke()
        }
        mutableState.value = mutableState.value.copy(unlocked = unlocked)
        if (mutableState.value.backgroundEnabled && canNotify?.invoke() != true) {
            setActionsEnabled(false)
        }
        publish()
    }

    fun setForeground(value: Boolean) {
        foreground = value
        refreshEnvironment()
        if (!value && !mutableState.value.backgroundEnabled) {
            setActionsEnabled(false)
        } else if (!value) {
            // Leave only the confirmed-key background listener, not raw diagnostics.
            handler.removeCallbacks(timeout)
            session.stop()
            publish()
        }
    }

    fun setActionsEnabled(enabled: Boolean) {
        stop()
        mutableState.value = mutableState.value.copy(
            actionsEnabled = enabled && foreground && mutableState.value.unlocked && toggleFlashlight != null,
        )
    }

    fun setBackgroundEnabled(enabled: Boolean) {
        val eligible = foreground && mutableState.value.serviceConnected && mutableState.value.actionsEnabled &&
            checkUnlocked?.invoke() == true && canNotify?.invoke() == true
        stop()
        if (enabled && eligible) {
            startedAt = SystemClock.uptimeMillis()
            gestures = emptyList()
            session.clear()
            mutableState.value = mutableState.value.copy(backgroundEnabled = true)
            publish()
        }
    }

    fun start() {
        refreshEnvironment()
        if (!foreground || !mutableState.value.serviceConnected || !mutableState.value.unlocked) return
        stop()
        gestures = emptyList()
        startedAt = SystemClock.uptimeMillis()
        session.start()
        publish()
        handler.postDelayed(timeout, 15_000L)
    }

    /** Stops either mode; the user may explicitly start a new one. */
    fun stop() {
        handler.removeCallbacks(timeout)
        handler.removeCallbacks(gestureTick)
        recognizer.cancel()
        session.stop()
        mutableState.value = mutableState.value.copy(backgroundEnabled = false)
        stopFlashlight?.invoke()
        publish()
    }

    fun clear() {
        stop()
        session.clear()
        gestures = emptyList()
        publish()
    }

    fun observe(
        keyCode: Int, scanCode: Int, keyName: String, edge: KeyEdge, repeatCount: Int,
        isTypingKey: Boolean, isVirtual: Boolean, isExcludedKey: Boolean, isCanceled: Boolean = false,
    ) {
        refreshEnvironment()
        if (!policy.canRecognize || isTypingKey || isVirtual || isExcludedKey) return
        if (policy.canRecordRaw) {
            session.observe(keyCode, scanCode, keyName, edge, repeatCount, false, false, false)
        }
        if (CmfTestKey.matches(keyCode, scanCode)) {
            if (isCanceled) recognizer.cancel()
            else recordGestures(recognizer.onEdge(edge, repeatCount))
            scheduleGestureTick()
        }
        publish()
    }

    private fun recordGestures(types: List<GestureType>) {
        for (type in types) {
            gestures = (listOf(GestureObservation(type, SystemClock.uptimeMillis() - startedAt)) + gestures).take(16)
            // Recheck lock state at execution, including delayed single/long callbacks.
            if (type == GestureType.DOUBLE_PRESS && policy.canAct && checkUnlocked?.invoke() == true) {
                toggleFlashlight?.invoke()
            }
        }
    }

    private fun scheduleGestureTick() {
        handler.removeCallbacks(gestureTick)
        recognizer.nextDeadlineMs?.let {
            handler.postDelayed(gestureTick, (it - SystemClock.uptimeMillis()).coerceAtLeast(1L))
        }
    }

    private fun publish() {
        mutableState.value = mutableState.value.copy(
            isCapturing = session.isCapturing, observations = session.observations, gestures = gestures,
        )
        setKeyFiltering?.invoke(policy.canRecognize)
    }
}
