package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.repository.ProjectRepository
import com.example.model.*
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
class ProjectPersistenceTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: ProjectRepository
    private lateinit var context: Context

    @Before
    fun createDb() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ProjectRepository(db)
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun test1_createProjectAndRetrieve() = runBlocking {
        // Teste 1: Criar projeto -> persistir -> recuperar do banco
        val newProj = ProjectItem(
            id = "proj_test_1",
            title = "Meu Vídeo Incrível",
            duration = "00:30",
            date = "Hoje",
            thumbUrl = "https://picsum.photos/400/600",
            aspectRatio = AspectRatio.RATIO_9_16,
            clips = listOf(
                MediaClip(id = "c1", title = "Cena 1", uri = "file:///sample.mp4", durationMs = 10000L)
            )
        )

        repository.saveProject(newProj)

        val retrieved = repository.getProjectByIdOnce("proj_test_1")
        assertNotNull(retrieved)
        assertEquals("proj_test_1", retrieved?.id)
        assertEquals("Meu Vídeo Incrível", retrieved?.title)
        assertEquals(AspectRatio.RATIO_9_16, retrieved?.aspectRatio)
        assertEquals(1, retrieved?.clips?.size)
        assertEquals("Cena 1", retrieved?.clips?.first()?.title)
    }

    @Test
    fun test2_renameProjectAndPersist() = runBlocking {
        // Teste 2: Renomear -> persistir -> verificar nome atualizado
        val proj = ProjectItem(
            id = "proj_rename",
            title = "Nome Antigo",
            duration = "00:15",
            date = "Hoje",
            thumbUrl = "https://picsum.photos/400/600"
        )
        repository.saveProject(proj)

        repository.renameProject("proj_rename", "Nome Novo e Atualizado")

        val retrieved = repository.getProjectByIdOnce("proj_rename")
        assertNotNull(retrieved)
        assertEquals("Nome Novo e Atualizado", retrieved?.title)
    }

    @Test
    fun test3_duplicateProjectIndependent() = runBlocking {
        // Teste 3: Duplicar -> os dois projetos existem e têm IDs independentes
        val original = ProjectItem(
            id = "proj_orig",
            title = "Original",
            duration = "00:20",
            date = "Hoje",
            thumbUrl = "https://picsum.photos/400/600",
            clips = listOf(
                MediaClip(id = "c_orig", title = "Clipe Original", uri = "file:///orig.mp4", durationMs = 5000L)
            ),
            texts = listOf(
                TextOverlayItem(id = "t_orig", text = "Texto Original", startTimeMs = 0L)
            )
        )
        repository.saveProject(original)

        val duplicated = repository.duplicateProject(original)

        assertNotEquals(original.id, duplicated.id)
        assertNotEquals(original.clips.first().id, duplicated.clips.first().id)
        assertNotEquals(original.texts.first().id, duplicated.texts.first().id)
        assertEquals("Original (Cópia)", duplicated.title)

        val all = repository.getAllProjects().first()
        assertEquals(2, all.size)
        assertTrue(all.any { it.id == original.id })
        assertTrue(all.any { it.id == duplicated.id })
    }

    @Test
    fun test4_deleteProject() = runBlocking {
        // Teste 4: Excluir -> projeto continua excluído
        val proj = ProjectItem(
            id = "proj_delete",
            title = "Para Deletar",
            duration = "00:10",
            date = "Hoje",
            thumbUrl = "https://picsum.photos/400/600"
        )
        repository.saveProject(proj)
        assertNotNull(repository.getProjectByIdOnce("proj_delete"))

        repository.deleteProject("proj_delete")

        val retrieved = repository.getProjectByIdOnce("proj_delete")
        assertNull(retrieved)
    }

    @Test
    fun test5_changeAspectRatioAndPersist() = runBlocking {
        // Teste 5: Alterar proporção -> persistir -> proporção permanece
        val proj = ProjectItem(
            id = "proj_aspect",
            title = "Projeto Proporção",
            duration = "00:10",
            date = "Hoje",
            thumbUrl = "https://picsum.photos/400/600",
            aspectRatio = AspectRatio.RATIO_9_16
        )
        repository.saveProject(proj)

        val updated = proj.copy(aspectRatio = AspectRatio.RATIO_16_9)
        repository.saveProject(updated)

        val retrieved = repository.getProjectByIdOnce("proj_aspect")
        assertEquals(AspectRatio.RATIO_16_9, retrieved?.aspectRatio)
    }

    @Test
    fun test6_addClipTextSubtitleAndPersist() = runBlocking {
        // Teste 6: Adicionar clip/texto/legenda -> persistir -> dados continuam
        val proj = ProjectItem(
            id = "proj_full_timeline",
            title = "Timeline Completa",
            duration = "01:00",
            date = "Hoje",
            thumbUrl = "https://picsum.photos/400/600",
            clips = listOf(
                MediaClip(id = "c1", title = "Intro", uri = "file:///intro.mp4", durationMs = 12000L, speed = 1.5f, filter = "Vívido"),
                MediaClip(id = "c2", title = "Ação", uri = "file:///action.mp4", durationMs = 18000L, transition = "Zoom")
            ),
            audios = listOf(
                AudioTrackItem(id = "a1", name = "Trilha Sonora", category = "Pop", duration = "03:00", volume = 0.9f)
            ),
            texts = listOf(
                TextOverlayItem(id = "t1", text = "Título Principal", startTimeMs = 1000L, colorHex = "#7C3AED", fontSizeSp = 28f)
            ),
            subtitles = listOf(
                SubtitleSegmentItem(id = "s1", text = "Olá mundo da edição!", startTimeMs = 2000L, endTimeMs = 5000L)
            ),
            activeVFX = listOf(
                VFXEffectItem(id = "e1", name = "Glitch", category = "Distorção", thumbUrl = "", intensity = 75f)
            )
        )

        repository.saveProject(proj)

        val retrieved = repository.getProjectByIdOnce("proj_full_timeline")
        assertNotNull(retrieved)
        assertEquals(2, retrieved?.clips?.size)
        assertEquals(1.5f, retrieved?.clips?.first()?.speed)
        assertEquals("Vívido", retrieved?.clips?.first()?.filter)
        assertEquals("Zoom", retrieved?.clips?.get(1)?.transition)

        assertEquals(1, retrieved?.audios?.size)
        assertEquals("Trilha Sonora", retrieved?.audios?.first()?.name)

        assertEquals(1, retrieved?.texts?.size)
        assertEquals("Título Principal", retrieved?.texts?.first()?.text)
        assertEquals("#7C3AED", retrieved?.texts?.first()?.colorHex)

        assertEquals(1, retrieved?.subtitles?.size)
        assertEquals("Olá mundo da edição!", retrieved?.subtitles?.first()?.text)

        assertEquals(1, retrieved?.activeVFX?.size)
        assertEquals("Glitch", retrieved?.activeVFX?.first()?.name)
    }

    @Test
    fun test7_deleteProjectCascadesChildren() = runBlocking {
        // Teste 7: Excluir projeto -> verificar que entidades relacionadas também foram removidas no Room/SQLite
        val proj = ProjectItem(
            id = "proj_cascade",
            title = "Para Cascade",
            duration = "00:15",
            date = "Hoje",
            thumbUrl = "https://picsum.photos/400/600",
            clips = listOf(MediaClip(id = "clip_casc", title = "C1", uri = "uri", durationMs = 4000L)),
            audios = listOf(AudioTrackItem(id = "aud_casc", name = "A1", category = "Cat", duration = "01:00")),
            texts = listOf(TextOverlayItem(id = "txt_casc", text = "T1")),
            subtitles = listOf(SubtitleSegmentItem(id = "sub_casc", text = "S1", startTimeMs = 0L, endTimeMs = 2000L)),
            activeVFX = listOf(VFXEffectItem(id = "vfx_casc", name = "V1", category = "C", thumbUrl = ""))
        )
        repository.saveProject(proj)

        // Verify children exist in DAOs
        val clipsBefore = db.clipDao().getClipsForProject("proj_cascade").first()
        val audiosBefore = db.audioTrackDao().getAudioTracksForProject("proj_cascade").first()
        val textsBefore = db.textOverlayDao().getTextOverlaysForProject("proj_cascade").first()
        val subsBefore = db.subtitleDao().getSubtitlesForProject("proj_cascade").first()
        val vfxBefore = db.vfxDao().getVfxForProject("proj_cascade").first()

        assertEquals(1, clipsBefore.size)
        assertEquals(1, audiosBefore.size)
        assertEquals(1, textsBefore.size)
        assertEquals(1, subsBefore.size)
        assertEquals(1, vfxBefore.size)

        // Delete parent project
        repository.deleteProject("proj_cascade")

        // Assert all children tables were completely cleaned up by Foreign Key CASCADE
        val clipsAfter = db.clipDao().getClipsForProject("proj_cascade").first()
        val audiosAfter = db.audioTrackDao().getAudioTracksForProject("proj_cascade").first()
        val textsAfter = db.textOverlayDao().getTextOverlaysForProject("proj_cascade").first()
        val subsAfter = db.subtitleDao().getSubtitlesForProject("proj_cascade").first()
        val vfxAfter = db.vfxDao().getVfxForProject("proj_cascade").first()

        assertTrue(clipsAfter.isEmpty())
        assertTrue(audiosAfter.isEmpty())
        assertTrue(textsAfter.isEmpty())
        assertTrue(subsAfter.isEmpty())
        assertTrue(vfxAfter.isEmpty())
    }

    @Test
    fun test8_seedInitialDataOnlyOnce() = runBlocking {
        // Teste 8: Se o banco estiver vazio, semeia os dados. Se já tiver dados, preserva sem sobrescrever.
        assertEquals(0, db.projectDao().getProjectsCount())

        repository.seedInitialDataIfNeeded()
        val countAfterFirstSeed = db.projectDao().getProjectsCount()
        assertTrue(countAfterFirstSeed > 0)

        // User deletes all except one or adds their own
        repository.deleteProject("p1")
        val countAfterDelete = db.projectDao().getProjectsCount()
        assertEquals(countAfterFirstSeed - 1, countAfterDelete)

        // App starts again
        repository.seedInitialDataIfNeeded()

        // Count must remain unchanged (deleted project p1 is NOT recreated)
        assertEquals(countAfterDelete, db.projectDao().getProjectsCount())
        assertNull(repository.getProjectByIdOnce("p1"))
    }
}
