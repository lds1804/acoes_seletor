package com.lynchscreener.worker

import androidx.work.Constraints
import androidx.work.NetworkType

/**
 * Shared WorkManager constraints.
 *
 * Design note: `setRequiresBatteryNotLow(true)` is deliberately NOT used anywhere.
 *
 * JobScheduler maps it to the platform's `BATTERY_NOT_LOW` flag, which turns false
 * below ~15% — a completely normal state for a phone. Combined with the other
 * constraints it blocked EVERY queue at once (observed on device: 8 jobs, all
 * "Unsatisfied constraints: BATTERY_NOT_LOW", battery at 14%, phone plugged in).
 * The app looked frozen while doing nothing wrong.
 *
 * Battery protection is expressed through CHARGING instead, which is what the user
 * actually controls in the settings screen and which implies power is available.
 */
object SyncConstraints {

    /**
     * Small metadata requests (B3 table, prices): a few hundred KB of HTML/JSON.
     * Only needs a connection — no battery or charging gate.
     */
    fun default(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * Slow, rate-limited queues (details, SEC balances, CVM).
     *
     * @param wifiOnly restrict to unmetered networks.
     * @param chargingOnly require the phone to be charging. Charging replaces the
     *   old battery-not-low gate: it is a clearer data-safety signal and it is
     *   visible to the user in the settings toggles.
     */
    fun slowQueue(wifiOnly: Boolean, chargingOnly: Boolean): Constraints {
        val builder = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        if (chargingOnly) builder.setRequiresCharging(true)
        return builder.build()
    }

    /** Price queue: light and frequent, so charging is never required. */
    fun priceQueue(wifiOnly: Boolean): Constraints = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .build()

    /**
     * Light enrichment (~200 detail pages, roughly 10 MB): runs on any connection.
     *
     * Separated from [slowQueue] because a name/sector batch is cheap enough for
     * mobile data, and gating it behind Wi-Fi left the ranking with tickers but no
     * company names whenever the user was on LTE.
     */
    fun lightQueue(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * Heavy one-shot download (~60–80 MB per CVM package, ~400 MB total).
     * Always Wi-Fi, regardless of the user preference, so a first sync cannot
     * silently consume hundreds of megabytes of mobile data. Charging is not
     * required — otherwise a fresh install shows an empty app until plugged in.
     */
    fun heavyDownload(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED)
        .build()

    /**
     * User-initiated work: the tap IS the consent.
     *
     * Applying background constraints to a button press means the user taps
     * "sync now", nothing happens, and there is no way to tell why.
     */
    fun userInitiated(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}
