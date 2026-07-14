package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DialogDao {
    @Query("SELECT * FROM dialog_configs ORDER BY timestamp DESC")
    fun getAllConfigs(): Flow<List<DialogConfig>>

    @Query("SELECT * FROM dialog_configs WHERE id = :id LIMIT 1")
    suspend fun getConfigById(id: Int): DialogConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: DialogConfig): Long

    @Update
    suspend fun updateConfig(config: DialogConfig)

    @Query("DELETE FROM dialog_configs WHERE id = :id")
    suspend fun deleteConfigById(id: Int)
}
