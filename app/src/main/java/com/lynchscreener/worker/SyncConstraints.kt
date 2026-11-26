package com.lynchscreener.worker

import androidx.work.Constraints
import androidx.work.NetworkType

/** Shared WorkManager constraints (Wi-Fi / battery tuning). */
object SyncConstraints {

    /** General network work: connected, battery not low. */
    fun default(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    /** Slow, rate-limited queues: optionally Wi-Fi + charging only. */
    fun slowQueue(wifiOnly: Boolean, chargingOnly: Boolean): Constraints {
        val builder = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
        if (chargingOnly) builder.setRequiresCharging(true)
        return builder.build()
    }

    /** Price queue: lighter, so charging is not required. */
    fun priceQueue(wifiOnly: Boolean): Constraints = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    /**
     * Heavy one-shot download (~60–80 MB per CVM package, ~400 MB total).
     * Always Wi-Fi, regardless of the user preference, so a first sync cannot
     * silently consume hundreds of megabytes of mobile data. Charging is not
     * required — otherwise a fresh install would show an empty app until the
     * phone is plugged in.
     */
    fun heavyDownload(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED)
        .setRequiresBatteryNotLow(true)
        .build()
}
