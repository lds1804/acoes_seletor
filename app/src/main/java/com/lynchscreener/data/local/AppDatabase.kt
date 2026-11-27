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
    version = 6,
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

        /**
         * Parent-company basis: equity attributable to controllers and which
         * basis the reported earnings use. Existing rows stay NULL and are
         * refilled by [com.lynchscreener.data.repository.AssetRepository.rescoreAll].
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE fundamentals ADD COLUMN equityControllers REAL")
                db.execSQL("ALTER TABLE fundamentals ADD COLUMN earningsBasis TEXT")
            }
        }

        /**
         * Ranking v3: earnings momentum (YoY) and the alert flags shown next to
         * the score and in the exported list.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE fundamentals ADD COLUMN earningsYoy REAL")
                db.execSQL("ALTER TABLE fundamentals ADD COLUMN alertFlags TEXT")
            }
        }

        /**
         * Real trailing-twelve-month earnings, from the quarterly (ITR) filings.
         * Existing rows keep NULL and are refilled by the CVM worker, which is
         * triggered again because the quarterly series is missing.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE fundamentals ADD COLUMN quarterlyJson TEXT")
                db.execSQL("ALTER TABLE fundamentals ADD COLUMN ttmNetIncome REAL")
                db.execSQL("ALTER TABLE fundamentals ADD COLUMN ttmPrevious REAL")
                db.execSQL("ALTER TABLE fundamentals ADD COLUMN ttmQuarter TEXT")
            }
        }
    }
}
