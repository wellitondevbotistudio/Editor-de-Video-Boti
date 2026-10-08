package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.repository.ProjectRepository
import com.example.model.*
import com.example.overlay.OverlayAnimationEngine
import com.example.overlay.StickerPresetsRepository
import com.example.template.TextTemplateRepository
import com.example.transition.TransitionEngine
import com.example.transition.TransitionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransitionsAndOverlaysTest {

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

    // ---------------- TRANSITIONS TESTS ----------------

    @Test
    fun testTransitionTypeParsing() {
        assertEquals(TransitionType.DISSOLVE, TransitionType.fromName("Dissolver"))
        assertEquals(TransitionType.DISSOLVE, TransitionType.fromName("dissolve"))
        assertEquals(TransitionType.FADE, TransitionType.fromName("Fade"))
        assertEquals(TransitionType.SLIDE_LEFT, TransitionType.fromName("Deslizar Esquerda"))
        assertEquals(TransitionType.SLIDE_RIGHT, TransitionType.fromName("Deslizar Direita"))
        assertEquals(TransitionType.SLIDE_UP, TransitionType.fromName("Deslizar Cima"))
        assertEquals(TransitionType.SLIDE_DOWN, TransitionType.fromName("Deslizar Baixo"))
        assertEquals(TransitionType.ZOOM, TransitionType.fromName("Zoom"))
        assertEquals(TransitionType.WIPE, TransitionType.fromName("Cortina"))
        assertEquals(TransitionType.CUT, TransitionType.fromName("Corte Seco"))
        assertEquals(TransitionType.CUT, TransitionType.fromName(null))
    }

    @Test
    fun testTransitionEngineClamping() {
        // Clipes de 4000ms e 6000ms: max duration = min(4000, 6000) / 2 = 2000ms
        val duration1 = TransitionEngine.clampTransitionDuration(
            clipADurationMs = 4000L,
            clipBDurationMs = 6000L,
            requestedDurationMs = 1500L
        )
        assertEquals(1500L, duration1)

        val durationExceeded = TransitionEngine.clampTransitionDuration(
            clipADurationMs = 4000L,
            clipBDurationMs = 6000L,
            requestedDurationMs = 3000L
        )
        assertEquals(2000L, durationExceeded)
    }

    @Test
    fun testTransitionEngineActiveDetection() {
        val clips = listOf(
            MediaClip(id = "c1", title = "Clip 1", uri = "uri1", durationMs = 4000L, transition = "Dissolver", transitionDurationMs = 1000L),
            MediaClip(id = "c2", title = "Clip 2", uri = "uri2", durationMs = 5000L)
        )

        // Split point é em 4000ms. Duração de transição = 1000ms (janela: 3500ms .. 4500ms)
        // Antes da transição (playhead = 2000ms)
        assertNull(TransitionEngine.findActiveTransition(clips, 2000L))

        // No início da transição (playhead = 3600ms)
        val activeStart = TransitionEngine.findActiveTransition(clips, 3600L)
        assertNotNull(activeStart)
        assertEquals(0, activeStart!!.clipAIndex)
        assertEquals(1, activeStart.clipBIndex)
        assertEquals(TransitionType.DISSOLVE, activeStart.type)
        assertEquals(0.1f, activeStart.progress, 0.05f)

        // No meio exato da transição (playhead = 4000ms)
        val activeMid = TransitionEngine.findActiveTransition(clips, 4000L)
        assertNotNull(activeMid)
        assertEquals(0.5f, activeMid!!.progress, 0.05f)
        assertEquals(0.5f, activeMid.transformA.alpha, 0.05f)
        assertEquals(0.5f, activeMid.transformB.alpha, 0.05f)

        // Depois da transição (playhead = 4800ms)
        assertNull(TransitionEngine.findActiveTransition(clips, 4800L))
    }

    @Test
    fun testTransitionCalculations() {
        // Dissolve
        val (dissolveA, dissolveB) = TransitionEngine.calculateTransforms(TransitionType.DISSOLVE, 0.3f)
        assertEquals(0.7f, dissolveA.alpha, 0.01f)
        assertEquals(0.3f, dissolveB.alpha, 0.01f)

        // Fade (dip to black no meio)
        val (fadeA_mid, fadeB_mid) = TransitionEngine.calculateTransforms(TransitionType.FADE, 0.5f)
        assertEquals(0.0f, fadeA_mid.alpha, 0.01f)
        assertEquals(0.0f, fadeB_mid.alpha, 0.01f)

        // Slide Left
        val (slideA, slideB) = TransitionEngine.calculateTransforms(TransitionType.SLIDE_LEFT, 0.5f)
        assertTrue(slideA.translationX < 0f)
        assertTrue(slideB.translationX > 0f)

        // Zoom
        val (zoomA, zoomB) = TransitionEngine.calculateTransforms(TransitionType.ZOOM, 0.5f)
        assertTrue(zoomA.scale > 1.0f)
        assertTrue(zoomB.scale < 1.0f)
    }

    // ---------------- OVERLAY ANIMATION ENGINE TESTS ----------------

    @Test
    fun testOverlayAnimationEngineTimeBoundaries() {
        val stateBefore = OverlayAnimationEngine.calculateTextState(
            playheadMs = 500L,
            startTimeMs = 1000L,
            durationMs = 3000L,
            fullText = "Boti"
        )
        assertFalse(stateBefore.isVisible)

        val stateInside = OverlayAnimationEngine.calculateTextState(
            playheadMs = 2000L,
            startTimeMs = 1000L,
            durationMs = 3000L,
            fullText = "Boti"
        )
        assertTrue(stateInside.isVisible)

        val stateAfter = OverlayAnimationEngine.calculateTextState(
            playheadMs = 4500L,
            startTimeMs = 1000L,
            durationMs = 3000L,
            fullText = "Boti"
        )
        assertFalse(stateAfter.isVisible)
    }

    @Test
    fun testOverlayAnimationEngineProgressiveText() {
        val fullText = "O editor Boti"

        // Modo Full: Sempre texto completo
        val stateFull = OverlayAnimationEngine.calculateTextState(
            playheadMs = 1500L,
            startTimeMs = 1000L,
            durationMs = 4000L,
            fullText = fullText,
            textAnimationMode = "Full"
        )
        assertEquals(fullText, stateFull.visibleText)

        // Modo Word: Palavras aparecem progressivamente
        val stateWordStart = OverlayAnimationEngine.calculateTextState(
            playheadMs = 1200L,
            startTimeMs = 1000L,
            durationMs = 4000L,
            fullText = fullText,
            textAnimationMode = "Word"
        )
        assertTrue(stateWordStart.visibleText.isNotBlank())
        assertTrue(stateWordStart.visibleText.length <= fullText.length)

        // Modo Letter: Máquina de escrever progressiva
        val stateLetterMid = OverlayAnimationEngine.calculateTextState(
            playheadMs = 2500L,
            startTimeMs = 1000L,
            durationMs = 4000L,
            fullText = fullText,
            textAnimationMode = "Letter"
        )
        assertTrue(stateLetterMid.visibleText.length in 1..fullText.length)
    }

    @Test
    fun testStickerAnimationState() {
        val stickerState = OverlayAnimationEngine.calculateStickerState(
            playheadMs = 1200L,
            startTimeMs = 1000L,
            durationMs = 4000L,
            baseScale = 1.2f,
            baseOpacity = 0.9f,
            animationIn = "Zoom"
        )
        assertTrue(stickerState.isVisible)
        assertTrue(stickerState.scale > 0f)
        assertTrue(stickerState.alpha in 0f..1f)
    }

    // ---------------- TEMPLATES & STICKER PRESETS TESTS ----------------

    @Test
    fun testTextTemplatesRepository() {
        val templates = TextTemplateRepository.templates
        assertTrue(templates.isNotEmpty())

        val titleTemplate = templates.find { it.id == "tpl_title_center" }
        assertNotNull(titleTemplate)

        val createdItem = titleTemplate!!.createOverlayItem(
            customText = "Meu Vídeo Incrível",
            startTimeMs = 500L,
            durationMs = 3000L
        )
        assertEquals("Meu Vídeo Incrível", createdItem.text)
        assertEquals(500L, createdItem.startTimeMs)
        assertEquals(3000L, createdItem.durationMs)
        assertEquals(titleTemplate.fontSizeSp, createdItem.fontSizeSp, 0.1f)
        assertEquals(titleTemplate.colorHex, createdItem.colorHex)
        assertTrue(createdItem.isVisible)
    }

    @Test
    fun testStickerPresetsRepository() {
        val presets = StickerPresetsRepository.presets
        assertTrue(presets.isNotEmpty())

        val gifPreset = presets.find { it.isGif }
        assertNotNull(gifPreset)

        val stickerItem = gifPreset!!.createStickerItem(startTimeMs = 1000L, durationMs = 4000L)
        assertEquals(1000L, stickerItem.startTimeMs)
        assertEquals(4000L, stickerItem.durationMs)
        assertTrue(stickerItem.isGif)
        assertTrue(stickerItem.isVisible)
    }

    // ---------------- ROOM PERSISTENCE OF OVERLAYS & TRANSITIONS ----------------

    @Test
    fun testPersistenceAndDuplicationOfOverlaysAndTransitions() = runBlocking {
        val clip1 = MediaClip(
            id = "c1",
            title = "Intro",
            uri = "uri_c1",
            durationMs = 4000L,
            transition = "Dissolver",
            transitionDurationMs = 1000L
        )
        val clip2 = MediaClip(
            id = "c2",
            title = "Corpo",
            uri = "uri_c2",
            durationMs = 6000L
        )

        val text1 = TextOverlayItem(
            id = "txt1",
            text = "Boti Vídeo Editor",
            startTimeMs = 500L,
            durationMs = 3500L,
            posX = 0.5f,
            posY = 0.3f,
            scale = 1.2f,
            rotation = 5f,
            colorHex = "#FACC15",
            bgHex = "#99000000",
            animationIn = "Zoom",
            animationOut = "Fade",
            textAnimationMode = "Word"
        )

        val sticker1 = StickerItem(
            id = "stk1",
            uri = "https://example.com/star.png",
            name = "Estrela",
            isGif = true,
            startTimeMs = 1000L,
            durationMs = 4000L,
            posX = 0.7f,
            posY = 0.2f,
            scale = 1.5f,
            animationIn = "Zoom",
            animationOut = "Fade"
        )

        val project = ProjectItem(
            id = "proj_test_overlays",
            title = "Projeto Overlays",
            clips = listOf(clip1, clip2),
            texts = listOf(text1),
            stickers = listOf(sticker1)
        )

        repository.saveProject(project)

        val loaded = repository.getAllProjects().first().find { it.id == project.id }
        assertNotNull(loaded)
        assertEquals(2, loaded!!.clips.size)
        assertEquals("Dissolver", loaded.clips[0].transition)
        assertEquals(1000L, loaded.clips[0].transitionDurationMs)

        // Verifica Text Overlay persistido
        assertEquals(1, loaded.texts.size)
        val loadedText = loaded.texts[0]
        assertEquals("txt1", loadedText.id)
        assertEquals("Boti Vídeo Editor", loadedText.text)
        assertEquals(500L, loadedText.startTimeMs)
        assertEquals(3500L, loadedText.durationMs)
        assertEquals(1.2f, loadedText.scale, 0.01f)
        assertEquals(5f, loadedText.rotation, 0.01f)
        assertEquals("#FACC15", loadedText.colorHex)
        assertEquals("Zoom", loadedText.animationIn)
        assertEquals("Word", loadedText.textAnimationMode)

        // Verifica Sticker persistido
        assertEquals(1, loaded.stickers.size)
        val loadedSticker = loaded.stickers[0]
        assertEquals("stk1", loadedSticker.id)
        assertEquals("Estrela", loadedSticker.name)
        assertTrue(loadedSticker.isGif)
        assertEquals(1.5f, loadedSticker.scale, 0.01f)
        assertEquals("Zoom", loadedSticker.animationIn)

        // Duplicação de projeto com Overlays
        val duplicated = repository.duplicateProject(loaded)
        assertNotEquals(loaded.id, duplicated.id)
        assertEquals(1, duplicated.texts.size)
        assertNotEquals(loadedText.id, duplicated.texts[0].id)
        assertEquals("Boti Vídeo Editor", duplicated.texts[0].text)

        assertEquals(1, duplicated.stickers.size)
        assertNotEquals(loadedSticker.id, duplicated.stickers[0].id)
        assertEquals("Estrela", duplicated.stickers[0].name)
    }
}
