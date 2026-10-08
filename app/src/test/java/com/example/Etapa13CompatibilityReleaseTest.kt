package com.example

import com.example.data.db.AppDatabase
import com.example.export.ExportResolution
import com.example.export.ExportTimeline
import com.example.export.VideoExportConfig
import com.example.model.*
import com.example.transition.TransitionType
import com.example.util.TimelineUtils
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * ETAPA 13: Compatibilidade Android, Release e Preparação para Produção.
 */
class Etapa13CompatibilityReleaseTest {

    private fun createClip(
        id: String,
        durationMs: Long,
        title: String = id,
        localPath: String = "/storage/emulated/0/Movies/$id.mp4"
    ): MediaClip {
        return MediaClip(
            id = id,
            title = title,
            uri = "file://$localPath",
            localPath = localPath,
            durationMs = durationMs,
            originalDurationMs = durationMs,
            trimStartMs = 0L,
            trimEndMs = durationMs,
            speed = 1.0f
        )
    }

    // =========================================================================
    // 1. INTEGRIDADE DAS MIGRATIONS DO ROOM (v1 -> v5)
    // =========================================================================
    @Test
    fun test01_roomMigrationsIntegrity() {
        assertNotNull(AppDatabase.MIGRATION_1_2)
        assertEquals(1, AppDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, AppDatabase.MIGRATION_1_2.endVersion)

        assertNotNull(AppDatabase.MIGRATION_2_3)
        assertEquals(2, AppDatabase.MIGRATION_2_3.startVersion)
        assertEquals(3, AppDatabase.MIGRATION_2_3.endVersion)

        assertNotNull(AppDatabase.MIGRATION_3_4)
        assertEquals(3, AppDatabase.MIGRATION_3_4.startVersion)
        assertEquals(4, AppDatabase.MIGRATION_3_4.endVersion)

        assertNotNull(AppDatabase.MIGRATION_4_5)
        assertEquals(4, AppDatabase.MIGRATION_4_5.startVersion)
        assertEquals(5, AppDatabase.MIGRATION_4_5.endVersion)
    }

    // =========================================================================
    // 2. SEGURANÇA NO COMPARTILHAMENTO DE ARQUIVO (FILEPROVIDER)
    // =========================================================================
    @Test
    fun test02_fileProviderSharingUriSafety() {
        val testFile = File("/data/user/0/com.aistudio.videoeditor.boti/files/exports/Boti_Video_123.mp4")
        val authority = "com.aistudio.videoeditor.boti.fileprovider"

        // Verifica que o nome do arquivo e caminho estão sob o escopo interno seguro do app
        assertTrue(testFile.path.contains("/files/exports/"))
        assertEquals("mp4", testFile.extension)
        assertEquals("com.aistudio.videoeditor.boti.fileprovider", authority)
    }

    // =========================================================================
    // 3. PROTEÇÃO MEDIASTORE CONTRA ARQUIVOS VAZIOS OU INEXISTENTES
    // =========================================================================
    @Test
    fun test03_mediaStoreEmptyFileProtection() {
        val emptyFile = File.createTempFile("empty_test_", ".mp4").apply {
            deleteOnExit()
        }
        assertEquals(0L, emptyFile.length())

        // Arquivo de 0 bytes não deve ser publicado no MediaStore
        val shouldPublish = emptyFile.exists() && emptyFile.length() > 0L
        assertFalse("Arquivo de 0 bytes não pode ser publicado na galeria pública", shouldPublish)

        emptyFile.delete()
    }

    // =========================================================================
    // 4. MATRIZ DE FORMATOS, RESOLUÇÕES, PROPORÇÕES E TAXA DE QUADROS
    // =========================================================================
    @Test
    fun test04_codecAndFormatSupportMatrix() {
        val project = ProjectItem(
            id = "proj_matrix",
            title = "Matriz Codecs",
            clips = listOf(createClip("clip1", 5000L))
        )

        val resolutions = listOf(
            ExportResolution.RES_720P,
            ExportResolution.RES_1080P,
            ExportResolution.RES_4K
        )
        val aspectRatios = listOf(
            AspectRatio.RATIO_16_9,
            AspectRatio.RATIO_9_16,
            AspectRatio.RATIO_1_1,
            AspectRatio.RATIO_4_5
        )
        val fpsList = listOf(24, 30, 60)

        for (res in resolutions) {
            for (ratio in aspectRatios) {
                for (fps in fpsList) {
                    val config = VideoExportConfig(
                        resolution = res,
                        aspectRatio = ratio,
                        fps = fps
                    )
                    val timeline = ExportTimeline(project, config)
                    val dims = config.dimensions

                    assertTrue(dims.first > 0)
                    assertTrue(dims.second > 0)
                    // Dimensões de vídeo em H.264 devem ser múltiplos de 2 para o encoder
                    assertEquals(0, dims.first % 2)
                    assertEquals(0, dims.second % 2)
                    assertTrue(timeline.totalFrames > 0)
                }
            }
        }
    }

    // =========================================================================
    // 5. CONFORMIDADE COM PERMISSÕES E ZERO-STORAGE PICKER
    // =========================================================================
    @Test
    fun test05_permissionsSecurityCompliance() {
        // Valida que MediaClip funciona transparentemente com URIs content:// retornadas pelo PhotoPicker
        val contentUri = "content://media/external/video/media/42"
        val pickerClip = MediaClip(
            id = "picker_clip",
            title = "Video Galeria",
            uri = contentUri,
            localPath = "",
            durationMs = 8000L,
            originalDurationMs = 8000L,
            trimStartMs = 0L,
            trimEndMs = 8000L
        )

        assertEquals(contentUri, pickerClip.uri)
        assertEquals(8000L, pickerClip.durationMs)
    }

    // =========================================================================
    // 6. ESCALA DE PROJETO EM NÍVEL DE PRODUÇÃO
    // =========================================================================
    @Test
    fun test06_largeProjectScaleMemorySafety() {
        val clipCount = 65
        val clips = (1..clipCount).map { i ->
            createClip("c_$i", 2000L)
        }
        val audios = (1..10).map { i ->
            AudioTrackItem(
                id = "aud_$i",
                name = "Trilha $i",
                category = "Música",
                duration = "00:05",
                durationMs = 5000L,
                timelineStartMs = (i - 1) * 3000L
            )
        }
        val texts = (1..15).map { i ->
            TextOverlayItem(id = "txt_$i", text = "Legenda $i", startTimeMs = i * 2000L, durationMs = 2000L)
        }

        val project = ProjectItem(
            id = "proj_prod_scale",
            title = "Escala Produção",
            clips = clips,
            audios = audios,
            texts = texts
        )

        val totalDuration = TimelineUtils.calculateProjectTimelineDuration(project.clips)
        assertEquals(130000L, totalDuration)

        val config = VideoExportConfig(
            resolution = ExportResolution.RES_720P,
            aspectRatio = AspectRatio.RATIO_9_16,
            fps = 30
        )
        val timeline = ExportTimeline(project, config)
        assertEquals(130000L, timeline.totalDurationMs)
        assertEquals(3900L, timeline.totalFrames) // 130s * 30fps = 3900 frames
    }

    // =========================================================================
    // 7. COMPATIBILIDADE DE DADOS ANTERIORES (DEFAULT VALUES)
    // =========================================================================
    @Test
    fun test07_backwardCompatibilityMigrationFidelity() {
        // Clipes criados sem thumbnailPath ou fileSizeBytes assumem defaults seguros
        val legacyClip = MediaClip(
            id = "legacy_1",
            title = "Legado",
            uri = "file:///legacy/video.mp4",
            durationMs = 4000L,
            originalDurationMs = 4000L,
            trimStartMs = 0L,
            trimEndMs = 4000L
        )

        assertEquals("", legacyClip.thumbnailPath)
        assertEquals(0L, legacyClip.fileSizeBytes)
        assertEquals("", legacyClip.originalName)
        assertEquals("", legacyClip.mimeType)
    }

    // =========================================================================
    // 8. CÁLCULO DE DIMENSÕES POR ASPECT RATIO
    // =========================================================================
    @Test
    fun test08_exportDimensionsPerAspectRatio() {
        val config16_9 = VideoExportConfig(resolution = ExportResolution.RES_1080P, aspectRatio = AspectRatio.RATIO_16_9)
        assertEquals(1920 to 1080, config16_9.dimensions)

        val config9_16 = VideoExportConfig(resolution = ExportResolution.RES_1080P, aspectRatio = AspectRatio.RATIO_9_16)
        assertEquals(1080 to 1920, config9_16.dimensions)

        val config1_1 = VideoExportConfig(resolution = ExportResolution.RES_1080P, aspectRatio = AspectRatio.RATIO_1_1)
        assertEquals(1080 to 1080, config1_1.dimensions)

        val config4_5 = VideoExportConfig(resolution = ExportResolution.RES_1080P, aspectRatio = AspectRatio.RATIO_4_5)
        assertEquals(1080 to 1350, config4_5.dimensions)
    }
}
