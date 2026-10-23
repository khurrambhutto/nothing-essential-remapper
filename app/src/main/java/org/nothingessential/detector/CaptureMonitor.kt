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
)

/** Process-local memory only. Activity, service callbacks, and timeout run on the main thread. */
object CaptureMonitor {
    private val handler = Handler(Looper.getMainLooper())
    private val session = CaptureSession(SystemClock::elapsedRealtime)
    private val mutableState = MutableStateFlow(DetectorState())
    val state = mutableState.asStateFlow()
    private var foreground = false
    private var setKeyFiltering: ((Boolean) -> Unit)? = null
    private val timeout = Runnable { stop() }

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
        session.start()
        publish()
        setKeyFiltering?.invoke(true)
        handler.removeCallbacks(timeout)
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
    ) {
        if (!foreground || !session.isCapturing) return
        if (session.observe(
                keyCode, scanCode, keyName, edge, repeatCount,
                isTypingKey, isVirtual, isExcludedKey,
            )
        ) publish()
    }

    private fun publish() {
        mutableState.value = mutableState.value.copy(
            isCapturing = session.isCapturing,
            observations = session.observations,
        )
    }
}

