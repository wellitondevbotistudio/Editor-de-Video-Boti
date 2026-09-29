package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.export.*
import com.example.model.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoExportTest {

    private lateinit var context: Context
    private lateinit var tempExportDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        tempExportDir = File(context.cacheDir, "test_exports").apply {
            if (!exists()) mkdirs()
        }
    }

    @After
    fun teardown() {
        tempExportDir.deleteRecursively()
    }

    // -------------------------------------------------------------
    // CONFIG & DIMENSION TESTS
    // -------------------------------------------------------------

    @Test
    fun testExportConfigDimensions_16_9() {
        val config720 = VideoExportConfig(resolution = ExportResolution.RES_720P, aspectRatio = AspectRatio.RATIO_16_9)
        assertEquals(1280, config720.width)
        assertEquals(720, config720.height)

        val config1080 = VideoExportConfig(resolution = ExportResolution.RES_1080P, aspectRatio = AspectRatio.RATIO_16_9)
        assertEquals(1920, config1080.width)
        assertEquals(1080, config1080.height)

        val config4K = VideoExportConfig(resolution = ExportResolution.RES_4K, aspectRatio = AspectRatio.RATIO_16_9)
        assertEquals(3840, config4K.width)
        assertEquals(2160, config4K.height)
    }

    @Test
    fun testExportConfigDimensions_9_16() {
        val config720 = VideoExportConfig(resolution = ExportResolution.RES_720P, aspectRatio = AspectRatio.RATIO_9_16)
        assertEquals(720, config720.width)
        assertEquals(1280, config720.height)

        val config1080 = VideoExportConfig(resolution = ExportResolution.RES_1080P, aspectRatio = AspectRatio.RATIO_9_16)
        assertEquals(1080, config1080.width)
        assertEquals(1920, config1080.height)

        val config4K = VideoExportConfig(resolution = ExportResolution.RES_4K, aspectRatio = AspectRatio.RATIO_9_16)
        assertEquals(2160, config4K.width)
        assertEquals(3840, config4K.height)
    }

    @Test
    fun testExportConfigDimensions_1_1() {
        val config720 = VideoExportConfig(resolution = ExportResolution.RES_720P, aspectRatio = AspectRatio.RATIO_1_1)
        assertEquals(720, config720.width)
        assertEquals(720, config720.height)

        val config1080 = VideoExportConfig(resolution = ExportResolution.RES_1080P, aspectRatio = AspectRatio.RATIO_1_1)
        assertEquals(1080, config1080.width)
        assertEquals(1080, config1080.height)

        val config4K = VideoExportConfig(resolution = ExportResolution.RES_4K, aspectRatio = AspectRatio.RATIO_1_1)
        assertEquals(2160, config4K.width)
        assertEquals(2160, config4K.height)
    }

    @Test
    fun testExportResolutionParsing() {
        assertEquals(ExportResolution.RES_720P, ExportResolution.fromLabel("720p"))
        assertEquals(ExportResolution.RES_1080P, ExportResolution.fromLabel("1080p (Full HD)"))
        assertEquals(ExportResolution.RES_4K, ExportResolution.fromLabel("4K"))
        assertEquals(ExportResolution.RES_4K, ExportResolution.fromLabel("2160p"))
    }

    @Test
    fun testBitrateCalculationScalesWithFpsAndQuality() {
        val standard = VideoExportConfig(resolution = ExportResolution.RES_1080P, fps = 30, quality = "Normal")
        val high60 = VideoExportConfig(resolution = ExportResolution.RES_1080P, fps = 60, quality = "Máxima")

        assertTrue(high60.videoBitrate > standard.videoBitrate)
    }

    // -------------------------------------------------------------
    // TIMELINE EVALUATION TESTS
    // -------------------------------------------------------------

    @Test
    fun testExportTimelineCalculation() {
        val clips = listOf(
            MediaClip(id = "c1", title = "Clip A", uri = "uriA", durationMs = 3000L, transition = "Dissolver", transitionDurationMs = 1000L),
            MediaClip(id = "c2", title = "Clip B", uri = "uriB", durationMs = 4000L)
        )
        val project = ProjectItem(
            id = "p1",
            title = "Projeto Teste",
            clips = clips,
            texts = listOf(
                TextOverlayItem(id = "t1", text = "Texto Boti", startTimeMs = 500L, durationMs = 2000L)
            ),
            stickers = listOf(
                StickerItem(id = "s1", uri = "uriS", startTimeMs = 1000L, durationMs = 1500L)
            )
        )
        val config = VideoExportConfig(fps = 30)
        val timeline = ExportTimeline(project, config)

        assertEquals(7000L, timeline.totalDurationMs)
        val expectedFrames = (7.0 * 30).toLong()
        assertEquals(expectedFrames, timeline.totalFrames)

        // Frame no início (500ms): Clip A ativo, Texto t1 ativo, sem transição
        val snapEarly = timeline.evaluateAt(500L)
        assertNull(snapEarly.activeTransition)
        assertEquals("c1", snapEarly.activeClipInfo?.clip?.id)
        assertEquals(1, snapEarly.activeTexts.size)
        assertEquals("t1", snapEarly.activeTexts[0].item.id)

        // Frame na transição (3000ms): Transição ativa entre c1 e c2
        val snapTrans = timeline.evaluateAt(3000L)
        assertNotNull(snapTrans.activeTransition)
        assertEquals("c1", snapTrans.activeTransition?.clipA?.id)
        assertEquals("c2", snapTrans.activeTransition?.clipB?.id)

        // Frame após a transição (5000ms): Clip B ativo, sem transição, sem textos
        val snapLate = timeline.evaluateAt(5000L)
        assertNull(snapLate.activeTransition)
        assertEquals("c2", snapLate.activeClipInfo?.clip?.id)
        assertEquals(0, snapLate.activeTexts.size)
    }

    // -------------------------------------------------------------
    // FRAME RENDERER TESTS
    // -------------------------------------------------------------

    @Test
    fun testVideoFrameRenderer_rendersSuccessfully() {
        val config = VideoExportConfig(
            resolution = ExportResolution.RES_720P,
            aspectRatio = AspectRatio.RATIO_9_16,
            removeWatermark = false
        )
        val renderer = VideoFrameRenderer(context, config)

        val project = ProjectItem(
            id = "p1",
            title = "Render Test",
            clips = listOf(
                MediaClip(
                    id = "c1",
                    title = "Mirante",
                    uri = "uri1",
                    durationMs = 3000L,
                    brightness = 20f,
                    contrast = 15f,
                    saturation = 10f,
                    filter = "Vívido"
                )
            ),
            texts = listOf(
                TextOverlayItem(
                    id = "t1",
                    text = "Boti Video",
                    startTimeMs = 0L,
                    durationMs = 3000L,
                    colorHex = "#FFBB86FC",
                    bgHex = "#000000",
                    strokeColorHex = "#000000",
                    strokeWidth = 2f
                )
            ),
            activeVFX = listOf(
                VFXEffectItem(id = "v1", name = "Vinheta", category = "Retro", thumbUrl = "", intensity = 60f),
                VFXEffectItem(id = "v2", name = "VHS 90s", category = "Retro", thumbUrl = "", intensity = 50f)
            )
        )

        val timeline = ExportTimeline(project, config)
        val snapshot = timeline.evaluateAt(1000L)

        val outputBitmap = Bitmap.createBitmap(config.width, config.height, Bitmap.Config.ARGB_8888)
        renderer.render(snapshot, outputBitmap)

        assertNotNull(outputBitmap)
        assertEquals(config.width, outputBitmap.width)
        assertEquals(config.height, outputBitmap.height)
        assertFalse(outputBitmap.isRecycled)

        renderer.close()
        outputBitmap.recycle()
    }

    @Test
    fun testVideoFrameRenderer_transitionRendering() {
        val config = VideoExportConfig(
            resolution = ExportResolution.RES_720P,
            aspectRatio = AspectRatio.RATIO_16_9
        )
        val renderer = VideoFrameRenderer(context, config)

        val clips = listOf(
            MediaClip(id = "c1", title = "Clip 1", uri = "uri1", durationMs = 2000L, transition = "Dissolver", transitionDurationMs = 1000L),
            MediaClip(id = "c2", title = "Clip 2", uri = "uri2", durationMs = 2000L)
        )
        val project = ProjectItem(id = "p_trans", title = "Transição", clips = clips)
        val timeline = ExportTimeline(project, config)

        // Momento exato da transição (2000ms)
        val snapshot = timeline.evaluateAt(2000L)
        assertNotNull(snapshot.activeTransition)

        val outputBitmap = Bitmap.createBitmap(config.width, config.height, Bitmap.Config.ARGB_8888)
        renderer.render(snapshot, outputBitmap)

        assertEquals(1280, outputBitmap.width)
        assertEquals(720, outputBitmap.height)

        renderer.close()
        outputBitmap.recycle()
    }

    // -------------------------------------------------------------
    // REAL MP4 EXPORT PIPELINE TESTS
    // -------------------------------------------------------------

    @Test
    fun testRealMp4Export_generatesPlayableFile() = runBlocking {
        val manager = VideoExportManager(context)

        val project = ProjectItem(
            id = "proj_export",
            title = "Viagem_Montanha",
            aspectRatio = AspectRatio.RATIO_9_16,
            clips = listOf(
                MediaClip(id = "c1", title = "Serra", uri = "uri1", durationMs = 2000L),
                MediaClip(id = "c2", title = "Topo", uri = "uri2", durationMs = 2000L, transition = "Fade")
            ),
            texts = listOf(
                TextOverlayItem(id = "t1", text = "Serra Gaúcha 🌲", startTimeMs = 200L, durationMs = 2500L)
            ),
            activeVFX = listOf(
                VFXEffectItem(id = "vfx1", name = "Vinheta", category = "Estilo", thumbUrl = "", intensity = 40f)
            )
        )

        val targetFile = File(tempExportDir, "Boti_Viagem_Test.mp4")
        val config = VideoExportConfig(
            resolution = ExportResolution.RES_720P,
            aspectRatio = AspectRatio.RATIO_9_16,
            fps = 30,
            removeWatermark = true,
            customOutputFile = targetFile
        )

        val progressUpdates = mutableListOf<VideoExportProgress>()
        val result = manager.exportProject(
            project = project,
            config = config,
            onProgress = { progress ->
                progressUpdates.add(progress)
            }
        )

        // 1. O resultado deve indicar sucesso
        assertTrue("A exportação deve ser bem sucedida", result.success)
        assertNotNull("Arquivo de saída não pode ser nulo", result.outputFile)

        // 2. O arquivo MP4 físico deve existir no sistema de arquivos e ter tamanho real
        val exportedFile = result.outputFile!!
        assertTrue("O arquivo MP4 deve existir fisicamente no disco", exportedFile.exists())
        assertTrue("O arquivo MP4 deve ter tamanho maior que 0 bytes", exportedFile.length() > 0L)
        assertTrue("A extensão deve ser .mp4", exportedFile.name.endsWith(".mp4"))

        // 3. Dimensões e parâmetros no resultado devem bater com a configuração
        assertEquals(720, result.width)
        assertEquals(1280, result.height)
        assertEquals(30, result.fps)
        assertEquals(4000L, result.durationMs)

        // 4. Deve ter emitido eventos de progresso do início ao fim
        assertTrue("Deve registrar atualizações de progresso", progressUpdates.isNotEmpty())
        assertEquals(ExportPhase.COMPLETED, progressUpdates.last().phase)
        assertEquals(1.0f, progressUpdates.last().progress, 0.01f)
    }

    @Test
    fun testMp4Export_respectsAspectRatioLetterbox() {
        val config16_9 = VideoExportConfig(resolution = ExportResolution.RES_720P, aspectRatio = AspectRatio.RATIO_16_9)
        assertEquals(1280, config16_9.width)
        assertEquals(720, config16_9.height)

        val config1_1 = VideoExportConfig(resolution = ExportResolution.RES_720P, aspectRatio = AspectRatio.RATIO_1_1)
        assertEquals(720, config1_1.width)
        assertEquals(720, config1_1.height)
    }

    @Test
    fun testAudioRenderer_createsValidAudioFormat() {
        val config = VideoExportConfig()
        val audioRenderer = AudioExportRenderer(context, config)
        val format = audioRenderer.createAudioMediaFormat()

        assertEquals("audio/mp4a-latm", format.getString(android.media.MediaFormat.KEY_MIME))
        assertEquals(44100, format.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE))
        assertEquals(2, format.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT))
        assertNotNull(format.getByteBuffer("csd-0"))
    }
}
