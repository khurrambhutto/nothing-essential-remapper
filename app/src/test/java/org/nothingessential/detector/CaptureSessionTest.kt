// SPDX-License-Identifier: GPL-3.0-only
package org.nothingessential.detector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureSessionTest {
    private var clock = 100L
    private val session = CaptureSession(now = { clock }, capacity = 3)

    private fun observe(
        edge: KeyEdge = KeyEdge.DOWN,
        isTyping: Boolean = false,
        isVirtual: Boolean = false,
        isExcluded: Boolean = false,
    ) = session.observe(999, 123, "999", edge, 0, isTyping, isVirtual, isExcluded)

    @Test fun `does not observe outside an explicitly started session`() {
        assertFalse(observe())
        session.start()
        assertTrue(observe())
        session.stop()
        assertFalse(observe())
        assertEquals(1, session.observations.size)
    }

    @Test fun `capture expires exactly at deadline even if timer callback is delayed`() {
        session.start()
        clock += 14_999
        assertTrue(observe())
        clock += 1
        assertFalse(session.isCapturing)
        assertFalse(observe())
    }

    @Test fun `typing virtual and excluded keys are never retained`() {
        session.start()
        assertFalse(observe(isTyping = true))
        assertFalse(observe(isVirtual = true))
        assertFalse(observe(isExcluded = true))
        assertTrue(session.observations.isEmpty())
    }

    @Test fun `unknown vendor key codes and both edges are preserved`() {
        session.start()
        observe()
        clock += 40
        observe(edge = KeyEdge.UP)
        assertEquals(listOf(KeyEdge.UP, KeyEdge.DOWN), session.observations.map { it.edge })
        assertEquals(999, session.observations.first().keyCode)
        assertEquals(123, session.observations.first().scanCode)
        assertEquals(40L, session.observations.first().elapsedMs)
    }

    @Test fun `history is bounded and restarting clears previous observations`() {
        session.start()
        repeat(5) { observe(); clock++ }
        assertEquals(3, session.observations.size)
        session.start()
        assertTrue(session.observations.isEmpty())
        assertTrue(session.isCapturing)
    }

    @Test fun `clear also stops capture`() {
        session.start()
        observe()
        session.clear()
        assertFalse(session.isCapturing)
        assertTrue(session.observations.isEmpty())
    }
}
