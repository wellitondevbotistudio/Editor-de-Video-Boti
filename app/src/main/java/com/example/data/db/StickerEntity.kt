package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stickers",
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
data class StickerEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val orderIndex: Int = 0,
    val uri: String,
    val localPath: String = "",
    val name: String = "",
    val isGif: Boolean = false,
    val startTimeMs: Long = 0L,
    val durationMs: Long = 3000L,
    val posX: Float = 0.5f,
    val posY: Float = 0.5f,
    val scale: Float = 1.0f,
    val rotation: Float = 0f,
    val opacity: Float = 1.0f,
    val animationIn: String = "Fade",
    val animationOut: String = "Fade",
    val animationDurationMs: Long = 500L,
    val isVisible: Boolean = true,
    val isVideo: Boolean = false,
    val isLocked: Boolean = false
)
