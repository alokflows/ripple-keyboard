/*
 * Copyright (C) 2026 The Ripple Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ripple

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisAppActivity
import dev.patrickgold.florisboard.rippleManager

/**
 * Keeps the Ripple socket (owned by [RippleManager]) alive while only the keyboard
 * is in use, so received text keeps arriving and the input path never waits to
 * reconnect. Started on pair, stopped on leave. On a sticky restart (process was
 * killed) it resumes the saved pairing code, so the keyboard reconnects without
 * user interaction.
 */
class RippleConnectionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val rippleManager by this.rippleManager()
        if (intent == null) {
            // Sticky restart after the process was killed: re-pair silently.
            rippleManager.resume()
        }
        val code = intent?.getStringExtra(EXTRA_CODE) ?: rippleManager.currentCode
        startForeground(NOTIF_ID, buildNotification(code))
        return START_STICKY
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun buildNotification(code: String): Notification {
        ensureChannel(this)
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, FlorisAppActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val detail = if (code.isNotEmpty()) "Code $code · text lands at your cursor" else "Text lands at your cursor"
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_app_icon_stable_monochrome)
            .setContentTitle("Ripple connected")
            .setContentText(detail)
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL = "ripple.connection"
        private const val NOTIF_ID = 0x52504C /* "RPL" */
        private const val EXTRA_CODE = "code"

        fun start(ctx: Context, code: String) {
            val i = Intent(ctx, RippleConnectionService::class.java).putExtra(EXTRA_CODE, code)
            ctx.startForegroundService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, RippleConnectionService::class.java))
        }

        fun ensureChannel(ctx: Context) {
            val mgr = ctx.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Ripple connection", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }
}
