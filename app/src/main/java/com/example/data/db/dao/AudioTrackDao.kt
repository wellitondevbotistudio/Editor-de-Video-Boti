package com.example.data.db.dao

import androidx.room.*
import com.example.data.db.AudioTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioTrackDao {

    @Query("SELECT * FROM audio_tracks WHERE projectId = :projectId ORDER BY orderIndex ASC")
    fun getAudioTracksForProject(projectId: String): Flow<List<AudioTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudioTracks(tracks: List<AudioTrackEntity>)

    @Query("DELETE FROM audio_tracks WHERE projectId = :projectId")
    suspend fun deleteAudioTracksForProject(projectId: String)

    @Query("DELETE FROM audio_tracks WHERE id = :id")
    suspend fun deleteAudioTrackById(id: String)
}
