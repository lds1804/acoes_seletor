package com.lynchscreener.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Phase 2 (portfolio) — seeded now so the schema is migration-stable.
 * The portfolio screen is a "Em breve" placeholder in this version.
 */
@Entity(
    tableName = "portfolio_positions",
    indices = [
        Index(value = ["ticker", "market"]),
    ]
)
data class PortfolioPositionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ticker: String,
    val market: String,
    val quantity: Double,
    val averagePrice: Double,
    /** Target weight in the portfolio, fraction (0.20 == 20%). */
    val targetWeight: Double?,
    /** Exit trigger: P/L above which the position should be reviewed. */
    val exitPlTrigger: Double?,
    /** Exit trigger: P/VP above which the position should be reviewed. */
    val exitPvpTrigger: Double?,
    val notes: String?,
    val updatedAt: Long,
)
