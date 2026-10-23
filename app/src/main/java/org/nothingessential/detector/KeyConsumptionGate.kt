// SPDX-License-Identifier: GPL-3.0-only
package org.nothingessential.detector

data class HardwareKey(val deviceId: Int, val keyCode: Int, val scanCode: Int)

/** Keep each DOWN/UP pair's original decision, even when capture stops mid-hold. */
class KeyConsumptionGate {
    private val decisions = mutableMapOf<HardwareKey, Boolean>()
    val hasConsumedPress: Boolean get() = decisions.values.any { it }

    fun onEdge(key: HardwareKey, edge: KeyEdge, repeatCount: Int, intercept: Boolean): Boolean {
        if (!CmfTestKey.matches(key.keyCode, key.scanCode)) return false
        return when (edge) {
            KeyEdge.DOWN -> decisions[key] ?: if (repeatCount > 0) false else {
                decisions[key] = intercept
                intercept
            }
            KeyEdge.UP -> decisions.remove(key) ?: false
        }
    }

    fun discardPassThroughPresses() {
        decisions.entries.removeAll { !it.value }
    }

    fun reset() = decisions.clear()
}
