package com.lynchscreener.worker

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager

/**
 * The device conditions that decide whether the background queues may run.
 *
 * WorkManager exposes *that* a request is blocked, but never *why* — the reason
 * lives in JobScheduler's internal constraint flags. Users (and whoever is
 * debugging) end up staring at "Aguardando condições" with no way to act.
 *
 * Reading the same signals Android uses makes the wait explainable: "bateria 14%,
 * sem Wi-Fi" is actionable, "Aguardando condições" is not.
 */
data class DeviceConditions(
    val batteryPercent: Int?,
    val isCharging: Boolean,
    val isOnWifi: Boolean,
    val hasConnection: Boolean,
) {
    /** True when the slow queues (Wi-Fi + optional charging) can run. */
    fun allowsSlowQueue(wifiOnly: Boolean, chargingOnly: Boolean): Boolean {
        if (wifiOnly && !isOnWifi) return false
        if (!hasConnection) return false
        if (chargingOnly && !isCharging) return false
        return true
    }

    /** Short, human explanation of what is missing. Empty when nothing is. */
    fun blockers(wifiOnly: Boolean, chargingOnly: Boolean): List<String> {
        val reasons = mutableListOf<String>()
        if (!hasConnection) reasons += "sem internet"
        if (wifiOnly && !isOnWifi) reasons += "sem Wi-Fi"
        if (chargingOnly && !isCharging) reasons += "fora do carregador"
        return reasons
    }

    companion object {
        fun read(context: Context): DeviceConditions {
            val battery = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val level = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.takeIf { it in 0..100 }

            val sticky = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            )
            val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork
            val caps = network?.let { cm.getNetworkCapabilities(it) }
            val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val connected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

            return DeviceConditions(
                batteryPercent = level,
                isCharging = charging,
                isOnWifi = wifi,
                hasConnection = connected,
            )
        }
    }
}
