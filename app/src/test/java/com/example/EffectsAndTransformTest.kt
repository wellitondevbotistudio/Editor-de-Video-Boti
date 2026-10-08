package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.repository.ProjectRepository
import com.example.effect.ClipEffectState
import com.example.effect.ColorMatrixPipeline
import com.example.model.AspectRatio
import com.example.model.MediaClip
import com.example.model.MediaType
import com.example.model.ProjectItem
import com.example.model.VFXEffectItem
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
class EffectsAndTransformTest {

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

    // ---------------- COLOR MATRIX PIPELINE TESTS ----------------

    @Test
    fun testBrightnessMatrixCalculations() {
        // Neutro: brilho = 0f
        val neutralMatrix = ColorMatrixPipeline.createBrightnessMatrix(0f)
        val arr0 = neutralMatrix.array
        assertEquals(0f, arr0[4], 0.001f) // R offset
        assertEquals(0f, arr0[9], 0.001f) // G offset
        assertEquals(0f, arr0[14], 0.001f) // B offset
        assertEquals(1f, arr0[0], 0.001f) // R scale

        // Brilho positivo (+50)
        val brightMatrix = ColorMatrixPipeline.createBrightnessMatrix(50f)
        val arr50 = brightMatrix.array
        assertEquals(127.5f, arr50[4], 0.01f)
        assertEquals(127.5f, arr50[9], 0.01f)
        assertEquals(127.5f, arr50[14], 0.01f)

        // Brilho negativo (-100)
        val darkMatrix = ColorMatrixPipeline.createBrightnessMatrix(-100f)
        val arrMinus100 = darkMatrix.array
        assertEquals(-255f, arrMinus100[4], 0.01f)
    }

    @Test
    fun testContrastMatrixCalculations() {
        // Neutro: contraste = 0f -> escala 1.0f, translate 0.0f
        val neutral = ColorMatrixPipeline.createContrastMatrix(0f)
        val arr0 = neutral.array
        assertEquals(1f, arr0[0], 0.001f)
        assertEquals(0f, arr0[4], 0.001f)

        // Contraste elevado (+100f) -> escala 2.5f, translate 128 * (1 - 2.5) = -192f
        val highContrast = ColorMatrixPipeline.createContrastMatrix(100f)
        val arrHigh = highContrast.array
        assertEquals(2.5f, arrHigh[0], 0.001f)
        assertEquals(-192f, arrHigh[4], 0.01f)

        // Contraste zerado (-100f) -> escala 0.0f, translate 128 * (1 - 0) = 128f
        val lowContrast = ColorMatrixPipeline.createContrastMatrix(-100f)
        val arrLow = lowContrast.array
        assertEquals(0f, arrLow[0], 0.001f)
        assertEquals(128f, arrLow[4], 0.01f)
    }

    @Test
    fun testSaturationMatrixCalculations() {
        // Neutro: saturação = 0f -> escala 1.0f
        val neutral = ColorMatrixPipeline.createSaturationMatrix(0f)
        val arr0 = neutral.array
        assertEquals(1f, arr0[0], 0.01f)
        assertEquals(0f, arr0[1], 0.01f)

        // Desaturado total (-100f) -> imagem monocromática real ITU-R BT.709
        val mono = ColorMatrixPipeline.createSaturationMatrix(-100f)
        val arrMono = mono.array
        // R row: [0.213, 0.715, 0.072]
        assertEquals(ColorMatrixPipeline.LUMA_R, arrMono[0], 0.01f)
        assertEquals(ColorMatrixPipeline.LUMA_G, arrMono[1], 0.01f)
        assertEquals(ColorMatrixPipeline.LUMA_B, arrMono[2], 0.01f)
        // G row must match
        assertEquals(ColorMatrixPipeline.LUMA_R, arrMono[5], 0.01f)
        assertEquals(ColorMatrixPipeline.LUMA_G, arrMono[6], 0.01f)
        assertEquals(ColorMatrixPipeline.LUMA_B, arrMono[7], 0.01f)
    }

    @Test
    fun testFilterPresets() {
        // Filtro Original produz identidade
        val orig = ColorMatrixPipeline.createFilterMatrix("Original")
        val arrOrig = orig.array
        assertEquals(1f, arrOrig[0], 0.001f)
        assertEquals(1f, arrOrig[6], 0.001f)
        assertEquals(1f, arrOrig[12], 0.001f)
        assertEquals(1f, arrOrig[18], 0.001f)

        // P&B
        val pb = ColorMatrixPipeline.createFilterMatrix("P&B")
        assertEquals(ColorMatrixPipeline.LUMA_R, pb.array[0], 0.01f)

        // Sépia
        val sepia = ColorMatrixPipeline.createFilterMatrix("Sépia")
        assertEquals(0.393f, sepia.array[0], 0.01f)
        assertEquals(0.769f, sepia.array[1], 0.01f)
        assertEquals(0.189f, sepia.array[2], 0.01f)

        // Outros filtros
        listOf("Vívido", "Quente", "Frio", "Cyberpunk", "Retrô", "Filme", "Suave", "Golden Hour").forEach { name ->
            val m = ColorMatrixPipeline.createFilterMatrix(name)
            assertNotNull(m)
            assertEquals(20, m.array.size)
            assertFalse("Matriz não pode conter NaN para $name", m.array.any { it.isNaN() })
        }
    }

    @Test
    fun testComposedMatrixPipeline() {
        // Combinação simultânea de Brilho + Contraste + Saturação + Filtro
        val composed = ColorMatrixPipeline.composeColorMatrix(
            brightness = 20f,
            contrast = 15f,
            saturation = -30f,
            filterName = "Quente"
        )
        assertNotNull(composed)
        assertEquals(20, composed.array.size)
        assertFalse(composed.array.any { it.isNaN() })
        assertFalse(composed.array.any { it.isInfinite() })

        // Conversão para Compose ColorMatrix
        val composeMatrix = ColorMatrixPipeline.toComposeColorMatrix(composed)
        assertEquals(20, composeMatrix.values.size)
    }

    // ---------------- CLIP EFFECT STATE TESTS ----------------

    @Test
    fun testClipEffectStateFlags() {
        val defaultState = ClipEffectState(clipId = "c1")
        assertFalse(defaultState.hasAdjustments)
        assertFalse(defaultState.hasFilter)
        assertFalse(defaultState.hasTransform)

        val adjustedState = defaultState.copy(brightness = 15f)
        assertTrue(adjustedState.hasAdjustments)

        val filteredState = defaultState.copy(filter = "Cyberpunk")
        assertTrue(filteredState.hasFilter)

        val transformedState = defaultState.copy(rotation = 90f, flipHorizontal = true)
        assertTrue(transformedState.hasTransform)
    }

    @Test
    fun testClipEffectStateFromClip() {
        val clip = MediaClip(
            id = "clip_100",
            title = "Vídeo Teste",
            uri = "file:///test.mp4",
            brightness = 25f,
            contrast = -10f,
            saturation = 40f,
            filter = "Filme",
            rotation = 180f,
            scale = 1.25f,
            flipHorizontal = true,
            flipVertical = false,
            opacity = 0.85f,
            positionX = 10f,
            positionY = -5f
        )

        val vfx = listOf(
            VFXEffectItem(id = "vfx1", name = "Vinheta", category = "Retrô", thumbUrl = "", intensity = 75f)
        )

        val state = ClipEffectState.fromClip(clip, vfx)
        assertEquals("clip_100", state.clipId)
        assertEquals(25f, state.brightness, 0.001f)
        assertEquals(-10f, state.contrast, 0.001f)
        assertEquals(40f, state.saturation, 0.001f)
        assertEquals("Filme", state.filter)
        assertEquals(180f, state.rotation, 0.001f)
        assertEquals(1.25f, state.scale, 0.001f)
        assertTrue(state.flipHorizontal)
        assertFalse(state.flipVertical)
        assertEquals(0.85f, state.opacity, 0.001f)
        assertEquals(10f, state.positionX, 0.001f)
        assertEquals(-5f, state.positionY, 0.001f)
        assertEquals(1, state.activeVfx.size)
        assertEquals("Vinheta", state.activeVfx.first().name)
    }

    // ---------------- ROOM PERSISTENCE OF EFFECTS & TRANSFORMS ----------------

    @Test
    fun testRoomPersistenceOfClipAdjustmentsAndTransforms() = runBlocking {
        val clip = MediaClip(
            id = "c_effects_1",
            title = "Clip com Efeitos",
            uri = "file:///clip.mp4",
            durationMs = 8000L,
            brightness = 35f,
            contrast = 45f,
            saturation = -20f,
            filter = "Sépia",
            rotation = 90f,
            scale = 1.5f,
            flipHorizontal = true,
            flipVertical = true,
            opacity = 0.9f,
            positionX = 15f,
            positionY = 25f
        )

        val vfx = VFXEffectItem(
            id = "vfx_vignette",
            name = "Vinheta",
            category = "Retrô",
            thumbUrl = "",
            intensity = 80f,
            isEnabled = true
        )

        val project = ProjectItem(
            id = "proj_effects_test",
            title = "Projeto com Efeitos",
            duration = "00:08",
            date = "Hoje",
            thumbUrl = "",
            aspectRatio = AspectRatio.RATIO_9_16,
            clips = listOf(clip),
            activeFilter = "Sépia",
            activeVFX = listOf(vfx)
        )

        repository.saveProject(project)

        val retrieved = repository.getProjectByIdOnce(project.id)
        assertNotNull(retrieved)
        assertEquals(1, retrieved!!.clips.size)

        val savedClip = retrieved.clips.first()
        assertEquals(35f, savedClip.brightness, 0.001f)
        assertEquals(45f, savedClip.contrast, 0.001f)
        assertEquals(-20f, savedClip.saturation, 0.001f)
        assertEquals("Sépia", savedClip.filter)
        assertEquals(90f, savedClip.rotation, 0.001f)
        assertEquals(1.5f, savedClip.scale, 0.001f)
        assertTrue(savedClip.flipHorizontal)
        assertTrue(savedClip.flipVertical)
        assertEquals(0.9f, savedClip.opacity, 0.001f)
        assertEquals(15f, savedClip.positionX, 0.001f)
        assertEquals(25f, savedClip.positionY, 0.001f)

        // Verifica persistência de VFX
        assertEquals(1, retrieved.activeVFX.size)
        val savedVfx = retrieved.activeVFX.first()
        assertEquals("vfx_vignette", savedVfx.id)
        assertEquals("Vinheta", savedVfx.name)
        assertEquals(80f, savedVfx.intensity, 0.001f)
        assertTrue(savedVfx.isEnabled)
    }

    @Test
    fun testRoomDuplicateProjectWithEffects() = runBlocking {
        val clip = MediaClip(
            id = "c_dup",
            title = "Clip Original",
            uri = "file:///clip.mp4",
            brightness = 50f,
            scale = 2.0f,
            flipHorizontal = true,
            filter = "Cyberpunk"
        )
        val project = ProjectItem(
            id = "p_original",
            title = "Projeto Base",
            duration = "00:05",
            date = "Hoje",
            thumbUrl = "",
            clips = listOf(clip)
        )
        repository.saveProject(project)

        val duplicated = repository.duplicateProject(project)
        assertNotNull(duplicated)
        assertNotEquals(project.id, duplicated.id)
        assertEquals(1, duplicated.clips.size)

        val dupClip = duplicated.clips.first()
        assertEquals(50f, dupClip.brightness, 0.001f)
        assertEquals(2.0f, dupClip.scale, 0.001f)
        assertTrue(dupClip.flipHorizontal)
        assertEquals("Cyberpunk", dupClip.filter)
    }
}
