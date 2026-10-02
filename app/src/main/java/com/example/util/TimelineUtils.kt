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

        val clipPart2 = clip.copy(
            id = newClipId,
            trimStartMs = splitSourceTimeMs,
            trimEndMs = trimEnd,
            durationMs = part2TimelineDuration,
            title = "${clip.title} (2)",
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
        stickers: List<com.example.model.StickerItem> = emptyList()
    ): Long {
        val videoDuration = calculateProjectTimelineDuration(clips)
        val maxAudioEnd = audios.maxOfOrNull { getAudioTimelineEndMs(it) } ?: 0L
        val maxTextEnd = texts.maxOfOrNull { it.startTimeMs + it.durationMs } ?: 0L
        val maxStickerEnd = stickers.maxOfOrNull { it.startTimeMs + it.durationMs } ?: 0L
        return maxOf(videoDuration, maxAudioEnd, maxTextEnd, maxStickerEnd).coerceAtLeast(0L)
    }

    fun calculateTotalProjectDuration(project: com.example.model.ProjectItem): Long {
        return calculateTotalProjectDuration(
            clips = project.clips,
            audios = project.audios,
            texts = project.texts,
            stickers = project.stickers
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
}
