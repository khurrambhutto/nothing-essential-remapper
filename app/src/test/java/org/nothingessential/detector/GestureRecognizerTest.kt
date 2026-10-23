// SPDX-License-Identifier: GPL-3.0-only
package org.nothingessential.detector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureRecognizerTest {
    private var time = 0L
    private val recognizer = GestureRecognizer(now = { time })

    private fun down(at: Long, repeat: Int = 0): List<GestureType> {
        time = at
        return recognizer.onEdge(KeyEdge.DOWN, repeat)
    }

    private fun up(at: Long): List<GestureType> {
        time = at
        return recognizer.onEdge(KeyEdge.UP)
    }

    private fun advance(at: Long): List<GestureType> {
        time = at
        return recognizer.advance()
    }

    @Test fun `single waits for double window and is emitted once`() {
        assertTrue(down(0).isEmpty())
        assertTrue(up(50).isEmpty())
        assertEquals(350L, recognizer.nextDeadlineMs)
        assertTrue(advance(349).isEmpty())
        assertEquals(listOf(GestureType.SINGLE_PRESS), advance(350))
        assertTrue(advance(500).isEmpty())
        assertNull(recognizer.nextDeadlineMs)
    }

    @Test fun `double produces no extra single`() {
        down(0)
        up(50)
        assertTrue(down(200).isEmpty())
        assertEquals(listOf(GestureType.DOUBLE_PRESS), up(250))
        assertTrue(advance(2_000).isEmpty())
    }

    @Test fun `second release can pass original double deadline`() {
        down(0)
        up(50)
        down(349)
        assertTrue(advance(350).isEmpty())
        assertEquals(listOf(GestureType.DOUBLE_PRESS), up(500))
    }

    @Test fun `second down at exact deadline starts a separate click`() {
        down(0)
        up(50)
        assertEquals(listOf(GestureType.SINGLE_PRESS), down(350))
        assertTrue(up(400).isEmpty())
        assertEquals(listOf(GestureType.SINGLE_PRESS), advance(700))
    }

    @Test fun `hold fires without repeats and release adds nothing`() {
        down(0)
        assertEquals(600L, recognizer.nextDeadlineMs)
        assertTrue(advance(599).isEmpty())
        assertEquals(listOf(GestureType.LONG_PRESS), advance(600))
        assertNull(recognizer.nextDeadlineMs)
        assertTrue(advance(900).isEmpty())
        assertTrue(up(1_000).isEmpty())
        assertTrue(advance(2_000).isEmpty())
    }

    @Test fun `release resolves hold if timer has not run`() {
        down(0)
        assertEquals(listOf(GestureType.LONG_PRESS), up(900))
        assertTrue(advance(1_500).isEmpty())
    }

    @Test fun `repeat and duplicate downs do not restart hold`() {
        down(0)
        assertTrue(down(200, repeat = 1).isEmpty())
        assertTrue(down(400).isEmpty())
        assertEquals(listOf(GestureType.LONG_PRESS), down(650, repeat = 2))
        assertTrue(down(800, repeat = 3).isEmpty())
        assertTrue(up(900).isEmpty())
    }

    @Test fun `orphan up and repeat do not invent presses`() {
        assertTrue(up(100).isEmpty())
        assertTrue(down(200, repeat = 2).isEmpty())
        assertTrue(up(300).isEmpty())
        assertTrue(advance(1_000).isEmpty())
    }

    @Test fun `short press followed by hold produces single and long`() {
        down(0)
        up(50)
        down(200)
        assertTrue(advance(350).isEmpty())
        assertEquals(listOf(GestureType.SINGLE_PRESS, GestureType.LONG_PRESS), advance(800))
        assertTrue(up(900).isEmpty())
    }

    @Test fun `three quick presses produce double followed by single`() {
        down(0)
        up(40)
        down(100)
        assertEquals(listOf(GestureType.DOUBLE_PRESS), up(140))
        down(200)
        up(240)
        assertEquals(listOf(GestureType.SINGLE_PRESS), advance(540))
    }

    @Test fun `cancel discards pending single and hold`() {
        down(0)
        up(50)
        recognizer.cancel()
        assertTrue(advance(500).isEmpty())
        down(600)
        recognizer.cancel()
        assertTrue(advance(1_500).isEmpty())
        assertTrue(up(1_600).isEmpty())
        assertNull(recognizer.nextDeadlineMs)
    }

    @Test fun `fresh press after cancel has no stale double state`() {
        down(0)
        up(50)
        recognizer.cancel()
        down(100)
        up(150)
        assertEquals(listOf(GestureType.SINGLE_PRESS), advance(450))
    }

    @Test fun `CMF profile checks both key and scan codes`() {
        assertTrue(CmfTestKey.matches(0, 250))
        assertFalse(CmfTestKey.matches(0, 251))
        assertFalse(CmfTestKey.matches(24, 250))
        assertFalse(CmfTestKey.matches(0, 0))
    }
}
