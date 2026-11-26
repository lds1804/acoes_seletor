package com.lynchscreener.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun assetDao(): AssetDao
    abstract fun fundamentalsDao(): FundamentalsDao
    abstract fun portfolioPositionDao(): PortfolioPositionDao

    companion object {
        const val NAME = "lynch_screener.db"

        /** Etapa 2: adds sharesOutstanding + dividendPerShare to `fundamentals`. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE fundamentals ADD COLUMN sharesOutstanding REAL")
                db.execSQL("ALTER TABLE fundamentals ADD COLUMN dividendPerShare REAL")
            }
        }

        /** CVM earnings history: 5-year net-income CAGR + consistency + history JSON. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE fundamentals ADD COLUMN earningsCagr5y REAL")
                db.execSQL("ALTER TABLE fundamentals ADD COLUMN earningsConsistent INTEGER")
                db.execSQL("ALTER TABLE fundamentals ADD COLUMN earningsHistoryJson TEXT")
            }
        }
    }
}
