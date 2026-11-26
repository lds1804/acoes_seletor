package com.lynchscreener

import com.lynchscreener.worker.DeviceConditions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The background queues are gated by device state, and WorkManager never says
 * which gate closed. These tests pin down the explanation shown to the user.
 */
class DeviceConditionsTest {

    private val lowBatteryNoWifi = DeviceConditions(
        batteryPercent = 14,
        isCharging = true,
        isOnWifi = false,
        hasConnection = true,
    )

    private val wifiCharging = DeviceConditions(
        batteryPercent = 80,
        isCharging = true,
        isOnWifi = true,
        hasConnection = true,
    )

    private val offline = DeviceConditions(
        batteryPercent = 80,
        isCharging = false,
        isOnWifi = false,
        hasConnection = false,
    )

    @Test
    fun `no wifi blocks the slow queue when wifi only is on`() {
        assertFalse(lowBatteryNoWifi.allowsSlowQueue(wifiOnly = true, chargingOnly = true))
        assertEquals(listOf("sem Wi-Fi"), lowBatteryNoWifi.blockers(true, true))
    }

    @Test
    fun `same device allows the slow queue when wifi only is off`() {
        // The phone is plugged in, so charging is satisfied: only Wi-Fi is missing.
        assertTrue(lowBatteryNoWifi.allowsSlowQueue(wifiOnly = false, chargingOnly = true))
        assertTrue(lowBatteryNoWifi.blockers(false, true).isEmpty())
    }

    @Test
    fun `battery level is never a blocker by itself`() {
        // Regression: the old constraints required BATTERY_NOT_LOW, which fails
        // below ~15% and silently blocked every queue at 14% while plugged in.
        val pluggedLowBattery = lowBatteryNoWifi.copy(isOnWifi = true)
        assertTrue(pluggedLowBattery.allowsSlowQueue(wifiOnly = true, chargingOnly = true))
        assertTrue(pluggedLowBattery.blockers(true, true).isEmpty())
    }

    @Test
    fun `missing charger is reported`() {
        val wifiNotCharging = wifiCharging.copy(isCharging = false)
        assertFalse(wifiNotCharging.allowsSlowQueue(wifiOnly = true, chargingOnly = true))
        assertEquals(listOf("fora do carregador"), wifiNotCharging.blockers(true, true))
        // Without the charging preference the same device is fine.
        assertTrue(wifiNotCharging.allowsSlowQueue(wifiOnly = true, chargingOnly = false))
    }

    @Test
    fun `offline device reports every missing condition`() {
        assertEquals(listOf("sem internet", "sem Wi-Fi"), offline.blockers(true, true))
        assertFalse(offline.allowsSlowQueue(wifiOnly = false, chargingOnly = false))
    }

    @Test
    fun `wifi with connection and charger allows everything`() {
        assertTrue(wifiCharging.allowsSlowQueue(wifiOnly = true, chargingOnly = true))
        assertTrue(wifiCharging.blockers(true, true).isEmpty())
    }
}
