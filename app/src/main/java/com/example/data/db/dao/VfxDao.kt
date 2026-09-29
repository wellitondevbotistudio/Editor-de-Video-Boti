package com.example.data.db.dao

import androidx.room.*
import com.example.data.db.VfxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VfxDao {

    @Query("SELECT * FROM vfx_effects WHERE projectId = :projectId")
    fun getVfxForProject(projectId: String): Flow<List<VfxEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVfxList(vfxList: List<VfxEntity>)

    @Query("DELETE FROM vfx_effects WHERE projectId = :projectId")
    suspend fun deleteVfxForProject(projectId: String)
}
