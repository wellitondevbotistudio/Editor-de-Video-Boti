package com.example.util

import com.example.model.MediaClip
import java.util.UUID

data class ClipTimelineInfo(
    val clip: MediaClip,
    val index: Int,
    val timelineStartMs: Long,
    val timelineEndMs: Long,
    val relativeTimelineOffsetMs: Long,
    val sourcePositionMs: Long
)

object TimelineUtils {

    const val MIN_CLIP_DURATION_MS = 100L
    const val SPLIT_EDGE_TOLERANCE_MS = 50L

    fun getSafeSpeed(clip: MediaClip): Float {
        return if (clip.speed > 0f && !clip.speed.isNaN() && !clip.speed.isInfinite()) {
            clip.speed
        } else {
            1.0f
        }
    }

    fun getEffectiveTrimStart(clip: MediaClip): Long {
        return clip.trimStartMs.coerceAtLeast(0L)
    }

    fun getEffectiveTrimEnd(clip: MediaClip): Long {
        val maxDuration = if (clip.originalDurationMs > 0L) {
            clip.originalDurationMs
        } else {
            clip.durationMs.coerceAtLeast(MIN_CLIP_DURATION_MS)
        }

        return if (clip.trimEndMs > clip.trimStartMs) {
            clip.trimEndMs.coerceIn(clip.trimStartMs + 1L, maxDuration)
        } else {
            maxDuration
        }
    }

    fun calculateClipSourceDuration(clip: MediaClip): Long {
        val start = getEffectiveTrimStart(clip)
        val end = getEffectiveTrimEnd(clip)
        return (end - start).coerceAtLeast(0L)
    }

    fun calculateClipTimelineDuration(clip: MediaClip): Long {
        val sourceDuration = calculateClipSourceDuration(clip)
        val speed = getSafeSpeed(clip)
        return (sourceDuration.toFloat() / speed).toLong().coerceAtLeast(0L)
    }

    fun calculateProjectTimelineDuration(clips: List<MediaClip>): Long {
        return clips.sumOf { calculateClipTimelineDuration(it) }.coerceAtLeast(0L)
    }

    fun findClipAtTimelinePosition(clips: List<MediaClip>, playheadMs: Long): ClipTimelineInfo? {
        if (clips.isEmpty()) return null
        val clampedPlayhead = playheadMs.coerceAtLeast(0L)

        var accumulatedMs = 0L
        for (index in clips.indices) {
            val clip = clips[index]
            val clipDuration = calculateClipTimelineDuration(clip)
            val clipStart = accumulatedMs
            val clipEnd = clipStart + clipDuration

            // If playhead falls into this clip, or it's the last clip and playhead is at or beyond
            val isWithin = clampedPlayhead in clipStart until clipEnd
            val isLastAndAtEnd = (index == clips.lastIndex && clampedPlayhead >= clipStart)

            if (isWithin || isLastAndAtEnd) {
                val relativeTimelineOffset = (clampedPlayhead - clipStart).coerceIn(0L, clipDuration)
                val speed = getSafeSpeed(clip)
                val sourceOffset = (relativeTimelineOffset * speed).toLong()
                val sourcePosition = (getEffectiveTrimStart(clip) + sourceOffset).coerceIn(
                    getEffectiveTrimStart(clip),
                    getEffectiveTrimEnd(clip)
                )

                return ClipTimelineInfo(
                    clip = clip,
                    index = index,
                    timelineStartMs = clipStart,
                    timelineEndMs = clipEnd,
                    relativeTimelineOffsetMs = relativeTimelineOffset,
                    sourcePositionMs = sourcePosition
                )
            }
            accumulatedMs = clipEnd
        }

        // If beyond end of all clips, return last clip
        val lastClip = clips.last()
        val lastDuration = calculateClipTimelineDuration(lastClip)
        return ClipTimelineInfo(
            clip = lastClip,
            index = clips.lastIndex,
            timelineStartMs = (accumulatedMs - lastDuration).coerceAtLeast(0L),
            timelineEndMs = accumulatedMs,
            relativeTimelineOffsetMs = lastDuration,
            sourcePositionMs = getEffectiveTrimEnd(lastClip)
        )
    }

    fun splitClipAtPlayhead(
        clips: List<MediaClip>,
        playheadMs: Long
    ): Pair<List<MediaClip>, String?>? {
        if (clips.isEmpty()) return null
        val info = findClipAtTimelinePosition(clips, playheadMs) ?: return null
        val clip = info.clip
        val speed = getSafeSpeed(clip)
        val trimStart = getEffectiveTrimStart(clip)
        val trimEnd = getEffectiveTrimEnd(clip)

        // Convert timeline offset to source duration
        val relativeTimelineOffset = info.relativeTimelineOffsetMs
        val sourceOffset = (relativeTimelineOffset * speed).toLong()
        val splitSourceTimeMs = trimStart + sourceOffset

        // Edge tolerance verification: cannot split too close to start or end
        if (splitSourceTimeMs <= trimStart + SPLIT_EDGE_TOLERANCE_MS) {
            return null // Too close to beginning of clip
        }
        if (splitSourceTimeMs >= trimEnd - SPLIT_EDGE_TOLERANCE_MS) {
            return null // Too close to end of clip
        }

        // Part 1 keeps the same ID (or part 2 gets a new UUID)
        val part1DurationSource = splitSourceTimeMs - trimStart
        val part1TimelineDuration = (part1DurationSource.toFloat() / speed).toLong()

        val part2DurationSource = trimEnd - splitSourceTimeMs
        val part2TimelineDuration = (part2DurationSource.toFloat() / speed).toLong()

        if (part1DurationSource <= 0L || part2DurationSource <= 0L) {
            return null
        }

        val clipPart1 = clip.copy(
            trimStartMs = trimStart,
            trimEndMs = splitSourceTimeMs,
            durationMs = part1TimelineDuration,
            transition = null // Split cut point is clean and seamless
        )

        val newClipId = "clip_split_" + UUID.randomUUID().toString().take(6)
        // Part 2 inherits transition to the next clip, with duration safely clamped
        val safeTransitionDuration = if (clip.transition != null) {
            clip.transitionDurationMs.coerceAtMost((part2TimelineDuration / 2).coerceAtLeast(100L))
        } else {
            clip.transitionDurationMs
        }

        // Part 2 herda integralmente todos os atributos de cor, filtros, proporções de corte e transformações espaciais do clipe original
        val clipPart2 = clip.copy(
            id = newClipId,
            trimStartMs = splitSourceTimeMs,
            trimEndMs = trimEnd,
            durationMs = part2TimelineDuration,
            title = "${clip.title} (2)",
            speed = clip.speed,
            volume = clip.volume,
            brightness = clip.brightness,
            contrast = clip.contrast,
            saturation = clip.saturation,
            filter = clip.filter,
            cropRatio = clip.cropRatio,
            rotation = clip.rotation,
            scale = clip.scale,
            flipHorizontal = clip.flipHorizontal,
            flipVertical = clip.flipVertical,
            opacity = clip.opacity,
            positionX = clip.positionX,
            positionY = clip.positionY,
            isReverse = clip.isReverse,
            isFrozen = clip.isFrozen,
            isVisible = clip.isVisible,
            isMuted = clip.isMuted,
            isLocked = clip.isLocked,
            transition = clip.transition,
            transitionDurationMs = safeTransitionDuration
        )

        val updatedClips = mutableListOf<MediaClip>()
        for (i in clips.indices) {
            if (i == info.index) {
                updatedClips.add(clipPart1)
                updatedClips.add(clipPart2)
            } else {
                updatedClips.add(clips[i])
            }
        }

        return Pair(updatedClips, newClipId)
    }

    fun duplicateClip(
        clips: List<MediaClip>,
        clipId: String
    ): Pair<List<MediaClip>, String>? {
        val index = clips.indexOfFirst { it.id == clipId }
        if (index == -1) return null
        val original = clips[index]
        val newId = "clip_dup_" + UUID.randomUUID().toString().take(6)
        val duplicated = original.copy(
            id = newId,
            title = "${original.title} (Cópia)"
        )
        val updated = clips.toMutableList().apply {
            add(index + 1, duplicated)
        }
        return Pair(updated, newId)
    }

    fun removeClip(
        clips: List<MediaClip>,
        clipIndex: Int
    ): List<MediaClip> {
        if (clipIndex !in clips.indices) return clips
        val mutable = clips.toMutableList()
        mutable.removeAt(clipIndex)

        // Ensure the last clip has no hanging transition
        if (mutable.isNotEmpty()) {
            val lastIdx = mutable.lastIndex
            if (mutable[lastIdx].transition != null) {
                mutable[lastIdx] = mutable[lastIdx].copy(transition = null)
            }
        }

        // Ensure transition on preceding clip is clamped to safe duration
        if (clipIndex > 0 && clipIndex - 1 < mutable.size && clipIndex < mutable.size) {
            val prevClip = mutable[clipIndex - 1]
            if (prevClip.transition != null) {
                val nextClip = mutable[clipIndex]
                val prevDur = calculateClipTimelineDuration(prevClip)
                val nextDur = calculateClipTimelineDuration(nextClip)
                val maxAllowed = kotlin.math.min(prevDur, nextDur) / 2
                val clampedDur = prevClip.transitionDurationMs.coerceIn(100L, maxAllowed.coerceAtLeast(100L))
                mutable[clipIndex - 1] = prevClip.copy(transitionDurationMs = clampedDur)
            }
        }

        return mutable
    }

    fun applyTrim(
        clip: MediaClip,
        newTrimStartMs: Long,
        newTrimEndMs: Long
    ): MediaClip? {
        val maxDuration = if (clip.originalDurationMs > 0L) {
            clip.originalDurationMs
        } else {
            clip.durationMs.coerceAtLeast(1000L)
        }

        val clampedStart = newTrimStartMs.coerceIn(0L, maxDuration - MIN_CLIP_DURATION_MS)
        val clampedEnd = newTrimEndMs.coerceIn(clampedStart + MIN_CLIP_DURATION_MS, maxDuration)

        if (clampedEnd <= clampedStart) return null

        val sourceDuration = clampedEnd - clampedStart
        val speed = getSafeSpeed(clip)
        val newTimelineDuration = (sourceDuration.toFloat() / speed).toLong()

        return clip.copy(
            trimStartMs = clampedStart,
            trimEndMs = clampedEnd,
            durationMs = newTimelineDuration
        )
    }

    fun reorderClips(
        clips: List<MediaClip>,
        fromIndex: Int,
        toIndex: Int
    ): List<MediaClip> {
        if (fromIndex !in clips.indices || toIndex !in clips.indices || fromIndex == toIndex) {
            return clips
        }
        val mutable = clips.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)

        // Ensure the last clip has no hanging transition
        if (mutable.isNotEmpty()) {
            val lastIdx = mutable.lastIndex
            if (mutable[lastIdx].transition != null) {
                mutable[lastIdx] = mutable[lastIdx].copy(transition = null)
            }
        }

        return mutable
    }

    fun timelineOffsetToSourcePosition(clip: MediaClip, timelineOffsetMs: Long): Long {
        val trimStart = getEffectiveTrimStart(clip)
        val trimEnd = getEffectiveTrimEnd(clip)
        val speed = getSafeSpeed(clip)
        val sourceOffset = (timelineOffsetMs.coerceAtLeast(0L) * speed).toLong()
        return (trimStart + sourceOffset).coerceIn(trimStart, trimEnd)
    }

    fun sourcePositionToTimelineOffset(clip: MediaClip, sourcePositionMs: Long): Long {
        val trimStart = getEffectiveTrimStart(clip)
        val trimEnd = getEffectiveTrimEnd(clip)
        val speed = getSafeSpeed(clip)
        val clampedSource = sourcePositionMs.coerceIn(trimStart, trimEnd)
        val sourceOffset = (clampedSource - trimStart).coerceAtLeast(0L)
        return (sourceOffset.toFloat() / speed).toLong()
    }

    fun getClipStartTimelineMs(clips: List<MediaClip>, clipIndex: Int): Long {
        if (clipIndex !in clips.indices) return 0L
        var accumulated = 0L
        for (i in 0 until clipIndex) {
            accumulated += calculateClipTimelineDuration(clips[i])
        }
        return accumulated
    }

    fun sourcePositionToTimelinePosition(clips: List<MediaClip>, clipIndex: Int, sourcePositionMs: Long): Long {
        if (clipIndex !in clips.indices) return 0L
        val clip = clips[clipIndex]
        val clipStart = getClipStartTimelineMs(clips, clipIndex)
        val offset = sourcePositionToTimelineOffset(clip, sourcePositionMs)
        return clipStart + offset
    }

    fun isClipFinished(clip: MediaClip, currentSourcePositionMs: Long): Boolean {
        return currentSourcePositionMs >= getEffectiveTrimEnd(clip)
    }

    fun getNextClipIndex(clips: List<MediaClip>, currentIndex: Int): Int? {
        if (currentIndex < 0) return null
        val next = currentIndex + 1
        return if (next in clips.indices) next else null
    }

    fun getEffectiveAudioTrimStart(track: com.example.model.AudioTrackItem): Long {
        return track.trimStartMs.coerceAtLeast(0L)
    }

    fun getEffectiveAudioTrimEnd(track: com.example.model.AudioTrackItem): Long {
        val maxDuration = track.durationMs.coerceAtLeast(100L)
        return if (track.trimEndMs > track.trimStartMs) {
            track.trimEndMs.coerceIn(track.trimStartMs + 100L, maxDuration)
        } else {
            maxDuration
        }
    }

    fun calculateAudioEffectiveDuration(track: com.example.model.AudioTrackItem): Long {
        val start = getEffectiveAudioTrimStart(track)
        val end = getEffectiveAudioTrimEnd(track)
        return (end - start).coerceAtLeast(0L)
    }

    fun getAudioTimelineEndMs(track: com.example.model.AudioTrackItem): Long {
        val start = track.timelineStartMs.coerceAtLeast(0L)
        return start + calculateAudioEffectiveDuration(track)
    }

    fun isAudioActiveAtTimelinePosition(track: com.example.model.AudioTrackItem, playheadMs: Long): Boolean {
        val start = track.timelineStartMs.coerceAtLeast(0L)
        val end = getAudioTimelineEndMs(track)
        return playheadMs in start until end
    }

    fun timelinePositionToAudioSourcePosition(track: com.example.model.AudioTrackItem, playheadMs: Long): Long? {
        if (!isAudioActiveAtTimelinePosition(track, playheadMs)) return null
        val offset = (playheadMs - track.timelineStartMs).coerceAtLeast(0L)
        val trimStart = getEffectiveAudioTrimStart(track)
        val trimEnd = getEffectiveAudioTrimEnd(track)
        return (trimStart + offset).coerceIn(trimStart, trimEnd)
    }

    fun calculateTotalProjectDuration(
        clips: List<MediaClip>,
        audios: List<com.example.model.AudioTrackItem> = emptyList(),
        texts: List<com.example.model.TextOverlayItem> = emptyList(),
        stickers: List<com.example.model.StickerItem> = emptyList(),
        vfx: List<com.example.model.VFXEffectItem> = emptyList()
    ): Long {
        val videoDuration = calculateProjectTimelineDuration(clips)
        val maxAudioEnd = audios.maxOfOrNull { getAudioTimelineEndMs(it) } ?: 0L
        val maxTextEnd = texts.maxOfOrNull { it.startTimeMs + it.durationMs } ?: 0L
        val maxStickerEnd = stickers.maxOfOrNull { it.startTimeMs + it.durationMs } ?: 0L
        val maxVfxEnd = vfx.maxOfOrNull { it.startTimeMs + it.durationMs } ?: 0L
        return maxOf(videoDuration, maxAudioEnd, maxTextEnd, maxStickerEnd, maxVfxEnd).coerceAtLeast(0L)
    }

    fun calculateTotalProjectDuration(project: com.example.model.ProjectItem): Long {
        return calculateTotalProjectDuration(
            clips = project.clips,
            audios = project.audios,
            texts = project.texts,
            stickers = project.stickers,
            vfx = project.activeVFX
        )
    }

    const val MIN_AUDIO_DURATION_MS = 200L

    fun applyAudioTrim(
        track: com.example.model.AudioTrackItem,
        newTrimStartMs: Long,
        newTrimEndMs: Long
    ): com.example.model.AudioTrackItem? {
        if (newTrimEndMs <= newTrimStartMs) return null

        val maxDuration = track.durationMs.coerceAtLeast(1000L)
        val clampedStart = newTrimStartMs.coerceIn(0L, maxDuration - MIN_AUDIO_DURATION_MS)
        val clampedEnd = newTrimEndMs.coerceIn(clampedStart + MIN_AUDIO_DURATION_MS, maxDuration)

        if (clampedEnd <= clampedStart) return null

        return track.copy(
            trimStartMs = clampedStart,
            trimEndMs = clampedEnd
        )
    }

    fun moveAudioTrackTimelineStart(
        track: com.example.model.AudioTrackItem,
        newTimelineStartMs: Long
    ): com.example.model.AudioTrackItem {
        return track.copy(timelineStartMs = newTimelineStartMs.coerceAtLeast(0L))
    }

    fun setAudioTrackVolume(
        track: com.example.model.AudioTrackItem,
        volume: Float
    ): com.example.model.AudioTrackItem {
        return track.copy(volume = volume.coerceIn(0f, 1f))
    }

    fun toggleAudioTrackMute(
        track: com.example.model.AudioTrackItem
    ): com.example.model.AudioTrackItem {
        return track.copy(isMuted = !track.isMuted)
    }

    fun splitAudioTrackAtPlayhead(
        tracks: List<com.example.model.AudioTrackItem>,
        trackId: String,
        playheadMs: Long
    ): Pair<List<com.example.model.AudioTrackItem>, String?> {
        val trackIndex = tracks.indexOfFirst { it.id == trackId }
        if (trackIndex == -1) return Pair(tracks, null)

        val track = tracks[trackIndex]
        val activeStart = track.timelineStartMs.coerceAtLeast(0L)
        val activeEnd = getAudioTimelineEndMs(track)

        if (playheadMs <= activeStart + MIN_AUDIO_DURATION_MS || playheadMs >= activeEnd - MIN_AUDIO_DURATION_MS) {
            return Pair(tracks, null)
        }

        val splitSourcePosition = timelinePositionToAudioSourcePosition(track, playheadMs) ?: return Pair(tracks, null)

        val newTrackId = "audio_" + java.util.UUID.randomUUID().toString().take(6)

        val leftTrack = track.copy(
            trimEndMs = splitSourcePosition
        )

        val rightTrack = track.copy(
            id = newTrackId,
            timelineStartMs = playheadMs,
            trimStartMs = splitSourcePosition
        )

        val updatedTracks = tracks.toMutableList().apply {
            set(trackIndex, leftTrack)
            add(trackIndex + 1, rightTrack)
        }

        return Pair(updatedTracks, newTrackId)
    }

    /**
     * Empacotamento ótimo em camadas (Lane Packing / Interval Scheduling)
     * Quantidade de camadas simultâneas = quantidade de elementos sobrepostos naquele intervalo.
     * Quando um elemento termina, a camada é reutilizada sem sobreposição.
     */
    fun <T> packIntoLanes(
        items: List<T>,
        getStartMs: (T) -> Long,
        getDurationMs: (T) -> Long
    ): List<List<T>> {
        if (items.isEmpty()) return emptyList()
        val sorted = items.sortedBy { getStartMs(it) }
        val lanes = mutableListOf<MutableList<T>>()
        val laneEndTimes = mutableListOf<Long>()

        for (item in sorted) {
            val start = getStartMs(item)
            val duration = getDurationMs(item).coerceAtLeast(100L)
            val end = start + duration
            var placed = false
            for (i in lanes.indices) {
                if (laneEndTimes[i] <= start) {
                    lanes[i].add(item)
                    laneEndTimes[i] = end
                    placed = true
                    break
                }
            }
            if (!placed) {
                lanes.add(mutableListOf(item))
                laneEndTimes.add(end)
            }
        }
        return lanes
    }

    fun splitStickerAtPlayhead(
        stickers: List<com.example.model.StickerItem>,
        stickerId: String,
        playheadMs: Long
    ): Pair<List<com.example.model.StickerItem>, String?> {
        val index = stickers.indexOfFirst { it.id == stickerId }
        if (index == -1) return Pair(stickers, null)
        val stk = stickers[index]
        val start = stk.startTimeMs
        val end = start + stk.durationMs
        if (playheadMs <= start + 150L || playheadMs >= end - 150L) {
            return Pair(stickers, null)
        }
        val part1Duration = playheadMs - start
        val part2Duration = end - playheadMs
        val newId = "stk_split_" + java.util.UUID.randomUUID().toString().take(6)
        val part1 = stk.copy(durationMs = part1Duration)
        val part2 = stk.copy(id = newId, startTimeMs = playheadMs, durationMs = part2Duration)
        val updated = stickers.toMutableList().apply {
            set(index, part1)
            add(index + 1, part2)
        }
        return Pair(updated, newId)
    }

    fun splitTextOverlayAtPlayhead(
        texts: List<com.example.model.TextOverlayItem>,
        textId: String,
        playheadMs: Long
    ): Pair<List<com.example.model.TextOverlayItem>, String?> {
        val index = texts.indexOfFirst { it.id == textId }
        if (index == -1) return Pair(texts, null)
        val txt = texts[index]
        val start = txt.startTimeMs
        val end = start + txt.durationMs
        if (playheadMs <= start + 150L || playheadMs >= end - 150L) {
            return Pair(texts, null)
        }
        val part1Duration = playheadMs - start
        val part2Duration = end - playheadMs
        val newId = "txt_split_" + java.util.UUID.randomUUID().toString().take(6)
        val part1 = txt.copy(durationMs = part1Duration)
        val part2 = txt.copy(id = newId, startTimeMs = playheadMs, durationMs = part2Duration)
        val updated = texts.toMutableList().apply {
            set(index, part1)
            add(index + 1, part2)
        }
        return Pair(updated, newId)
    }

    fun duplicateSticker(
        stickers: List<com.example.model.StickerItem>,
        stickerId: String
    ): Pair<List<com.example.model.StickerItem>, String?> {
        val index = stickers.indexOfFirst { it.id == stickerId }
        if (index == -1) return Pair(stickers, null)
        val original = stickers[index]
        val newId = "stk_dup_" + java.util.UUID.randomUUID().toString().take(6)
        val dup = original.copy(
            id = newId,
            posX = (original.posX + 0.04f).coerceIn(0.05f, 0.95f),
            posY = (original.posY + 0.04f).coerceIn(0.05f, 0.95f),
            name = "${original.name} (Cópia)"
        )
        val updated = stickers.toMutableList().apply {
            add(index + 1, dup)
        }
        return Pair(updated, newId)
    }

    fun duplicateTextOverlay(
        texts: List<com.example.model.TextOverlayItem>,
        textId: String
    ): Pair<List<com.example.model.TextOverlayItem>, String?> {
        val index = texts.indexOfFirst { it.id == textId }
        if (index == -1) return Pair(texts, null)
        val original = texts[index]
        val newId = "txt_dup_" + java.util.UUID.randomUUID().toString().take(6)
        val dup = original.copy(
            id = newId,
            posX = (original.posX + 0.04f).coerceIn(0.05f, 0.95f),
            posY = (original.posY + 0.04f).coerceIn(0.05f, 0.95f)
        )
        val updated = texts.toMutableList().apply {
            add(index + 1, dup)
        }
        return Pair(updated, newId)
    }

    fun duplicateAudioTrack(
        tracks: List<com.example.model.AudioTrackItem>,
        trackId: String
    ): Pair<List<com.example.model.AudioTrackItem>, String?> {
        val index = tracks.indexOfFirst { it.id == trackId }
        if (index == -1) return Pair(tracks, null)
        val original = tracks[index]
        val newId = "audio_dup_" + java.util.UUID.randomUUID().toString().take(6)
        val dup = original.copy(
            id = newId,
            timelineStartMs = getAudioTimelineEndMs(original),
            name = "${original.name} (Cópia)"
        )
        val updated = tracks.toMutableList().apply {
            add(index + 1, dup)
        }
        return Pair(updated, newId)
    }

    data class PackedLaneItem<T>(
        val item: T,
        val startMs: Long,
        val durationMs: Long,
        val laneIndex: Int
    )

    /**
     * Empacotamento dinâmico em camadas/trilhas (Greedy Interval Coloring).
     * Quantidade de elementos simultâneos = quantidade de camadas necessárias naquele intervalo.
     * Quando um elemento termina, a camada é reutilizada sem sobreposição.
     */
    fun <T> packItemsIntoLanes(
        items: List<T>,
        getStartMs: (T) -> Long,
        getDurationMs: (T) -> Long
    ): List<PackedLaneItem<T>> {
        if (items.isEmpty()) return emptyList()
        val sorted = items.sortedBy { getStartMs(it) }
        val laneEndTimes = mutableListOf<Long>()
        val result = mutableListOf<PackedLaneItem<T>>()

        for (item in sorted) {
            val start = getStartMs(item).coerceAtLeast(0L)
            val duration = getDurationMs(item).coerceAtLeast(100L)
            val end = start + duration

            var assignedLane = -1
            for (i in laneEndTimes.indices) {
                if (laneEndTimes[i] <= start) {
                    assignedLane = i
                    laneEndTimes[i] = end
                    break
                }
            }
            if (assignedLane == -1) {
                assignedLane = laneEndTimes.size
                laneEndTimes.add(end)
            }

            result.add(PackedLaneItem(item, start, duration, assignedLane))
        }
        return result
    }

    /**
     * Interpolação Linear de Keyframes de Transformação espacial (CapCut Style).
     * Se não houver keyframes, retorna os valores base do clipe.
     */
    fun interpolateKeyframeTransform(
        clip: MediaClip,
        currentTimelineMs: Long,
        clipStartMs: Long
    ): com.example.model.KeyframePoint {
        val keyframes = clip.keyframes.sortedBy { it.timeMs }
        if (keyframes.isEmpty()) {
            return com.example.model.KeyframePoint(
                timeMs = 0L,
                scale = clip.scale,
                rotation = clip.rotation,
                positionX = clip.positionX,
                positionY = clip.positionY,
                opacity = clip.opacity
            )
        }

        val relativeTimeMs = (currentTimelineMs - clipStartMs).coerceAtLeast(0L)

        // Antes do primeiro keyframe
        if (relativeTimeMs <= keyframes.first().timeMs) {
            return keyframes.first()
        }
        // Depois do último keyframe
        if (relativeTimeMs >= keyframes.last().timeMs) {
            return keyframes.last()
        }

        // Encontra o segmento [k1, k2]
        for (i in 0 until keyframes.size - 1) {
            val k1 = keyframes[i]
            val k2 = keyframes[i + 1]
            if (relativeTimeMs in k1.timeMs..k2.timeMs) {
                val span = (k2.timeMs - k1.timeMs).toFloat().coerceAtLeast(1f)
                val fraction = (relativeTimeMs - k1.timeMs) / span
                return com.example.model.KeyframePoint(
                    timeMs = relativeTimeMs,
                    scale = k1.scale + (k2.scale - k1.scale) * fraction,
                    rotation = k1.rotation + (k2.rotation - k1.rotation) * fraction,
                    positionX = k1.positionX + (k2.positionX - k1.positionX) * fraction,
                    positionY = k1.positionY + (k2.positionY - k1.positionY) * fraction,
                    opacity = k1.opacity + (k2.opacity - k1.opacity) * fraction
                )
            }
        }

        return keyframes.last()
    }

    /**
     * Congela o quadro (Freeze Frame / Parar Vídeo estilo CapCut).
     * Divide o clipe no playhead e insere um segmento de 3 segundos congelado como Foto.
     */
    fun insertFreezeFrame(
        clips: List<MediaClip>,
        playheadTimelineMs: Long,
        freezeDurationMs: Long = 3000L
    ): Pair<List<MediaClip>, String>? {
        val info = findClipAtTimelinePosition(clips, playheadTimelineMs) ?: return null
        val clip = info.clip
        val clipIndex = info.index

        val freezeClipId = "clip_freeze_" + UUID.randomUUID().toString().take(6)
        val freezeClip = clip.copy(
            id = freezeClipId,
            title = "${clip.title} (Congelado)",
            type = com.example.model.MediaType.PHOTO,
            isFrozen = true,
            durationMs = freezeDurationMs,
            localPath = if (clip.thumbnailPath.isNotBlank()) clip.thumbnailPath else clip.localPath,
            thumbnailPath = clip.thumbnailPath,
            speed = 1.0f,
            transition = null
        )

        // Se o corte for no meio do clipe, divide em Parte 1, Freeze e Parte 2
        val (splitClips, _) = splitClipAtPlayhead(clips, playheadTimelineMs) ?: return null
        val mutable = splitClips.toMutableList()
        // Insere o clipe congelado entre a parte 1 e parte 2 (no índice clipIndex + 1)
        val insertIndex = (clipIndex + 1).coerceIn(0, mutable.size)
        mutable.add(insertIndex, freezeClip)

        return Pair(mutable, freezeClipId)
    }
}
