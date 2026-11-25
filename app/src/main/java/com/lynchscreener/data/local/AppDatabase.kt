package com.lynchscreener.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lynchscreener.data.local.dao.AssetDao
import com.lynchscreener.data.local.dao.FundamentalsDao
import com.lynchscreener.data.local.dao.PortfolioPositionDao
import com.lynchscreener.data.local.entity.AssetEntity
import com.lynchscreener.data.local.entity.FundamentalsEntity
import com.lynchscreener.data.local.entity.PortfolioPositionEntity

@Database(
    entities = [
        AssetEntity::class,
        FundamentalsEntity::class,
        PortfolioPositionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun assetDao(): AssetDao
    abstract fun fundamentalsDao(): FundamentalsDao
    abstract fun portfolioPositionDao(): PortfolioPositionDao

    companion object {
        const val NAME = "lynch_screener.db"
    }
}
