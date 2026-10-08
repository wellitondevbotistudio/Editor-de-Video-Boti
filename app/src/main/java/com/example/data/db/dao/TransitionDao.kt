package com.example.data.db.dao

import androidx.room.*
import com.example.data.db.TransitionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransitionDao {

    @Query("SELECT * FROM transitions WHERE projectId = :projectId")
    fun getTransitionsForProject(projectId: String): Flow<List<TransitionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransitions(transitions: List<TransitionEntity>)

    @Query("DELETE FROM transitions WHERE projectId = :projectId")
    suspend fun deleteTransitionsForProject(projectId: String)
}
