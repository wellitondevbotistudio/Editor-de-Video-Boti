package com.example

import com.example.model.MediaClip
import com.example.model.MediaType
import com.example.util.TimelineUtils
import org.junit.Assert.*
import org.junit.Test

class PlayerPlaybackSyncTest {

    private fun createTestClip(
        id: String = "clip1",
        originalDurationMs: Long = 10000L,
        trimStartMs: Long = 0L,
        trimEndMs: Long = 10000L,
        speed: Float = 1.0f
    ): MediaClip {
        val srcDuration = (trimEndMs - trimStartMs).coerceAtLeast(0L)
        val timelineDuration = (srcDuration.toFloat() / speed).toLong()
        return MediaClip(
            id = id,
            title = "Test Clip",
            uri = "/data/projects/p1/media/$id.mp4",
            localPath = "/data/projects/p1/media/$id.mp4",
            durationMs = timelineDuration,
            originalDurationMs = originalDurationMs,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            speed = speed
        )
    }

    @Test
    fun test01_timelineToMediaSourceConversion() {
        // 1. Conversão Timeline -> posição da mídia
        val clip = createTestClip(originalDurationMs = 10000L, trimStartMs = 2000L, trimEndMs = 8000L, speed = 1.0f)
        val timelineOffset = 2500L
        val sourcePos = TimelineUtils.timelineOffsetToSourcePosition(clip, timelineOffset)
        // trimStart (2000) + offset (2500) = 4500ms
        assertEquals(4500L, sourcePos)
    }

    @Test
    fun test02_mediaSourceToTimelineConversion() {
        // 2. Conversão posição da mídia -> Timeline
        val clip = createTestClip(originalDurationMs = 10000L, trimStartMs = 2000L, trimEndMs = 8000L, speed = 1.0f)
        val sourcePos = 4500L
        val timelineOffset = TimelineUtils.sourcePositionToTimelineOffset(clip, sourcePos)
        // 4500 - 2000 = 2500ms
        assertEquals(2500L, timelineOffset)

        val clips = listOf(createTestClip("c0", originalDurationMs = 3000L), clip)
        val globalTimelinePos = TimelineUtils.sourcePositionToTimelinePosition(clips, clipIndex = 1, sourcePositionMs = sourcePos)
        // clip 0 duration (3000) + offset (2500) = 5500ms
        assertEquals(5500L, globalTimelinePos)
    }

    @Test
    fun test03_seekWithTrim() {
        // 3. Seek com Trim
        val clip = createTestClip(originalDurationMs = 15000L, trimStartMs = 3000L, trimEndMs = 11000L)
        val timelineOffset = 1500L
        val seekSourcePos = TimelineUtils.timelineOffsetToSourcePosition(clip, timelineOffset)
        assertEquals(4500L, seekSourcePos)
    }

    @Test
    fun test04_seekWithSpeed() {
        // 4. Seek com Speed
        val clip = createTestClip(originalDurationMs = 10000L, trimStartMs = 0L, trimEndMs = 10000L, speed = 2.0f)
        val timelineOffset = 2000L
        val seekSourcePos = TimelineUtils.timelineOffsetToSourcePosition(clip, timelineOffset)
        // offset 2000ms na timeline * speed 2x = 4000ms na fonte
        assertEquals(4000L, seekSourcePos)
    }

    @Test
    fun test05_seekWithTrimAndSpeed() {
        // 5. Seek com Trim + Speed
        val clip = createTestClip(originalDurationMs = 20000L, trimStartMs = 4000L, trimEndMs = 16000L, speed = 2.0f)
        val timelineOffset = 3000L
        val seekSourcePos = TimelineUtils.timelineOffsetToSourcePosition(clip, timelineOffset)
        // trimStart (4000) + 3000 * 2.0 = 10000ms
        assertEquals(10000L, seekSourcePos)
    }

    @Test
    fun test06_identifyClipAtPlayhead() {
        // 6. Identificação do clip no playhead
        val c1 = createTestClip("cA", originalDurationMs = 4000L) // 0..4000
        val c2 = createTestClip("cB", originalDurationMs = 6000L) // 4000..10000
        val c3 = createTestClip("cC", originalDurationMs = 5000L) // 10000..15000
        val clips = listOf(c1, c2, c3)

        val info = TimelineUtils.findClipAtTimelinePosition(clips, 7500L)
        assertNotNull(info)
        assertEquals("cB", info?.clip?.id)
        assertEquals(1, info?.index)
        assertEquals(3500L, info?.relativeTimelineOffsetMs)
    }

    @Test
    fun test07_transitionClipAToB() {
        // 7. Transição A -> B
        val c1 = createTestClip("cA", originalDurationMs = 4000L)
        val c2 = createTestClip("cB", originalDurationMs = 6000L)
        val clips = listOf(c1, c2)

        // When clip 0 finishes:
        val finished = TimelineUtils.isClipFinished(c1, currentSourcePositionMs = 4000L)
        assertTrue(finished)

        val nextIndex = TimelineUtils.getNextClipIndex(clips, currentIndex = 0)
        assertEquals(1, nextIndex)

        val nextClipStart = TimelineUtils.getClipStartTimelineMs(clips, nextIndex!!)
        assertEquals(4000L, nextClipStart)
    }

    @Test
    fun test08_endOfLastClip() {
        // 8. Final do último clip
        val c1 = createTestClip("cA", originalDurationMs = 4000L)
        val c2 = createTestClip("cB", originalDurationMs = 6000L)
        val clips = listOf(c1, c2)

        val nextIndex = TimelineUtils.getNextClipIndex(clips, currentIndex = 1)
        assertNull(nextIndex) // No more clips -> project end reached

        val total = TimelineUtils.calculateProjectTimelineDuration(clips)
        assertEquals(10000L, total)
    }

    @Test
    fun test09_emptyProjectHandledSafely() {
        // 9. Projeto vazio
        val clips = emptyList<MediaClip>()
        val total = TimelineUtils.calculateProjectTimelineDuration(clips)
        assertEquals(0L, total)

        val info = TimelineUtils.findClipAtTimelinePosition(clips, 500L)
        assertNull(info)

        val next = TimelineUtils.getNextClipIndex(clips, 0)
        assertNull(next)
    }

    @Test
    fun test10_nonExistentClipHandledSafely() {
        // 10. Clip inexistente (índice fora dos limites)
        val clips = listOf(createTestClip("cA"))
        val start = TimelineUtils.getClipStartTimelineMs(clips, clipIndex = 99)
        assertEquals(0L, start)

        val pos = TimelineUtils.sourcePositionToTimelinePosition(clips, clipIndex = 99, sourcePositionMs = 2000L)
        assertEquals(0L, pos)
    }

    @Test
    fun test11_completeTrimPlaybackBoundary() {
        // 11. Trim completo
        val clip = createTestClip(originalDurationMs = 20000L, trimStartMs = 5000L, trimEndMs = 12000L)
        val trimStart = TimelineUtils.getEffectiveTrimStart(clip)
        val trimEnd = TimelineUtils.getEffectiveTrimEnd(clip)

        assertEquals(5000L, trimStart)
        assertEquals(12000L, trimEnd)

        // At 11999ms, clip is not finished
        assertFalse(TimelineUtils.isClipFinished(clip, 11999L))
        // At 12000ms, clip is finished
        assertTrue(TimelineUtils.isClipFinished(clip, 12000L))
    }

    @Test
    fun test12_speed2xAltersEffectiveDuration() {
        // 12. Speed 2x
        val clip = createTestClip(originalDurationMs = 10000L, speed = 2.0f)
        val timelineDuration = TimelineUtils.calculateClipTimelineDuration(clip)
        assertEquals(5000L, timelineDuration)
    }

    @Test
    fun test13_speedHalfAltersEffectiveDuration() {
        // 13. Speed 0.5x
        val clip = createTestClip(originalDurationMs = 10000L, speed = 0.5f)
        val timelineDuration = TimelineUtils.calculateClipTimelineDuration(clip)
        assertEquals(20000L, timelineDuration)
    }

    @Test
    fun test14_playheadAtBoundaryBetweenClips() {
        // 14. Playhead no limite entre clips
        val c1 = createTestClip("cA", originalDurationMs = 5000L)
        val c2 = createTestClip("cB", originalDurationMs = 5000L)
        val clips = listOf(c1, c2)

        val info = TimelineUtils.findClipAtTimelinePosition(clips, 5000L)
        assertNotNull(info)
        assertEquals("cB", info?.clip?.id)
        assertEquals(1, info?.index)
        assertEquals(0L, info?.relativeTimelineOffsetMs)
    }

    @Test
    fun test15_playheadAtStartOfProject() {
        // 15. Playhead no início
        val c1 = createTestClip("cA", originalDurationMs = 5000L)
        val info = TimelineUtils.findClipAtTimelinePosition(listOf(c1), 0L)
        assertNotNull(info)
        assertEquals(0L, info?.timelineStartMs)
        assertEquals(0L, info?.relativeTimelineOffsetMs)
    }

    @Test
    fun test16_playheadAtEndOfProject() {
        // 16. Playhead no final
        val c1 = createTestClip("cA", originalDurationMs = 4000L)
        val c2 = createTestClip("cB", originalDurationMs = 6000L)
        val clips = listOf(c1, c2)

        val info = TimelineUtils.findClipAtTimelinePosition(clips, 10000L)
        assertNotNull(info)
        assertEquals("cB", info?.clip?.id)
        assertEquals(6000L, info?.relativeTimelineOffsetMs)
    }

    @Test
    fun test17_rewindCalculation() {
        val totalMs = 12000L
        val pos1 = 8000L
        val rewindTarget1 = (pos1 - 5000L).coerceAtLeast(0L)
        assertEquals(3000L, rewindTarget1)

        val pos2 = 2500L
        val rewindTarget2 = (pos2 - 5000L).coerceAtLeast(0L)
        assertEquals(0L, rewindTarget2)
    }

    @Test
    fun test18_forwardCalculation() {
        val totalMs = 10000L
        val pos1 = 2000L
        val fwdTarget1 = (pos1 + 5000L).coerceAtMost(totalMs)
        assertEquals(7000L, fwdTarget1)

        val pos2 = 8000L
        val fwdTarget2 = (pos2 + 5000L).coerceAtMost(totalMs)
        assertEquals(10000L, fwdTarget2)
    }

    @Test
    fun test19_previousClipNavigationRules() {
        val c1 = createTestClip("cA", originalDurationMs = 4000L) // 0..4000
        val c2 = createTestClip("cB", originalDurationMs = 6000L) // 4000..10000
        val clips = listOf(c1, c2)

        // At 6500ms (> 1s past start of clip B at 4000ms), jumps back to start of clip B (4000ms)
        val info1 = TimelineUtils.findClipAtTimelinePosition(clips, 6500L)
        assertNotNull(info1)
        val clipStart1 = info1!!.timelineStartMs
        val target1 = if (6500L - clipStart1 > 1000L) clipStart1 else 0L
        assertEquals(4000L, target1)

        // At 4500ms (<= 1s past start of clip B), jumps to previous clip (clip A at 0ms)
        val info2 = TimelineUtils.findClipAtTimelinePosition(clips, 4500L)
        assertNotNull(info2)
        val prevIndex = info2!!.index - 1
        val target2 = if (4500L - info2.timelineStartMs > 1000L) info2.timelineStartMs else {
            if (prevIndex >= 0) TimelineUtils.getClipStartTimelineMs(clips, prevIndex) else 0L
        }
        assertEquals(0L, target2)
    }

    @Test
    fun test20_nextClipNavigationRules() {
        val c1 = createTestClip("cA", originalDurationMs = 4000L)  // 0..4000
        val c2 = createTestClip("cB", originalDurationMs = 6000L)  // 4000..10000
        val c3 = createTestClip("cC", originalDurationMs = 5000L)  // 10000..15000
        val clips = listOf(c1, c2, c3)
        val total = TimelineUtils.calculateProjectTimelineDuration(clips)

        // From 1500ms (in clip A), next clip starts at 4000ms
        val info1 = TimelineUtils.findClipAtTimelinePosition(clips, 1500L)
        val nextStart1 = TimelineUtils.getClipStartTimelineMs(clips, info1!!.index + 1)
        assertEquals(4000L, nextStart1)

        // From 7000ms (in clip B), next clip starts at 10000ms
        val info2 = TimelineUtils.findClipAtTimelinePosition(clips, 7000L)
        val nextStart2 = TimelineUtils.getClipStartTimelineMs(clips, info2!!.index + 1)
        assertEquals(10000L, nextStart2)

        // From 12000ms (in last clip C), next jumps to total project duration (15000ms)
        val info3 = TimelineUtils.findClipAtTimelinePosition(clips, 12000L)
        val isLast = info3!!.index >= clips.lastIndex
        val target3 = if (isLast) total else TimelineUtils.getClipStartTimelineMs(clips, info3.index + 1)
        assertEquals(15000L, target3)
    }

    @Test
    fun test21_timelineInvertibilityWithSpeedAndTrim() {
        val clip = createTestClip(
            id = "c1",
            originalDurationMs = 20000L,
            trimStartMs = 4000L,
            trimEndMs = 16000L,
            speed = 2.0f
        )
        val clips = listOf(clip)

        // Clip source duration = 12000ms. Timeline duration = 6000ms
        assertEquals(6000L, TimelineUtils.calculateClipTimelineDuration(clip))

        // Playhead at 3000ms timeline:
        val info = TimelineUtils.findClipAtTimelinePosition(clips, 3000L)
        assertNotNull(info)
        // Source pos: 4000 + (3000 * 2.0) = 10000ms
        assertEquals(10000L, info!!.sourcePositionMs)

        // Inverting source position (10000ms) back to timeline position:
        val invertedTimelinePos = TimelineUtils.sourcePositionToTimelinePosition(clips, 0, 10000L)
        assertEquals(3000L, invertedTimelinePos)
    }
}
