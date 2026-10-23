// SPDX-License-Identifier: GPL-3.0-only
package org.nothingessential.detector

enum class GestureType(val label: String) {
    SINGLE_PRESS("Single press"),
    DOUBLE_PRESS("Double press"),
    LONG_PRESS("Long press"),
}

data class GestureObservation(val type: GestureType, val elapsedMs: Long)

/**
 * Pure state machine. A single waits for the double-press window after release.
 * A second DOWN reserves that click until its UP or long-press threshold.
 * Call advance at nextDeadlineMs even when no further key events arrive.
 */
class GestureRecognizer(
    private val now: () -> Long,
    val doublePressMs: Long = 300L,
    val longPressMs: Long = 600L,
) {
    init {
        require(doublePressMs > 0)
        require(longPressMs > 0)
    }

    private var pressedAt: Long? = null
    private var singleDeadline: Long? = null
    private var secondPress = false
    private var longEmitted = false

    val nextDeadlineMs: Long?
        get() = pressedAt?.let { if (longEmitted) null else it + longPressMs }
            ?: if (pressedAt == null) singleDeadline else null

    fun onEdge(edge: KeyEdge, repeatCount: Int = 0): List<GestureType> {
        val result = advance().toMutableList()
        when (edge) {
            KeyEdge.DOWN -> {
                // Repeated DOWN events from holding must not create new presses.
                if (pressedAt == null && repeatCount == 0) {
                    pressedAt = now()
                    secondPress = singleDeadline != null
                    longEmitted = false
                }
            }
            KeyEdge.UP -> {
                if (pressedAt == null) return result
                if (!longEmitted) {
                    if (secondPress) {
                        result += GestureType.DOUBLE_PRESS
                        singleDeadline = null
                    } else {
                        singleDeadline = now() + doublePressMs
                    }
                }
                pressedAt = null
                secondPress = false
                longEmitted = false
            }
        }
        return result
    }

    fun advance(): List<GestureType> {
        val time = now()
        val result = mutableListOf<GestureType>()
        val down = pressedAt
        if (down != null && !longEmitted && time - down >= longPressMs) {
            // A short first press followed by a hold is one single plus one long.
            if (secondPress) result += GestureType.SINGLE_PRESS
            singleDeadline = null
            result += GestureType.LONG_PRESS
            longEmitted = true
        } else if (down == null && singleDeadline?.let { time >= it } == true) {
            singleDeadline = null
            result += GestureType.SINGLE_PRESS
        }
        return result
    }

    /** Capture cancellation must discard pending clicks and holds. */
    fun cancel() {
        pressedAt = null
        singleDeadline = null
        secondPress = false
        longEmitted = false
    }
}

/** Observed on the connected CMF A001; not a universal Nothing key mapping. */
object CmfTestKey {
    fun matches(keyCode: Int, scanCode: Int): Boolean = keyCode == 0 && scanCode == 250
}
