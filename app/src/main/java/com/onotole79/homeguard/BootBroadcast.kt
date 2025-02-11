package com.onotole79.homeguard

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

public class BootBroadcast : BroadcastReceiver() {
    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(Constants.TAG, "BootBroadcast")
        MyLog(context, "BootBroadcast")

        context.startForegroundService(Intent(context, MqttClientService::class.java)
            .putExtra(Constants.COMMAND, Constants.CONNECT)
            .putExtra(Constants.VALUE, "1")
        )
    }
}