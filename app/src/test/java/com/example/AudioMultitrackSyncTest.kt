package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.audio.WaveformGenerator
import com.example.data.db.AppDatabase
import com.example.data.repository.ProjectRepository
import com.example.model.AspectRatio
import com.example.model.AudioTrackItem
import com.example.model.MediaClip
import com.example.model.ProjectItem
import com.example.player.AudioSyncManager
import com.example.util.TimelineUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioMultitrackSyncTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: ProjectRepository
    private lateinit var waveformGenerator: WaveformGenerator

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ProjectRepository(db)
        waveformGenerator = WaveformGenerator(context)
    }

    @After
    @Throws(IOException::class)
    fun teardown() {
        db.close()
    }

    private fun createTestAudioTrack(
        id: String = "track1",
        name: String = "Test Song",
        durationMs: Long = 30000L,
        timelineStartMs: Long = 0L,
        trimStartMs: Long = 0L,
        trimEndMs: Long = 30000L,
        volume: Float = 0.8f,
        isMuted: Boolean = false
    ): AudioTrackItem {
        return AudioTrackItem(
            id = id,
            name = name,
            category = "Música",
            duration = "${durationMs / 1000}s",
            durationMs = durationMs,
            uri = "/data/projects/p1/audio/$id.mp3",
            localPath = "/data/projects/p1/audio/$id.mp3",
            originalName = "$name.mp3",
            mimeType = "audio/mpeg",
            volume = volume,
            isMuted = isMuted,
            timelineStartMs = timelineStartMs,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs
        )
    }

    @Test
    fun test01_multiTrackProjectDurationWithSequentialTracks() {
        // 1. Duração total do projeto com múltiplas faixas de áudio
        val t1 = createTestAudioTrack("t1", timelineStartMs = 0L, trimStartMs = 0L, trimEndMs = 10000L) // 0..10000
        val t2 = createTestAudioTrack("t2", timelineStartMs = 8000L, trimStartMs = 0L, trimEndMs = 12000L) // 8000..20000
        val clips = emptyList<MediaClip>()

        val totalDuration = TimelineUtils.calculateTotalProjectDuration(clips, listOf(t1, t2))
        // O áudio t2 termina em 8000 + 12000 = 20000ms
        assertEquals(20000L, totalDuration)
    }

    @Test
    fun test02_isAudioActiveAtTimelinePosition() {
        // 2. Verificação de ativação da faixa em pontos específicos da Timeline
        val track = createTestAudioTrack(
            timelineStartMs = 5000L,
            trimStartMs = 2000L,
            trimEndMs = 10000L
        ) // Duração efetiva: 8000ms -> Janela ativa na Timeline: [5000ms .. 13000ms)

        // Antes do início
        assertFalse(TimelineUtils.isAudioActiveAtTimelinePosition(track, 4999L))
        // No exato início
        assertTrue(TimelineUtils.isAudioActiveAtTimelinePosition(track, 5000L))
        // No meio da faixa
        assertTrue(TimelineUtils.isAudioActiveAtTimelinePosition(track, 9000L))
        // No exato final (limite exclusivo)
        assertFalse(TimelineUtils.isAudioActiveAtTimelinePosition(track, 13000L))
        // Após o final
        assertFalse(TimelineUtils.isAudioActiveAtTimelinePosition(track, 15000L))
    }

    @Test
    fun test03_timelinePositionToAudioSourcePosition() {
        // 3. Conversão de posição na Timeline para posição na fonte da mídia
        val track = createTestAudioTrack(
            timelineStartMs = 4000L,
            trimStartMs = 3000L,
            trimEndMs = 15000L
        )

        // Playhead em 7000ms na Timeline:
        // offset na timeline = 7000 - 4000 = 3000ms
        // posição na fonte = trimStart (3000) + offset (3000) = 6000ms
        val sourcePos = TimelineUtils.timelinePositionToAudioSourcePosition(track, 7000L)
        assertNotNull(sourcePos)
        assertEquals(6000L, sourcePos)

        // Fora da faixa deve retornar null
        assertNull(TimelineUtils.timelinePositionToAudioSourcePosition(track, 2000L))
        assertNull(TimelineUtils.timelinePositionToAudioSourcePosition(track, 20000L))
    }

    @Test
    fun test04_splitAudioTrackAtPlayheadExactCalculation() {
        // 4. Corte preciso de áudio no playhead (Split)
        val track = createTestAudioTrack(
            id = "track_a",
            durationMs = 40000L,
            timelineStartMs = 2000L,
            trimStartMs = 5000L,
            trimEndMs = 25000L
        ) // Janela na timeline: 2000 .. 22000 ms

        val playheadMs = 10000L // 8000ms dentro da faixa na timeline
        val (updatedTracks, newTrackId) = TimelineUtils.splitAudioTrackAtPlayhead(
            listOf(track),
            "track_a",
            playheadMs
        )

        assertNotNull(newTrackId)
        assertEquals(2, updatedTracks.size)

        val leftTrack = updatedTracks[0]
        val rightTrack = updatedTracks[1]

        // Posição de corte na fonte = trimStart (5000) + (10000 - 2000) = 13000ms
        assertEquals("track_a", leftTrack.id)
        assertEquals(2000L, leftTrack.timelineStartMs)
        assertEquals(5000L, leftTrack.trimStartMs)
        assertEquals(13000L, leftTrack.trimEndMs)

        // Faixa direita inicia no playhead (10000ms) com trimStart = 13000ms
        assertEquals(newTrackId, rightTrack.id)
        assertEquals(10000L, rightTrack.timelineStartMs)
        assertEquals(13000L, rightTrack.trimStartMs)
        assertEquals(25000L, rightTrack.trimEndMs)

        // Duração total combinada deve ser preservada (20000ms)
        val durLeft = TimelineUtils.calculateAudioEffectiveDuration(leftTrack)
        val durRight = TimelineUtils.calculateAudioEffectiveDuration(rightTrack)
        assertEquals(8000L, durLeft)
        assertEquals(12000L, durRight)
        assertEquals(20000L, durLeft + durRight)
    }

    @Test
    fun test05_splitAudioTrackRejectsBoundaries() {
        // 5. Rejeição de corte nas bordas ou fora do áudio
        val track = createTestAudioTrack(
            id = "t1",
            timelineStartMs = 1000L,
            trimStartMs = 0L,
            trimEndMs = 5000L
        )

        // Corte muito próximo do início (< MIN_AUDIO_DURATION_MS)
        val (resStart, idStart) = TimelineUtils.splitAudioTrackAtPlayhead(listOf(track), "t1", 1050L)
        assertNull(idStart)
        assertEquals(1, resStart.size)

        // Corte muito próximo do fim
        val (resEnd, idEnd) = TimelineUtils.splitAudioTrackAtPlayhead(listOf(track), "t1", 5950L)
        assertNull(idEnd)
        assertEquals(1, resEnd.size)

        // Corte completamente fora da faixa
        val (resOut, idOut) = TimelineUtils.splitAudioTrackAtPlayhead(listOf(track), "t1", 8000L)
        assertNull(idOut)
        assertEquals(1, resOut.size)
    }

    @Test
    fun test06_applyAudioTrimWithinBounds() {
        // 6. Aplicação de Trim de áudio com limites seguros
        val track = createTestAudioTrack(durationMs = 60000L, trimStartMs = 0L, trimEndMs = 60000L)

        val trimmed = TimelineUtils.applyAudioTrim(track, 5000L, 25000L)
        assertNotNull(trimmed)
        assertEquals(5000L, trimmed?.trimStartMs)
        assertEquals(25000L, trimmed?.trimEndMs)
        assertEquals(20000L, TimelineUtils.calculateAudioEffectiveDuration(trimmed!!))

        // Rejeitar corte inválido (fim menor que início)
        val invalidTrim = TimelineUtils.applyAudioTrim(track, 30000L, 20000L)
        assertNull(invalidTrim)
    }

    @Test
    fun test07_moveAudioTrackTimelineStart() {
        // 7. Reposicionamento temporal da faixa na Timeline
        val track = createTestAudioTrack(timelineStartMs = 0L)
        val moved = TimelineUtils.moveAudioTrackTimelineStart(track, 7500L)
        assertEquals(7500L, moved.timelineStartMs)

        // Clamp para não permitir valor negativo
        val clamped = TimelineUtils.moveAudioTrackTimelineStart(track, -500L)
        assertEquals(0L, clamped.timelineStartMs)
    }

    @Test
    fun test08_volumeAndMuteControls() {
        // 8. Controle de volume com clamp [0..1] e alternância de mudo
        val track = createTestAudioTrack(volume = 0.5f, isMuted = false)

        val updatedVol = TimelineUtils.setAudioTrackVolume(track, 0.95f)
        assertEquals(0.95f, updatedVol.volume, 0.001f)

        val clampedHigh = TimelineUtils.setAudioTrackVolume(track, 1.5f)
        assertEquals(1.0f, clampedHigh.volume, 0.001f)

        val clampedLow = TimelineUtils.setAudioTrackVolume(track, -0.2f)
        assertEquals(0.0f, clampedLow.volume, 0.001f)

        val muted = TimelineUtils.toggleAudioTrackMute(track)
        assertTrue(muted.isMuted)

        val unmuted = TimelineUtils.toggleAudioTrackMute(muted)
        assertFalse(unmuted.isMuted)
    }

    @Test
    fun test09_waveformGeneratorCachingAndBaseline() = runBlocking {
        // 9. Extração de Waveform com memória cache e fallback determinístico
        val dummyPath = File(context.cacheDir, "sample_audio.mp3").apply {
            writeBytes(ByteArray(1024) { (it % 128).toByte() })
        }.absolutePath

        val wave1 = waveformGenerator.getWaveform(dummyPath, sampleCount = 40)
        assertNotNull(wave1)
        assertEquals(40, wave1.size)
        // Amplitudes normalizadas entre 0.12f e 1.0f
        assertTrue(wave1.all { it in 0.10f..1.0f })

        // Chamada subsequente deve vir da memória cache (mesma instância)
        val wave2 = waveformGenerator.getWaveform(dummyPath, sampleCount = 40)
        assertEquals(wave1, wave2)
    }

    @Test
    fun test10_multitrackPersistenceInRoom() = runBlocking {
        // 10. Persistência de múltiplas faixas de áudio no Room
        val projId = "proj_multi_audio"
        val t1 = createTestAudioTrack("a1", name = "Voz", volume = 0.9f, timelineStartMs = 0L, trimStartMs = 1000L, trimEndMs = 5000L)
        val t2 = createTestAudioTrack("a2", name = "Trilha de Fundo", volume = 0.4f, isMuted = true, timelineStartMs = 3000L, trimStartMs = 0L, trimEndMs = 15000L)

        val project = ProjectItem(
            id = projId,
            title = "Projeto com Áudio Multifaixa",
            duration = "00:18",
            date = "Hoje",
            thumbUrl = "",
            aspectRatio = AspectRatio.RATIO_9_16,
            audios = listOf(t1, t2)
        )

        repository.saveProject(project)

        val loadedProjects = repository.getAllProjects().first()
        val loaded = loadedProjects.find { it.id == projId }
        assertNotNull(loaded)
        assertEquals(2, loaded?.audios?.size)

        val loadedT1 = loaded?.audios?.find { it.id == "a1" }
        assertNotNull(loadedT1)
        assertEquals("Voz", loadedT1?.name)
        assertEquals(0.9f, loadedT1?.volume ?: 0f, 0.001f)
        assertEquals(0L, loadedT1?.timelineStartMs)
        assertEquals(1000L, loadedT1?.trimStartMs)
        assertEquals(5000L, loadedT1?.trimEndMs)
        assertFalse(loadedT1?.isMuted ?: true)

        val loadedT2 = loaded?.audios?.find { it.id == "a2" }
        assertNotNull(loadedT2)
        assertEquals("Trilha de Fundo", loadedT2?.name)
        assertEquals(0.4f, loadedT2?.volume ?: 0f, 0.001f)
        assertTrue(loadedT2?.isMuted ?: false)
        assertEquals(3000L, loadedT2?.timelineStartMs)
    }

    @Test
    fun test11_audioTrackRemovalPreservesRemainingTracks() = runBlocking {
        // 11. Remoção de uma faixa preservando as faixas restantes
        val projId = "proj_remove_audio"
        val t1 = createTestAudioTrack("a1", name = "Efeito Sonoro")
        val t2 = createTestAudioTrack("a2", name = "Música Principal")
        val t3 = createTestAudioTrack("a3", name = "Locução")

        val project = ProjectItem(
            id = projId,
            title = "Projeto 3 Faixas",
            duration = "00:30",
            date = "Hoje",
            thumbUrl = "",
            audios = listOf(t1, t2, t3)
        )

        repository.saveProject(project)

        // Remover a2
        val updatedProject = project.copy(audios = listOf(t1, t3))
        repository.saveProject(updatedProject)

        val loaded = repository.getAllProjects().first().find { it.id == projId }
        assertNotNull(loaded)
        assertEquals(2, loaded?.audios?.size)
        assertEquals(listOf("a1", "a3"), loaded?.audios?.map { it.id })
    }

    @Test
    fun test12_audioSyncManagerPlayerConfiguration() {
        // 12. AudioSyncManager gerenciando configurações de volume e mute
        val syncManager = AudioSyncManager(context)
        val t1 = createTestAudioTrack("t1", volume = 0.7f, isMuted = false)
        val t2 = createTestAudioTrack("t2", volume = 0.5f, isMuted = true)

        syncManager.setTracks(listOf(t1, t2))
        syncManager.updateTrackVolumeAndMute("t1", 0.9f, false)
        syncManager.updateTrackVolumeAndMute("t2", 0.5f, true)

        syncManager.release()
    }
}
