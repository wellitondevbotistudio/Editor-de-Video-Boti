package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "clips",
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
data class ClipEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val orderIndex: Int = 0,
    val track: Int = 0,
    val title: String,
    val uri: String,
    val type: String = "VIDEO", // VIDEO, PHOTO, AUDIO
    val localPath: String = "",
    val thumbnailPath: String = "",
    val originalName: String = "",
    val mimeType: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val fileSizeBytes: Long = 0L,
    val durationMs: Long = 5000L,
    val originalDurationMs: Long = 0L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val speed: Float = 1.0f,
    val volume: Float = 1.0f,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val filter: String = "Original",
    val transition: String? = null,
    val transitionDurationMs: Long = 1000L,
    val cropRatio: String = "Original",
    val rotation: Float = 0f,
    val scale: Float = 1.0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val opacity: Float = 1.0f,
    val positionX: Float = 0f,
    val positionY: Float = 0f,
    val isReverse: Boolean = false,
    val isFrozen: Boolean = false
)
