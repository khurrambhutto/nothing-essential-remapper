// SPDX-License-Identifier: GPL-3.0-only
package org.nothingessential.detector

enum class KeyEdge { DOWN, UP }

data class KeyObservation(
    val keyCode: Int,
    val scanCode: Int,
    val keyName: String,
    val edge: KeyEdge,
    val repeatCount: Int,
    val elapsedMs: Long,
)

/** No Android dependency: the capture boundary is testable with a monotonic fake clock. */
class CaptureSession(
    private val now: () -> Long,
    private val durationMs: Long = 15_000L,
    private val capacity: Int = 32,
) {
    init {
        require(durationMs > 0)
        require(capacity > 0)
    }

    private var startedAt: Long? = null
    var observations: List<KeyObservation> = emptyList()
        private set

    val isCapturing: Boolean
        get() = startedAt?.let { now() - it < durationMs } ?: false

    fun start() {
        observations = emptyList()
        startedAt = now()
    }

    fun stop() {
        startedAt = null
    }

    fun clear() {
        stop()
        observations = emptyList()
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
    ): Boolean {
        val start = startedAt ?: return false
        val elapsed = now() - start
        if (elapsed >= durationMs || isTypingKey || isVirtual || isExcludedKey) return false
        observations = (listOf(
            KeyObservation(keyCode, scanCode, keyName, edge, repeatCount, elapsed),
        ) + observations).take(capacity)
        return true
    }
}

