package com.example.data.db

import androidx.room.Embedded
import androidx.room.Relation

data class ProjectWithDetails(
    @Embedded val project: ProjectEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "projectId"
    )
    val clips: List<ClipEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "projectId"
    )
    val audios: List<AudioTrackEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "projectId"
    )
    val texts: List<TextOverlayEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "projectId"
    )
    val stickers: List<StickerEntity> = emptyList(),
    @Relation(
        parentColumn = "id",
        entityColumn = "projectId"
    )
    val subtitles: List<SubtitleEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "projectId"
    )
    val vfxList: List<VfxEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "projectId"
    )
    val transitions: List<TransitionEntity>
)
