package com.lynchscreener.data.repository

import android.content.Context
import android.content.SharedPreferences

/**
 * User-tunable sync toggles (wifi-only, charging-only). The sync screen binds to
 * this; the scheduler reads it to build WorkManager constraints.
 *
 * Defaults: Wi-Fi only ON (a full CVM import is ~400 MB), charging only OFF.
 * Charging used to default to ON, which meant the app only ever collected while
 * plugged in — a phone on the sofa with 100% battery showed an empty ranking and
 * no explanation.
 */
class SyncSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sync_settings", Context.MODE_PRIVATE)

    init {
        // One-time fix for installs created before the default changed: their
        // stored `charging_only=true` would keep the old cable-only behaviour.
        if (!prefs.getBoolean(KEY_CHARGING_MIGRATED, false)) {
            prefs.edit()
                .putBoolean(KEY_CHARGING_ONLY, false)
                .putBoolean(KEY_CHARGING_MIGRATED, true)
                .apply()
        }
    }

    var wifiOnly: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, true)
        set(value) = prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()

    var chargingOnly: Boolean
        get() = prefs.getBoolean(KEY_CHARGING_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_CHARGING_ONLY, value).apply()

    private companion object {
        const val KEY_WIFI_ONLY = "wifi_only"
        const val KEY_CHARGING_ONLY = "charging_only"
        const val KEY_CHARGING_MIGRATED = "charging_default_migrated"
    }
}
