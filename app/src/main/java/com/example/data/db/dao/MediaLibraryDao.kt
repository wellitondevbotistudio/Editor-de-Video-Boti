package com.example.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.db.MediaLibraryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaLibraryDao {
    @Query("SELECT * FROM media_library ORDER BY dateAdded DESC")
    fun getAllMedia(): Flow<List<MediaLibraryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: MediaLibraryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(media: List<MediaLibraryEntity>)

    @Delete
    suspend fun deleteMedia(media: MediaLibraryEntity)

    @Query("DELETE FROM media_library WHERE id = :id")
    suspend fun deleteById(id: String)
}
