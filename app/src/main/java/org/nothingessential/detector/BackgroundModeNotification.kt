// SPDX-License-Identifier: GPL-3.0-only
package org.nothingessential.detector

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

class BackgroundModeNotification(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Background button control", NotificationManager.IMPORTANCE_LOW))
    }

    fun allowed(): Boolean {
        val permission = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permission && manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun update(state: DetectorState) {
        if (!state.backgroundEnabled || !allowed()) {
            cancel()
            return
        }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getBroadcast(context, 1, Intent(context, BackgroundControlReceiver::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_button)
            .setContentTitle("Essential flashlight control")
            .setContentText(if (state.unlocked) "Double press while using other apps. Tap Stop to disable." else "Paused while screen is off or locked.")
            .setContentIntent(open)
            .setDeleteIntent(stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
        try {
            manager.notify(ID, notification)
        } catch (_: SecurityException) {
            // Permission may have been revoked after the readiness check.
            CaptureMonitor.setActionsEnabled(false)
            cancel()
        }
    }

    fun cancel() = manager.cancel(ID)

    companion object {
        private const val CHANNEL = "background_button"
        private const val ID = 40
        const val STOP = "org.nothingessential.detector.STOP_BACKGROUND"
    }
}

class BackgroundControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == BackgroundModeNotification.STOP) CaptureMonitor.setActionsEnabled(false)
    }
}
