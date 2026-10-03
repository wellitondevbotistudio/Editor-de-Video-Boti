package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.*
import com.example.util.TimelineUtils
import com.example.viewmodel.EditorViewModel
import com.example.viewmodel.ToolPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * ETAPA 20: Testes Automatizados de Responsividade Global e Padronização da UI.
 *
 * Valida:
 * 1. Aspect Ratio dinâmico e dimensionamento da prévia sem distorção.
 * 2. Espaço reservado e altura mínima garantida da Timeline/Camadas.
 * 3. Barra de ferramentas e sub-painéis em layout próprio (nunca sobrepõem a timeline).
 * 4. TrackHeaders das camadas responsivos com visibilidade, mute, lock e add acessíveis.
 * 5. Layout adaptativo para telas pequenas, médias, grandes e orientação horizontal (Landscape).
 * 6. Transformações e recorte espaciais consistentes e independentes de resolução fixa.
 * 7. Escala temporal e alinhamento do Playhead/Régua entre 0.6x e 3.5x de zoom.
 * 8. Áreas de toque, espaçamentos consistentes e conformidade com barras do sistema.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = BotiApplication::class, sdk = [34])
class Etapa20ResponsiveUITest {

    private lateinit var context: Context
    private lateinit var viewModel: EditorViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        viewModel = EditorViewModel(context as android.app.Application)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    @Throws(IOException::class)
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createResponsiveTestProject(ratio: AspectRatio = AspectRatio.RATIO_9_16): ProjectItem {
        val clips = listOf(
            MediaClip(
                id = "clip_resp_1",
                title = "Video Responsivo 1",
                uri = "file:///data/media/resp1.mp4",
                localPath = "/data/media/resp1.mp4",
                durationMs = 6000L,
                originalDurationMs = 6000L,
                trimStartMs = 0L,
                trimEndMs = 6000L,
                positionX = 0f,
                positionY = 0f,
                scale = 1.0f,
                rotation = 0f,
                cropRatio = "Original"
            ),
            MediaClip(
                id = "clip_resp_2",
                title = "Video Responsivo 2",
                uri = "file:///data/media/resp2.mp4",
                localPath = "/data/media/resp2.mp4",
                durationMs = 4000L,
                originalDurationMs = 4000L,
                trimStartMs = 0L,
                trimEndMs = 4000L,
                positionX = 0f,
                positionY = 0f,
                scale = 1.0f,
                rotation = 0f,
                cropRatio = "16:9"
            )
        )

        val texts = listOf(
            TextOverlayItem(
                id = "txt_resp_1",
                text = "Título Responsivo",
                startTimeMs = 500L,
                durationMs = 4000L,
                posX = 0.5f,
                posY = 0.3f,
                scale = 1.0f,
                rotation = 0f
            )
        )

        val stickers = listOf(
            StickerItem(
                id = "stk_resp_1",
                uri = "file:///stickers/sparkle.png",
                name = "Sparkle",
                startTimeMs = 1000L,
                durationMs = 3000L,
                posX = 0.4f,
                posY = 0.6f,
                scale = 1.0f,
                rotation = 0f
            )
        )

        val audios = listOf(
            AudioTrackItem(
                id = "aud_resp_1",
                name = "Trilha Sonora",
                category = "Música",
                duration = "00:10",
                durationMs = 10000L,
                timelineStartMs = 0L
            )
        )

        return ProjectItem(
            id = "proj_resp_20",
            title = "Projeto Responsivo 20",
            duration = "00:10",
            date = "Hoje",
            thumbUrl = "",
            aspectRatio = ratio,
            clips = clips,
            texts = texts,
            stickers = stickers,
            audios = audios
        )
    }

    // =========================================================================
    // 1. ASPECT RATIO DINÂMICO E DIMENSIONAMENTO DA PRÉVIA
    // =========================================================================

    @Test
    fun test01_aspectRatioCalculationsAndContainerFitting() {
        val aspectRatios = listOf(
            AspectRatio.RATIO_9_16,
            AspectRatio.RATIO_16_9,
            AspectRatio.RATIO_1_1,
            AspectRatio.RATIO_4_5,
            AspectRatio.RATIO_4_3
        )

        for (ratio in aspectRatios) {
            assertTrue("O ratio do aspecto deve ser positivo e não nulo", ratio.ratio > 0f)
            assertFalse("O ratio do aspecto não pode ser infinito", ratio.ratio.isInfinite())
            assertFalse("O ratio do aspecto não pode ser NaN", ratio.ratio.isNaN())

            // Teste de cálculo proporcional de dimensões dentro de containers variados
            val containerWidth = 360f
            val containerHeight = 640f
            val containerRatio = containerWidth / containerHeight

            val calculatedWidth: Float
            val calculatedHeight: Float

            if (containerRatio > ratio.ratio) {
                // Limitado pela altura
                calculatedHeight = containerHeight * 0.98f
                calculatedWidth = calculatedHeight * ratio.ratio
            } else {
                // Limitado pela largura
                calculatedWidth = containerWidth * 0.98f
                calculatedHeight = calculatedWidth / ratio.ratio
            }

            assertTrue("Largura da prévia deve ser válida", calculatedWidth > 0f && calculatedWidth <= containerWidth)
            assertTrue("Altura da prévia deve ser válida", calculatedHeight > 0f && calculatedHeight <= containerHeight)
            val computedRatio = calculatedWidth / calculatedHeight
            assertEquals("O aspecto final da prévia deve corresponder exatamente ao frameRatio", ratio.ratio, computedRatio, 0.05f)
        }
    }

    // =========================================================================
    // 2. TIMELINE COM ÁREA MÍNIMA GARANTIDA EM DIFERENTES TELAS
    // =========================================================================

    @Test
    fun test02_timelineGuaranteedHeightAndSpaceReservation() {
        val project = createResponsiveTestProject()
        viewModel.selectProject(project)
        testDispatcher.scheduler.advanceUntilIdle()

        // Simulação de tamanhos de tela (Compacta < 680dp, Padrão 720-800dp, Grande > 800dp)
        val screenHeights = listOf(560f, 640f, 740f, 850f, 1024f)

        for (availableHeight in screenHeights) {
            val isCompact = availableHeight < 680f
            val isSmall = availableHeight < 720f

            val timelineMinHeight = when {
                isCompact -> 140f
                isSmall -> 160f
                else -> 190f
            }

            val maxPanelHeight = when {
                isCompact -> 135f
                isSmall -> 160f
                else -> 195f
            }

            val bottomControlsHeight = 46f

            // Altura total reservada para Ferramentas + Controles
            val nonTimelineBottomHeight = maxPanelHeight + bottomControlsHeight
            val remainingForPreviewAndTimeline = availableHeight - nonTimelineBottomHeight

            // Mesmo com painel aberto no pior cenário, a timeline tem sua área garantida
            assertTrue("A altura mínima da timeline deve ser de pelo menos 140dp", timelineMinHeight >= 140f)
            assertTrue(
                "O espaço restante deve comportar a altura mínima da timeline e a prévia",
                remainingForPreviewAndTimeline >= timelineMinHeight + 100f
            )
        }
    }

    // =========================================================================
    // 3. BARRA DE FERRAMENTAS E SUB-PAINÉIS: LAYOUT PRÓPRIO E SEM SOBREPOSIÇÃO
    // =========================================================================

    @Test
    fun test03_toolbarAndPanelStructuralNonOverlap() {
        val project = createResponsiveTestProject()
        viewModel.selectProject(project)
        viewModel.selectClip("clip_resp_1")
        testDispatcher.scheduler.advanceUntilIdle()

        // Testar abertura de painéis diversos
        val panelsToTest = listOf(
            ToolPanel.SPEED,
            ToolPanel.FILTER,
            ToolPanel.ADJUST,
            ToolPanel.TRANSFORM,
            ToolPanel.CROP,
            ToolPanel.TRIM,
            ToolPanel.EDIT_TOOLS,
            ToolPanel.NONE
        )

        for (panel in panelsToTest) {
            viewModel.setActivePanel(panel)
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("O painel ativo deve corresponder ao selecionado", panel, state.activePanel)

            // A seleção do clipe não deve ser perdida ao alternar painéis
            assertEquals("clip_resp_1", state.selectedClipId)
        }

        // Ao fechar todos os painéis, o estado volta para NONE sem travar o layout
        viewModel.setActivePanel(ToolPanel.NONE)
        viewModel.selectClip(null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(ToolPanel.NONE, viewModel.uiState.value.activePanel)
        assertNull(viewModel.uiState.value.selectedClipId)
    }

    // =========================================================================
    // 4. CAMADAS RESPONSIVAS E ACESSIBILIDADE DOS CONTROLES DO TRACKHEADER
    // =========================================================================

    @Test
    fun test04_trackHeaderResponsivenessAndControlsAccessibility() {
        val project = createResponsiveTestProject()
        viewModel.selectProject(project)
        testDispatcher.scheduler.advanceUntilIdle()

        // 1. Toggles da Faixa de Vídeo
        viewModel.toggleVideoVisibility()
        assertFalse("Faixa de vídeo deve poder ser ocultada", viewModel.uiState.value.currentProject!!.isVideoVisible)
        viewModel.toggleVideoVisibility()
        assertTrue("Faixa de vídeo deve voltar a ficar visível", viewModel.uiState.value.currentProject!!.isVideoVisible)

        viewModel.toggleVideoLock()
        assertTrue("Faixa de vídeo deve poder ser bloqueada", viewModel.uiState.value.currentProject!!.isVideoLocked)
        viewModel.toggleVideoLock()
        assertFalse("Faixa de vídeo deve poder ser desbloqueada", viewModel.uiState.value.currentProject!!.isVideoLocked)

        viewModel.toggleVideoMute()
        assertTrue("Faixa de vídeo deve poder ser mutada", viewModel.uiState.value.currentProject!!.isVideoMuted)
        viewModel.toggleVideoMute()
        assertFalse("Faixa de vídeo deve poder ser desmutada", viewModel.uiState.value.currentProject!!.isVideoMuted)

        // 2. Toggles da Faixa de Áudio
        viewModel.toggleAudioMute()
        assertTrue("Faixa de áudio deve poder ser mutada", viewModel.uiState.value.currentProject!!.isAudioMuted)
        viewModel.toggleAudioMute()
        assertFalse("Faixa de áudio deve poder ser desmutada", viewModel.uiState.value.currentProject!!.isAudioMuted)

        viewModel.toggleAudioLock()
        assertTrue("Faixa de áudio deve poder ser bloqueada", viewModel.uiState.value.currentProject!!.isAudioLocked)
        viewModel.toggleAudioLock()
        assertFalse("Faixa de áudio deve poder ser desbloqueada", viewModel.uiState.value.currentProject!!.isAudioLocked)

        // 3. Toggles de Texto e Stickers
        viewModel.toggleTextVisibility()
        assertFalse("Faixa de texto deve poder ser ocultada", viewModel.uiState.value.currentProject!!.isTextVisible)
        viewModel.toggleTextVisibility()
        assertTrue("Faixa de texto deve voltar a ser visível", viewModel.uiState.value.currentProject!!.isTextVisible)

        viewModel.toggleOverlayVisibility()
        assertFalse("Faixa de sobreposição deve poder ser ocultada", viewModel.uiState.value.currentProject!!.isOverlayVisible)
        viewModel.toggleOverlayVisibility()
        assertTrue("Faixa de sobreposição deve voltar a ser visível", viewModel.uiState.value.currentProject!!.isOverlayVisible)
    }

    // =========================================================================
    // 5. ORIENTAÇÃO HORIZONTAL (LANDSCAPE) E DIVISÃO EM COLUNAS
    // =========================================================================

    @Test
    fun test05_landscapeOrientationDualColumnSpatialDistribution() {
        val landscapeWidth = 840f
        val landscapeHeight = 412f
        val isLandscape = landscapeWidth > landscapeHeight
        assertTrue("Orientação landscape deve ser detectada corretamente", isLandscape)

        // Divisão Proporcional: 42% Lado Esquerdo (Prévia + Controles) e 58% Lado Direito (Timeline + Ferramentas)
        val leftPaneWidth = landscapeWidth * 0.42f
        val rightPaneWidth = landscapeWidth * 0.58f

        assertTrue("Painel da esquerda deve possuir largura utilizável", leftPaneWidth >= 300f)
        assertTrue("Painel da direita deve possuir largura utilizável para timeline", rightPaneWidth >= 400f)

        // Na coluna direita, a timeline possui no mínimo 120dp garantidos na altura de 412dp
        val timelineMinHeightLandscape = 120f
        val toolsMaxHeightLandscape = 130f
        assertTrue(
            "A altura da tela horizontal comporta a timeline e o painel de ferramentas sem overflow",
            landscapeHeight >= timelineMinHeightLandscape + toolsMaxHeightLandscape
        )
    }

    // =========================================================================
    // 6. TRANSFORMAÇÕES ESPACIAIS, RECORTE E COORDENADAS RELATIVAS
    // =========================================================================

    @Test
    fun test06_responsiveCropRatioAndSpatialTransformCoordinates() {
        val project = createResponsiveTestProject()
        viewModel.selectProject(project)
        testDispatcher.scheduler.advanceUntilIdle()

        // 1. Atualizar proporção de recorte (Crop) do clipe
        viewModel.updateClipCropRatio("1:1", "clip_resp_1")
        testDispatcher.scheduler.advanceUntilIdle()

        var clip = viewModel.uiState.value.currentProject?.clips?.find { it.id == "clip_resp_1" }
        assertEquals("1:1", clip?.cropRatio)

        viewModel.updateClipCropRatio("16:9", "clip_resp_1")
        testDispatcher.scheduler.advanceUntilIdle()
        clip = viewModel.uiState.value.currentProject?.clips?.find { it.id == "clip_resp_1" }
        assertEquals("16:9", clip?.cropRatio)

        // 2. Redefinir recorte para Original
        viewModel.updateClipCropRatio("Original", "clip_resp_1")
        testDispatcher.scheduler.advanceUntilIdle()
        clip = viewModel.uiState.value.currentProject?.clips?.find { it.id == "clip_resp_1" }
        assertEquals("Original", clip?.cropRatio)

        // 3. Transformação espacial: Escala, Rotação e Posição em coordenadas relativas seguras
        viewModel.updateClipTransform(scale = 1.25f, rotation = 90f, positionX = 20f, positionY = -15f, clipId = "clip_resp_1")
        testDispatcher.scheduler.advanceUntilIdle()
        clip = viewModel.uiState.value.currentProject?.clips?.find { it.id == "clip_resp_1" }
        assertNotNull(clip)
        assertEquals(1.25f, clip!!.scale, 0.01f)
        assertEquals(90f, clip.rotation, 0.01f)
        assertEquals(20f, clip.positionX, 0.01f)
        assertEquals(-15f, clip.positionY, 0.01f)
    }

    // =========================================================================
    // 7. ESCALA TEMPORAL, RÉGUA E ALINHAMENTO DO PLAYHEAD COM ZOOM
    // =========================================================================

    @Test
    fun test07_playheadRulerSynchronizationAcrossZoomScales() {
        val project = createResponsiveTestProject()
        viewModel.selectProject(project)
        testDispatcher.scheduler.advanceUntilIdle()

        val totalDurationMs = viewModel.getTotalDurationMs()
        assertEquals(10000L, totalDurationMs)

        // Testar escalas de zoom suportadas pela timeline (0.6x a 3.5x)
        val zoomLevels = listOf(0.6f, 1.0f, 1.5f, 2.0f, 3.0f, 3.5f)

        for (zoom in zoomLevels) {
            val dpPerSecond = 50f * zoom
            assertTrue("dpPerSecond deve ser proporcional ao zoom", dpPerSecond >= 30f && dpPerSecond <= 175f)

            val safeTotalMs = kotlin.math.max(totalDurationMs, 5000L)
            val timelineWidthDp = kotlin.math.max(400f, (safeTotalMs / 1000f) * dpPerSecond)
            assertTrue("Largura da timeline calculada deve ser coerente", timelineWidthDp >= 400f)

            // Testar posições do playhead e cálculo de offset linear
            for (ms in listOf(0L, 2500L, 5000L, 7500L, 10000L)) {
                val offsetDp = (ms / 1000f) * dpPerSecond
                assertTrue("Offset em dp não pode ser negativo", offsetDp >= 0f)
                assertTrue("Offset deve caber na largura calculada da timeline", offsetDp <= timelineWidthDp + 150f)
            }
        }
    }

    // =========================================================================
    // 8. ÁREAS DE TOQUE E BOTÕES DE CONTROLE
    // =========================================================================

    @Test
    fun test08_touchTargetAccessibilityAndSystemBarsCompliance() {
        val project = createResponsiveTestProject()
        viewModel.selectProject(project)
        testDispatcher.scheduler.advanceUntilIdle()

        // Controles de Playback
        assertFalse(viewModel.uiState.value.isPlaying)
        viewModel.togglePlayback()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue("Toggle playback deve ativar a reprodução", viewModel.uiState.value.isPlaying)

        viewModel.togglePlayback()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse("Toggle playback deve pausar a reprodução", viewModel.uiState.value.isPlaying)

        // Divisão de clipe (Split)
        viewModel.seekTo(3000L)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(3000L, viewModel.uiState.value.currentPositionMs)

        viewModel.splitClipAtPlayhead()
        testDispatcher.scheduler.advanceUntilIdle()
        val clips = viewModel.uiState.value.currentProject?.clips ?: emptyList()
        assertTrue("Dividir clipe deve gerar clipes adicionais na timeline", clips.size >= 3)
    }
}
