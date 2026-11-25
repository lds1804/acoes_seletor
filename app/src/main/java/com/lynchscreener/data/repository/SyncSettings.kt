package com.lynchscreener.data.repository

import android.content.Context
import android.content.SharedPreferences

/**
 * User-tunable sync toggles (wifi-only, charging-only). The Etapa 3 sync
 * screen binds to this; the scheduler reads it to build WorkManager
 * constraints. Defaults are conservative: wifi-only + charging-only.
 */
class SyncSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sync_settings", Context.MODE_PRIVATE)

    var wifiOnly: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, true)
        set(value) = prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()

    var chargingOnly: Boolean
        get() = prefs.getBoolean(KEY_CHARGING_ONLY, true)
        set(value) = prefs.edit().putBoolean(KEY_CHARGING_ONLY, value).apply()

    private companion object {
        const val KEY_WIFI_ONLY = "wifi_only"
        const val KEY_CHARGING_ONLY = "charging_only"
    }
}
