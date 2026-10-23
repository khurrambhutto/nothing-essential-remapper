// SPDX-License-Identifier: GPL-3.0-only
package org.nothingessential.detector

data class MonitoringPolicy(
    val foreground: Boolean,
    val unlocked: Boolean,
    val captureActive: Boolean,
    val backgroundEnabled: Boolean,
    val actionsEnabled: Boolean,
) {
    val canRecognize: Boolean
        get() = unlocked && ((foreground && captureActive) || (backgroundEnabled && actionsEnabled))
    val canAct: Boolean get() = canRecognize && actionsEnabled
    val canRecordRaw: Boolean get() = unlocked && foreground && captureActive
}
