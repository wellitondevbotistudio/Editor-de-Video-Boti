package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.repository.ProjectRepository
import com.example.model.*
import com.example.util.TimelineUtils
import com.example.viewmodel.EditorViewModel
import com.example.viewmodel.ToolPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
 * ETAPA 18: Testes de Seleção, Timeline e Manipulação Direta na Prévia.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = BotiApplication::class, sdk = [34])
class Etapa18InteractionTest {

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

    private fun createTestProjectWithElements(): ProjectItem {
        val clips = listOf(
            MediaClip(
                id = "clip_1",
                title = "Video 1",
                uri = "file:///data/media/v1.mp4",
                localPath = "/data/media/v1.mp4",
                durationMs = 5000L,
                originalDurationMs = 5000L,
                trimStartMs = 0L,
                trimEndMs = 5000L,
                positionX = 0f,
                positionY = 0f,
                scale = 1.0f,
                rotation = 0f
            ),
            MediaClip(
                id = "clip_2",
                title = "Video 2",
                uri = "file:///data/media/v2.mp4",
                localPath = "/data/media/v2.mp4",
                durationMs = 4000L,
                originalDurationMs = 4000L,
                trimStartMs = 0L,
                trimEndMs = 4000L,
                positionX = 0f,
                positionY = 0f,
                scale = 1.0f,
                rotation = 0f
            )
        )

        val texts = listOf(
            TextOverlayItem(
                id = "txt_1",
                text = "Título de Teste",
                startTimeMs = 500L,
                durationMs = 3000L,
                posX = 0.5f,
                posY = 0.5f,
                scale = 1.0f,
                rotation = 0f
            )
        )

        val stickers = listOf(
            StickerItem(
                id = "stk_1",
                uri = "file:///stickers/star.png",
                name = "Star",
                startTimeMs = 1000L,
                durationMs = 2500L,
                posX = 0.3f,
                posY = 0.4f,
                scale = 1.0f,
                rotation = 0f
            )
        )

        val audios = listOf(
            AudioTrackItem(
                id = "aud_1",
                name = "Trilha Sonora",
                category = "Música",
                duration = "00:09",
                durationMs = 9000L,
                timelineStartMs = 0L
            )
        )

        return ProjectItem(
            id = "proj_test_18",
            title = "Projeto Etapa 18",
            duration = "00:09",
            date = "Hoje",
            thumbUrl = "",
            aspectRatio = AspectRatio.RATIO_9_16,
            clips = clips,
            texts = texts,
            stickers = stickers,
            audios = audios
        )
    }

    // =========================================================================
    // 1. SELEÇÃO PRECISA E PERSISTÊNCIA DA SELEÇÃO
    // =========================================================================

    @Test
    fun test01_elementSelectionPrecisionAndNoAutoSelection() {
        val project = createTestProjectWithElements()
        viewModel.selectProject(project)
        testDispatcher.scheduler.advanceUntilIdle()

        val stateAfterSelectProject = viewModel.uiState.value
        // Após selecionar o projeto, nenhum clipe deve ser selecionado automaticamente
        assertNull("Nenhum clipe deve ser selecionado automaticamente", stateAfterSelectProject.selectedClipId)
        assertNull("Nenhum texto deve ser selecionado automaticamente", stateAfterSelectProject.selectedTextId)
        assertNull("Nenhum sticker deve ser selecionado automaticamente", stateAfterSelectProject.selectedStickerId)

        // Seleção explícita de um clipe
        viewModel.selectClip("clip_1")
        assertEquals("clip_1", viewModel.uiState.value.selectedClipId)

        // Mover o playhead não deve desselecionar nem alterar o clipe selecionado
        viewModel.seekTo(3500L)
        assertEquals("A seleção deve persistir ao mover o playhead", "clip_1", viewModel.uiState.value.selectedClipId)

        // Mover o playhead para o clipe 2 não deve mudar a seleção forçadamente
        viewModel.seekTo(6500L)
        assertEquals("A seleção deve permanecer em clip_1 mesmo quando o playhead passa para outro clipe", "clip_1", viewModel.uiState.value.selectedClipId)
    }

    @Test
    fun test02_selectionPersistenceDuringToolOpeningAndModifications() {
        val project = createTestProjectWithElements()
        viewModel.selectProject(project)
        viewModel.selectClip("clip_2")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("clip_2", viewModel.uiState.value.selectedClipId)

        // Abrir ferramenta de velocidade
        viewModel.setActivePanel(ToolPanel.SPEED)
        assertEquals("A seleção deve ser mantida ao abrir ferramenta", "clip_2", viewModel.uiState.value.selectedClipId)
        assertEquals(ToolPanel.SPEED, viewModel.uiState.value.activePanel)

        // Modificar velocidade
        viewModel.updateClipSpeed(1.5f)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("A seleção deve persistir ao modificar propriedades", "clip_2", viewModel.uiState.value.selectedClipId)

        // Fechar painel
        viewModel.setActivePanel(ToolPanel.NONE)
        assertEquals("A seleção deve persistir ao fechar o painel", "clip_2", viewModel.uiState.value.selectedClipId)

        // Desseleção explícita
        viewModel.selectClip(null)
        assertNull("A seleção deve ser removida quando explicitamente desmarcada", viewModel.uiState.value.selectedClipId)
    }

    // =========================================================================
    // 2. MANIPULAÇÃO DIRETA DO CLIPE NA PRÉVIA (ARRASTO, ESCALA, ROTAÇÃO)
    // =========================================================================

    @Test
    fun test03_directPreviewClipSpatialTransform() {
        val project = createTestProjectWithElements()
        viewModel.selectProject(project)
        viewModel.selectClip("clip_1")
        testDispatcher.scheduler.advanceUntilIdle()

        // 1. Arrastar posição na prévia (Pan)
        viewModel.updateClipTransform(positionX = 45f, positionY = -30f, clipId = "clip_1")
        testDispatcher.scheduler.advanceUntilIdle()

        var currentClip = viewModel.uiState.value.currentProject?.clips?.find { it.id == "clip_1" }
        assertNotNull(currentClip)
        assertEquals(45f, currentClip!!.positionX, 0.01f)
        assertEquals(-30f, currentClip.positionY, 0.01f)

        // 2. Redimensionar / Escalar na prévia (Pinch / Resize handle)
        viewModel.updateClipTransform(scale = 1.35f, clipId = "clip_1")
        testDispatcher.scheduler.advanceUntilIdle()
        currentClip = viewModel.uiState.value.currentProject?.clips?.find { it.id == "clip_1" }
        assertEquals(1.35f, currentClip!!.scale, 0.01f)

        // 3. Rotação direta
        viewModel.updateClipTransform(rotation = 45f, clipId = "clip_1")
        testDispatcher.scheduler.advanceUntilIdle()
        currentClip = viewModel.uiState.value.currentProject?.clips?.find { it.id == "clip_1" }
        assertEquals(45f, currentClip!!.rotation, 0.01f)

        // 4. Resetar transformações
        viewModel.updateClipTransform(scale = 1.0f, rotation = 0f, positionX = 0f, positionY = 0f, clipId = "clip_1")
        testDispatcher.scheduler.advanceUntilIdle()
        currentClip = viewModel.uiState.value.currentProject?.clips?.find { it.id == "clip_1" }
        assertEquals(1.0f, currentClip!!.scale, 0.01f)
        assertEquals(0f, currentClip.rotation, 0.01f)
        assertEquals(0f, currentClip.positionX, 0.01f)
        assertEquals(0f, currentClip.positionY, 0.01f)
    }

    // =========================================================================
    // 3. MANIPULAÇÃO DIRETA DE TEXTOS E STICKERS
    // =========================================================================

    @Test
    fun test04_textAndStickerDirectManipulationAndMutualExclusion() {
        val project = createTestProjectWithElements()
        viewModel.selectProject(project)
        testDispatcher.scheduler.advanceUntilIdle()

        // Selecionar texto
        viewModel.selectTextOverlay("txt_1")
        assertEquals("txt_1", viewModel.uiState.value.selectedTextId)
        assertNull("Clip deve ser desselecionado ao selecionar texto", viewModel.uiState.value.selectedClipId)
        assertNull("Sticker deve ser desselecionado ao selecionar texto", viewModel.uiState.value.selectedStickerId)

        // Mover texto na prévia
        viewModel.updateTextOverlayPosition("txt_1", posX = 0.7f, posY = 0.2f)
        testDispatcher.scheduler.advanceUntilIdle()
        val text = viewModel.uiState.value.currentProject?.texts?.find { it.id == "txt_1" }
        assertNotNull(text)
        assertEquals(0.7f, text!!.posX, 0.01f)
        assertEquals(0.2f, text.posY, 0.01f)

        // Escalar e girar texto
        viewModel.updateTextOverlayScale("txt_1", 1.8f)
        viewModel.updateTextOverlayRotation("txt_1", 90f)
        testDispatcher.scheduler.advanceUntilIdle()
        val textUpdated = viewModel.uiState.value.currentProject?.texts?.find { it.id == "txt_1" }
        assertEquals(1.8f, textUpdated!!.scale, 0.01f)
        assertEquals(90f, textUpdated.rotation, 0.01f)

        // Selecionar sticker deve desmarcar o texto
        viewModel.selectSticker("stk_1")
        assertEquals("stk_1", viewModel.uiState.value.selectedStickerId)
        assertNull("Texto deve ser desmarcado ao selecionar sticker", viewModel.uiState.value.selectedTextId)

        // Mover sticker na prévia
        viewModel.updateStickerPosition("stk_1", posX = 0.8f, posY = 0.6f)
        viewModel.updateStickerScale("stk_1", 1.5f)
        viewModel.updateStickerRotation("stk_1", 30f)
        testDispatcher.scheduler.advanceUntilIdle()
        val sticker = viewModel.uiState.value.currentProject?.stickers?.find { it.id == "stk_1" }
        assertNotNull(sticker)
        assertEquals(0.8f, sticker!!.posX, 0.01f)
        assertEquals(0.6f, sticker.posY, 0.01f)
        assertEquals(1.5f, sticker.scale, 0.01f)
        assertEquals(30f, sticker.rotation, 0.01f)
    }

    // =========================================================================
    // 4. TIMELINE PLAYHEAD, ARRASTO DE ELEMENTOS E LIMPEZA
    // =========================================================================

    @Test
    fun test05_playheadDragSmoothnessAndReorderClips() {
        val project = createTestProjectWithElements()
        viewModel.selectProject(project)
        testDispatcher.scheduler.advanceUntilIdle()

        val totalMs = viewModel.getTotalDurationMs()
        assertEquals(9000L, totalMs)

        // Simulação de arraste contínuo de playhead
        for (pos in listOf(0L, 1200L, 2500L, 4800L, 6200L, 9000L)) {
            viewModel.seekTo(pos)
            assertEquals(pos, viewModel.uiState.value.currentPositionMs)
        }

        // Reordenar clipes na timeline (arraste do clip 0 para o index 1)
        val initialFirstClip = viewModel.uiState.value.currentProject?.clips?.get(0)?.id
        assertEquals("clip_1", initialFirstClip)

        viewModel.reorderClip(0, 1)
        testDispatcher.scheduler.advanceUntilIdle()

        val newFirstClip = viewModel.uiState.value.currentProject?.clips?.get(0)?.id
        val newSecondClip = viewModel.uiState.value.currentProject?.clips?.get(1)?.id
        assertEquals("clip_2", newFirstClip)
        assertEquals("clip_1", newSecondClip)
        assertEquals(9000L, viewModel.getTotalDurationMs())
    }

    @Test
    fun test06_clearAllSelections() {
        val project = createTestProjectWithElements()
        viewModel.selectProject(project)
        viewModel.selectClip("clip_1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("clip_1", viewModel.uiState.value.selectedClipId)

        viewModel.clearAllSelections()
        assertNull(viewModel.uiState.value.selectedClipId)
        assertNull(viewModel.uiState.value.selectedTextId)
        assertNull(viewModel.uiState.value.selectedStickerId)
        assertNull(viewModel.uiState.value.selectedAudioTrackId)
    }
}
