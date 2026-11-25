package com.lynchscreener.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lynchscreener.data.local.entity.PortfolioPositionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PortfolioPositionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(position: PortfolioPositionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(positions: List<PortfolioPositionEntity>)

    @Delete
    suspend fun delete(position: PortfolioPositionEntity)

    @Query("SELECT * FROM portfolio_positions WHERE id = :id")
    suspend fun getById(id: Long): PortfolioPositionEntity?

    @Query("SELECT * FROM portfolio_positions ORDER BY ticker")
    fun observeAll(): Flow<List<PortfolioPositionEntity>>

    @Query("DELETE FROM portfolio_positions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
