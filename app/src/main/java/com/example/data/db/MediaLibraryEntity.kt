package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_library")
data class MediaLibraryEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val localPath: String,
    val uri: String,
    val mimeType: String,
    val durationMs: Long,
    val thumbnailPath: String? = null,
    val mediaType: String, // VIDEO, PHOTO, AUDIO
    val dateAdded: Long = System.currentTimeMillis()
)
