package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.db.*
import com.example.data.MockData
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class ProjectRepository(private val database: AppDatabase) {

    private val projectDao = database.projectDao()
    private val clipDao = database.clipDao()
    private val audioTrackDao = database.audioTrackDao()
    private val textOverlayDao = database.textOverlayDao()
    private val stickerDao = database.stickerDao()
    private val subtitleDao = database.subtitleDao()
    private val vfxDao = database.vfxDao()
    private val transitionDao = database.transitionDao()

    fun getAllProjects(): Flow<List<ProjectItem>> {
        return projectDao.getAllProjectsWithDetails().map { list ->
            list.map { it.toDomain() }
        }
    }

    fun getProjectById(id: String): Flow<ProjectItem?> {
        return projectDao.getProjectWithDetailsById(id).map { it?.toDomain() }
    }

    suspend fun getProjectByIdOnce(id: String): ProjectItem? = withContext(Dispatchers.IO) {
        projectDao.getProjectWithDetailsByIdOnce(id)?.toDomain()
    }

    suspend fun seedInitialDataIfNeeded() = withContext(Dispatchers.IO) {
        val count = projectDao.getProjectsCount()
        if (count == 0) {
            MockData.defaultProjects.forEach { defaultProject ->
                saveProject(defaultProject)
            }
        }
    }

    suspend fun saveProject(project: ProjectItem) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val projectEntity = project.toEntity()
            projectDao.insertProject(projectEntity)

            // Replace clips
            clipDao.deleteClipsForProject(project.id)
            val clipEntities = project.clips.mapIndexed { index, clip ->
                clip.toEntity(projectId = project.id, orderIndex = index)
            }
            if (clipEntities.isNotEmpty()) {
                clipDao.insertClips(clipEntities)
            }

            // Replace audios
            audioTrackDao.deleteAudioTracksForProject(project.id)
            val audioEntities = project.audios.mapIndexed { index, audio ->
                audio.toEntity(projectId = project.id, orderIndex = index)
            }
            if (audioEntities.isNotEmpty()) {
                audioTrackDao.insertAudioTracks(audioEntities)
            }

            // Replace texts
            textOverlayDao.deleteTextOverlaysForProject(project.id)
            val textEntities = project.texts.mapIndexed { index, text ->
                text.toEntity(projectId = project.id, orderIndex = index)
            }
            if (textEntities.isNotEmpty()) {
                textOverlayDao.insertTextOverlays(textEntities)
            }

            // Replace stickers
            stickerDao.deleteStickersForProject(project.id)
            val stickerEntities = project.stickers.mapIndexed { index, sticker ->
                sticker.toEntity(projectId = project.id, orderIndex = index)
            }
            if (stickerEntities.isNotEmpty()) {
                stickerDao.insertStickers(stickerEntities)
            }

            // Replace subtitles
            subtitleDao.deleteSubtitlesForProject(project.id)
            val subtitleEntities = project.subtitles.mapIndexed { index, sub ->
                sub.toEntity(projectId = project.id, orderIndex = index)
            }
            if (subtitleEntities.isNotEmpty()) {
                subtitleDao.insertSubtitles(subtitleEntities)
            }

            // Replace VFX
            vfxDao.deleteVfxForProject(project.id)
            val vfxEntities = project.activeVFX.map { it.toEntity(projectId = project.id) }
            if (vfxEntities.isNotEmpty()) {
                vfxDao.insertVfxList(vfxEntities)
            }

            // Replace transitions
            transitionDao.deleteTransitionsForProject(project.id)
            val transitionEntities = project.transitions.map { it.toEntity(projectId = project.id) }
            if (transitionEntities.isNotEmpty()) {
                transitionDao.insertTransitions(transitionEntities)
            }
        }
    }

    suspend fun renameProject(id: String, newTitle: String) = withContext(Dispatchers.IO) {
        projectDao.renameProject(id = id, newTitle = newTitle, updatedAt = System.currentTimeMillis())
    }

    suspend fun deleteProject(id: String) = withContext(Dispatchers.IO) {
        // Because of ForeignKey CASCADE, child rows are deleted automatically
        projectDao.deleteProjectById(id)
    }

    suspend fun duplicateProject(project: ProjectItem): ProjectItem = withContext(Dispatchers.IO) {
        val newProjId = "proj_copy_" + UUID.randomUUID().toString().take(6)
        val clipIdMap = mutableMapOf<String, String>()
        val duplicatedClips = project.clips.map { clip ->
            val newClipId = "clip_" + UUID.randomUUID().toString().take(6)
            clipIdMap[clip.id] = newClipId
            clip.copy(id = newClipId)
        }
        val duplicatedAudios = project.audios.map { audio ->
            audio.copy(id = "audio_" + UUID.randomUUID().toString().take(6))
        }
        val duplicatedTexts = project.texts.map { text ->
            text.copy(id = "txt_" + UUID.randomUUID().toString().take(6))
        }
        val duplicatedStickers = project.stickers.map { sticker ->
            sticker.copy(id = "stk_" + UUID.randomUUID().toString().take(6))
        }
        val duplicatedSubtitles = project.subtitles.map { sub ->
            sub.copy(id = "sub_" + UUID.randomUUID().toString().take(6))
        }
        val duplicatedVfx = project.activeVFX.map { vfx ->
            vfx.copy(id = "vfx_" + UUID.randomUUID().toString().take(6))
        }
        val duplicatedTransitions = project.transitions.map { tr ->
            tr.copy(
                id = "tr_" + UUID.randomUUID().toString().take(6),
                fromClipId = clipIdMap[tr.fromClipId] ?: tr.fromClipId,
                toClipId = clipIdMap[tr.toClipId] ?: tr.toClipId
            )
        }

        val duplicatedProject = project.copy(
            id = newProjId,
            title = "${project.title} (Cópia)",
            date = "Agora",
            clips = duplicatedClips,
            audios = duplicatedAudios,
            texts = duplicatedTexts,
            stickers = duplicatedStickers,
            subtitles = duplicatedSubtitles,
            activeVFX = duplicatedVfx,
            transitions = duplicatedTransitions
        )

        saveProject(duplicatedProject)
        duplicatedProject
    }
}

// ----------------- MAPPERS -----------------

fun ProjectWithDetails.toDomain(): ProjectItem {
    val ratio = try {
        AspectRatio.valueOf(project.aspectRatio)
    } catch (_: Exception) {
        AspectRatio.RATIO_9_16
    }

    return ProjectItem(
        id = project.id,
        title = project.title,
        duration = project.duration,
        date = project.date,
        thumbUrl = project.thumbUrl,
        aspectRatio = ratio,
        clips = clips.sortedBy { it.orderIndex }.map { it.toDomain() },
        audios = audios.sortedBy { it.orderIndex }.map { it.toDomain() },
        texts = texts.sortedBy { it.orderIndex }.map { it.toDomain() },
        stickers = stickers.sortedBy { it.orderIndex }.map { it.toDomain() },
        subtitles = subtitles.sortedBy { it.orderIndex }.map { it.toDomain() },
        subtitleStyle = SubtitleStyleConfig(
            fontSizeSp = project.subtitleFontSize,
            textColorHex = project.subtitleTextColor,
            strokeColorHex = project.subtitleStrokeColor,
            strokeWidth = project.subtitleStrokeWidth,
            positionYPercent = project.subtitlePositionY,
            bgEnabled = project.subtitleBgEnabled,
            bgColorHex = project.subtitleBgColor,
            bgOpacity = project.subtitleBgOpacity
        ),
        activeFilter = project.activeFilter,
        activeVFX = vfxList.map { it.toDomain() },
        transitions = transitions.map { it.toDomain() }
    )
}

fun ClipEntity.toDomain(): MediaClip {
    val mediaType = try {
        MediaType.valueOf(type)
    } catch (_: Exception) {
        MediaType.VIDEO
    }
    return MediaClip(
        id = id,
        title = title,
        uri = uri,
        type = mediaType,
        localPath = localPath,
        thumbnailPath = thumbnailPath,
        originalName = originalName,
        mimeType = mimeType,
        width = width,
        height = height,
        fileSizeBytes = fileSizeBytes,
        durationMs = durationMs,
        originalDurationMs = originalDurationMs,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        speed = speed,
        volume = volume,
        brightness = brightness,
        contrast = contrast,
        saturation = saturation,
        filter = filter,
        transition = transition,
        transitionDurationMs = transitionDurationMs,
        cropRatio = cropRatio,
        rotation = rotation,
        scale = scale,
        flipHorizontal = flipHorizontal,
        flipVertical = flipVertical,
        opacity = opacity,
        positionX = positionX,
        positionY = positionY,
        isReverse = isReverse,
        isFrozen = isFrozen
    )
}

fun AudioTrackEntity.toDomain(): AudioTrackItem {
    return AudioTrackItem(
        id = id,
        name = name,
        category = category,
        duration = duration,
        durationMs = durationMs,
        uri = uri,
        localPath = localPath,
        originalName = originalName,
        mimeType = mimeType,
        fileSizeBytes = fileSizeBytes,
        volume = volume,
        isMuted = isMuted,
        timelineStartMs = timelineStartMs,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        fadeInMs = fadeInMs,
        fadeOutMs = fadeOutMs
    )
}

fun TextOverlayEntity.toDomain(): TextOverlayItem {
    return TextOverlayItem(
        id = id,
        text = text,
        startTimeMs = startTimeMs,
        durationMs = durationMs,
        posX = posX,
        posY = posY,
        scale = scale,
        rotation = rotation,
        opacity = opacity,
        fontSizeSp = fontSizeSp,
        colorHex = colorHex,
        bgHex = bgHex,
        strokeColorHex = strokeColorHex,
        strokeWidth = strokeWidth,
        shadowColorHex = shadowColorHex,
        shadowRadius = shadowRadius,
        alignment = alignment,
        fontFamily = fontFamily,
        animation = animation,
        animationIn = animationIn,
        animationOut = animationOut,
        animationDurationMs = animationDurationMs,
        textAnimationMode = textAnimationMode,
        styleTemplateId = styleTemplateId,
        isVisible = isVisible
    )
}

fun StickerEntity.toDomain(): StickerItem {
    return StickerItem(
        id = id,
        uri = uri,
        localPath = localPath,
        name = name,
        isGif = isGif,
        startTimeMs = startTimeMs,
        durationMs = durationMs,
        posX = posX,
        posY = posY,
        scale = scale,
        rotation = rotation,
        opacity = opacity,
        animationIn = animationIn,
        animationOut = animationOut,
        animationDurationMs = animationDurationMs,
        isVisible = isVisible
    )
}

fun TransitionEntity.toDomain(): TransitionItem {
    return TransitionItem(
        id = id,
        fromClipId = fromClipId,
        toClipId = toClipId,
        name = name,
        iconName = iconName,
        durationMs = durationMs,
        isEnabled = isEnabled
    )
}

fun SubtitleEntity.toDomain(): SubtitleSegmentItem {
    return SubtitleSegmentItem(
        id = id,
        text = text,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        isEnabled = isEnabled
    )
}

fun VfxEntity.toDomain(): VFXEffectItem {
    return VFXEffectItem(
        id = vfxId,
        name = name,
        category = category,
        thumbUrl = thumbUrl,
        intensity = intensity,
        isPremium = isPremium,
        clipId = clipId,
        isEnabled = isEnabled
    )
}

fun ProjectItem.toEntity(): ProjectEntity {
    return ProjectEntity(
        id = id,
        title = title,
        duration = duration,
        date = date,
        thumbUrl = thumbUrl,
        aspectRatio = aspectRatio.name,
        activeFilter = activeFilter,
        subtitleFontSize = subtitleStyle.fontSizeSp,
        subtitleTextColor = subtitleStyle.textColorHex,
        subtitleStrokeColor = subtitleStyle.strokeColorHex,
        subtitleStrokeWidth = subtitleStyle.strokeWidth,
        subtitlePositionY = subtitleStyle.positionYPercent,
        subtitleBgEnabled = subtitleStyle.bgEnabled,
        subtitleBgColor = subtitleStyle.bgColorHex,
        subtitleBgOpacity = subtitleStyle.bgOpacity,
        updatedAt = System.currentTimeMillis()
    )
}

fun MediaClip.toEntity(projectId: String, orderIndex: Int): ClipEntity {
    return ClipEntity(
        id = id,
        projectId = projectId,
        orderIndex = orderIndex,
        track = 0,
        title = title,
        uri = uri,
        type = type.name,
        localPath = localPath,
        thumbnailPath = thumbnailPath,
        originalName = originalName,
        mimeType = mimeType,
        width = width,
        height = height,
        fileSizeBytes = fileSizeBytes,
        durationMs = durationMs,
        originalDurationMs = originalDurationMs,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        speed = speed,
        volume = volume,
        brightness = brightness,
        contrast = contrast,
        saturation = saturation,
        filter = filter,
        transition = transition,
        transitionDurationMs = transitionDurationMs,
        cropRatio = cropRatio,
        rotation = rotation,
        scale = scale,
        flipHorizontal = flipHorizontal,
        flipVertical = flipVertical,
        opacity = opacity,
        positionX = positionX,
        positionY = positionY,
        isReverse = isReverse,
        isFrozen = isFrozen
    )
}

fun AudioTrackItem.toEntity(projectId: String, orderIndex: Int): AudioTrackEntity {
    return AudioTrackEntity(
        id = id,
        projectId = projectId,
        orderIndex = orderIndex,
        name = name,
        category = category,
        duration = duration,
        durationMs = durationMs,
        uri = uri,
        localPath = localPath,
        originalName = originalName,
        mimeType = mimeType,
        fileSizeBytes = fileSizeBytes,
        volume = volume,
        isMuted = isMuted,
        timelineStartMs = timelineStartMs,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        fadeInMs = fadeInMs,
        fadeOutMs = fadeOutMs
    )
}

fun TextOverlayItem.toEntity(projectId: String, orderIndex: Int): TextOverlayEntity {
    return TextOverlayEntity(
        id = id,
        projectId = projectId,
        orderIndex = orderIndex,
        text = text,
        startTimeMs = startTimeMs,
        durationMs = durationMs,
        posX = posX,
        posY = posY,
        scale = scale,
        rotation = rotation,
        opacity = opacity,
        fontSizeSp = fontSizeSp,
        colorHex = colorHex,
        bgHex = bgHex,
        strokeColorHex = strokeColorHex,
        strokeWidth = strokeWidth,
        shadowColorHex = shadowColorHex,
        shadowRadius = shadowRadius,
        alignment = alignment,
        fontFamily = fontFamily,
        animation = animation,
        animationIn = animationIn,
        animationOut = animationOut,
        animationDurationMs = animationDurationMs,
        textAnimationMode = textAnimationMode,
        styleTemplateId = styleTemplateId,
        isVisible = isVisible
    )
}

fun StickerItem.toEntity(projectId: String, orderIndex: Int): StickerEntity {
    return StickerEntity(
        id = id,
        projectId = projectId,
        orderIndex = orderIndex,
        uri = uri,
        localPath = localPath,
        name = name,
        isGif = isGif,
        startTimeMs = startTimeMs,
        durationMs = durationMs,
        posX = posX,
        posY = posY,
        scale = scale,
        rotation = rotation,
        opacity = opacity,
        animationIn = animationIn,
        animationOut = animationOut,
        animationDurationMs = animationDurationMs,
        isVisible = isVisible
    )
}

fun TransitionItem.toEntity(projectId: String): TransitionEntity {
    return TransitionEntity(
        id = id,
        projectId = projectId,
        transitionId = id,
        fromClipId = fromClipId,
        toClipId = toClipId,
        name = name,
        iconName = iconName,
        durationMs = durationMs,
        isEnabled = isEnabled
    )
}

fun SubtitleSegmentItem.toEntity(projectId: String, orderIndex: Int): SubtitleEntity {
    return SubtitleEntity(
        id = id,
        projectId = projectId,
        orderIndex = orderIndex,
        text = text,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        isEnabled = isEnabled
    )
}

fun VFXEffectItem.toEntity(projectId: String): VfxEntity {
    return VfxEntity(
        id = "vfx_${projectId}_${id}",
        projectId = projectId,
        vfxId = id,
        name = name,
        category = category,
        thumbUrl = thumbUrl,
        intensity = intensity,
        isPremium = isPremium,
        clipId = clipId,
        isEnabled = isEnabled
    )
}
