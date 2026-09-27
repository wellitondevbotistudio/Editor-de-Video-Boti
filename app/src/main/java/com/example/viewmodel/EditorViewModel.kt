package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MockData
import com.example.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class ToolPanel {
    NONE,
    TRIM,
    SPEED,
    ADJUST,
    FILTER,
    AUDIO,
    TEXT,
    CAPTIONS,
    VFX,
    TRANSITION,
    CANVAS,
    LAYERS
}

data class EditorUiState(
    val projects: List<ProjectItem> = MockData.defaultProjects,
    val currentProject: ProjectItem? = MockData.defaultProjects.firstOrNull(),
    val currentPositionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val selectedClipId: String? = null,
    val activePanel: ToolPanel = ToolPanel.NONE,
    val isPremiumUser: Boolean = false,
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val exportSuccess: Boolean = false,
    val selectedExportOptions: ExportOptions = ExportOptions(),
    val lastExportedFile: String? = null,
    val feedbackMessage: String? = null
)

class EditorViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null
    private var exportJob: Job? = null

    init {
        // Initialize with default selected clip
        val initialClip = _uiState.value.currentProject?.clips?.firstOrNull()?.id
        _uiState.update { it.copy(selectedClipId = initialClip) }
    }

    fun getTotalDurationMs(): Long {
        val clips = _uiState.value.currentProject?.clips ?: return 10000L
        val total = clips.sumOf { (it.durationMs / it.speed).toLong() }
        return if (total > 0) total else 10000L
    }

    fun togglePlayback() {
        val currentlyPlaying = _uiState.value.isPlaying
        if (currentlyPlaying) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        playbackJob?.cancel()
        _uiState.update { it.copy(isPlaying = true) }
        playbackJob = viewModelScope.launch {
            val total = getTotalDurationMs()
            while (_uiState.value.isPlaying) {
                delay(50)
                _uiState.update { state ->
                    val next = state.currentPositionMs + 50
                    if (next >= total) {
                        state.copy(currentPositionMs = 0L, isPlaying = false)
                    } else {
                        state.copy(currentPositionMs = next)
                    }
                }
            }
        }
    }

    fun pausePlayback() {
        playbackJob?.cancel()
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun seekTo(positionMs: Long) {
        val total = getTotalDurationMs()
        val clamped = positionMs.coerceIn(0L, total)
        _uiState.update { it.copy(currentPositionMs = clamped) }
    }

    fun setActivePanel(panel: ToolPanel) {
        _uiState.update {
            it.copy(activePanel = if (it.activePanel == panel) ToolPanel.NONE else panel)
        }
    }

    fun selectClip(clipId: String) {
        _uiState.update { it.copy(selectedClipId = clipId) }
    }

    fun selectProject(project: ProjectItem) {
        pausePlayback()
        _uiState.update {
            it.copy(
                currentProject = project,
                currentPositionMs = 0L,
                selectedClipId = project.clips.firstOrNull()?.id,
                activePanel = ToolPanel.NONE
            )
        }
    }

    fun createNewProject(title: String = "Novo Projeto", aspectRatio: AspectRatio = AspectRatio.RATIO_9_16) {
        val newProj = ProjectItem(
            id = "proj_" + UUID.randomUUID().toString().take(6),
            title = title,
            duration = "00:15",
            date = "Agora",
            thumbUrl = "https://picsum.photos/seed/${System.currentTimeMillis()}/400/600",
            aspectRatio = aspectRatio,
            clips = listOf(
                MediaClip(
                    id = "c_" + UUID.randomUUID().toString().take(5),
                    title = "Clipe 1",
                    uri = "https://picsum.photos/seed/clip1/600/800",
                    durationMs = 15000L
                )
            )
        )
        _uiState.update { state ->
            val updated = listOf(newProj) + state.projects
            state.copy(
                projects = updated,
                currentProject = newProj,
                currentPositionMs = 0L,
                selectedClipId = newProj.clips.firstOrNull()?.id,
                activePanel = ToolPanel.NONE
            )
        }
    }

    fun createFromTemplate(template: VideoTemplateItem) {
        val clips = List(template.clipsCount) { index ->
            MediaClip(
                id = "tpl_clip_${index}_" + UUID.randomUUID().toString().take(4),
                title = "Cena ${index + 1}",
                uri = "https://picsum.photos/seed/${template.id}_clip_$index/600/800",
                durationMs = 4000L,
                transition = if (index > 0) "Fade" else null
            )
        }
        val newProj = ProjectItem(
            id = "proj_tpl_" + UUID.randomUUID().toString().take(6),
            title = template.title,
            duration = template.duration,
            date = "Agora",
            thumbUrl = template.thumbUrl,
            aspectRatio = AspectRatio.RATIO_9_16,
            clips = clips,
            audios = listOf(
                AudioTrackItem(
                    id = "tpl_audio",
                    name = "Beat Sync Track",
                    category = template.category,
                    duration = template.duration,
                    volume = 0.85f
                )
            ),
            texts = listOf(
                TextOverlayItem(
                    id = "t_tpl",
                    text = template.title,
                    startTimeMs = 500L,
                    durationMs = 3000L,
                    posY = 0.3f
                )
            )
        )
        _uiState.update { state ->
            state.copy(
                projects = listOf(newProj) + state.projects,
                currentProject = newProj,
                currentPositionMs = 0L,
                selectedClipId = newProj.clips.firstOrNull()?.id,
                activePanel = ToolPanel.NONE
            )
        }
    }

    fun deleteProject(projectId: String) {
        _uiState.update { state ->
            val updated = state.projects.filterNot { it.id == projectId }
            val nextCurrent = if (state.currentProject?.id == projectId) updated.firstOrNull() else state.currentProject
            state.copy(projects = updated, currentProject = nextCurrent)
        }
    }

    fun duplicateProject(project: ProjectItem) {
        val duplicated = project.copy(
            id = "proj_copy_" + UUID.randomUUID().toString().take(6),
            title = "${project.title} (Cópia)",
            date = "Agora"
        )
        _uiState.update { state ->
            state.copy(projects = listOf(duplicated) + state.projects)
        }
    }

    fun renameProject(projectId: String, newTitle: String) {
        if (newTitle.isBlank()) return
        _uiState.update { state ->
            val updatedProjects = state.projects.map {
                if (it.id == projectId) it.copy(title = newTitle) else it
            }
            val updatedCurrent = if (state.currentProject?.id == projectId) {
                state.currentProject.copy(title = newTitle)
            } else {
                state.currentProject
            }
            state.copy(projects = updatedProjects, currentProject = updatedCurrent)
        }
    }

    fun addClips(newClips: List<MediaClip>) {
        val cur = _uiState.value.currentProject ?: return
        val updatedClips = cur.clips + newClips
        val updatedProject = cur.copy(clips = updatedClips)
        updateCurrentProject(updatedProject)
    }

    fun deleteSelectedClip() {
        val cur = _uiState.value.currentProject ?: return
        val selId = _uiState.value.selectedClipId ?: return
        if (cur.clips.size <= 1) {
            setFeedback("O projeto precisa ter pelo menos um clipe.")
            return
        }
        val updatedClips = cur.clips.filterNot { it.id == selId }
        val updatedProject = cur.copy(clips = updatedClips)
        _uiState.update {
            it.copy(
                currentProject = updatedProject,
                selectedClipId = updatedClips.firstOrNull()?.id
            )
        }
        setFeedback("Clipe removido")
    }

    fun splitClipAtPlayhead() {
        val cur = _uiState.value.currentProject ?: return
        val selId = _uiState.value.selectedClipId ?: cur.clips.firstOrNull()?.id ?: return
        val clip = cur.clips.find { it.id == selId } ?: return

        val halfDuration = clip.durationMs / 2
        val clipPart1 = clip.copy(
            durationMs = halfDuration,
            title = "${clip.title} (Parte 1)"
        )
        val clipPart2 = clip.copy(
            id = "clip_split_" + UUID.randomUUID().toString().take(5),
            durationMs = halfDuration,
            title = "${clip.title} (Parte 2)"
        )

        val updatedClips = cur.clips.flatMap {
            if (it.id == selId) listOf(clipPart1, clipPart2) else listOf(it)
        }
        updateCurrentProject(cur.copy(clips = updatedClips))
        setFeedback("Clipe dividido com sucesso")
    }

    fun updateClipSpeed(speed: Float) {
        val cur = _uiState.value.currentProject ?: return
        val selId = _uiState.value.selectedClipId ?: return
        val updatedClips = cur.clips.map {
            if (it.id == selId) it.copy(speed = speed) else it
        }
        updateCurrentProject(cur.copy(clips = updatedClips))
    }

    fun updateClipFilter(filter: String) {
        val cur = _uiState.value.currentProject ?: return
        val selId = _uiState.value.selectedClipId ?: return
        val updatedClips = cur.clips.map {
            if (it.id == selId) it.copy(filter = filter) else it
        }
        updateCurrentProject(cur.copy(clips = updatedClips, activeFilter = filter))
    }

    fun updateClipAdjustments(brightness: Float, contrast: Float, saturation: Float) {
        val cur = _uiState.value.currentProject ?: return
        val selId = _uiState.value.selectedClipId ?: return
        val updatedClips = cur.clips.map {
            if (it.id == selId) it.copy(
                brightness = brightness,
                contrast = contrast,
                saturation = saturation
            ) else it
        }
        updateCurrentProject(cur.copy(clips = updatedClips))
    }

    fun updateClipTransition(transitionName: String?) {
        val cur = _uiState.value.currentProject ?: return
        val selId = _uiState.value.selectedClipId ?: return
        val updatedClips = cur.clips.map {
            if (it.id == selId) it.copy(transition = transitionName) else it
        }
        updateCurrentProject(cur.copy(clips = updatedClips))
        setFeedback(if (transitionName != null) "Transição $transitionName aplicada" else "Transição removida")
    }

    fun setAspectRatio(aspectRatio: AspectRatio) {
        val cur = _uiState.value.currentProject ?: return
        updateCurrentProject(cur.copy(aspectRatio = aspectRatio))
    }

    fun addTextOverlay(text: String, colorHex: String = "#FFFFFF") {
        if (text.isBlank()) return
        val cur = _uiState.value.currentProject ?: return
        val newText = TextOverlayItem(
            id = "txt_" + UUID.randomUUID().toString().take(5),
            text = text,
            startTimeMs = _uiState.value.currentPositionMs,
            durationMs = 4000L,
            colorHex = colorHex
        )
        updateCurrentProject(cur.copy(texts = cur.texts + newText))
        setFeedback("Texto adicionado")
    }

    fun removeTextOverlay(id: String) {
        val cur = _uiState.value.currentProject ?: return
        updateCurrentProject(cur.copy(texts = cur.texts.filterNot { it.id == id }))
    }

    fun addAudioTrack(track: AudioTrackItem) {
        val cur = _uiState.value.currentProject ?: return
        updateCurrentProject(cur.copy(audios = listOf(track)))
        setFeedback("Áudio '${track.name}' adicionado")
    }

    fun removeAudioTrack(id: String) {
        val cur = _uiState.value.currentProject ?: return
        updateCurrentProject(cur.copy(audios = cur.audios.filterNot { it.id == id }))
    }

    fun toggleVFX(vfx: VFXEffectItem) {
        val cur = _uiState.value.currentProject ?: return
        val exists = cur.activeVFX.any { it.id == vfx.id }
        val updated = if (exists) {
            cur.activeVFX.filterNot { it.id == vfx.id }
        } else {
            cur.activeVFX + vfx
        }
        updateCurrentProject(cur.copy(activeVFX = updated))
    }

    fun generateCaptions(language: String) {
        val cur = _uiState.value.currentProject ?: return
        val generated = listOf(
            SubtitleSegmentItem("sub1", "Bem-vindos ao meu vídeo!", 1000L, 3500L),
            SubtitleSegmentItem("sub2", "Hoje vamos explorar lugares incríveis.", 4000L, 8000L),
            SubtitleSegmentItem("sub3", "Não se esqueça de curtir e compartilhar!", 8500L, 12000L)
        )
        updateCurrentProject(cur.copy(subtitles = generated))
        setFeedback("Legendas geradas em $language com IA")
    }

    fun updateSubtitle(id: String, newText: String) {
        val cur = _uiState.value.currentProject ?: return
        val updated = cur.subtitles.map {
            if (it.id == id) it.copy(text = newText) else it
        }
        updateCurrentProject(cur.copy(subtitles = updated))
    }

    fun deleteSubtitle(id: String) {
        val cur = _uiState.value.currentProject ?: return
        updateCurrentProject(cur.copy(subtitles = cur.subtitles.filterNot { it.id == id }))
    }

    private fun updateCurrentProject(project: ProjectItem) {
        _uiState.update { state ->
            val updatedProjects = state.projects.map {
                if (it.id == project.id) project else it
            }
            state.copy(currentProject = project, projects = updatedProjects)
        }
    }

    fun startExport(options: ExportOptions) {
        exportJob?.cancel()
        _uiState.update {
            it.copy(
                isExporting = true,
                exportProgress = 0f,
                exportSuccess = false,
                selectedExportOptions = options
            )
        }
        exportJob = viewModelScope.launch {
            for (step in 1..20) {
                delay(150)
                val progress = step / 20f
                _uiState.update { it.copy(exportProgress = progress) }
            }
            _uiState.update {
                it.copy(
                    isExporting = false,
                    exportSuccess = true,
                    lastExportedFile = "Boti_Export_${System.currentTimeMillis()}.mp4"
                )
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        _uiState.update { it.copy(isExporting = false, exportProgress = 0f) }
    }

    fun resetExportState() {
        _uiState.update { it.copy(exportSuccess = false, isExporting = false, exportProgress = 0f) }
    }

    fun subscribePremium() {
        _uiState.update { it.copy(isPremiumUser = true) }
        setFeedback("Parabéns! Boti Pro ativado com sucesso.")
    }

    fun setFeedback(msg: String) {
        _uiState.update { it.copy(feedbackMessage = msg) }
        viewModelScope.launch {
            delay(3000)
            _uiState.update { if (it.feedbackMessage == msg) it.copy(feedbackMessage = null) else it }
        }
    }
}
