package com.lynchscreener

import com.lynchscreener.domain.TtmEarnings
import com.lynchscreener.domain.TtmEarnings.Quarter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The YoY column must compare twelve months against the twelve months before it,
 * not two closed fiscal years. These cases pin the arithmetic and the fallbacks.
 */
class TtmEarningsTest {

    @Test
    fun `eight quarters produce a real yoy`() {
        // 2025 quarters: 100 each → TTM 400. 2026: 150 each → TTM 600.
        val quarters = listOf(
            q(2025, 1, 100.0), q(2025, 2, 100.0), q(2025, 3, 100.0), q(2025, 4, 100.0),
            q(2026, 1, 150.0), q(2026, 2, 150.0), q(2026, 3, 150.0),
        )
        // Q4 2026 is not filed yet: the window ends at 2026Q3.
        val result = TtmEarnings.compute(quarters, annual = emptyMap(), currentYear = 2026)

        assertTrue(result.quarterly)
        assertEquals(450.0, result.ttmCurrent!!, 1e-9) // 2025Q4 + 2026Q1..Q3
        assertEquals(400.0, result.ttmPrevious!!, 1e-9) // 2025Q1..Q4
        assertEquals(0.125, result.yoy!!, 1e-9)
        assertEquals("2026Q3", result.latestQuarter)
    }

    @Test
    fun `missing fourth quarter is derived from the fiscal year`() {
        // Only Q1..Q3 of 2025 plus the full year: Q4 = 400 - 300 = 100.
        val quarters = listOf(
            q(2024, 1, 80.0), q(2024, 2, 80.0), q(2024, 3, 80.0), q(2024, 4, 80.0),
            q(2025, 1, 100.0), q(2025, 2, 100.0), q(2025, 3, 100.0),
            q(2026, 1, 120.0), q(2026, 2, 120.0), q(2026, 3, 120.0),
        )
        val result = TtmEarnings.compute(
            quarters,
            annual = mapOf(2025 to 400.0, 2024 to 320.0),
            currentYear = 2026,
        )

        assertTrue(result.quarterly)
        // 2025Q4 (100, derived) + 2026Q1..Q3 (360) = 460
        assertEquals(460.0, result.ttmCurrent!!, 1e-9)
        // 2024Q4 + 2025Q1..Q3 = 380
        assertEquals(380.0, result.ttmPrevious!!, 1e-9)
        assertEquals("2026Q3", result.latestQuarter)
    }

    @Test
    fun `a gap in the series falls back to fiscal years`() {
        // 2025Q2 is missing and 2025 has no closed fiscal year to derive it from.
        val quarters = listOf(
            q(2024, 1, 100.0), q(2024, 2, 100.0), q(2024, 3, 100.0), q(2024, 4, 100.0),
            q(2025, 1, 100.0), q(2025, 3, 100.0), q(2025, 4, 100.0),
            q(2026, 1, 200.0), q(2026, 2, 200.0), q(2026, 3, 200.0),
        )
        val result = TtmEarnings.compute(
            quarters,
            annual = mapOf(2024 to 400.0, 2025 to 400.0, 2026 to 800.0),
            currentYear = 2026,
        )

        assertFalse(result.quarterly)
        assertEquals("FY2026", result.latestQuarter)
        assertEquals(800.0, result.ttmCurrent!!, 1e-9)
        assertEquals(400.0, result.ttmPrevious!!, 1e-9)
        assertEquals(1.0, result.yoy!!, 1e-9)
    }

    @Test
    fun `fewer than eight quarters uses fiscal years`() {
        val quarters = listOf(q(2026, 1, 100.0), q(2026, 2, 100.0), q(2026, 3, 100.0))
        val result = TtmEarnings.compute(
            quarters,
            annual = mapOf(2024 to 50.0, 2025 to 100.0),
            currentYear = 2026,
        )

        assertFalse(result.quarterly)
        assertEquals("FY2025", result.latestQuarter)
        assertEquals(1.0, result.yoy!!, 1e-9) // 100 / 50 - 1
    }

    @Test
    fun `no data at all yields empty result`() {
        val result = TtmEarnings.compute(emptyList(), emptyMap(), currentYear = 2026)
        assertNull(result.yoy)
        assertNull(result.ttmCurrent)
        assertEquals(0, result.quartersUsed)
    }

    @Test
    fun `latest filing wins when the same quarter is filed twice`() {
        // The 2026 ITR carries the 2025 comparatives; a restatement must not be
        // averaged with the original.
        val quarters = listOf(
            q(2024, 4, 10.0),
            q(2025, 1, 10.0), q(2025, 2, 10.0), q(2025, 3, 10.0), q(2025, 4, 10.0),
            q(2026, 1, 20.0), q(2026, 2, 20.0), q(2026, 3, 20.0),
            q(2025, 1, 40.0), // restated, arrives last
        )
        val result = TtmEarnings.compute(quarters, annual = emptyMap(), currentYear = 2026)

        // Current window: 2025Q4 + 2026Q1..Q3 = 10 + 60
        assertEquals(70.0, result.ttmCurrent!!, 1e-9)
        // Previous window: 2024Q4 + 2025Q1..Q3 = 10 + 40 + 10 + 10
        assertEquals(70.0, result.ttmPrevious!!, 1e-9)
    }

    @Test
    fun `quarter index is monotonic across year boundaries`() {
        assertTrue(q(2026, 1, 0.0) > q(2025, 4, 0.0))
        assertEquals(1, q(2026, 1, 0.0).index - q(2025, 4, 0.0).index)
    }

    private fun q(year: Int, quarter: Int, value: Double) = Quarter(year, quarter, value)
}
