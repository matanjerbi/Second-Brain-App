package com.matan.secondbrain.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Item>>

    @Query("SELECT * FROM items")
    suspend fun all(): List<Item>

    @Query("SELECT * FROM items WHERE id = :id")
    fun observe(id: Long): Flow<Item?>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: Long): Item?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: Item): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<Item>): List<Long>

    @Update
    suspend fun update(item: Item)

    @Delete
    suspend fun delete(item: Item)

    @Query("SELECT * FROM items WHERE enrichedAt IS NULL AND sensitive = 0 ORDER BY createdAt DESC")
    suspend fun pendingEnrichment(): List<Item>

    @Query("SELECT COUNT(*) FROM items WHERE enrichedAt IS NULL AND sensitive = 0")
    fun observePendingCount(): Flow<Int>
}
