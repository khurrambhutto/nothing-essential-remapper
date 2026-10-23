// SPDX-License-Identifier: GPL-3.0-only
package org.nothingessential.detector

import android.content.Intent
import android.os.Looper
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Host JVM tests of the real monitor, Handler deadlines, and lifecycle boundaries. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class CaptureMonitorLifecycleTest {
    private var unlocked = true
    private var notifications = true
    private var filtering = false
    private var toggles = 0
    private var lightOn = false
    private var unlockChecks = 0

    @Before fun connectAndArm() {
        CaptureMonitor.disconnected()
        CaptureMonitor.clear()
        connect()
        CaptureMonitor.setForeground(true)
        CaptureMonitor.setActionsEnabled(true)
        CaptureMonitor.setBackgroundEnabled(true)
        assertTrue(filtering)
    }

    private fun connect() {
        CaptureMonitor.connected(
            setFiltering = { filtering = it }, unlocked = { unlockChecks++; unlocked },
            notificationsAllowed = { notifications },
            toggle = { toggles++; lightOn = !lightOn }, cleanup = { lightOn = false },
        )
    }

    @After fun cleanup() {
        CaptureMonitor.disconnected()
        CaptureMonitor.clear()
        CaptureMonitor.setForeground(false)
    }

    private fun waitMs(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun edge(edge: KeyEdge) = CaptureMonitor.observe(0, 250, "KEYCODE_UNKNOWN", edge, 0, false, false, false)
    private fun click() { edge(KeyEdge.DOWN); waitMs(80); edge(KeyEdge.UP) }
    private fun doubleClick() { click(); waitMs(100); click() }
    private fun lock() { unlocked = false; CaptureMonitor.refreshEnvironment() }
    private fun unlock() { unlocked = true; CaptureMonitor.refreshEnvironment() }

    @Test fun `background works after activity pause and beyond diagnostic timeout without raw logs`() {
        CaptureMonitor.setForeground(false)
        waitMs(16_000)
        doubleClick()
        assertEquals(1, toggles)
        assertTrue(lightOn)
        assertTrue(CaptureMonitor.state.value.backgroundEnabled)
        assertTrue(CaptureMonitor.state.value.observations.isEmpty())
    }

    @Test fun `lock turns light off but retains armed mode and resumes after unlock`() {
        CaptureMonitor.setForeground(false)
        doubleClick()
        assertTrue(lightOn)
        lock()
        assertFalse(lightOn)
        assertFalse(filtering)
        assertTrue(CaptureMonitor.state.value.backgroundEnabled)
        doubleClick()
        assertEquals(1, toggles)
        unlock()
        assertTrue(filtering)
        doubleClick()
        assertEquals(2, toggles)
        assertTrue(lightOn)
    }

    @Test fun `pending single before lock never combines with a press after unlock`() {
        click()
        lock()
        waitMs(1_000)
        unlock()
        click()
        waitMs(301)
        assertEquals(0, toggles)
        assertEquals(listOf(GestureType.SINGLE_PRESS), CaptureMonitor.state.value.gestures.map { it.type })
    }

    @Test fun `holding across lock does not emit a stale long or release click`() {
        edge(KeyEdge.DOWN)
        waitMs(599)
        lock()
        waitMs(1_000)
        unlock()
        edge(KeyEdge.UP)
        waitMs(1_000)
        assertTrue(CaptureMonitor.state.value.gestures.isEmpty())
        assertEquals(0, toggles)
        doubleClick()
        assertEquals(1, toggles)
    }

    @Test fun `notification Stop receiver disarms mode and it does not resume on unlock`() {
        CaptureMonitor.setForeground(false)
        doubleClick()
        BackgroundControlReceiver().onReceive(RuntimeEnvironment.getApplication(), Intent(BackgroundModeNotification.STOP))
        assertFalse(lightOn)
        assertFalse(filtering)
        assertFalse(CaptureMonitor.state.value.actionsEnabled)
        assertFalse(CaptureMonitor.state.value.backgroundEnabled)
        lock(); unlock(); doubleClick()
        assertEquals(1, toggles)
    }

    @Test fun `notification permission revocation disarms control and turns light off`() {
        doubleClick()
        notifications = false
        CaptureMonitor.refreshEnvironment()
        assertFalse(lightOn)
        assertFalse(filtering)
        assertFalse(CaptureMonitor.state.value.backgroundEnabled)
        doubleClick()
        assertEquals(1, toggles)
    }

    @Test fun `service reconnection never silently rearms actions`() {
        doubleClick()
        CaptureMonitor.disconnected()
        assertFalse(lightOn)
        connect()
        assertFalse(CaptureMonitor.state.value.actionsEnabled)
        assertFalse(CaptureMonitor.state.value.backgroundEnabled)
        assertFalse(filtering)
        doubleClick()
        assertEquals(1, toggles)
    }

    @Test fun `foreground capture still stops and cleans up on leaving without background mode`() {
        CaptureMonitor.start()
        doubleClick()
        assertTrue(lightOn)
        CaptureMonitor.setForeground(false)
        assertFalse(lightOn)
        assertFalse(CaptureMonitor.state.value.actionsEnabled)
        assertFalse(CaptureMonitor.state.value.isCapturing)
        assertFalse(filtering)
    }

    @Test fun `locked and notification denied starts cannot arm background mode`() {
        CaptureMonitor.stop()
        lock()
        CaptureMonitor.setBackgroundEnabled(true)
        assertFalse(CaptureMonitor.state.value.backgroundEnabled)
        unlock()
        notifications = false
        CaptureMonitor.setBackgroundEnabled(true)
        assertFalse(CaptureMonitor.state.value.backgroundEnabled)
    }

    @Test fun `late unlock after screen on resumes without a later user present broadcast`() {
        CaptureMonitor.setForeground(false)
        lock()
        CaptureMonitor.onScreenWake() // SCREEN_ON arrives while keyguard still reports locked.
        waitMs(120)
        unlocked = true // Actual OS state changes, but no later broadcast is delivered.
        waitMs(3_000)
        assertTrue("Background mode must not stay stuck paused after a wake transition", CaptureMonitor.state.value.unlocked)
        assertTrue(filtering)
        doubleClick()
        assertEquals(1, toggles)
    }

    @Test fun `wake checks remain bounded and never enable actions while still locked`() {
        CaptureMonitor.setForeground(false)
        lock()
        CaptureMonitor.onScreenWake()
        waitMs(3_100)
        val checksAfterTransition = unlockChecks
        assertFalse(CaptureMonitor.state.value.unlocked)
        assertFalse(filtering)
        assertTrue(CaptureMonitor.state.value.backgroundEnabled)
        waitMs(60_000)
        assertEquals(checksAfterTransition, unlockChecks)
        assertEquals(0, toggles)
    }

    @Test fun `Stop cancels scheduled wake recovery and does not silently rearm`() {
        lock()
        CaptureMonitor.onScreenWake()
        CaptureMonitor.setActionsEnabled(false)
        val checksAfterStop = unlockChecks
        unlocked = true
        waitMs(4_000)
        assertEquals(checksAfterStop, unlockChecks)
        assertFalse(CaptureMonitor.state.value.backgroundEnabled)
        assertFalse(filtering)
        assertEquals(0, toggles)
    }

    @Test fun `screen sleep cancels an earlier wake transition`() {
        lock()
        CaptureMonitor.onScreenWake()
        CaptureMonitor.onScreenSleep()
        val checksAfterSleep = unlockChecks
        waitMs(4_000)
        assertEquals(checksAfterSleep, unlockChecks)
        assertFalse(filtering)
    }

    @Test fun `foreground wake restores ready state without rearming disabled actions`() {
        CaptureMonitor.setActionsEnabled(false)
        CaptureMonitor.setForeground(false)
        lock()
        CaptureMonitor.setForeground(true)
        waitMs(120)
        unlocked = true
        waitMs(3_000)
        assertTrue(CaptureMonitor.state.value.unlocked)
        assertFalse(CaptureMonitor.state.value.actionsEnabled)
        assertFalse(CaptureMonitor.state.value.backgroundEnabled)
        assertFalse(filtering)
    }
}
