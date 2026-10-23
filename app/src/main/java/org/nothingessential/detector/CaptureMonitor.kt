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
)

/** Process-local memory only. Activity, service callbacks, and timeout run on the main thread. */
object CaptureMonitor {
    private val handler = Handler(Looper.getMainLooper())
    private val session = CaptureSession(SystemClock::uptimeMillis)
    private val recognizer = GestureRecognizer(SystemClock::uptimeMillis)
    private var captureStartedAt = 0L
    private var gestures: List<GestureObservation> = emptyList()
    private val mutableState = MutableStateFlow(DetectorState())
    val state = mutableState.asStateFlow()
    private var foreground = false
    private var setKeyFiltering: ((Boolean) -> Unit)? = null
    private val timeout = Runnable { stop() }
    private val gestureTick = Runnable {
        if (foreground && session.isCapturing) {
            recordGestures(recognizer.advance())
            publish()
            scheduleGestureTick()
        } else {
            recognizer.cancel()
        }
    }

    fun connected(setFiltering: (Boolean) -> Unit) {
        setKeyFiltering = setFiltering
        setFiltering(false)
        mutableState.value = mutableState.value.copy(serviceConnected = true)
    }

    fun disconnected() {
        stop()
        setKeyFiltering = null
        mutableState.value = mutableState.value.copy(serviceConnected = false)
    }

    fun setForeground(value: Boolean) {
        foreground = value
        if (!value) stop()
    }

    fun start() {
        if (!foreground || !mutableState.value.serviceConnected) return
        handler.removeCallbacks(gestureTick)
        recognizer.cancel()
        gestures = emptyList()
        captureStartedAt = SystemClock.uptimeMillis()
        session.start()
        publish()
        setKeyFiltering?.invoke(true)
        handler.removeCallbacks(timeout)
        handler.removeCallbacks(gestureTick)
        recognizer.cancel()
        handler.postDelayed(timeout, 15_000L)
    }

    fun stop() {
        handler.removeCallbacks(timeout)
        session.stop()
        setKeyFiltering?.invoke(false)
        publish()
    }

    fun clear() {
        stop()
        session.clear()
        gestures = emptyList()
        publish()
    }

    fun observe(
        keyCode: Int,
        scanCode: Int,
        keyName: String,
        edge: KeyEdge,
        repeatCount: Int,
        isTypingKey: Boolean,
        isVirtual: Boolean,
        isExcludedKey: Boolean,
        isCanceled: Boolean = false,
    ) {
        if (!foreground || !session.isCapturing) return
        if (session.observe(
                keyCode, scanCode, keyName, edge, repeatCount,
                isTypingKey, isVirtual, isExcludedKey,
            )
        ) {
            if (CmfTestKey.matches(keyCode, scanCode)) {
                if (isCanceled) recognizer.cancel()
                else recordGestures(recognizer.onEdge(edge, repeatCount))
                scheduleGestureTick()
            }
            publish()
        }
    }

    private fun recordGestures(types: List<GestureType>) {
        for (type in types) {
            gestures = (listOf(GestureObservation(
                type, SystemClock.uptimeMillis() - captureStartedAt,
            )) + gestures).take(16)
        }
    }

    private fun scheduleGestureTick() {
        handler.removeCallbacks(gestureTick)
        recognizer.nextDeadlineMs?.let { deadline ->
            handler.postDelayed(gestureTick, (deadline - SystemClock.uptimeMillis()).coerceAtLeast(1L))
        }
    }

    private fun publish() {
        mutableState.value = mutableState.value.copy(
            isCapturing = session.isCapturing,
            observations = session.observations,
            gestures = gestures,
        )
    }
}

