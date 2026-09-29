package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audio_tracks",
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
data class AudioTrackEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val orderIndex: Int = 0,
    val name: String,
    val category: String,
    val duration: String,
    val durationMs: Long = 60000L,
    val uri: String = "",
    val localPath: String = "",
    val originalName: String = "",
    val mimeType: String = "",
    val fileSizeBytes: Long = 0L,
    val volume: Float = 0.8f,
    val isMuted: Boolean = false,
    val timelineStartMs: Long = 0L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L
)
