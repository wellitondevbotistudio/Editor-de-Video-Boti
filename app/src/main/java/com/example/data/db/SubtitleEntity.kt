package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subtitles",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class SubtitleEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val orderIndex: Int = 0,
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val isEnabled: Boolean = true
)
