package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val duration: String,
    val date: String,
    val thumbUrl: String,
    val aspectRatio: String, // RATIO_9_16, RATIO_16_9, etc.
    val activeFilter: String = "Original",
    val subtitleFontSize: Float = 18f,
    val subtitleTextColor: String = "#FFFFFF",
    val subtitleStrokeColor: String = "#000000",
    val subtitleStrokeWidth: Float = 2f,
    val subtitlePositionY: Float = 0.82f,
    val subtitleBgEnabled: Boolean = true,
    val subtitleBgColor: String = "#000000",
    val subtitleBgOpacity: Float = 0.7f,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
