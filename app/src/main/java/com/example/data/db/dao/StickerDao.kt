package com.example.data.db.dao

import androidx.room.*
import com.example.data.db.StickerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StickerDao {

    @Query("SELECT * FROM stickers WHERE projectId = :projectId ORDER BY orderIndex ASC")
    fun getStickersForProject(projectId: String): Flow<List<StickerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStickers(stickers: List<StickerEntity>)

    @Query("DELETE FROM stickers WHERE projectId = :projectId")
    suspend fun deleteStickersForProject(projectId: String)

    @Query("DELETE FROM stickers WHERE id = :id")
    suspend fun deleteStickerById(id: String)
}
