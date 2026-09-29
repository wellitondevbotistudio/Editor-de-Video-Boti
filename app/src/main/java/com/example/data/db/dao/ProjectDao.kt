package com.example.data.db.dao

import androidx.room.*
import com.example.data.db.ProjectEntity
import com.example.data.db.ProjectWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Transaction
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjectsWithDetails(): Flow<List<ProjectWithDetails>>

    @Transaction
    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    fun getProjectWithDetailsById(id: String): Flow<ProjectWithDetails?>

    @Transaction
    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectWithDetailsByIdOnce(id: String): ProjectWithDetails?

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun getProjectsCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("UPDATE projects SET title = :newTitle, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renameProject(id: String, newTitle: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)

    @Query("DELETE FROM projects")
    suspend fun deleteAllProjects()
}
