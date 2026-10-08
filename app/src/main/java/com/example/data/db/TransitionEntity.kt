package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transitions",
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
data class TransitionEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val transitionId: String,
    val fromClipId: String = "",
    val toClipId: String = "",
    val name: String,
    val iconName: String = "shuffle",
    val durationMs: Long = 1000L,
    val isEnabled: Boolean = true
)
