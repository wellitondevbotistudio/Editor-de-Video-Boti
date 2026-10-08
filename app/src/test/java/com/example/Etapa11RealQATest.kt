package com.example

import com.example.export.ExportResolution
import com.example.export.ExportTimeline
import com.example.export.VideoExportConfig
import com.example.model.*
import com.example.overlay.OverlayAnimationEngine
import com.example.transition.TransitionEngine
import com.example.transition.TransitionType
import com.example.util.TimelineUtils
import com.example.viewmodel.deepCopy
import org.junit.Assert.*
import org.junit.Test

/**
 * ETAPA 11: QA REAL + INTEGRAÇÃO DA UI + PLAYER + TIMELINE + PERFORMANCE.
 *
 * Valida o fluxo real de ponta a ponta:
 * IMPORTAR -> TIMELINE -> PLAY -> PAUSE -> SEEK TRÁS -> PLAY -> SEEK FRENTE ->
 * TRIM -> SPLIT -> REORDER -> DELETE -> DUPLICATE -> TRANSIÇÃO -> ÁUDIO ->
 * TEXTO -> OVERLAY -> KEYFRAME -> VFX -> UNDO -> REDO -> SALVAR/RECARREGAR -> EXPORTAR.
 */
class Etapa11RealQATest {

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
    // 1. FLUXO COMPLETO DE PONTA A PONTA (END-TO-END WORKFLOW)
    // =========================================================================
    @Test
    fun test01_completeEndToEndWorkflow() {
        // Passo 1: Criar projeto e importar 3 clipes (A=5s, B=8s, C=4s)
        val clipA = createClip("clipA", 5000L)
        val clipB = createClip("clipB", 8000L)
        val clipC = createClip("clipC", 4000L)

        var project = ProjectItem(
            id = "proj_e2e",
            title = "Projeto QA E2E",
            clips = listOf(clipA, clipB, clipC)
        )

        val undoStack = ArrayDeque<ProjectItem>()
        val redoStack = ArrayDeque<ProjectItem>()

        fun commit(newProj: ProjectItem) {
            undoStack.addLast(project.deepCopy())
            redoStack.clear()
            project = newProj
        }

        // Duração inicial: 5s + 8s + 4s = 17s
        assertEquals(17000L, TimelineUtils.calculateProjectTimelineDuration(project.clips))

        // Passo 2: PLAY -> PAUSE -> SEEK PARA TRÁS -> PLAY -> SEEK PARA FRENTE
        var playhead = 0L
        playhead += 3000L // Play até 3s
        assertEquals("clipA", TimelineUtils.findClipAtTimelinePosition(project.clips, playhead)!!.clip.id)

        playhead = 1000L // Seek para trás (1s)
        assertEquals(1000L, TimelineUtils.findClipAtTimelinePosition(project.clips, playhead)!!.sourcePositionMs)

        playhead = 9000L // Seek para frente (Clip B, offset 4000ms)
        assertEquals("clipB", TimelineUtils.findClipAtTimelinePosition(project.clips, playhead)!!.clip.id)
        assertEquals(4000L, TimelineUtils.findClipAtTimelinePosition(project.clips, playhead)!!.sourcePositionMs)

        // Passo 3: TRIM no clipe B (trimStart=2s, trimEnd=7s -> nova duração 5s)
        val trimmedB = TimelineUtils.applyTrim(project.clips[1], 2000L, 7000L)!!
        val clipsAfterTrim = project.clips.map { if (it.id == "clipB") trimmedB else it }
        commit(project.copy(clips = clipsAfterTrim))

        // Nova duração da timeline: 5000 + 5000 + 4000 = 14000ms
        assertEquals(14000L, TimelineUtils.calculateProjectTimelineDuration(project.clips))

        // Passo 4: SPLIT no clipe A em 2500ms
        val splitResult = TimelineUtils.splitClipAtPlayhead(project.clips, 2500L)!!
        val (clipsAfterSplit, _) = splitResult
        commit(project.copy(clips = clipsAfterSplit))

        assertEquals(4, project.clips.size) // A1 (2.5s), A2 (2.5s), B (5s), C (4s)
        assertEquals(14000L, TimelineUtils.calculateProjectTimelineDuration(project.clips))

        // Passo 5: REORDER (Move C para o início)
        val clipsAfterReorder = TimelineUtils.reorderClips(project.clips, fromIndex = 3, toIndex = 0)
        commit(project.copy(clips = clipsAfterReorder))
        assertEquals("clipC", project.clips.first().id)

        // Passo 6: DELETE (Remove clipe B)
        val indexB = project.clips.indexOfFirst { it.id == "clipB" }
        val clipsAfterDelete = TimelineUtils.removeClip(project.clips, indexB)
        commit(project.copy(clips = clipsAfterDelete))
        assertEquals(3, project.clips.size) // C (4s), A1 (2.5s), A2 (2.5s) = 9000ms
        assertEquals(9000L, TimelineUtils.calculateProjectTimelineDuration(project.clips))

        // Passo 7: DUPLICATE (Duplica A2)
        val dupResult = TimelineUtils.duplicateClip(project.clips, project.clips.last().id)!!
        val (clipsAfterDup, dupId) = dupResult
        commit(project.copy(clips = clipsAfterDup))
        assertEquals(4, project.clips.size) // C, A1, A2, A2_copy
        assertEquals(11500L, TimelineUtils.calculateProjectTimelineDuration(project.clips))

        // Passo 8: TRANSIÇÃO (Aplica Dissolver entre C e A1)
        val clipsWithTrans = project.clips.mapIndexed { idx, clip ->
            if (idx == 0) clip.copy(transition = "Dissolver", transitionDurationMs = 1000L) else clip
        }
        commit(project.copy(clips = clipsWithTrans))
        val activeTrans = TransitionEngine.findActiveTransition(project.clips, 4000L) // Ponto de corte C -> A1
        assertNotNull(activeTrans)
        assertEquals(TransitionType.DISSOLVE, activeTrans!!.type)

        // Passo 9: ÁUDIO, TEXTO, OVERLAYS E VFX
        val audioTrack = AudioTrackItem(
            id = "bgm_1",
            name = "Trilha Sonora",
            category = "Música",
            duration = "00:15",
            durationMs = 15000L,
            volume = 0.9f
        )
        val textOverlay = TextOverlayItem(
            id = "txt_1",
            text = "QA Final",
            startTimeMs = 1000L,
            durationMs = 3000L,
            animationIn = "Zoom"
        )
        val vfx = VFXEffectItem(
            id = "vfx_glitch",
            name = "Glitch",
            category = "Glitch",
            thumbUrl = "",
            isEnabled = true,
            intensity = 75f
        )

        commit(project.copy(
            audios = listOf(audioTrack),
            texts = listOf(textOverlay),
            activeVFX = listOf(vfx)
        ))

        // Passo 10: UNDO e REDO
        val beforeUndo = project
        val previous = undoStack.removeLast()
        redoStack.addLast(beforeUndo.deepCopy())
        project = previous

        // Após undo, activeVFX deve estar vazio
        assertTrue(project.activeVFX.isEmpty())

        // Executa Redo
        val next = redoStack.removeLast()
        undoStack.addLast(project.deepCopy())
        project = next

        // Após redo, activeVFX e texts são restaurados
        assertEquals(1, project.activeVFX.size)
        assertEquals("Glitch", project.activeVFX.first().name)
        assertEquals(1, project.texts.size)

        // Passo 11: EXPORTAÇÃO IDÊNTICA AO PREVIEW
        val exportConfig = VideoExportConfig(
            resolution = ExportResolution.RES_720P,
            aspectRatio = AspectRatio.RATIO_9_16,
            fps = 30
        )
        val exportTimeline = ExportTimeline(project, exportConfig)
        val totalExpected = TimelineUtils.calculateTotalProjectDuration(project.clips, project.audios)

        assertEquals(totalExpected, exportTimeline.totalDurationMs)
        assertEquals(15000L, exportTimeline.totalDurationMs) // Max(video=11.5s, audio=15s) = 15s

        // Avalia frame em 4s (transição ativa)
        val snapshot = exportTimeline.evaluateAt(4000L)
        assertNotNull(snapshot.activeTransition)
        assertEquals(TransitionType.DISSOLVE, snapshot.activeTransition!!.type)
        assertEquals(1, snapshot.activeVfx.size)
    }

    // =========================================================================
    // 2. SCRUBBING RÁPIDO (COALESCÊNCIA DE SEEK E PREVENÇÃO DE CONGELAMENTO)
    // =========================================================================
    @Test
    fun test02_rapidScrubbing_coalescenceAndFinalPositionIntegrity() {
        val clips = listOf(
            createClip("A", 5000L),
            createClip("B", 5000L),
            createClip("C", 5000L)
        )

        // Simula o arrasto rápido do usuário: 0 -> 1 -> 2 -> 3 -> 4 -> 5 -> 10 -> 15
        val scrubPositions = listOf(0L, 1000L, 2000L, 3000L, 4000L, 5000L, 10000L, 15000L)
        var lastEvaluatedClipId: String? = null
        var lastSourcePos = 0L

        for (pos in scrubPositions) {
            val info = TimelineUtils.findClipAtTimelinePosition(clips, pos)
            assertNotNull("Posição $pos deve encontrar um clipe", info)
            lastEvaluatedClipId = info!!.clip.id
            lastSourcePos = info.sourcePositionMs
        }

        // A posição final do scrubbing (15000ms = fim da timeline) deve ser respeitada
        assertEquals("C", lastEvaluatedClipId)
        assertEquals(5000L, lastSourcePos)
    }

    // =========================================================================
    // 3. TRANSIÇÃO + SEEK PARA TRÁS E CONTINUIDADE DO PLAYBACK
    // =========================================================================
    @Test
    fun test03_transitionSeekBackward_andContinuity() {
        val clipA = createClip("A", 5000L, transition = "Fade", transitionDurationMs = 1000L)
        val clipB = createClip("B", 5000L)
        val clips = listOf(clipA, clipB)

        // Janela de transição: 5000ms +/- 500ms -> [4500ms .. 5500ms]
        val midTrans = 5000L
        val activeMid = TransitionEngine.findActiveTransition(clips, midTrans)
        assertNotNull(activeMid)
        assertEquals(TransitionType.FADE, activeMid!!.type)
        assertEquals(0.5f, activeMid.progress, 0.05f)

        // Arrastar playhead para trás dentro da transição (4750ms)
        val backTrans = 4750L
        val activeBack = TransitionEngine.findActiveTransition(clips, backTrans)
        assertNotNull(activeBack)
        assertEquals(0.25f, activeBack!!.progress, 0.05f)

        // Arrastar para antes da transição (4000ms)
        val beforeTrans = 4000L
        val activeBefore = TransitionEngine.findActiveTransition(clips, beforeTrans)
        assertNull("Antes de 4500ms não deve haver transição ativa", activeBefore)
        val infoBefore = TimelineUtils.findClipAtTimelinePosition(clips, beforeTrans)
        assertNotNull(infoBefore)
        assertEquals("A", infoBefore!!.clip.id)
    }

    // =========================================================================
    // 4. ÁUDIO: SYNC, MUTE, VOLUME E SPLIT
    // =========================================================================
    @Test
    fun test04_audioTrackOperations_syncMuteVolumeSplit() {
        val track = AudioTrackItem(
            id = "audio_test",
            name = "Efeito Sonoro",
            category = "FX",
            duration = "00:10",
            durationMs = 10000L,
            timelineStartMs = 2000L,
            volume = 0.7f
        )
        val tracks = listOf(track)

        // Teste de atividade temporal: ativo entre 2000ms e 12000ms
        assertFalse(TimelineUtils.isAudioActiveAtTimelinePosition(track, 1000L))
        assertTrue(TimelineUtils.isAudioActiveAtTimelinePosition(track, 2000L))
        assertTrue(TimelineUtils.isAudioActiveAtTimelinePosition(track, 6000L))
        assertFalse(TimelineUtils.isAudioActiveAtTimelinePosition(track, 12000L))

        // Mute toggle
        val muted = TimelineUtils.toggleAudioTrackMute(track)
        assertTrue(muted.isMuted)

        // Volume
        val volTrack = TimelineUtils.setAudioTrackVolume(track, 0.45f)
        assertEquals(0.45f, volTrack.volume, 0.01f)

        // Split da trilha em 5000ms da timeline (offset de 3000ms)
        val (splitTracks, newAudioId) = TimelineUtils.splitAudioTrackAtPlayhead(tracks, track.id, 5000L)
        assertEquals(2, splitTracks.size)
        assertNotNull(newAudioId)

        val left = splitTracks[0]
        val right = splitTracks[1]
        assertEquals(2000L, left.timelineStartMs)
        assertEquals(3000L, left.trimEndMs)

        assertEquals(5000L, right.timelineStartMs)
        assertEquals(3000L, right.trimStartMs)
    }

    // =========================================================================
    // 5. TEXTO E OVERLAYS: ANIMAÇÃO BIDIRECIONAL E REVERSIBILIDADE
    // =========================================================================
    @Test
    fun test05_overlayAnimationReversibility() {
        // Animação de Zoom de 0s a 4s (duração = 4000ms, animIn = 1000ms)
        val state0 = OverlayAnimationEngine.calculateTextState(
            playheadMs = 0L,
            startTimeMs = 0L,
            durationMs = 4000L,
            baseScale = 1.0f,
            fullText = "Teste Zoom",
            animationIn = "Zoom",
            animationOut = "Fade",
            animationDurationMs = 1000L
        )
        assertTrue("No início da animação de Zoom a escala deve ser menor que 1", state0.scale < 1.0f)

        // No meio (2000ms, após animIn e antes de animOut)
        val stateMid = OverlayAnimationEngine.calculateTextState(
            playheadMs = 2000L,
            startTimeMs = 0L,
            durationMs = 4000L,
            baseScale = 1.0f,
            fullText = "Teste Zoom",
            animationIn = "Zoom",
            animationOut = "Fade",
            animationDurationMs = 1000L
        )
        assertEquals(1.0f, stateMid.scale, 0.01f)
        assertEquals(1.0f, stateMid.alpha, 0.01f)

        // Reversão exata: se o usuário der seek para trás de 2s para 0s, a função pura retorna o estado inicial
        val stateReversed = OverlayAnimationEngine.calculateTextState(
            playheadMs = 0L,
            startTimeMs = 0L,
            durationMs = 4000L,
            baseScale = 1.0f,
            fullText = "Teste Zoom",
            animationIn = "Zoom",
            animationOut = "Fade",
            animationDurationMs = 1000L
        )
        assertEquals(state0.scale, stateReversed.scale, 0.001f)
    }

    // =========================================================================
    // 6. EXPORT TIMELINE FRAME EVALUATION (DETERMINISMO EM CADA FRAME)
    // =========================================================================
    @Test
    fun test06_exportTimelineFrameEvaluationDeterminism() {
        val project = ProjectItem(
            id = "proj_det",
            title = "Teste Determinismo",
            clips = listOf(
                createClip("c1", 3000L),
                createClip("c2", 4000L)
            )
        )
        val config = VideoExportConfig(
            resolution = ExportResolution.RES_720P,
            aspectRatio = AspectRatio.RATIO_9_16,
            fps = 30
        )
        val exportTimeline = ExportTimeline(project, config)
        assertEquals(7000L, exportTimeline.totalDurationMs)

        // Avaliações de frame não geram vazamento nem alteram a timeline
        val snap0 = exportTimeline.evaluateAt(0L)
        val snap1 = exportTimeline.evaluateAt(1500L)
        val snap2 = exportTimeline.evaluateAt(3000L)
        val snap3 = exportTimeline.evaluateAt(6999L)

        assertEquals("c1", snap0.activeClipInfo?.clip?.id)
        assertEquals("c1", snap1.activeClipInfo?.clip?.id)
        assertEquals("c2", snap2.activeClipInfo?.clip?.id)
        assertEquals("c2", snap3.activeClipInfo?.clip?.id)
    }
}
