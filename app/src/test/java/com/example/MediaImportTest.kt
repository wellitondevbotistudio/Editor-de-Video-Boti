package com.example

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.media.MediaMetadataExtractor
import com.example.data.media.MediaStorageManager
import com.example.data.repository.ProjectRepository
import com.example.model.AspectRatio
import com.example.model.MediaClip
import com.example.model.MediaType
import com.example.model.ProjectItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaImportTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: ProjectRepository
    private lateinit var storageManager: MediaStorageManager
    private lateinit var metadataExtractor: MediaMetadataExtractor

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ProjectRepository(db)
        storageManager = MediaStorageManager(context)
        metadataExtractor = MediaMetadataExtractor(context)
    }

    @After
    fun teardown() {
        db.close()
        // Clean test files
        File(context.filesDir, "projects").deleteRecursively()
    }

    private fun createSampleFile(fileName: String, content: ByteArray): File {
        val sample = File(context.cacheDir, fileName)
        FileOutputStream(sample).use { it.write(content) }
        return sample
    }

    @Test
    fun test1_videoUriCopiedCorrectlyToPrivateStorage() = runBlocking {
        // Teste 1: URI de vídeo -> copiado para armazenamento privado via streaming
        val sampleVideo = createSampleFile("sample_video.mp4", "SimulatedVideoData123456789".toByteArray())
        val uri = Uri.fromFile(sampleVideo)

        val result = storageManager.copyUriToProjectMedia("proj_test", uri)

        assertTrue(result.file.exists())
        assertEquals("sample_video.mp4", result.originalName)
        assertEquals(sampleVideo.length(), result.sizeBytes)
        assertTrue(result.file.absolutePath.contains("projects/proj_test/media"))
    }

    @Test
    fun test2_imageUriCopiedCorrectly() = runBlocking {
        // Teste 2: URI de imagem -> arquivo copiado corretamente
        val sampleImg = createSampleFile("photo_vacation.jpg", "ImagePixelDataHeader".toByteArray())
        val uri = Uri.fromFile(sampleImg)

        val result = storageManager.copyUriToProjectMedia("proj_test", uri)

        assertTrue(result.file.exists())
        assertEquals("photo_vacation.jpg", result.originalName)
        assertEquals(sampleImg.length(), result.sizeBytes)
    }

    @Test
    fun test3_audioUriCopiedCorrectly() = runBlocking {
        // Teste 3: URI de áudio -> arquivo copiado corretamente
        val sampleAudio = createSampleFile("soundtrack.mp3", "AudioBitrateSamplesStream".toByteArray())
        val uri = Uri.fromFile(sampleAudio)

        val result = storageManager.copyUriToProjectMedia("proj_test", uri)

        assertTrue(result.file.exists())
        assertEquals("soundtrack.mp3", result.originalName)
        assertEquals(sampleAudio.length(), result.sizeBytes)
    }

    @Test
    fun test4_metadataExtractionForMediaTypes() = runBlocking {
        // Teste 4: Extração de metadados para tipos de mídia
        val sampleImg = createSampleFile("test.png", "PNGFakeData".toByteArray())
        val storedImg = storageManager.copyUriToProjectMedia("proj_test", Uri.fromFile(sampleImg))

        val meta = metadataExtractor.extractMetadata("proj_test", storedImg.file, "image/png")

        assertEquals(MediaType.PHOTO, meta.mediaType)
        assertEquals(5000L, meta.durationMs) // 5s padrão para foto na timeline
        assertNotNull(meta.thumbnailPath)
    }

    @Test
    fun test5_corruptedOrZeroByteFileFailsGracefully() = runBlocking {
        // Teste 5: Arquivo vazio (0 bytes) -> lança erro e não deixa arquivo corrompido
        val emptyFile = createSampleFile("empty.mp4", ByteArray(0))
        val uri = Uri.fromFile(emptyFile)

        try {
            storageManager.copyUriToProjectMedia("proj_test", uri)
            fail("Deveria ter lançado IOException para arquivo de 0 bytes")
        } catch (_: Exception) {
            // Expected
        }

        val projectDir = storageManager.getProjectMediaDir("proj_test")
        val validFiles = projectDir.listFiles { _, name -> !name.startsWith("tmp_") }
        assertTrue(validFiles == null || validFiles.isEmpty())
    }

    @Test
    fun test6_multipleFilesAllProcessed() = runBlocking {
        // Teste 6: Múltiplos arquivos -> todos processados
        val f1 = createSampleFile("f1.mp4", "Vid1".toByteArray())
        val f2 = createSampleFile("f2.jpg", "Img2".toByteArray())
        val f3 = createSampleFile("f3.mp3", "Aud3".toByteArray())

        val res1 = storageManager.copyUriToProjectMedia("proj_multi", Uri.fromFile(f1))
        val res2 = storageManager.copyUriToProjectMedia("proj_multi", Uri.fromFile(f2))
        val res3 = storageManager.copyUriToProjectMedia("proj_multi", Uri.fromFile(f3))

        assertTrue(res1.file.exists())
        assertTrue(res2.file.exists())
        assertTrue(res3.file.exists())
    }

    @Test
    fun test7_partialFailureDoesNotBlockValidFiles() = runBlocking {
        // Teste 7: Um arquivo inválido não cancela os outros válidos
        val validFile1 = createSampleFile("valid1.mp4", "ValidContent1".toByteArray())
        val invalidFile = createSampleFile("empty_fail.mp4", ByteArray(0))
        val validFile2 = createSampleFile("valid2.jpg", "ValidContent2".toByteArray())

        var countSuccess = 0
        var countFail = 0

        listOf(validFile1, invalidFile, validFile2).forEach { file ->
            try {
                storageManager.copyUriToProjectMedia("proj_partial", Uri.fromFile(file))
                countSuccess++
            } catch (_: Exception) {
                countFail++
            }
        }

        assertEquals(2, countSuccess)
        assertEquals(1, countFail)
    }

    @Test
    fun test8_importedMediaPersistedInRoom() = runBlocking {
        // Teste 8: Mídia importada -> persistida no Room com caminho local e metadados
        val sample = createSampleFile("clip_real.mp4", "VideoDataBytes".toByteArray())
        val stored = storageManager.copyUriToProjectMedia("proj_room", Uri.fromFile(sample))

        val clip = MediaClip(
            id = "clip_persisted_1",
            title = stored.originalName,
            uri = stored.file.toURI().toString(),
            localPath = stored.file.absolutePath,
            thumbnailPath = stored.file.absolutePath,
            originalName = stored.originalName,
            mimeType = stored.mimeType,
            width = 1920,
            height = 1080,
            durationMs = 12500L,
            originalDurationMs = 12500L,
            fileSizeBytes = stored.sizeBytes
        )

        val project = ProjectItem(
            id = "proj_room",
            title = "Projeto com Mídia Real",
            duration = "00:12",
            date = "Hoje",
            thumbUrl = stored.file.absolutePath,
            clips = listOf(clip)
        )

        repository.saveProject(project)

        val retrieved = repository.getProjectByIdOnce("proj_room")
        assertNotNull(retrieved)
        assertEquals(1, retrieved?.clips?.size)

        val retrievedClip = retrieved?.clips?.first()
        assertEquals("clip_real.mp4", retrievedClip?.originalName)
        assertEquals(stored.file.absolutePath, retrievedClip?.localPath)
        assertEquals(1920, retrievedClip?.width)
        assertEquals(1080, retrievedClip?.height)
        assertEquals(12500L, retrievedClip?.durationMs)
    }

    @Test
    fun test9_closeAndReopenMaintainsLocalPath() = runBlocking {
        // Teste 9: Fechar/reabrir banco -> caminho local continua intacto
        val sample = createSampleFile("persistent.mp4", "PersistentStream".toByteArray())
        val stored = storageManager.copyUriToProjectMedia("proj_reopen", Uri.fromFile(sample))

        val project = ProjectItem(
            id = "proj_reopen",
            title = "Persistência Real",
            duration = "00:05",
            date = "Hoje",
            thumbUrl = "",
            clips = listOf(
                MediaClip(
                    id = "c_reopen",
                    title = "Persistent Clip",
                    uri = stored.file.toURI().toString(),
                    localPath = stored.file.absolutePath,
                    durationMs = 5000L
                )
            )
        )
        repository.saveProject(project)

        // Simulate reopening repository with database
        val newRepo = ProjectRepository(db)
        val loaded = newRepo.getProjectByIdOnce("proj_reopen")

        assertEquals(stored.file.absolutePath, loaded?.clips?.first()?.localPath)
        assertTrue(File(loaded?.clips?.first()?.localPath ?: "").exists())
    }

    @Test
    fun test10_duplicateProjectKeepsMediaReferences() = runBlocking {
        // Teste 10: Projeto duplicado -> referências de mídia continuam válidas
        val sample = createSampleFile("duplicated_video.mp4", "DuplicatedContent".toByteArray())
        val stored = storageManager.copyUriToProjectMedia("proj_orig", Uri.fromFile(sample))

        val orig = ProjectItem(
            id = "proj_orig",
            title = "Orig",
            duration = "00:10",
            date = "Hoje",
            thumbUrl = "",
            clips = listOf(
                MediaClip(
                    id = "c_orig",
                    title = "Orig Clip",
                    uri = stored.file.toURI().toString(),
                    localPath = stored.file.absolutePath,
                    durationMs = 10000L
                )
            )
        )
        repository.saveProject(orig)

        val copy = repository.duplicateProject(orig)
        assertNotEquals(orig.id, copy.id)
        assertEquals(stored.file.absolutePath, copy.clips.first().localPath)
        assertTrue(File(copy.clips.first().localPath).exists())
    }

    @Test
    fun test11_deleteProjectRemovesDbCascadeWithoutBreakingOthers() = runBlocking {
        // Teste 11: Excluir projeto -> limpa dados do banco sem afetar outros projetos
        val p1 = ProjectItem(id = "p1", title = "P1", duration = "00:01", date = "Hoje", thumbUrl = "")
        val p2 = ProjectItem(id = "p2", title = "P2", duration = "00:02", date = "Hoje", thumbUrl = "")
        repository.saveProject(p1)
        repository.saveProject(p2)

        repository.deleteProject("p1")

        assertNull(repository.getProjectByIdOnce("p1"))
        assertNotNull(repository.getProjectByIdOnce("p2"))
    }
}
