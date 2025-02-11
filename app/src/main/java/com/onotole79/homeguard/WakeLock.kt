package com.onotole79.homeguard

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager

class WakeLock private constructor(context: Context) {

    companion object {
        private var wakeLock: PowerManager.WakeLock? = null

        @SuppressLint("ServiceCast")
        fun acquire(context: Context, timeout: Long) {
            if (wakeLock == null) {
                wakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                    .newWakeLock(PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "App:HomeGuard")
            }
            wakeLock?.acquire(timeout)
        }

        fun release() {
            wakeLock?.release()
            wakeLock = null
        }
    }
}