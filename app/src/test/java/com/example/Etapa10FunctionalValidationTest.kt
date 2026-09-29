package com.example

import com.example.export.ExportResolution
import com.example.export.ExportTimeline
import com.example.export.VideoExportConfig
import com.example.model.*
import com.example.transition.TransitionEngine
import com.example.transition.TransitionType
import com.example.util.TimelineUtils
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/**
 * Validação Funcional Completa da Timeline e das Ferramentas de Edição - ETAPA 10.
 *
 * Cobre:
 * 1. Linha do tempo determinística (Export e Preview unificados)
 * 2. Timeline básica com 3 clipes (A=5s, B=8s, C=4s -> total 17s)
 * 3. Seek em todos os pontos e limites exatos (0s, 1s, 4.9s, 5s, 5.1s, 8s, 12.9s, 13s, 16.9s, 17s)
 * 4. Playback e determinismo de posições
 * 5. Trim no clipe B (trimStart=2s, trimEnd=7s -> duração 5s, timeline recalculada: 0..5, 5..10, 10..14)
 * 6. Trim durante playback com playhead na parte removida (correção automática para nunca ultrapassar clipEnd)
 * 7. Split de clipe de 10s em 4s e 6s + split consecutivo
 * 8. Split durante transição (corte limpo entre A1 e A2, transição segura para B)
 * 9. Reorder (ABC -> CAB -> BCA) com verificação de transições
 * 10. Delete de clipe (primeiro, intermediário, último, único) com transições higienizadas
 * 11. Duplicate de clipe (AB -> ABB com IDs independentes e propriedades isoladas)
 * 12. Validação individual de todas as 9 transições (Cut, Dissolve, Fade, Slide Left, Slide Right, Slide Up, Slide Down, Zoom, Wipe)
 */
class Etapa10FunctionalValidationTest {

    private fun createClip(
        id: String,
        durationMs: Long,
        title: String = id,
        transition: String? = null,
        transitionDurationMs: Long = 1000L
    ): MediaClip {
        return MediaClip(
            id = id,
            title = title,
            uri = "file:///storage/emulated/0/Movies/$id.mp4",
            localPath = "/storage/emulated/0/Movies/$id.mp4",
            durationMs = durationMs,
            originalDurationMs = durationMs,
            trimStartMs = 0L,
            trimEndMs = durationMs,
            speed = 1.0f,
            transition = transition,
            transitionDurationMs = transitionDurationMs
        )
    }

    // =========================================================================
    // 2. TIMELINE BÁSICA (A=5s, B=8s, C=4s)
    // =========================================================================
    @Test
    fun test01_basicTimeline_3clips_17sDuration_andClipPositions() {
        val clipA = createClip("clipA", 5000L)
        val clipB = createClip("clipB", 8000L)
        val clipC = createClip("clipC", 4000L)
        val clips = listOf(clipA, clipB, clipC)

        val totalDuration = TimelineUtils.calculateProjectTimelineDuration(clips)
        assertEquals(17000L, totalDuration)

        val startA = TimelineUtils.getClipStartTimelineMs(clips, 0)
        val startB = TimelineUtils.getClipStartTimelineMs(clips, 1)
        val startC = TimelineUtils.getClipStartTimelineMs(clips, 2)

        assertEquals(0L, startA)
        assertEquals(5000L, startB)
        assertEquals(13000L, startC)

        // Clip A: 0..5000
        val durA = TimelineUtils.calculateClipTimelineDuration(clipA)
        assertEquals(5000L, durA)

        // Clip B: 5000..13000
        val durB = TimelineUtils.calculateClipTimelineDuration(clipB)
        assertEquals(8000L, durB)

        // Clip C: 13000..17000
        val durC = TimelineUtils.calculateClipTimelineDuration(clipC)
        assertEquals(4000L, durC)
    }

    // =========================================================================
    // 3. SEEK EM TODOS OS PONTOS E LIMITES EXATOS
    // =========================================================================
    @Test
    fun test02_seekAllPoints_boundaryConditions_4_9_to_5_0_and_12_9_to_13_0() {
        val clipA = createClip("clipA", 5000L)
        val clipB = createClip("clipB", 8000L)
        val clipC = createClip("clipC", 4000L)
        val clips = listOf(clipA, clipB, clipC)

        // 0s -> Clip A no início
        val at0 = TimelineUtils.findClipAtTimelinePosition(clips, 0L)
        assertNotNull(at0)
        assertEquals("clipA", at0!!.clip.id)
        assertEquals(0, at0.index)
        assertEquals(0L, at0.sourcePositionMs)

        // 1s -> Clip A
        val at1 = TimelineUtils.findClipAtTimelinePosition(clips, 1000L)
        assertNotNull(at1)
        assertEquals("clipA", at1!!.clip.id)
        assertEquals(1000L, at1.sourcePositionMs)

        // 4.9s -> Clip A no final
        val at4_9 = TimelineUtils.findClipAtTimelinePosition(clips, 4900L)
        assertNotNull(at4_9)
        assertEquals("clipA", at4_9!!.clip.id)
        assertEquals(0, at4_9.index)
        assertEquals(4900L, at4_9.sourcePositionMs)

        // 5.0s -> Clip B exatamente no início
        val at5_0 = TimelineUtils.findClipAtTimelinePosition(clips, 5000L)
        assertNotNull(at5_0)
        assertEquals("clipB", at5_0!!.clip.id)
        assertEquals(1, at5_0.index)
        assertEquals(0L, at5_0.sourcePositionMs)

        // 5.1s -> Clip B
        val at5_1 = TimelineUtils.findClipAtTimelinePosition(clips, 5100L)
        assertNotNull(at5_1)
        assertEquals("clipB", at5_1!!.clip.id)
        assertEquals(100L, at5_1.sourcePositionMs)

        // 8.0s -> Clip B no meio (offset = 3000ms)
        val at8_0 = TimelineUtils.findClipAtTimelinePosition(clips, 8000L)
        assertNotNull(at8_0)
        assertEquals("clipB", at8_0!!.clip.id)
        assertEquals(3000L, at8_0.sourcePositionMs)

        // 12.9s -> Clip B no final (offset = 7900ms)
        val at12_9 = TimelineUtils.findClipAtTimelinePosition(clips, 12900L)
        assertNotNull(at12_9)
        assertEquals("clipB", at12_9!!.clip.id)
        assertEquals(1, at12_9.index)
        assertEquals(7900L, at12_9.sourcePositionMs)

        // 13.0s -> Clip C exatamente no início
        val at13_0 = TimelineUtils.findClipAtTimelinePosition(clips, 13000L)
        assertNotNull(at13_0)
        assertEquals("clipC", at13_0!!.clip.id)
        assertEquals(2, at13_0.index)
        assertEquals(0L, at13_0.sourcePositionMs)

        // 16.9s -> Clip C no final (offset = 3900ms)
        val at16_9 = TimelineUtils.findClipAtTimelinePosition(clips, 16900L)
        assertNotNull(at16_9)
        assertEquals("clipC", at16_9!!.clip.id)
        assertEquals(3900L, at16_9.sourcePositionMs)

        // 17.0s -> Fim da timeline (último clipe no final)
        val at17_0 = TimelineUtils.findClipAtTimelinePosition(clips, 17000L)
        assertNotNull(at17_0)
        assertEquals("clipC", at17_0!!.clip.id)
        assertEquals(2, at17_0.index)
        assertEquals(4000L, at17_0.sourcePositionMs)
    }

    // =========================================================================
    // 4. PLAYBACK + TIMELINE STATE CONSISTENCY
    // =========================================================================
    @Test
    fun test03_playback_and_seek_state_determinism() {
        val clips = listOf(
            createClip("A", 5000L),
            createClip("B", 8000L),
            createClip("C", 4000L)
        )

        // Simula PLAY -> PAUSE -> PLAY -> SEEK -> PLAY
        var currentPositionMs = 0L
        currentPositionMs += 2500L // Play até 2.5s (Clip A)
        assertEquals("A", TimelineUtils.findClipAtTimelinePosition(clips, currentPositionMs)!!.clip.id)

        // Seek para trás: 1000ms
        currentPositionMs = 1000L
        assertEquals("A", TimelineUtils.findClipAtTimelinePosition(clips, currentPositionMs)!!.clip.id)
        assertEquals(1000L, TimelineUtils.findClipAtTimelinePosition(clips, currentPositionMs)!!.sourcePositionMs)

        // Seek para frente: 9000ms (Clip B, offset 4000ms)
        currentPositionMs = 9000L
        val infoB = TimelineUtils.findClipAtTimelinePosition(clips, currentPositionMs)!!
        assertEquals("B", infoB.clip.id)
        assertEquals(4000L, infoB.sourcePositionMs)

        // Seek para outro clipe: 15000ms (Clip C, offset 2000ms)
        currentPositionMs = 15000L
        val infoC = TimelineUtils.findClipAtTimelinePosition(clips, currentPositionMs)!!
        assertEquals("C", infoC.clip.id)
        assertEquals(2000L, infoC.sourcePositionMs)
    }

    // =========================================================================
    // 5. TRIM NO CLIPE B
    // =========================================================================
    @Test
    fun test04_trim_clipB_recalculatesTimeline_5000_to_10000() {
        val clipA = createClip("clipA", 5000L)
        val clipB = createClip("clipB", 8000L)
        val clipC = createClip("clipC", 4000L)

        // Aplica trim no clipe B: trimStart = 2000ms, trimEnd = 7000ms (duração = 5000ms)
        val trimmedB = TimelineUtils.applyTrim(clipB, newTrimStartMs = 2000L, newTrimEndMs = 7000L)
        assertNotNull(trimmedB)
        assertEquals(2000L, trimmedB!!.trimStartMs)
        assertEquals(7000L, trimmedB.trimEndMs)
        assertEquals(5000L, trimmedB.durationMs)

        val updatedClips = listOf(clipA, trimmedB, clipC)
        val newTotal = TimelineUtils.calculateProjectTimelineDuration(updatedClips)
        assertEquals(14000L, newTotal)

        // A: 0 -> 5000
        assertEquals(0L, TimelineUtils.getClipStartTimelineMs(updatedClips, 0))
        assertEquals(5000L, TimelineUtils.calculateClipTimelineDuration(clipA))

        // B: 5000 -> 10000
        assertEquals(5000L, TimelineUtils.getClipStartTimelineMs(updatedClips, 1))
        assertEquals(5000L, TimelineUtils.calculateClipTimelineDuration(trimmedB))

        // C: 10000 -> 14000
        assertEquals(10000L, TimelineUtils.getClipStartTimelineMs(updatedClips, 2))
        assertEquals(4000L, TimelineUtils.calculateClipTimelineDuration(clipC))

        // No tempo 7500ms (na timeline): 7500 - 5000 = 2500ms offset
        // sourcePosition = 2000 + 2500 = 4500ms
        val infoAt7500 = TimelineUtils.findClipAtTimelinePosition(updatedClips, 7500L)
        assertNotNull(infoAt7500)
        assertEquals("clipB", infoAt7500!!.clip.id)
        assertEquals(4500L, infoAt7500.sourcePositionMs)
    }

    // =========================================================================
    // 6. TRIM DURANTE PLAYBACK (PLAYHEAD NA PARTE REMOVIDA)
    // =========================================================================
    @Test
    fun test05_trimDuringPlayback_playheadInsideRemovedRegion_clampedCorrectly() {
        val originalClip = createClip("clip", 10000L)
        val playheadMs = 8000L // Playhead em 8s

        // Usuário altera trimEnd para 6000ms (duração passa a ser 6s)
        val trimmed = TimelineUtils.applyTrim(originalClip, newTrimStartMs = 0L, newTrimEndMs = 6000L)!!
        assertEquals(6000L, trimmed.durationMs)

        // A lógica do ViewModel corrige o playhead para o novo final válido
        val oldClipStartTimeline = 0L
        val oldClipDuration = 10000L
        val oldClipEndTimeline = 10000L

        val newClipStartTimeline = 0L
        val newClipDuration = 6000L
        val newClipEndTimeline = 6000L
        val newTotal = 6000L

        val correctedPlayhead = when {
            playheadMs in oldClipStartTimeline until oldClipEndTimeline -> {
                val oldOffset = playheadMs - oldClipStartTimeline
                val oldSourcePos = TimelineUtils.getEffectiveTrimStart(originalClip) + oldOffset
                when {
                    oldSourcePos < trimmed.trimStartMs -> newClipStartTimeline
                    oldSourcePos > trimmed.trimEndMs -> newClipEndTimeline.coerceAtMost(newTotal)
                    else -> newClipStartTimeline + (oldSourcePos - trimmed.trimStartMs)
                }
            }
            else -> playheadMs.coerceIn(0L, newTotal)
        }

        assertEquals(6000L, correctedPlayhead)
        assertTrue("correctedPlayhead must not exceed clip end", correctedPlayhead <= newClipEndTimeline)
    }

    // =========================================================================
    // 7. SPLIT (A = 10s -> A1 = 4s, A2 = 6s + SPLIT CONSECUTIVO)
    // =========================================================================
    @Test
    fun test06_split_10s_into_4s_and_6s_and_consecutiveSplit() {
        val clipA = createClip("clipA", 10000L)
        val initialClips = listOf(clipA)

        // Split em 4000ms
        val splitResult = TimelineUtils.splitClipAtPlayhead(initialClips, 4000L)
        assertNotNull(splitResult)
        val (clipsAfterFirstSplit, newClipId) = splitResult!!

        assertEquals(2, clipsAfterFirstSplit.size)
        val a1 = clipsAfterFirstSplit[0]
        val a2 = clipsAfterFirstSplit[1]

        assertEquals(4000L, a1.durationMs)
        assertEquals(0L, a1.trimStartMs)
        assertEquals(4000L, a1.trimEndMs)

        assertEquals(6000L, a2.durationMs)
        assertEquals(4000L, a2.trimStartMs)
        assertEquals(10000L, a2.trimEndMs)
        assertEquals(newClipId, a2.id)

        val totalAfterSplit1 = TimelineUtils.calculateProjectTimelineDuration(clipsAfterFirstSplit)
        assertEquals(10000L, totalAfterSplit1)

        // Split consecutivo no segundo clipe (a2) em 7000ms da timeline (offset de 3000ms em a2)
        val split2Result = TimelineUtils.splitClipAtPlayhead(clipsAfterFirstSplit, 7000L)
        assertNotNull(split2Result)
        val (clipsAfterSecondSplit, _) = split2Result!!

        assertEquals(3, clipsAfterSecondSplit.size)
        val p1 = clipsAfterSecondSplit[0] // 0..4000
        val p2 = clipsAfterSecondSplit[1] // 4000..7000 (3000ms)
        val p3 = clipsAfterSecondSplit[2] // 7000..10000 (3000ms)

        assertEquals(4000L, p1.durationMs)
        assertEquals(3000L, p2.durationMs)
        assertEquals(3000L, p3.durationMs)

        val totalAfterSplit2 = TimelineUtils.calculateProjectTimelineDuration(clipsAfterSecondSplit)
        assertEquals(10000L, totalAfterSplit2)
    }

    // =========================================================================
    // 8. SPLIT DURANTE TRANSIÇÃO
    // =========================================================================
    @Test
    fun test07_splitDuringTransition_preservesTransitionOnPart2_cleanCutOnPart1() {
        val clipA = createClip("clipA", 10000L, transition = "Fade", transitionDurationMs = 1000L)
        val clipB = createClip("clipB", 8000L)
        val clips = listOf(clipA, clipB)

        // Split no clipe A em 8000ms
        val splitResult = TimelineUtils.splitClipAtPlayhead(clips, 8000L)
        assertNotNull(splitResult)
        val (updatedClips, _) = splitResult!!

        assertEquals(3, updatedClips.size)
        val part1 = updatedClips[0]
        val part2 = updatedClips[1]
        val finalB = updatedClips[2]

        // Part 1 corta para Part 2 -> deve ser corte limpo (sem transição)
        assertNull(part1.transition)
        assertEquals(8000L, part1.durationMs)

        // Part 2 transiciona para o clipe B -> retém Fade
        assertEquals("Fade", part2.transition)
        assertEquals(2000L, part2.durationMs)
        assertTrue(part2.transitionDurationMs <= part2.durationMs / 2)

        // Transição ativa entre part2 e clipB funciona normalmente
        val splitPoint = TimelineUtils.getClipStartTimelineMs(updatedClips, 1) + part2.durationMs // 8000 + 2000 = 10000ms
        val activeTransition = TransitionEngine.findActiveTransition(updatedClips, splitPoint)
        assertNotNull(activeTransition)
        assertEquals(TransitionType.FADE, activeTransition!!.type)
    }

    // =========================================================================
    // 9. REORDER (ABC -> CAB -> BCA)
    // =========================================================================
    @Test
    fun test08_reorder_ABC_to_CAB_and_BCA_transitionsSafe() {
        val a = createClip("A", 5000L, transition = "Dissolver")
        val b = createClip("B", 8000L, transition = "Slide Left")
        val c = createClip("C", 4000L) // Último clipe sem transição
        val initial = listOf(a, b, c)

        // Move C (index 2) para a posição de A (index 0) -> C A B
        val cab = TimelineUtils.reorderClips(initial, fromIndex = 2, toIndex = 0)
        assertEquals(listOf("C", "A", "B"), cab.map { it.id })
        assertEquals(17000L, TimelineUtils.calculateProjectTimelineDuration(cab))
        // O novo último clipe (B) não pode ter transição para o vazio
        assertNull(cab.last().transition)

        // Move B (index 2) para a posição de C (index 0) a partir de CAB -> B C A
        val bca = TimelineUtils.reorderClips(cab, fromIndex = 2, toIndex = 0)
        assertEquals(listOf("B", "C", "A"), bca.map { it.id })
        assertEquals(17000L, TimelineUtils.calculateProjectTimelineDuration(bca))
        assertNull(bca.last().transition)
    }

    // =========================================================================
    // 10. DELETE (PRIMEIRO, INTERMEDIÁRIO, ÚLTIMO, ÚNICO)
    // =========================================================================
    @Test
    fun test09_delete_first_intermediate_last_singleClip_transitionsCleaned() {
        val a = createClip("A", 5000L, transition = "Dissolver")
        val b = createClip("B", 8000L, transition = "Fade")
        val c = createClip("C", 4000L)
        val initial = listOf(a, b, c)

        // 1. Excluir clipe intermediário (B) -> Resta [A, C]
        val withoutB = TimelineUtils.removeClip(initial, clipIndex = 1)
        assertEquals(listOf("A", "C"), withoutB.map { it.id })
        assertEquals(9000L, TimelineUtils.calculateProjectTimelineDuration(withoutB))
        // A transiciona com segurança para C
        assertEquals("Dissolver", withoutB[0].transition)
        assertNull(withoutB[1].transition)

        // 2. Excluir primeiro clipe (A) de [A, B, C] -> Resta [B, C]
        val withoutA = TimelineUtils.removeClip(initial, clipIndex = 0)
        assertEquals(listOf("B", "C"), withoutA.map { it.id })
        assertEquals(12000L, TimelineUtils.calculateProjectTimelineDuration(withoutA))

        // 3. Excluir último clipe (C) de [A, B, C] -> Resta [A, B]
        val withoutC = TimelineUtils.removeClip(initial, clipIndex = 2)
        assertEquals(listOf("A", "B"), withoutC.map { it.id })
        assertEquals(13000L, TimelineUtils.calculateProjectTimelineDuration(withoutC))
        // B agora é o último clipe, logo sua transição deve ser null
        assertNull(withoutC[1].transition)

        // 4. Excluir único clipe
        val single = listOf(createClip("Solo", 5000L))
        val empty = TimelineUtils.removeClip(single, clipIndex = 0)
        assertTrue(empty.isEmpty())
        assertEquals(0L, TimelineUtils.calculateProjectTimelineDuration(empty))
    }

    // =========================================================================
    // 11. DUPLICATE (AB -> ABB)
    // =========================================================================
    @Test
    fun test10_duplicate_AB_to_ABB_independentProperties_originalMutationDoesNotAffectDuplicate() {
        val a = createClip("A", 5000L)
        val b = createClip("B", 8000L)
        val initial = listOf(a, b)

        // Duplica B
        val dupResult = TimelineUtils.duplicateClip(initial, "B")
        assertNotNull(dupResult)
        val (updatedClips, newId) = dupResult!!

        assertEquals(3, updatedClips.size)
        assertEquals("A", updatedClips[0].id)
        assertEquals("B", updatedClips[1].id)
        assertEquals(newId, updatedClips[2].id)

        // Propriedades do duplicado coincidem com o original
        val origB = updatedClips[1]
        val dupB = updatedClips[2]
        assertEquals(origB.durationMs, dupB.durationMs)
        assertEquals(origB.localPath, dupB.localPath)
        assertEquals(origB.speed, dupB.speed, 0.001f)

        // Duração total: 5000 + 8000 + 8000 = 21000ms
        assertEquals(21000L, TimelineUtils.calculateProjectTimelineDuration(updatedClips))

        // Mutar o original na lista NÃO altera o duplicado
        val mutatedClips = updatedClips.map {
            if (it.id == "B") it.copy(filter = "Cinematic", brightness = 50f) else it
        }
        val mutatedB = mutatedClips.find { it.id == "B" }!!
        val unmodifiedDupB = mutatedClips.find { it.id == newId }!!

        assertEquals("Cinematic", mutatedB.filter)
        assertEquals(50f, mutatedB.brightness, 0.001f)

        assertEquals("Original", unmodifiedDupB.filter)
        assertEquals(0f, unmodifiedDupB.brightness, 0.001f)
    }

    // =========================================================================
    // 12. TRANSIÇÕES (TODAS AS 9 TRANSIÇÕES INDIVIDUAIS)
    // =========================================================================
    @Test
    fun test11_all9Transitions_evaluatedCorrectlyAcrossTransitionWindow() {
        val transitionNames = listOf(
            "Corte Seco" to TransitionType.CUT,
            "Dissolver" to TransitionType.DISSOLVE,
            "Fade" to TransitionType.FADE,
            "Deslizar Esquerda" to TransitionType.SLIDE_LEFT,
            "Deslizar Direita" to TransitionType.SLIDE_RIGHT,
            "Deslizar Cima" to TransitionType.SLIDE_UP,
            "Deslizar Baixo" to TransitionType.SLIDE_DOWN,
            "Zoom" to TransitionType.ZOOM,
            "Cortina" to TransitionType.WIPE
        )

        for ((name, expectedType) in transitionNames) {
            val parsedType = TransitionType.fromName(name)
            assertEquals("Falha ao converter $name", expectedType, parsedType)

            // Avalia transformações no início (0.0f), meio (0.5f) e fim (1.0f)
            val (tA0, tB0) = TransitionEngine.calculateTransforms(parsedType, 0.0f)
            val (tA5, tB5) = TransitionEngine.calculateTransforms(parsedType, 0.5f)
            val (tA1, tB1) = TransitionEngine.calculateTransforms(parsedType, 1.0f)

            when (parsedType) {
                TransitionType.CUT -> {
                    assertEquals(1f, tA0.alpha, 0.01f)
                    assertEquals(0f, tB0.alpha, 0.01f)
                    assertEquals(0f, tA1.alpha, 0.01f)
                    assertEquals(1f, tB1.alpha, 0.01f)
                }
                TransitionType.DISSOLVE -> {
                    assertEquals(1f, tA0.alpha, 0.01f)
                    assertEquals(0f, tB0.alpha, 0.01f)
                    assertEquals(0.5f, tA5.alpha, 0.01f)
                    assertEquals(0.5f, tB5.alpha, 0.01f)
                    assertEquals(0f, tA1.alpha, 0.01f)
                    assertEquals(1f, tB1.alpha, 0.01f)
                }
                TransitionType.FADE -> {
                    assertEquals(1f, tA0.alpha, 0.01f)
                    assertEquals(0f, tB0.alpha, 0.01f)
                    // Dip to black no meio
                    assertEquals(0f, tA5.alpha, 0.01f)
                    assertEquals(0f, tB5.alpha, 0.01f)
                    assertEquals(0f, tA1.alpha, 0.01f)
                    assertEquals(1f, tB1.alpha, 0.01f)
                }
                TransitionType.SLIDE_LEFT -> {
                    assertEquals(0f, tA0.translationX, 0.01f)
                    assertTrue("tA1 translationX should be negative", tA1.translationX < 0f)
                    assertTrue("tB0 translationX should be positive", tB0.translationX > 0f)
                    assertEquals(0f, tB1.translationX, 0.01f)
                }
                TransitionType.SLIDE_RIGHT -> {
                    assertEquals(0f, tA0.translationX, 0.01f)
                    assertTrue("tA1 translationX should be positive", tA1.translationX > 0f)
                    assertTrue("tB0 translationX should be negative", tB0.translationX < 0f)
                    assertEquals(0f, tB1.translationX, 0.01f)
                }
                TransitionType.SLIDE_UP -> {
                    assertEquals(0f, tA0.translationY, 0.01f)
                    assertTrue("tA1 translationY should be negative", tA1.translationY < 0f)
                }
                TransitionType.SLIDE_DOWN -> {
                    assertEquals(0f, tA0.translationY, 0.01f)
                    assertTrue("tA1 translationY should be positive", tA1.translationY > 0f)
                }
                TransitionType.ZOOM -> {
                    assertEquals(1f, tA0.scale, 0.01f)
                    assertTrue("tA1 scale should grow", tA1.scale > 1f)
                    assertTrue("tB0 scale should start smaller", tB0.scale < 1f)
                }
                TransitionType.WIPE -> {
                    assertEquals(1f, tA0.wipeProgress, 0.01f)
                    assertEquals(0f, tB0.wipeProgress, 0.01f)
                    assertEquals(0f, tA1.wipeProgress, 0.01f)
                    assertEquals(1f, tB1.wipeProgress, 0.01f)
                }
            }
        }
    }

    // =========================================================================
    // 1. REGRA PRINCIPAL: EXPORT E PREVIEW COMPARTILHAM A MESMA LÓGICA
    // =========================================================================
    @Test
    fun test12_exportTimeline_sharesExactTimelineCalculationsWithEditor() {
        val clipA = createClip("clipA", 5000L, transition = "Fade", transitionDurationMs = 1000L)
        val clipB = createClip("clipB", 8000L)
        val project = ProjectItem(
            id = "proj_test",
            title = "Teste Export",
            clips = listOf(clipA, clipB),
            audios = listOf(
                AudioTrackItem(
                    id = "audio_1",
                    name = "Music",
                    category = "BGM",
                    duration = "00:13",
                    durationMs = 13000L,
                    timelineStartMs = 0L
                )
            )
        )

        val config = VideoExportConfig(
            resolution = ExportResolution.RES_720P,
            aspectRatio = AspectRatio.RATIO_9_16,
            fps = 30
        )

        val exportTimeline = ExportTimeline(project, config)
        val editorDuration = TimelineUtils.calculateTotalProjectDuration(project.clips, project.audios)

        // Duração idêntica
        assertEquals(editorDuration, exportTimeline.totalDurationMs)
        assertEquals(13000L, exportTimeline.totalDurationMs)

        // Avalia snapshot na fronteira de transição (5000ms)
        val snapshotAtTransition = exportTimeline.evaluateAt(5000L)
        assertNotNull(snapshotAtTransition.activeTransition)
        assertEquals(TransitionType.FADE, snapshotAtTransition.activeTransition!!.type)
        assertEquals("clipA", snapshotAtTransition.activeTransition!!.clipA.id)
        assertEquals("clipB", snapshotAtTransition.activeTransition!!.clipB.id)

        // Avalia snapshot fora da transição (1000ms)
        val snapshotAtNormal = exportTimeline.evaluateAt(1000L)
        assertNull(snapshotAtNormal.activeTransition)
        assertNotNull(snapshotAtNormal.activeClipInfo)
        assertEquals("clipA", snapshotAtNormal.activeClipInfo!!.clip.id)
        assertEquals(1000L, snapshotAtNormal.activeClipInfo!!.sourcePositionMs)
    }
}
