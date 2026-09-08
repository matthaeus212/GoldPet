package com.mannam.goldpet

import android.app.Service
import android.content.Intent
import android.os.IBinder

class WalkForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val helper = WalkNotificationHelper(this)
        startForeground(WalkNotificationHelper.NOTIFICATION_ID, helper.buildInitial())
        return START_STICKY
    }
}
