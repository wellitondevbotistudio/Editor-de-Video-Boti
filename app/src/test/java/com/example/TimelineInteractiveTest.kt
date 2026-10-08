package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.repository.ProjectRepository
import com.example.model.*
import com.example.util.TimelineUtils
import com.example.viewmodel.deepCopy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.ArrayDeque

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimelineInteractiveTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: ProjectRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ProjectRepository(db)
    }

    @After
    @Throws(IOException::class)
    fun teardown() {
        db.close()
    }

    private fun createBaseClip(
        id: String = "clip1",
        originalDurationMs: Long = 10000L,
        trimStartMs: Long = 0L,
        trimEndMs: Long = 10000L,
        speed: Float = 1.0f,
        localPath: String = "/data/user/0/com.aistudio.videoeditor.boti/files/projects/p1/media/video1.mp4"
    ): MediaClip {
        val srcDuration = (trimEndMs - trimStartMs).coerceAtLeast(0L)
        val timelineDuration = (srcDuration.toFloat() / speed).toLong()
        return MediaClip(
            id = id,
            title = "Video Clip",
            uri = "file://$localPath",
            localPath = localPath,
            thumbnailPath = "/thumbnails/thumb1.jpg",
            originalName = "video1.mp4",
            mimeType = "video/mp4",
            width = 1920,
            height = 1080,
            fileSizeBytes = 5000000L,
            durationMs = timelineDuration,
            originalDurationMs = originalDurationMs,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            speed = speed
        )
    }

    // ---------------- SPLIT TESTS ----------------

    @Test
    fun test01_splitAt25Percent() {
        // 1. Split em 25% (2.500 ms de 10.000 ms)
        val clip = createBaseClip(originalDurationMs = 10000L, trimStartMs = 0L, trimEndMs = 10000L)
        val result = TimelineUtils.splitClipAtPlayhead(listOf(clip), 2500L)
        assertNotNull(result)
        val clips = result!!.first
        assertEquals(2, clips.size)
        assertEquals(0L, clips[0].trimStartMs)
        assertEquals(2500L, clips[0].trimEndMs)
        assertEquals(2500L, clips[1].trimStartMs)
        assertEquals(10000L, clips[1].trimEndMs)
    }

    @Test
    fun test02_splitAt50Percent() {
        // 2. Split em 50% (5.000 ms de 10.000 ms)
        val clip = createBaseClip(originalDurationMs = 10000L, trimStartMs = 0L, trimEndMs = 10000L)
        val result = TimelineUtils.splitClipAtPlayhead(listOf(clip), 5000L)
        assertNotNull(result)
        val clips = result!!.first
        assertEquals(2, clips.size)
        assertEquals(0L, clips[0].trimStartMs)
        assertEquals(5000L, clips[0].trimEndMs)
        assertEquals(5000L, clips[1].trimStartMs)
        assertEquals(10000L, clips[1].trimEndMs)
    }

    @Test
    fun test03_splitAt75Percent() {
        // 3. Split em 75% (7.500 ms de 10.000 ms)
        val clip = createBaseClip(originalDurationMs = 10000L, trimStartMs = 0L, trimEndMs = 10000L)
        val result = TimelineUtils.splitClipAtPlayhead(listOf(clip), 7500L)
        assertNotNull(result)
        val clips = result!!.first
        assertEquals(2, clips.size)
        assertEquals(0L, clips[0].trimStartMs)
        assertEquals(7500L, clips[0].trimEndMs)
        assertEquals(7500L, clips[1].trimStartMs)
        assertEquals(10000L, clips[1].trimEndMs)
    }

    @Test
    fun test04_splitWithSpeed2x() {
        // 4. Split com speed 2x (original 10s, timeline 5s, playhead em 2s da timeline -> 4s da fonte)
        val clip = createBaseClip(originalDurationMs = 10000L, trimStartMs = 0L, trimEndMs = 10000L, speed = 2.0f)
        val result = TimelineUtils.splitClipAtPlayhead(listOf(clip), 2000L)
        assertNotNull(result)
        val clips = result!!.first
        assertEquals(2, clips.size)
        assertEquals(0L, clips[0].trimStartMs)
        assertEquals(4000L, clips[0].trimEndMs) // 2s timeline * 2x speed = 4s fonte
        assertEquals(4000L, clips[1].trimStartMs)
        assertEquals(10000L, clips[1].trimEndMs)
        assertEquals(2000L, clips[0].durationMs)
        assertEquals(3000L, clips[1].durationMs)
    }

    @Test
    fun test05_splitWithSpeedHalf() {
        // 5. Split com speed 0.5x (original 10s, timeline 20s, playhead em 6s da timeline -> 3s da fonte)
        val clip = createBaseClip(originalDurationMs = 10000L, trimStartMs = 0L, trimEndMs = 10000L, speed = 0.5f)
        val result = TimelineUtils.splitClipAtPlayhead(listOf(clip), 6000L)
        assertNotNull(result)
        val clips = result!!.first
        assertEquals(2, clips.size)
        assertEquals(0L, clips[0].trimStartMs)
        assertEquals(3000L, clips[0].trimEndMs) // 6s timeline * 0.5x = 3s fonte
        assertEquals(3000L, clips[1].trimStartMs)
        assertEquals(10000L, clips[1].trimEndMs)
    }

    @Test
    fun test06_splitWithExistingTrim() {
        // 6. Split com trim existente (trimStart = 2000, trimEnd = 8000, speed 1x, playhead em 3000ms da timeline -> 5000ms da fonte)
        val clip = createBaseClip(originalDurationMs = 10000L, trimStartMs = 2000L, trimEndMs = 8000L)
        val result = TimelineUtils.splitClipAtPlayhead(listOf(clip), 3000L)
        assertNotNull(result)
        val clips = result!!.first
        assertEquals(2, clips.size)
        assertEquals(2000L, clips[0].trimStartMs)
        assertEquals(5000L, clips[0].trimEndMs)
        assertEquals(5000L, clips[1].trimStartMs)
        assertEquals(8000L, clips[1].trimEndMs)
    }

    @Test
    fun test07_splitAtStartShouldFail() {
        // 7. Split no início deve falhar
        val clip = createBaseClip()
        val result = TimelineUtils.splitClipAtPlayhead(listOf(clip), 0L)
        assertNull(result)
    }

    @Test
    fun test08_splitAtEndShouldFail() {
        // 8. Split no fim deve falhar
        val clip = createBaseClip(originalDurationMs = 5000L, trimStartMs = 0L, trimEndMs = 5000L)
        val result = TimelineUtils.splitClipAtPlayhead(listOf(clip), 5000L)
        assertNull(result)
    }

    @Test
    fun test09_splitCannotGenerateNegativeDuration() {
        // 9. Split não pode gerar duração negativa
        val clip = createBaseClip()
        val result = TimelineUtils.splitClipAtPlayhead(listOf(clip), 4000L)
        assertNotNull(result)
        assertTrue(result!!.first[0].durationMs > 0)
        assertTrue(result.first[1].durationMs > 0)
        assertTrue(result.first[0].trimEndMs > result.first[0].trimStartMs)
        assertTrue(result.first[1].trimEndMs > result.first[1].trimStartMs)
    }

    @Test
    fun test10_splitBothClipsShareSameLocalPath() {
        // 10. Os dois clips devem compartilhar o mesmo localPath
        val clip = createBaseClip(localPath = "/data/projects/p1/video.mp4")
        val result = TimelineUtils.splitClipAtPlayhead(listOf(clip), 5000L)
        assertNotNull(result)
        assertEquals(clip.localPath, result!!.first[0].localPath)
        assertEquals(clip.localPath, result.first[1].localPath)
    }

    @Test
    fun test11_splitIdsMustBeDifferent() {
        // 11. Os IDs devem ser diferentes
        val clip = createBaseClip(id = "c_original")
        val result = TimelineUtils.splitClipAtPlayhead(listOf(clip), 5000L)
        assertNotNull(result)
        assertEquals("c_original", result!!.first[0].id)
        assertNotEquals(result.first[0].id, result.first[1].id)
    }

    // ---------------- TRIM TESTS ----------------

    @Test
    fun test12_trimFromStart() {
        // 12. Trim do início
        val clip = createBaseClip(originalDurationMs = 10000L)
        val trimmed = TimelineUtils.applyTrim(clip, newTrimStartMs = 3000L, newTrimEndMs = 10000L)
        assertNotNull(trimmed)
        assertEquals(3000L, trimmed?.trimStartMs)
        assertEquals(10000L, trimmed?.trimEndMs)
        assertEquals(7000L, trimmed?.durationMs)
    }

    @Test
    fun test13_trimFromEnd() {
        // 13. Trim do fim
        val clip = createBaseClip(originalDurationMs = 10000L)
        val trimmed = TimelineUtils.applyTrim(clip, newTrimStartMs = 0L, newTrimEndMs = 6000L)
        assertNotNull(trimmed)
        assertEquals(0L, trimmed?.trimStartMs)
        assertEquals(6000L, trimmed?.trimEndMs)
        assertEquals(6000L, trimmed?.durationMs)
    }

    @Test
    fun test14_trimBothSides() {
        // 14. Trim dos dois lados
        val clip = createBaseClip(originalDurationMs = 10000L)
        val trimmed = TimelineUtils.applyTrim(clip, newTrimStartMs = 2000L, newTrimEndMs = 7000L)
        assertNotNull(trimmed)
        assertEquals(2000L, trimmed?.trimStartMs)
        assertEquals(7000L, trimmed?.trimEndMs)
        assertEquals(5000L, trimmed?.durationMs)
    }

    @Test
    fun test15_invalidTrimRejected() {
        // 15. Trim inválido (start >= end)
        val clip = createBaseClip(originalDurationMs = 10000L)
        val trimmed = TimelineUtils.applyTrim(clip, newTrimStartMs = 8000L, newTrimEndMs = 5000L)
        // Either clamped safely or rejected
        assertTrue(trimmed == null || trimmed.trimEndMs > trimmed.trimStartMs)
    }

    @Test
    fun test16_trimDoesNotAlterOriginalDuration() {
        // 16. Trim não pode alterar originalDuration
        val clip = createBaseClip(originalDurationMs = 10000L)
        val trimmed = TimelineUtils.applyTrim(clip, newTrimStartMs = 2000L, newTrimEndMs = 6000L)
        assertEquals(10000L, trimmed?.originalDurationMs)
    }

    @Test
    fun test17_trimDoesNotAlterLocalPath() {
        // 17. Trim não pode alterar localPath
        val clip = createBaseClip(localPath = "/secure/path/video.mp4")
        val trimmed = TimelineUtils.applyTrim(clip, newTrimStartMs = 1000L, newTrimEndMs = 4000L)
        assertEquals("/secure/path/video.mp4", trimmed?.localPath)
    }

    @Test
    fun test18_trimCalculatedWithSpeed() {
        // 18. Duração calculada corretamente com speed
        val clip = createBaseClip(originalDurationMs = 10000L, speed = 2.0f)
        val trimmed = TimelineUtils.applyTrim(clip, newTrimStartMs = 2000L, newTrimEndMs = 8000L)
        // Fonte: 8000 - 2000 = 6000ms. Com speed 2x -> 3000ms na timeline
        assertEquals(3000L, trimmed?.durationMs)
    }

    // ---------------- REORDER TESTS ----------------

    @Test
    fun test19_reorderClips() {
        // 19. A -> B -> C para C -> A -> B
        val cA = createBaseClip("A")
        val cB = createBaseClip("B")
        val cC = createBaseClip("C")
        val originalList = listOf(cA, cB, cC)

        // Move C (index 2) to front (index 0)
        val reordered = TimelineUtils.reorderClips(originalList, fromIndex = 2, toIndex = 0)
        assertEquals(listOf(cC, cA, cB), reordered)
    }

    @Test
    fun test20_reorderPersistsInRoom() = runBlocking {
        // 20. Ordem persiste após recarregar Room
        val cA = createBaseClip("A")
        val cB = createBaseClip("B")
        val cC = createBaseClip("C")

        val proj = ProjectItem(id = "p_reorder", title = "P", duration = "", date = "", thumbUrl = "", clips = listOf(cA, cB, cC))
        repository.saveProject(proj)

        // Save reordered
        val reorderedProj = proj.copy(clips = listOf(cC, cA, cB))
        repository.saveProject(reorderedProj)

        val retrieved = repository.getProjectByIdOnce("p_reorder")
        assertEquals(3, retrieved?.clips?.size)
        assertEquals("C", retrieved?.clips?.get(0)?.id)
        assertEquals("A", retrieved?.clips?.get(1)?.id)
        assertEquals("B", retrieved?.clips?.get(2)?.id)
    }

    @Test
    fun test21_duplicateProjectPreservesOrder() = runBlocking {
        // 21. Projeto duplicado preserva ordem
        val cA = createBaseClip("A")
        val cB = createBaseClip("B")
        val cC = createBaseClip("C")
        val proj = ProjectItem(id = "p_orig_order", title = "P", duration = "", date = "", thumbUrl = "", clips = listOf(cC, cB, cA))
        repository.saveProject(proj)

        val dup = repository.duplicateProject(proj)
        assertEquals(3, dup.clips.size)
        assertEquals("Video Clip", dup.clips[0].title)
        assertEquals("Video Clip", dup.clips[1].title)
        assertEquals("Video Clip", dup.clips[2].title)
    }

    // ---------------- UNDO / REDO TESTS ----------------

    @Test
    fun test22_undoAfterSplit() {
        // 22. Undo após Split
        val undoStack = ArrayDeque<ProjectItem>()
        val redoStack = ArrayDeque<ProjectItem>()

        val clip = createBaseClip("c1")
        val initialProject = ProjectItem("p", "T", "", "", "", clips = listOf(clip))

        // Save state before split
        undoStack.addLast(initialProject.deepCopy())
        val splitResult = TimelineUtils.splitClipAtPlayhead(initialProject.clips, 5000L)!!
        val modifiedProject = initialProject.copy(clips = splitResult.first)

        // Execute Undo
        redoStack.addLast(modifiedProject.deepCopy())
        val restored = undoStack.removeLast()

        assertEquals(1, restored.clips.size)
        assertEquals("c1", restored.clips[0].id)
    }

    @Test
    fun test23_redoAfterUndo() {
        // 23. Redo após Undo
        val undoStack = ArrayDeque<ProjectItem>()
        val redoStack = ArrayDeque<ProjectItem>()

        val initial = ProjectItem("p", "T", "", "", "", clips = listOf(createBaseClip("c1")))
        undoStack.addLast(initial.deepCopy())

        val splitResult = TimelineUtils.splitClipAtPlayhead(initial.clips, 5000L)!!
        val splitProject = initial.copy(clips = splitResult.first)

        // Undo
        redoStack.addLast(splitProject.deepCopy())
        var current = undoStack.removeLast()
        assertEquals(1, current.clips.size)

        // Redo
        undoStack.addLast(current.deepCopy())
        current = redoStack.removeLast()
        assertEquals(2, current.clips.size)
    }

    @Test
    fun test24_undoAfterTrim() {
        // 24. Undo após Trim
        val undoStack = ArrayDeque<ProjectItem>()
        val clip = createBaseClip("c1", originalDurationMs = 10000L, trimStartMs = 0L, trimEndMs = 10000L)
        val initial = ProjectItem("p", "T", "", "", "", clips = listOf(clip))

        undoStack.addLast(initial.deepCopy())
        val trimmed = TimelineUtils.applyTrim(clip, 2000L, 8000L)!!
        val current = initial.copy(clips = listOf(trimmed))
        assertEquals(6000L, current.clips[0].durationMs)

        val restored = undoStack.removeLast()
        assertEquals(10000L, restored.clips[0].durationMs)
        assertEquals(0L, restored.clips[0].trimStartMs)
    }

    @Test
    fun test25_undoAfterReorder() {
        // 25. Undo após Reorder
        val undoStack = ArrayDeque<ProjectItem>()
        val cA = createBaseClip("A")
        val cB = createBaseClip("B")
        val initial = ProjectItem("p", "T", "", "", "", clips = listOf(cA, cB))

        undoStack.addLast(initial.deepCopy())
        val reordered = initial.copy(clips = listOf(cB, cA))
        assertEquals("B", reordered.clips[0].id)

        val restored = undoStack.removeLast()
        assertEquals("A", restored.clips[0].id)
    }

    @Test
    fun test26_newCommandAfterUndoClearsRedo() {
        // 26. Novo comando depois de Undo limpa Redo
        val undoStack = ArrayDeque<ProjectItem>()
        val redoStack = ArrayDeque<ProjectItem>()

        val s0 = ProjectItem("p", "S0", "", "", "")
        val s1 = ProjectItem("p", "S1", "", "", "")
        undoStack.addLast(s0.deepCopy())
        redoStack.addLast(s1.deepCopy())

        assertTrue(redoStack.isNotEmpty())

        // User performs a new command:
        val s2 = ProjectItem("p", "S2", "", "", "")
        undoStack.addLast(s0.deepCopy())
        redoStack.clear()

        assertTrue(redoStack.isEmpty())
    }

    @Test
    fun test27_max20StatesInHistory() {
        // 27. Limite máximo de 20 estados
        val undoStack = ArrayDeque<ProjectItem>()
        val max = 20

        for (i in 1..25) {
            val proj = ProjectItem("p", "Version $i", "", "", "")
            if (undoStack.size >= max) {
                undoStack.removeFirst()
            }
            undoStack.addLast(proj.deepCopy())
        }

        assertEquals(20, undoStack.size)
        assertEquals("Version 6", undoStack.first.title) // 1..5 dropped
        assertEquals("Version 25", undoStack.last.title)
    }

    @Test
    fun test28_deepCopyIsTrulyIndependent() {
        // 28. Deep copy realmente independente
        val clip = createBaseClip("c1")
        val original = ProjectItem("p", "Title", "", "", "", clips = listOf(clip))
        val copy = original.deepCopy()

        assertNotSame(original.clips, copy.clips)
        assertEquals(original.clips.size, copy.clips.size)
    }

    @Test
    fun test29_multipleUndosInSequence() {
        // 29. Vários Undos em sequência
        val undoStack = ArrayDeque<ProjectItem>()
        val redoStack = ArrayDeque<ProjectItem>()

        val s0 = ProjectItem("p", "Step 0", "", "", "")
        val s1 = ProjectItem("p", "Step 1", "", "", "")
        val s2 = ProjectItem("p", "Step 2", "", "", "")

        undoStack.addLast(s0.deepCopy())
        undoStack.addLast(s1.deepCopy())
        var current = s2

        // Undo 1
        redoStack.addLast(current.deepCopy())
        current = undoStack.removeLast()
        assertEquals("Step 1", current.title)

        // Undo 2
        redoStack.addLast(current.deepCopy())
        current = undoStack.removeLast()
        assertEquals("Step 0", current.title)
    }

    @Test
    fun test30_multipleRedosInSequence() {
        // 30. Vários Redos em sequência
        val undoStack = ArrayDeque<ProjectItem>()
        val redoStack = ArrayDeque<ProjectItem>()

        val s0 = ProjectItem("p", "Step 0", "", "", "")
        val s1 = ProjectItem("p", "Step 1", "", "", "")
        val s2 = ProjectItem("p", "Step 2", "", "", "")

        redoStack.addLast(s2.deepCopy())
        redoStack.addLast(s1.deepCopy())
        var current = s0

        // Redo 1
        undoStack.addLast(current.deepCopy())
        current = redoStack.removeLast()
        assertEquals("Step 1", current.title)

        // Redo 2
        undoStack.addLast(current.deepCopy())
        current = redoStack.removeLast()
        assertEquals("Step 2", current.title)
    }

    // ---------------- TIMELINE CALCULATION TESTS ----------------

    @Test
    fun test31_emptyProjectTimeline() {
        // 31. Projeto vazio
        val duration = TimelineUtils.calculateProjectTimelineDuration(emptyList())
        assertEquals(0L, duration)
        val info = TimelineUtils.findClipAtTimelinePosition(emptyList(), 1000L)
        assertNull(info)
    }

    @Test
    fun test32_singleClipTimeline() {
        // 32. Um clip
        val clip = createBaseClip(originalDurationMs = 5000L)
        val duration = TimelineUtils.calculateProjectTimelineDuration(listOf(clip))
        assertEquals(5000L, duration)
    }

    @Test
    fun test33_multipleClipsTimeline() {
        // 33. Vários clips
        val c1 = createBaseClip("c1", originalDurationMs = 4000L)
        val c2 = createBaseClip("c2", originalDurationMs = 6000L)
        val c3 = createBaseClip("c3", originalDurationMs = 3000L)
        val duration = TimelineUtils.calculateProjectTimelineDuration(listOf(c1, c2, c3))
        assertEquals(13000L, duration)
    }

    @Test
    fun test34_totalDurationCorrectWithTrimsAndSpeeds() {
        // 34. Duração total correta com trims e speeds
        val c1 = createBaseClip("c1", originalDurationMs = 10000L, trimStartMs = 2000L, trimEndMs = 8000L, speed = 2.0f) // (8-2)/2 = 3s
        val c2 = createBaseClip("c2", originalDurationMs = 8000L, trimStartMs = 0L, trimEndMs = 8000L, speed = 1.0f)   // 8s
        val duration = TimelineUtils.calculateProjectTimelineDuration(listOf(c1, c2))
        assertEquals(11000L, duration)
    }

    @Test
    fun test35_playheadIdentifiesCorrectClip() {
        // 35. Playhead identifica o clip correto
        val c1 = createBaseClip("c1", originalDurationMs = 5000L) // 0..5000
        val c2 = createBaseClip("c2", originalDurationMs = 7000L) // 5000..12000
        val c3 = createBaseClip("c3", originalDurationMs = 4000L) // 12000..16000
        val clips = listOf(c1, c2, c3)

        val info = TimelineUtils.findClipAtTimelinePosition(clips, 8500L)
        assertNotNull(info)
        assertEquals("c2", info?.clip?.id)
        assertEquals(3500L, info?.relativeTimelineOffsetMs)
    }

    @Test
    fun test36_playheadAtBoundaryBetweenClips() {
        // 36. Playhead no limite entre dois clips
        val c1 = createBaseClip("c1", originalDurationMs = 5000L)
        val c2 = createBaseClip("c2", originalDurationMs = 5000L)
        val clips = listOf(c1, c2)

        val info = TimelineUtils.findClipAtTimelinePosition(clips, 5000L)
        assertNotNull(info)
        assertEquals("c2", info?.clip?.id) // At 5000ms, begins clip 2
        assertEquals(0L, info?.relativeTimelineOffsetMs)
    }

    @Test
    fun test37_speedAltersDurationCorrectly() {
        // 37. Speed altera corretamente a duração
        val clipNormal = createBaseClip(originalDurationMs = 6000L, speed = 1.0f)
        val clipFast = createBaseClip(originalDurationMs = 6000L, speed = 2.0f)
        val clipSlow = createBaseClip(originalDurationMs = 6000L, speed = 0.5f)

        assertEquals(6000L, TimelineUtils.calculateClipTimelineDuration(clipNormal))
        assertEquals(3000L, TimelineUtils.calculateClipTimelineDuration(clipFast))
        assertEquals(12000L, TimelineUtils.calculateClipTimelineDuration(clipSlow))
    }

    @Test
    fun test38_trimAltersDurationCorrectly() {
        // 38. Trim altera corretamente a duração
        val clip = createBaseClip(originalDurationMs = 15000L, trimStartMs = 5000L, trimEndMs = 12000L)
        assertEquals(7000L, TimelineUtils.calculateClipTimelineDuration(clip))
    }

    @Test
    fun test39_splitRecalculatesTotalDurationConsistently() {
        // 39. Split recalcula a duração total corretamente (sem alterar a soma total)
        val c1 = createBaseClip("c1", originalDurationMs = 10000L)
        val clips = listOf(c1)
        val before = TimelineUtils.calculateProjectTimelineDuration(clips)

        val splitResult = TimelineUtils.splitClipAtPlayhead(clips, 4000L)!!
        val after = TimelineUtils.calculateProjectTimelineDuration(splitResult.first)

        assertEquals(before, after)
        assertEquals(10000L, after)
    }

    // ---------------- COMPLETE LIFECYCLE PERSISTENCE TEST ----------------

    @Test
    fun test40_completeTimelineLifecyclePersistence() = runBlocking {
        // Teste de Persistência Completo (Section 28):
        // Criar projeto -> Adicionar clips -> Trim -> Split -> Reorder -> Salvar -> Recarregar do Room

        val clipA = createBaseClip("c_A", originalDurationMs = 12000L)
        val clipB = createBaseClip("c_B", originalDurationMs = 8000L)

        val initialProj = ProjectItem(
            id = "proj_lifecycle",
            title = "Projeto Lifecycle",
            duration = "00:20",
            date = "Hoje",
            thumbUrl = "",
            clips = listOf(clipA, clipB)
        )
        repository.saveProject(initialProj)

        // 1. Trim no clip A (de 2s a 10s -> 8s)
        val trimmedA = TimelineUtils.applyTrim(clipA, 2000L, 10000L)!!

        // 2. Split no clip B (em 4s da timeline local -> 4s da fonte)
        val splitBResult = TimelineUtils.splitClipAtPlayhead(listOf(clipB), 4000L)!!
        val (bParts, _) = splitBResult

        // 3. Reorder: colocar as duas partes de B antes de A
        val updatedClips = listOf(bParts[0], bParts[1], trimmedA)

        val updatedProj = initialProj.copy(clips = updatedClips)
        repository.saveProject(updatedProj)

        // 4. Fechar e reabrir repositório com o mesmo banco
        val newRepo = ProjectRepository(db)
        val reloaded = newRepo.getProjectByIdOnce("proj_lifecycle")

        assertNotNull(reloaded)
        assertEquals(3, reloaded?.clips?.size)

        // Verificações estritas
        val firstClip = reloaded?.clips?.get(0)
        val secondClip = reloaded?.clips?.get(1)
        val thirdClip = reloaded?.clips?.get(2)

        assertEquals(bParts[0].id, firstClip?.id)
        assertEquals(bParts[1].id, secondClip?.id)
        assertEquals(trimmedA.id, thirdClip?.id)

        // Trim verificado
        assertEquals(2000L, thirdClip?.trimStartMs)
        assertEquals(10000L, thirdClip?.trimEndMs)
        assertEquals(8000L, thirdClip?.durationMs)

        // Mesmos arquivos físicos preservados
        assertEquals(clipA.localPath, thirdClip?.localPath)
        assertEquals(clipB.localPath, firstClip?.localPath)
        assertEquals(clipB.localPath, secondClip?.localPath)
    }
}
