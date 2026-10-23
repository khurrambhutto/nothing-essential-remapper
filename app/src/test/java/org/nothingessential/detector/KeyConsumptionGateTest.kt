// SPDX-License-Identifier: GPL-3.0-only
package org.nothingessential.detector

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyConsumptionGateTest {
    private val gate = KeyConsumptionGate()
    private val key = HardwareKey(3, 0, 250)

    @Test fun `disabled actions pass both edges through`() {
        assertFalse(gate.onEdge(key, KeyEdge.DOWN, 0, false))
        assertFalse(gate.onEdge(key, KeyEdge.UP, 0, false))
        assertFalse(gate.hasConsumedPress)
    }

    @Test fun `enabled actions consume both edges`() {
        assertTrue(gate.onEdge(key, KeyEdge.DOWN, 0, true))
        assertTrue(gate.hasConsumedPress)
        assertTrue(gate.onEdge(key, KeyEdge.UP, 0, true))
        assertFalse(gate.hasConsumedPress)
    }

    @Test fun `capture ending mid hold still consumes repeats and matching release`() {
        assertTrue(gate.onEdge(key, KeyEdge.DOWN, 0, true))
        gate.discardPassThroughPresses()
        assertTrue(gate.onEdge(key, KeyEdge.DOWN, 1, false))
        assertTrue(gate.onEdge(key, KeyEdge.UP, 0, false))
        assertFalse(gate.hasConsumedPress)
    }

    @Test fun `enabling actions mid press does not swallow a previously passed release`() {
        assertFalse(gate.onEdge(key, KeyEdge.DOWN, 0, false))
        assertFalse(gate.onEdge(key, KeyEdge.DOWN, 1, true))
        assertFalse(gate.onEdge(key, KeyEdge.UP, 0, true))
    }

    @Test fun `orphan repeats and releases never consume`() {
        assertFalse(gate.onEdge(key, KeyEdge.DOWN, 2, true))
        assertFalse(gate.onEdge(key, KeyEdge.UP, 0, true))
    }

    @Test fun `other keys and other devices do not steal a pending release`() {
        assertTrue(gate.onEdge(key, KeyEdge.DOWN, 0, true))
        assertFalse(gate.onEdge(HardwareKey(3, 24, 250), KeyEdge.DOWN, 0, true))
        assertFalse(gate.onEdge(HardwareKey(3, 0, 251), KeyEdge.DOWN, 0, true))
        assertFalse(gate.onEdge(HardwareKey(4, 0, 250), KeyEdge.UP, 0, true))
        assertTrue(gate.hasConsumedPress)
        assertTrue(gate.onEdge(key, KeyEdge.UP, 0, false))
    }

    @Test fun `discarding unconsumed state permits a fresh capture`() {
        gate.onEdge(key, KeyEdge.DOWN, 0, false)
        gate.discardPassThroughPresses()
        assertTrue(gate.onEdge(key, KeyEdge.DOWN, 0, true))
    }

    @Test fun `service reset forgets stale decisions`() {
        gate.onEdge(key, KeyEdge.DOWN, 0, true)
        gate.reset()
        assertFalse(gate.hasConsumedPress)
        assertFalse(gate.onEdge(key, KeyEdge.UP, 0, false))
    }
}
