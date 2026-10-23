// SPDX-License-Identifier: GPL-3.0-only
package org.nothingessential.detector

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FlashlightState(
    val available: Boolean = false,
    val isOn: Boolean = false,
    val busy: Boolean = false,
    val message: String = "Checking flashlight…",
)

/** Uses torch APIs only. Never opens a camera, captures an image, or stores media. */
class FlashlightController(private val context: Context) {
    private val manager = context.getSystemService(CameraManager::class.java)
    private val mutableState = MutableStateFlow(FlashlightState())
    val state = mutableState.asStateFlow()
    private var cameraId: String? = null
    private var active = false
    private var registered = false
    private var ownsLight = false

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (!active || id != cameraId) return
            if (!enabled) ownsLight = false
            mutableState.value = mutableState.value.copy(
                available = true, isOn = enabled, busy = false,
                message = if (enabled) "Flashlight is on" else "Flashlight is off",
            )
        }

        override fun onTorchModeUnavailable(id: String) {
            if (!active || id != cameraId) return
            ownsLight = false
            mutableState.value = mutableState.value.copy(
                available = false, isOn = false, busy = false,
                message = "Flashlight unavailable. Close any app using the camera and try again.",
            )
        }
    }

    fun activate() {
        active = true
        if (registered) return
        try {
            cameraId = manager.cameraIdList.firstOrNull { id ->
                val info = manager.getCameraCharacteristics(id)
                info.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    info.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
            mutableState.value = FlashlightState(
                message = if (cameraId == null) "No rear flashlight found." else "Checking flashlight…",
            )
            if (cameraId != null) {
                manager.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
                registered = true
            }
        } catch (_: CameraAccessException) {
            fail("Camera service unavailable. Reopen the app to retry.")
        } catch (_: SecurityException) {
            fail("This device restricts torch access. Background support needs a device-specific check.")
        } catch (_: IllegalArgumentException) {
            fail("The camera configuration changed. Reopen the app to retry.")
        }
    }

    fun toggle() {
        val current = mutableState.value
        val id = cameraId ?: return
        if (!active || !current.available || current.busy) return
        val desired = !current.isOn
        mutableState.value = current.copy(busy = true, message = "Changing flashlight…")
        try {
            manager.setTorchMode(id, desired)
            ownsLight = desired
        } catch (_: CameraAccessException) {
            fail("Flashlight could not change. Close camera apps and retry.")
        } catch (_: SecurityException) {
            fail("Android restricted torch access. No camera will be opened to work around it.")
        } catch (_: IllegalArgumentException) {
            fail("This camera no longer provides a flashlight.")
        }
    }

    fun turnOffOwnedLight() {
        if (!ownsLight) return
        val id = cameraId ?: return
        try {
            manager.setTorchMode(id, false)
            ownsLight = false
        } catch (_: CameraAccessException) {
            fail("Could not turn off the flashlight. Use Android Quick Settings to turn it off.")
        } catch (_: SecurityException) {
            fail("Permission changed. Use Android Quick Settings to turn off the flashlight.")
        } catch (_: IllegalArgumentException) {
            fail("Camera unavailable. Check the flashlight in Android Quick Settings.")
        }
    }

    fun deactivate() {
        turnOffOwnedLight()
        active = false
        if (registered) manager.unregisterTorchCallback(callback)
        registered = false
    }

    private fun fail(message: String) {
        mutableState.value = mutableState.value.copy(available = false, busy = false, message = message)
    }
}

/** Application context avoids retaining an activity while background mode is enabled. */
object FlashlightRuntime {
    private var instance: FlashlightController? = null
    fun get(context: Context): FlashlightController = instance ?: FlashlightController(context.applicationContext).also { instance = it }
}
