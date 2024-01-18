package com.background.hms_gms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class LocatorBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        LocatorService.enqueueWork(context, intent)
    }
}