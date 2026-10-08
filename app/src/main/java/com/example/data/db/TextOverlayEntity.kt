package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "text_overlays",
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
data class TextOverlayEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val orderIndex: Int = 0,
    val text: String,
    val startTimeMs: Long = 0L,
    val durationMs: Long = 3000L,
    val posX: Float = 0.5f,
    val posY: Float = 0.5f,
    val scale: Float = 1.0f,
    val rotation: Float = 0f,
    val opacity: Float = 1.0f,
    val fontSizeSp: Float = 24f,
    val colorHex: String = "#FFFFFF",
    val bgHex: String? = null,
    val strokeColorHex: String? = null,
    val strokeWidth: Float = 0f,
    val shadowColorHex: String? = null,
    val shadowRadius: Float = 0f,
    val alignment: String = "Center",
    val fontFamily: String = "Inter",
    val animation: String = "Fade",
    val animationIn: String = "Fade",
    val animationOut: String = "Fade",
    val animationDurationMs: Long = 500L,
    val textAnimationMode: String = "Full",
    val styleTemplateId: String? = null,
    val zIndex: Int = 0,
    val isVisible: Boolean = true,
    val isLocked: Boolean = false
)
