package com.fallbracelet.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Starts the app after a reboot or an update: creating the Application starts the relay link and
 * the PMO stream, and the reminder check sets the next alarm again (alarms are lost on reboot).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        com.fallbracelet.pengingat.periksaDiLatar(context, goAsync())
    }
}
