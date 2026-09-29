package com.example.data.db.dao

import androidx.room.*
import com.example.data.db.SubtitleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubtitleDao {

    @Query("SELECT * FROM subtitles WHERE projectId = :projectId ORDER BY startTimeMs ASC")
    fun getSubtitlesForProject(projectId: String): Flow<List<SubtitleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubtitles(subtitles: List<SubtitleEntity>)

    @Query("DELETE FROM subtitles WHERE projectId = :projectId")
    suspend fun deleteSubtitlesForProject(projectId: String)

    @Query("DELETE FROM subtitles WHERE id = :id")
    suspend fun deleteSubtitleById(id: String)
}
