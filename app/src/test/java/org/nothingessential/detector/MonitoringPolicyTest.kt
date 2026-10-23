// SPDX-License-Identifier: GPL-3.0-only
package org.nothingessential.detector

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringPolicyTest {
    private val diagnostic = MonitoringPolicy(true, true, true, false, false)

    @Test fun `foreground diagnostic records without enabling actions`() {
        assertTrue(diagnostic.canRecognize)
        assertTrue(diagnostic.canRecordRaw)
        assertFalse(diagnostic.canAct)
    }

    @Test fun `leaving diagnostic activity cannot recognize record or act`() {
        val left = diagnostic.copy(foreground = false, actionsEnabled = true)
        assertFalse(left.canRecognize)
        assertFalse(left.canRecordRaw)
        assertFalse(left.canAct)
    }

    @Test fun `explicit unlocked background mode acts without capture`() {
        val background = MonitoringPolicy(false, true, false, true, true)
        assertTrue(background.canRecognize)
        assertTrue(background.canAct)
        assertFalse(background.canRecordRaw)
    }

    @Test fun `background mode never extends raw capture beyond foreground timer`() {
        val background = diagnostic.copy(backgroundEnabled = true, actionsEnabled = true)
        assertFalse(background.copy(foreground = false).canRecordRaw)
        assertFalse(background.copy(captureActive = false).canRecordRaw)
    }

    @Test fun `locked mode denies every operation across all mode combinations`() {
        for (foreground in listOf(false, true)) for (capture in listOf(false, true)) {
            for (background in listOf(false, true)) for (actions in listOf(false, true)) {
                val locked = MonitoringPolicy(foreground, false, capture, background, actions)
                assertFalse(locked.canRecognize)
                assertFalse(locked.canAct)
                assertFalse(locked.canRecordRaw)
            }
        }
    }

    @Test fun `disabled actions cannot run in any mode`() {
        for (foreground in listOf(false, true)) for (capture in listOf(false, true)) {
            for (background in listOf(false, true)) {
                assertFalse(MonitoringPolicy(foreground, true, capture, background, false).canAct)
            }
        }
    }

    @Test fun `a stale background flag alone does not enable recognition`() {
        val stale = MonitoringPolicy(false, true, false, true, false)
        assertFalse(stale.canRecognize)
        assertFalse(stale.canAct)
    }

    @Test fun `armed background policy pauses on lock and resumes only after unlock`() {
        val armed = MonitoringPolicy(false, true, false, true, true)
        assertTrue(armed.canAct)
        assertFalse(armed.copy(unlocked = false).canAct)
        assertTrue(armed.copy(unlocked = false).copy(unlocked = true).canAct)
        assertFalse(armed.copy(backgroundEnabled = false).canAct)
    }
}
