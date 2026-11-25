package com.lynchscreener.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Derived fundamentals + Peter Lynch score for each ticker.
 *
 * Ratio fields ([roe], [roic], [dividendYield], [cagr5y], [netCashToMarketCap])
 * are stored as FRACTIONS (0.15 == 15%) — see [com.lynchscreener.domain.model.Financials].
 */
@Entity(
    tableName = "fundamentals",
    foreignKeys = [
        ForeignKey(
            entity = AssetEntity::class,
            parentColumns = ["ticker"],
            childColumns = ["ticker"],
            onDelete = ForeignKey.CASCADE,
        )
    ]
)
data class FundamentalsEntity(
    @PrimaryKey val ticker: String,
    val pe: Double?,
    val pb: Double?,
    val roe: Double?,
    val roic: Double?,
    val dividendYield: Double?,
    val cagr5y: Double?,
    val pegy: Double?,
    /** Positive = net debt, negative = net cash. */
    val netDebt: Double?,
    /** Net cash (or negative net debt) divided by market cap. */
    val netCashToMarketCap: Double?,
    val equity: Double?,
    val netIncome12m: Double?,
    /** Average daily traded volume (R$ for BR, USD for US). */
    val liquidity: Double?,
    val lynchScore: Int,
    val lynchCategory: String,
    /** Position in the overall market ranking (1 = best). Null until ranked. */
    val overallRank: Int?,
    /** Position within the company's sector (1 = best). Null until ranked. */
    val sectorRank: Int?,
    /** JSON payload explaining the score, for the "Raio-X" screen (Etapa 3). */
    val breakdownJson: String?,
)
