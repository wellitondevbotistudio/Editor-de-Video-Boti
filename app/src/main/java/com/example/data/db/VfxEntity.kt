package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vfx_effects",
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
data class VfxEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val vfxId: String,
    val name: String,
    val category: String,
    val thumbUrl: String,
    val intensity: Float = 50f,
    val isPremium: Boolean = false,
    val clipId: String? = null,
    val isEnabled: Boolean = true
)
