package com.example.data.db.dao

import androidx.room.*
import com.example.data.db.TextOverlayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TextOverlayDao {

    @Query("SELECT * FROM text_overlays WHERE projectId = :projectId ORDER BY orderIndex ASC")
    fun getTextOverlaysForProject(projectId: String): Flow<List<TextOverlayEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTextOverlays(texts: List<TextOverlayEntity>)

    @Query("DELETE FROM text_overlays WHERE projectId = :projectId")
    suspend fun deleteTextOverlaysForProject(projectId: String)

    @Query("DELETE FROM text_overlays WHERE id = :id")
    suspend fun deleteTextOverlayById(id: String)
}
