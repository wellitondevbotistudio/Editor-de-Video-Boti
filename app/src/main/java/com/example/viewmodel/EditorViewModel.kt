package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BotiApplication
import com.example.data.repository.ProjectRepository
import com.example.model.*
import com.example.util.TimelineUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.ArrayDeque
import java.util.UUID

enum class ToolPanel {
    NONE,
    EDIT_TOOLS,
    TRIM,
    SPEED,
    ADJUST,
    FILTER,
    AUDIO,
    TEXT,
    TEMPLATES,
    STICKER,
    CAPTIONS,
    VFX,
    TRANSITION,
    CANVAS,
    LAYERS,
    TRANSFORM,
    CROP,
    FILES
}

data class EditorUiState(
    val projects: List<ProjectItem> = emptyList(),
    val currentProject: ProjectItem? = null,
    val currentPositionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val selectedClipId: String? = null,
    val selectedTextId: String? = null,
    val selectedStickerId: String? = null,
    val activePanel: ToolPanel = ToolPanel.NONE,
    val isPremiumUser: Boolean = false,
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val exportSuccess: Boolean = false,
    val selectedExportOptions: ExportOptions = ExportOptions(),
    val lastExportedFile: String? = null,
    val lastExportedFilePath: String? = null,
    val exportStatusMessage: String? = null,
    val feedbackMessage: String? = null,
    val isDatabaseInitialized: Boolean = false,
    val isImportingMedia: Boolean = false,
    val importProgress: Float = 0f,
    val importStatusMessage: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val selectedAudioTrackId: String? = null,
    val selectedVfxId: String? = null,
    val waveforms: Map<String, List<Float>> = emptyMap()
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ProjectRepository = (application as BotiApplication).projectRepository
    private val storageManager: com.example.data.media.MediaStorageManager = (application as BotiApplication).mediaStorageManager
    private val metadataExtractor: com.example.data.media.MediaMetadataExtractor = (application as BotiApplication).mediaMetadataExtractor

    val playerManager: com.example.player.EditorPlayerManager = com.example.player.EditorPlayerManager(application, viewModelScope)
    val waveformGenerator: com.example.data.audio.WaveformGenerator = com.example.data.audio.WaveformGenerator(application)
    val exportManager: com.example.export.VideoExportManager = com.example.export.VideoExportManager(application)
    private val templateRepository = com.example.data.repository.TemplateRepository()

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    // Tamanho do cache real do aplicativo (calculado via Dispatchers.IO)
    private val _cacheSizeMb = MutableStateFlow("Calculando...")
    val cacheSizeMb: StateFlow<String> = _cacheSizeMb.asStateFlow()

    // Planos de assinatura consumidos dinamicamente (prontos para Google Play Billing)
    private val _premiumPlans = MutableStateFlow(
        listOf(
            PremiumProductPlan(
                id = "boti_premium_monthly",
                title = "Mensal",
                price = "R$ 9,90",
                period = "/ mês",
                tag = "",
                isPopular = false,
                priceInCents = 990
            ),
            PremiumProductPlan(
                id = "boti_premium_annual",
                title = "Anual",
                price = "R$ 49,90",
                period = "/ ano",
                tag = "Mais popular",
                isPopular = true,
                priceInCents = 4990
            ),
            PremiumProductPlan(
                id = "boti_premium_lifetime",
                title = "Vitalício",
                price = "R$ 99,90",
                period = "/ vitalício",
                tag = "Melhor valor",
                isPopular = false,
                priceInCents = 9990
            )
        )
    )
    val premiumPlans: StateFlow<List<PremiumProductPlan>> = _premiumPlans.asStateFlow()

    // Lista de templates carregados dinamicamente via Repository
    private val _templates = MutableStateFlow<List<VideoTemplateItem>>(emptyList())
    val templates: StateFlow<List<VideoTemplateItem>> = _templates.asStateFlow()

    private var exportJob: Job? = null

    // Undo / Redo history stacks (Max 20 snapshots)
    private val undoStack = ArrayDeque<ProjectItem>()
    private val redoStack = ArrayDeque<ProjectItem>()
    private val MAX_HISTORY_SIZE = 20

    init {
        // Inicialização estritamente em background IO para não bloquear Splash ou UI
        viewModelScope.launch(Dispatchers.IO) {
            repository.seedInitialDataIfNeeded()
            _uiState.update { it.copy(isDatabaseInitialized = true) }
            calculateAppCacheSize()
            loadTemplates()

            // Restaura caminho persistido do último vídeo exportado caso ocorra recreation de processo
            val savedExportPath = repository.getLastExportedPath(null)
            if (!savedExportPath.isNullOrBlank()) {
                val f = File(savedExportPath)
                if (f.exists()) {
                    _uiState.update {
                        it.copy(
                            lastExportedFilePath = savedExportPath,
                            lastExportedFile = f.name
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            playerManager.playbackState.collect { playState ->
                _uiState.update {
                    it.copy(
                        isPlaying = playState.isPlaying,
                        currentPositionMs = playState.currentPositionMs,
                        feedbackMessage = playState.errorMessage ?: it.feedbackMessage
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.getAllProjects().collect { projectList ->
                _uiState.update { state ->
                    val updatedCurrent = if (state.currentProject != null) {
                        projectList.find { it.id == state.currentProject.id } ?: state.currentProject
                    } else {
                        projectList.firstOrNull()
                    }

                    val updatedClip = if (state.selectedClipId != null && updatedCurrent != null) {
                        if (updatedCurrent.clips.any { it.id == state.selectedClipId }) state.selectedClipId
                        else null
                    } else {
                        null
                    }

                    if (updatedCurrent != null) {
                        playerManager.setClipsAndAudios(
                            updatedCurrent.clips,
                            updatedCurrent.audios,
                            updatedCurrent.texts,
                            updatedCurrent.stickers,
                            updatedCurrent.activeVFX,
                            initialSeekPlayhead = state.currentPositionMs
                        )
                    } else {
                        playerManager.setClipsAndAudios(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
                    }

                    state.copy(
                        projects = projectList,
                        currentProject = updatedCurrent,
                        selectedClipId = updatedClip
                    )
                }
                _uiState.value.currentProject?.let { loadWaveformsForProject(it) }
            }
        }
    }

    // ---------------- HISTORY MANAGEMENT (UNDO / REDO) ----------------

    private fun pushUndoState(project: ProjectItem) {
        if (undoStack.size >= MAX_HISTORY_SIZE) {
            undoStack.removeFirst()
        }
        undoStack.addLast(project.deepCopy())
        redoStack.clear()
        updateHistoryUiFlags()
    }

    private fun updateHistoryUiFlags() {
        _uiState.update {
            it.copy(
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    fun undo() {
        val current = _uiState.value.currentProject ?: return
        if (undoStack.isEmpty()) return

        val previous = undoStack.removeLast()
        if (redoStack.size >= MAX_HISTORY_SIZE) {
            redoStack.removeFirst()
        }
        redoStack.addLast(current.deepCopy())

        updateHistoryUiFlags()
        applyRestoredProject(previous)
        setFeedback("Ação desfeita")
    }

    fun redo() {
        val current = _uiState.value.currentProject ?: return
        if (redoStack.isEmpty()) return

        val next = redoStack.removeLast()
        if (undoStack.size >= MAX_HISTORY_SIZE) {
            undoStack.removeFirst()
        }
        undoStack.addLast(current.deepCopy())

        updateHistoryUiFlags()
        applyRestoredProject(next)
        setFeedback("Ação refeita")
    }

    private fun applyRestoredProject(project: ProjectItem) {
        val total = TimelineUtils.calculateTotalProjectDuration(project.clips, project.audios, project.texts, project.stickers)
        val clampedPlayhead = _uiState.value.currentPositionMs.coerceIn(0L, total.coerceAtLeast(0L))
        val selectedId = if (project.clips.any { it.id == _uiState.value.selectedClipId }) {
            _uiState.value.selectedClipId
        } else {
            null
        }

        playerManager.setClipsAndAudios(
            project.clips,
            project.audios,
            project.texts,
            project.stickers,
            project.activeVFX,
            initialSeekPlayhead = clampedPlayhead
        )
        playerManager.exoPlayer.volume = if (project.isVideoMuted) 0f else 1f
        playerManager.audioSyncManager.isMasterMuted = project.isAudioMuted

        _uiState.update { state ->
            val updatedProjects = state.projects.map { if (it.id == project.id) project else it }
            state.copy(
                currentProject = project,
                projects = updatedProjects,
                currentPositionMs = clampedPlayhead,
                selectedClipId = selectedId
            )
        }

        viewModelScope.launch {
            repository.saveProject(project)
        }
    }

    private fun commitProjectChange(newProject: ProjectItem, registerUndo: Boolean = true) {
        val current = _uiState.value.currentProject
        if (registerUndo && current != null) {
            pushUndoState(current)
        }
        updateCurrentProject(newProject)
    }

    // ---------------- PLAYBACK & TIMELINE CALCULATIONS ----------------

    fun getTotalDurationMs(): Long {
        val proj = _uiState.value.currentProject ?: return 0L
        return TimelineUtils.calculateTotalProjectDuration(proj.clips, proj.audios, proj.texts, proj.stickers)
    }

    fun togglePlayback() {
        if (playerManager.playbackState.value.isPlaying) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    fun startPlayback() {
        playerManager.play()
    }

    fun pausePlayback() {
        playerManager.pause()
    }

    fun stopPlayback() {
        playerManager.stop()
    }

    fun seekTo(positionMs: Long) {
        val total = getTotalDurationMs()
        val clamped = positionMs.coerceIn(0L, maxOf(0L, total))
        _uiState.update { it.copy(currentPositionMs = clamped) }
        playerManager.seekTo(clamped)
    }

    fun rewind(stepMs: Long = 5000L) {
        playerManager.rewind(stepMs)
    }

    fun forward(stepMs: Long = 5000L) {
        playerManager.forward(stepMs)
    }

    fun previousClip() {
        playerManager.previousClip()
    }

    fun nextClip() {
        playerManager.nextClip()
    }

    fun setActivePanel(panel: ToolPanel) {
        _uiState.update {
            it.copy(activePanel = if (it.activePanel == panel) ToolPanel.NONE else panel)
        }
    }

    fun selectClip(clipId: String?, seekToClipStart: Boolean = false) {
        _uiState.update {
            it.copy(
                selectedClipId = clipId,
                selectedTextId = if (clipId != null) null else it.selectedTextId,
                selectedStickerId = if (clipId != null) null else it.selectedStickerId,
                selectedAudioTrackId = if (clipId != null) null else it.selectedAudioTrackId,
                selectedVfxId = if (clipId != null) null else it.selectedVfxId
            )
        }
        if (seekToClipStart && clipId != null) {
            val cur = _uiState.value.currentProject ?: return
            val idx = cur.clips.indexOfFirst { it.id == clipId }
            if (idx >= 0) {
                val start = TimelineUtils.getClipStartTimelineMs(cur.clips, idx)
                seekTo(start)
            }
        }
    }

    fun selectVFX(vfxId: String?) {
        _uiState.update {
            it.copy(
                selectedVfxId = vfxId,
                selectedClipId = if (vfxId != null) null else it.selectedClipId,
                selectedTextId = if (vfxId != null) null else it.selectedTextId,
                selectedStickerId = if (vfxId != null) null else it.selectedStickerId,
                selectedAudioTrackId = if (vfxId != null) null else it.selectedAudioTrackId
            )
        }
    }

    fun updateVfxTiming(vfxId: String, newStartMs: Long, newDurationMs: Long? = null) {
        val cur = _uiState.value.currentProject ?: return
        val updated = cur.activeVFX.map {
            if (it.id == vfxId) {
                it.copy(
                    startTimeMs = newStartMs.coerceAtLeast(0L),
                    durationMs = (newDurationMs ?: it.durationMs).coerceAtLeast(200L)
                )
            } else it
        }
        commitProjectChange(cur.copy(activeVFX = updated))
    }

    fun clearAllSelections() {
        _uiState.update {
            it.copy(
                selectedClipId = null,
                selectedTextId = null,
                selectedStickerId = null,
                selectedAudioTrackId = null,
                selectedVfxId = null
            )
        }
    }

    fun selectProject(project: ProjectItem) {
        pausePlayback()
        undoStack.clear()
        redoStack.clear()
        updateHistoryUiFlags()

        playerManager.setClipsAndAudios(
            project.clips,
            project.audios,
            project.texts,
            project.stickers,
            project.activeVFX,
            initialSeekPlayhead = 0L
        )
        playerManager.exoPlayer.volume = if (project.isVideoMuted) 0f else 1f
        playerManager.audioSyncManager.isMasterMuted = project.isAudioMuted

        _uiState.update {
            it.copy(
                currentProject = project,
                currentPositionMs = 0L,
                selectedClipId = null,
                selectedTextId = null,
                selectedStickerId = null,
                selectedAudioTrackId = null,
                selectedVfxId = null,
                activePanel = ToolPanel.NONE
            )
        }
    }

    // ---------------- PROJECT CRUD ----------------

    fun createNewProject(title: String = "Novo Projeto", aspectRatio: AspectRatio = AspectRatio.RATIO_9_16) {
        val newProj = ProjectItem(
            id = "proj_" + UUID.randomUUID().toString().take(6),
            title = title,
            duration = "00:00",
            date = "Hoje",
            thumbUrl = "",
            aspectRatio = aspectRatio,
            clips = emptyList()
        )

        undoStack.clear()
        redoStack.clear()
        updateHistoryUiFlags()

        _uiState.update { state ->
            state.copy(
                currentProject = newProj,
                currentPositionMs = 0L,
                selectedClipId = null,
                activePanel = ToolPanel.NONE
            )
        }

        viewModelScope.launch {
            repository.saveProject(newProj)
            setFeedback("Projeto criado")
        }
    }

    fun createFromTemplate(template: VideoTemplateItem) {
        val newProjId = "proj_tpl_" + UUID.randomUUID().toString().take(6)
        val clips = List(template.clipsCount) { index ->
            MediaClip(
                id = "tpl_clip_${index}_" + UUID.randomUUID().toString().take(4),
                title = "Cena ${index + 1}",
                uri = "https://picsum.photos/seed/${template.id}_clip_$index/600/800",
                durationMs = 4000L,
                originalDurationMs = 4000L,
                trimStartMs = 0L,
                trimEndMs = 4000L,
                transition = if (index > 0) "Fade" else null
            )
        }
        val newProj = ProjectItem(
            id = newProjId,
            title = template.title,
            duration = template.duration,
            date = "Hoje",
            thumbUrl = template.thumbUrl,
            aspectRatio = AspectRatio.RATIO_9_16,
            clips = clips,
            audios = listOf(
                AudioTrackItem(
                    id = "tpl_audio_" + UUID.randomUUID().toString().take(4),
                    name = "Beat Sync Track",
                    category = template.category,
                    duration = template.duration,
                    volume = 0.85f
                )
            ),
            texts = listOf(
                TextOverlayItem(
                    id = "t_tpl_" + UUID.randomUUID().toString().take(4),
                    text = template.title,
                    startTimeMs = 500L,
                    durationMs = 3000L,
                    posY = 0.3f
                )
            )
        )

        undoStack.clear()
        redoStack.clear()
        updateHistoryUiFlags()

        _uiState.update { state ->
            state.copy(
                currentProject = newProj,
                currentPositionMs = 0L,
                selectedClipId = null,
                activePanel = ToolPanel.NONE
            )
        }

        viewModelScope.launch {
            repository.saveProject(newProj)
            setFeedback("Modelo carregado e salvo")
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
            setFeedback("Projeto excluído")
        }
    }

    fun duplicateProject(project: ProjectItem) {
        viewModelScope.launch {
            val duplicated = repository.duplicateProject(project)
            setFeedback("Projeto duplicado com sucesso")
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
        viewModelScope.launch {
            repository.renameProject(projectId, newTitle)
            setFeedback("Projeto renomeado")
        }
    }

    // ---------------- MEDIA IMPORT ----------------

    fun importMediaUris(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        val cur = _uiState.value.currentProject ?: return
        val projectId = cur.id

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isImportingMedia = true,
                    importProgress = 0f,
                    importStatusMessage = "Iniciando importação de ${uris.size} arquivos..."
                )
            }

            var successCount = 0
            var failCount = 0
            val newClips = mutableListOf<MediaClip>()
            val newAudios = mutableListOf<AudioTrackItem>()

            uris.forEachIndexed { index, uri ->
                _uiState.update {
                    it.copy(
                        importProgress = (index + 1).toFloat() / uris.size,
                        importStatusMessage = "Processando ${index + 1} de ${uris.size}..."
                    )
                }

                try {
                    val stored = storageManager.copyUriToProjectMedia(projectId, uri)
                    val meta = metadataExtractor.extractMetadata(
                        projectId = projectId,
                        file = stored.file,
                        detectedMime = stored.mimeType
                    )

                    if (meta.mediaType == MediaType.AUDIO) {
                        val totalSec = meta.durationMs / 1000
                        val min = totalSec / 60
                        val sec = totalSec % 60
                        val formattedDuration = String.format("%02d:%02d", min, sec)

                        val audio = AudioTrackItem(
                            id = "audio_" + UUID.randomUUID().toString().take(6),
                            name = stored.originalName,
                            category = "Importado",
                            duration = formattedDuration,
                            durationMs = meta.durationMs,
                            uri = stored.file.toURI().toString(),
                            localPath = stored.file.absolutePath,
                            originalName = stored.originalName,
                            mimeType = meta.mimeType,
                            fileSizeBytes = stored.sizeBytes
                        )
                        newAudios.add(audio)
                    } else {
                        val clip = MediaClip(
                            id = "clip_" + UUID.randomUUID().toString().take(6),
                            title = stored.originalName,
                            uri = stored.file.toURI().toString(),
                            type = meta.mediaType,
                            localPath = stored.file.absolutePath,
                            thumbnailPath = meta.thumbnailPath ?: stored.file.absolutePath,
                            originalName = stored.originalName,
                            mimeType = meta.mimeType,
                            width = meta.width,
                            height = meta.height,
                            rotation = meta.rotation,
                            fileSizeBytes = stored.sizeBytes,
                            durationMs = meta.durationMs,
                            originalDurationMs = meta.durationMs,
                            trimStartMs = 0L,
                            trimEndMs = meta.durationMs
                        )
                        newClips.add(clip)
                    }
                    successCount++
                } catch (e: Exception) {
                    failCount++
                }
            }

            val latest = _uiState.value.currentProject
            if (latest != null && (newClips.isNotEmpty() || newAudios.isNotEmpty())) {
                val updatedProject = latest.copy(
                    clips = latest.clips + newClips,
                    audios = latest.audios + newAudios
                )
                commitProjectChange(updatedProject)
            }

            val feedback = when {
                failCount == 0 -> "$successCount arquivo(s) importado(s) com sucesso!"
                successCount > 0 -> "$successCount importado(s), $failCount arquivo(s) inválido(s)."
                else -> "Falha ao importar arquivos selecionados."
            }

            _uiState.update {
                it.copy(
                    isImportingMedia = false,
                    importProgress = 1f,
                    importStatusMessage = null
                )
            }
            setFeedback(feedback)
        }
    }

    // ---------------- TIMELINE OPERATIONS (SPLIT, TRIM, REORDER) ----------------

    fun splitSelectedElementAtPlayhead() {
        val state = _uiState.value
        when {
            state.selectedVfxId != null -> splitVfxAtPlayhead(state.selectedVfxId)
            state.selectedStickerId != null -> splitStickerAtPlayhead(state.selectedStickerId)
            state.selectedTextId != null -> splitTextOverlayAtPlayhead(state.selectedTextId)
            state.selectedAudioTrackId != null -> splitAudioTrackAtPlayhead(state.selectedAudioTrackId)
            else -> splitMainClipAtPlayhead()
        }
    }

    private fun splitVfxAtPlayhead(vfxId: String) {
        val cur = _uiState.value.currentProject ?: return
        val vfx = cur.activeVFX.find { it.id == vfxId } ?: return
        val playhead = _uiState.value.currentPositionMs
        val vfxStart = vfx.startTimeMs
        val vfxEnd = vfxStart + vfx.durationMs

        if (playhead <= vfxStart + 200L || playhead >= vfxEnd - 200L) {
            setFeedback("Não é possível dividir muito próximo das bordas do efeito.")
            return
        }

        val part1Duration = playhead - vfxStart
        val part2Duration = vfxEnd - playhead
        val part1 = vfx.copy(durationMs = part1Duration)
        val part2 = vfx.copy(id = "vfx_${java.util.UUID.randomUUID().toString().take(6)}", startTimeMs = playhead, durationMs = part2Duration)

        val updated = cur.activeVFX.flatMap {
            if (it.id == vfxId) listOf(part1, part2) else listOf(it)
        }
        commitProjectChange(cur.copy(activeVFX = updated))
        _uiState.update { it.copy(selectedVfxId = part2.id) }
        setFeedback("Efeito VFX dividido com precisão")
    }

    fun splitClipAtPlayhead() {
        splitSelectedElementAtPlayhead()
    }

    private fun splitMainClipAtPlayhead() {
        val cur = _uiState.value.currentProject ?: return
        if (cur.clips.isEmpty()) {
            setFeedback("Não há clipes na timeline para dividir.")
            return
        }

        val playhead = _uiState.value.currentPositionMs
        val splitResult = TimelineUtils.splitClipAtPlayhead(cur.clips, playhead)
        if (splitResult == null) {
            setFeedback("Não é possível dividir muito próximo do início ou fim do clipe.")
            return
        }

        val (updatedClips, newClipId) = splitResult
        val updatedProject = cur.copy(clips = updatedClips)
        commitProjectChange(updatedProject)

        _uiState.update {
            it.copy(selectedClipId = newClipId ?: it.selectedClipId)
        }
        setFeedback("Clipe dividido com precisão")
    }

    fun commitTrim(clipId: String, newTrimStartMs: Long, newTrimEndMs: Long) {
        val cur = _uiState.value.currentProject ?: return
        val clipIndex = cur.clips.indexOfFirst { it.id == clipId }
        if (clipIndex == -1) return
        val clip = cur.clips[clipIndex]
        val trimmed = TimelineUtils.applyTrim(clip, newTrimStartMs, newTrimEndMs)
        if (trimmed == null) {
            setFeedback("Intervalo de corte inválido.")
            return
        }

        val oldClipStartTimeline = TimelineUtils.getClipStartTimelineMs(cur.clips, clipIndex)
        val oldClipDuration = TimelineUtils.calculateClipTimelineDuration(clip)
        val oldClipEndTimeline = oldClipStartTimeline + oldClipDuration

        val updatedClips = cur.clips.map { if (it.id == clipId) trimmed else it }
        val updatedProject = cur.copy(clips = updatedClips)

        val newClipStartTimeline = TimelineUtils.getClipStartTimelineMs(updatedClips, clipIndex)
        val newClipDuration = TimelineUtils.calculateClipTimelineDuration(trimmed)
        val newClipEndTimeline = newClipStartTimeline + newClipDuration
        val newTotal = TimelineUtils.calculateTotalProjectDuration(updatedClips, cur.audios, cur.texts, cur.stickers)

        val currentPos = _uiState.value.currentPositionMs

        // Adjust playhead if inside the trimmed clip or beyond duration bounds
        val newPlayhead = when {
            currentPos in oldClipStartTimeline until oldClipEndTimeline -> {
                // Playhead was inside this clip before trim
                val oldOffset = currentPos - oldClipStartTimeline
                val oldSpeed = TimelineUtils.getSafeSpeed(clip)
                val oldSourcePos = TimelineUtils.getEffectiveTrimStart(clip) + (oldOffset * oldSpeed).toLong()

                when {
                    oldSourcePos < trimmed.trimStartMs -> {
                        // Playhead was in the part trimmed away at start -> move to new start of clip
                        newClipStartTimeline
                    }
                    oldSourcePos > trimmed.trimEndMs -> {
                        // Playhead was in the part trimmed away at end -> move to new end of clip
                        newClipEndTimeline.coerceAtMost(newTotal)
                    }
                    else -> {
                        // Map preserved source position to new timeline position
                        val newOffset = ((oldSourcePos - trimmed.trimStartMs).toFloat() / TimelineUtils.getSafeSpeed(trimmed)).toLong()
                        (newClipStartTimeline + newOffset).coerceIn(newClipStartTimeline, newClipEndTimeline)
                    }
                }
            }
            currentPos >= oldClipEndTimeline -> {
                // Playhead was in a subsequent clip; shift by delta duration
                val delta = newClipDuration - oldClipDuration
                (currentPos + delta).coerceIn(0L, newTotal)
            }
            else -> {
                // Playhead was before this clip; stays unchanged
                currentPos.coerceIn(0L, newTotal)
            }
        }

        commitProjectChange(updatedProject)
        seekTo(newPlayhead)
        setFeedback("Corte aplicado")
    }

    fun duplicateSelectedElement() {
        val state = _uiState.value
        when {
            state.selectedStickerId != null -> duplicateSticker(state.selectedStickerId)
            state.selectedTextId != null -> duplicateTextOverlay(state.selectedTextId)
            state.selectedAudioTrackId != null -> duplicateAudioTrack(state.selectedAudioTrackId)
            state.selectedClipId != null -> duplicateMainClip(state.selectedClipId)
            else -> {
                val cur = _uiState.value.currentProject ?: return
                cur.clips.firstOrNull()?.let { duplicateMainClip(it.id) }
            }
        }
    }

    fun duplicateClip(clipId: String? = null) {
        if (clipId != null) {
            duplicateMainClip(clipId)
        } else {
            duplicateSelectedElement()
        }
    }

    private fun duplicateMainClip(clipId: String) {
        val cur = _uiState.value.currentProject ?: return
        val result = TimelineUtils.duplicateClip(cur.clips, clipId) ?: return
        val (updatedClips, newClipId) = result
        val updatedProject = cur.copy(clips = updatedClips)
        commitProjectChange(updatedProject)
        _uiState.update { it.copy(selectedClipId = newClipId) }
        setFeedback("Clipe duplicado com sucesso")
    }

    fun reorderClip(fromIndex: Int, toIndex: Int) {
        val cur = _uiState.value.currentProject ?: return
        val updatedClips = TimelineUtils.reorderClips(cur.clips, fromIndex, toIndex)
        if (updatedClips == cur.clips) return

        val updatedProject = cur.copy(clips = updatedClips)
        commitProjectChange(updatedProject)
        setFeedback("Clipe reordenado")
    }

    fun moveClipLeft(clipId: String) {
        val cur = _uiState.value.currentProject ?: return
        val index = cur.clips.indexOfFirst { it.id == clipId }
        if (index > 0) {
            reorderClip(index, index - 1)
        }
    }

    fun moveClipRight(clipId: String) {
        val cur = _uiState.value.currentProject ?: return
        val index = cur.clips.indexOfFirst { it.id == clipId }
        if (index != -1 && index < cur.clips.lastIndex) {
            reorderClip(index, index + 1)
        }
    }

    fun addClips(newClips: List<MediaClip>) {
        val cur = _uiState.value.currentProject ?: return
        val updatedClips = cur.clips + newClips
        val updatedProject = cur.copy(clips = updatedClips)
        commitProjectChange(updatedProject)
    }

    fun deleteClip(clipId: String? = null) {
        val cur = _uiState.value.currentProject ?: return
        val targetId = clipId ?: _uiState.value.selectedClipId ?: return
        val index = cur.clips.indexOfFirst { it.id == targetId }
        if (index == -1) return

        val clipToDelete = cur.clips[index]
        val clipStart = TimelineUtils.getClipStartTimelineMs(cur.clips, index)
        val clipDuration = TimelineUtils.calculateClipTimelineDuration(clipToDelete)
        val clipEnd = clipStart + clipDuration

        val updatedClips = TimelineUtils.removeClip(cur.clips, index)
        val newTotal = TimelineUtils.calculateTotalProjectDuration(updatedClips, cur.audios, cur.texts, cur.stickers)
        val currentPos = _uiState.value.currentPositionMs

        // Calculate new playhead position
        val newPlayhead = when {
            updatedClips.isEmpty() -> 0L
            currentPos in clipStart until clipEnd -> {
                // Was inside deleted clip -> position at start of this clip slot
                clipStart.coerceIn(0L, newTotal)
            }
            currentPos >= clipEnd -> {
                // Was after deleted clip -> shift backward by deleted clip duration
                (currentPos - clipDuration).coerceIn(0L, newTotal)
            }
            else -> {
                // Was before deleted clip -> keep currentPos
                currentPos.coerceIn(0L, newTotal)
            }
        }

        // Determine new selected clip
        val newSelectedId = when {
            updatedClips.isEmpty() -> null
            index < updatedClips.size -> updatedClips[index].id
            else -> updatedClips.last().id
        }

        val updatedProject = cur.copy(clips = updatedClips)
        commitProjectChange(updatedProject)

        _uiState.update {
            it.copy(
                selectedClipId = newSelectedId,
                currentPositionMs = newPlayhead
            )
        }

        if (updatedClips.isEmpty()) {
            playerManager.stop()
        } else {
            seekTo(newPlayhead)
        }
        setFeedback("Clipe removido")
    }

    fun deleteSelectedElement() {
        val state = _uiState.value
        when {
            state.selectedVfxId != null -> {
                val cur = _uiState.value.currentProject ?: return
                val updated = cur.activeVFX.filterNot { it.id == state.selectedVfxId }
                commitProjectChange(cur.copy(activeVFX = updated))
                _uiState.update { it.copy(selectedVfxId = null) }
                setFeedback("Efeito VFX removido")
            }
            state.selectedStickerId != null -> removeSticker(state.selectedStickerId)
            state.selectedTextId != null -> removeTextOverlay(state.selectedTextId)
            state.selectedAudioTrackId != null -> removeAudioTrack(state.selectedAudioTrackId)
            else -> deleteClip(state.selectedClipId)
        }
    }

    fun deleteSelectedClip() {
        deleteSelectedElement()
    }

    fun updateClipSpeed(speed: Float, clipId: String? = null) {
        val cur = _uiState.value.currentProject ?: return
        val targetId = clipId ?: _uiState.value.selectedClipId ?: cur.clips.firstOrNull()?.id ?: return
        val updatedClips = cur.clips.map { clip ->
            if (clip.id == targetId) {
                val updatedClip = clip.copy(speed = speed)
                val newDuration = TimelineUtils.calculateClipTimelineDuration(updatedClip)
                updatedClip.copy(durationMs = newDuration)
            } else clip
        }
        commitProjectChange(cur.copy(clips = updatedClips))
        if (playerManager.playbackState.value.currentClipId == targetId) {
            playerManager.exoPlayer.setPlaybackParameters(androidx.media3.common.PlaybackParameters(speed))
        }
        setFeedback("Velocidade ajustada: ${speed}x")
    }

    fun updateClipVolume(volume: Float, clipId: String? = null) {
        val cur = _uiState.value.currentProject ?: return
        val targetId = clipId ?: _uiState.value.selectedClipId ?: cur.clips.firstOrNull()?.id ?: return
        val updatedClips = cur.clips.map {
            if (it.id == targetId) it.copy(volume = volume.coerceIn(0f, 1f)) else it
        }
        commitProjectChange(cur.copy(clips = updatedClips))
        if (playerManager.playbackState.value.currentClipId == targetId && !cur.isVideoMuted) {
            playerManager.exoPlayer.volume = volume.coerceIn(0f, 1f)
        }
        setFeedback("Volume do clipe: ${(volume * 100).toInt()}%")
    }

    fun updateClipFilter(filter: String, clipId: String? = null) {
        val cur = _uiState.value.currentProject ?: return
        val targetId = clipId ?: _uiState.value.selectedClipId ?: cur.clips.firstOrNull()?.id ?: return
        val updatedClips = cur.clips.map {
            if (it.id == targetId) it.copy(filter = filter) else it
        }
        commitProjectChange(cur.copy(clips = updatedClips, activeFilter = filter))
        setFeedback("Filtro '$filter' aplicado")
    }

    fun removeClipFilter(clipId: String? = null) {
        val cur = _uiState.value.currentProject ?: return
        val targetId = clipId ?: _uiState.value.selectedClipId ?: return
        val updatedClips = cur.clips.map {
            if (it.id == targetId) it.copy(filter = "Original") else it
        }
        commitProjectChange(cur.copy(clips = updatedClips, activeFilter = "Original"))
        setFeedback("Filtro removido")
    }

    fun updateClipAdjustments(brightness: Float, contrast: Float, saturation: Float, clipId: String? = null) {
        val cur = _uiState.value.currentProject ?: return
        val targetId = clipId ?: _uiState.value.selectedClipId ?: cur.clips.firstOrNull()?.id ?: return
        val updatedClips = cur.clips.map {
            if (it.id == targetId) it.copy(
                brightness = brightness.coerceIn(-100f, 100f),
                contrast = contrast.coerceIn(-100f, 100f),
                saturation = saturation.coerceIn(-100f, 100f)
            ) else it
        }
        commitProjectChange(cur.copy(clips = updatedClips))
    }

    // ---------------- CONTROLES DE CAMADAS / FAIXAS ----------------

    fun toggleVideoVisibility() {
        val cur = _uiState.value.currentProject ?: return
        val newVis = !cur.isVideoVisible
        commitProjectChange(cur.copy(isVideoVisible = newVis))
        setFeedback(if (newVis) "Faixa de vídeo visível" else "Faixa de vídeo oculta")
    }

    fun toggleVideoMute() {
        val cur = _uiState.value.currentProject ?: return
        val newMute = !cur.isVideoMuted
        val updated = cur.copy(isVideoMuted = newMute)
        playerManager.setLayerMuteState(newMute, cur.isAudioMuted)
        commitProjectChange(updated)
        setFeedback(if (newMute) "Faixa de vídeo mutada" else "Som da faixa de vídeo ativado")
    }

    fun toggleVideoLock() {
        val cur = _uiState.value.currentProject ?: return
        val newLock = !cur.isVideoLocked
        commitProjectChange(cur.copy(isVideoLocked = newLock))
        setFeedback(if (newLock) "Faixa de vídeo bloqueada" else "Faixa de vídeo desbloqueada")
    }

    fun toggleAudioMute() {
        val cur = _uiState.value.currentProject ?: return
        val newMute = !cur.isAudioMuted
        val updated = cur.copy(isAudioMuted = newMute)
        playerManager.setLayerMuteState(cur.isVideoMuted, newMute)
        commitProjectChange(updated)
        setFeedback(if (newMute) "Faixa de áudio mutada" else "Som da faixa de áudio ativado")
    }

    fun toggleAudioLock() {
        val cur = _uiState.value.currentProject ?: return
        val newLock = !cur.isAudioLocked
        commitProjectChange(cur.copy(isAudioLocked = newLock))
        setFeedback(if (newLock) "Faixa de áudio bloqueada" else "Faixa de áudio desbloqueada")
    }

    fun toggleTextVisibility() {
        val cur = _uiState.value.currentProject ?: return
        val newVis = !cur.isTextVisible
        commitProjectChange(cur.copy(isTextVisible = newVis))
        setFeedback(if (newVis) "Faixa de texto visível" else "Faixa de texto oculta")
    }

    fun toggleTextLock() {
        val cur = _uiState.value.currentProject ?: return
        val newLock = !cur.isTextLocked
        commitProjectChange(cur.copy(isTextLocked = newLock))
        setFeedback(if (newLock) "Faixa de texto bloqueada" else "Faixa de texto desbloqueada")
    }

    fun toggleVfxVisibility() {
        val cur = _uiState.value.currentProject ?: return
        val newVis = !cur.isVfxVisible
        commitProjectChange(cur.copy(isVfxVisible = newVis))
        setFeedback(if (newVis) "Faixa de efeitos visível" else "Faixa de efeitos oculta")
    }

    fun toggleVfxLock() {
        val cur = _uiState.value.currentProject ?: return
        val newLock = !cur.isVfxLocked
        commitProjectChange(cur.copy(isVfxLocked = newLock))
        setFeedback(if (newLock) "Faixa de efeitos bloqueada" else "Faixa de efeitos desbloqueada")
    }

    fun toggleOverlayVisibility() {
        val cur = _uiState.value.currentProject ?: return
        val newVis = !cur.isOverlayVisible
        commitProjectChange(cur.copy(isOverlayVisible = newVis))
        setFeedback(if (newVis) "Faixa de sobreposição visível" else "Faixa de sobreposição oculta")
    }

    fun toggleOverlayLock() {
        val cur = _uiState.value.currentProject ?: return
        val newLock = !cur.isOverlayLocked
        commitProjectChange(cur.copy(isOverlayLocked = newLock))
        setFeedback(if (newLock) "Faixa de sobreposição bloqueada" else "Faixa de sobreposição desbloqueada")
    }

    fun resetClipAdjustments(clipId: String? = null) {
        val cur = _uiState.value.currentProject ?: return
        val targetId = clipId ?: _uiState.value.selectedClipId ?: return
        val updatedClips = cur.clips.map {
            if (it.id == targetId) it.copy(
                brightness = 0f,
                contrast = 0f,
                saturation = 0f
            ) else it
        }
        commitProjectChange(cur.copy(clips = updatedClips))
        setFeedback("Ajustes redefinidos")
    }

    // ---------------- SPATIAL TRANSFORMATIONS ----------------

    fun updateClipTransform(
        scale: Float? = null,
        rotation: Float? = null,
        flipHorizontal: Boolean? = null,
        flipVertical: Boolean? = null,
        opacity: Float? = null,
        positionX: Float? = null,
        positionY: Float? = null,
        clipId: String? = null
    ) {
        val cur = _uiState.value.currentProject ?: return
        val targetId = clipId ?: _uiState.value.selectedClipId ?: return
        val updatedClips = cur.clips.map { clip ->
            if (clip.id == targetId) {
                clip.copy(
                    scale = scale?.coerceIn(0.2f, 4.0f) ?: clip.scale,
                    rotation = rotation ?: clip.rotation,
                    flipHorizontal = flipHorizontal ?: clip.flipHorizontal,
                    flipVertical = flipVertical ?: clip.flipVertical,
                    opacity = opacity?.coerceIn(0f, 1f) ?: clip.opacity,
                    positionX = positionX ?: clip.positionX,
                    positionY = positionY ?: clip.positionY
                )
            } else clip
        }
        commitProjectChange(cur.copy(clips = updatedClips))
    }

    fun rotateClip90(clockwise: Boolean = true, clipId: String? = null) {
        val cur = _uiState.value.currentProject ?: return
        val targetId = clipId ?: _uiState.value.selectedClipId ?: return
        val targetClip = cur.clips.find { it.id == targetId } ?: return
        val delta = if (clockwise) 90f else -90f
        val newRotation = (targetClip.rotation + delta) % 360f
        updateClipTransform(rotation = newRotation, clipId = targetId)
        setFeedback("Girar: ${newRotation.toInt()}°")
    }

    fun toggleFlipHorizontal(clipId: String? = null) {
        val cur = _uiState.value.currentProject ?: return
        val targetId = clipId ?: _uiState.value.selectedClipId ?: return
        val targetClip = cur.clips.find { it.id == targetId } ?: return
        val newFlip = !targetClip.flipHorizontal
        updateClipTransform(flipHorizontal = newFlip, clipId = targetId)
        setFeedback(if (newFlip) "Espelhado horizontalmente" else "Espelhamento horizontal removido")
    }

    fun toggleFlipVertical(clipId: String? = null) {
        val cur = _uiState.value.currentProject ?: return
        val targetId = clipId ?: _uiState.value.selectedClipId ?: return
        val targetClip = cur.clips.find { it.id == targetId } ?: return
        val newFlip = !targetClip.flipVertical
        updateClipTransform(flipVertical = newFlip, clipId = targetId)
        setFeedback(if (newFlip) "Espelhado verticalmente" else "Espelhamento vertical removido")
    }

    fun resetClipTransform(clipId: String? = null) {
        val cur = _uiState.value.currentProject ?: return
        val targetId = clipId ?: _uiState.value.selectedClipId ?: return
        val updatedClips = cur.clips.map { clip ->
            if (clip.id == targetId) {
                clip.copy(
                    scale = 1.0f,
                    rotation = 0f,
                    flipHorizontal = false,
                    flipVertical = false,
                    opacity = 1.0f,
                    positionX = 0f,
                    positionY = 0f
                )
            } else clip
        }
        commitProjectChange(cur.copy(clips = updatedClips))
        setFeedback("Transformações redefinidas")
    }

    fun updateClipCropRatio(cropRatio: String, clipId: String? = null) {
        val cur = _uiState.value.currentProject ?: return
        val targetId = clipId ?: _uiState.value.selectedClipId ?: return
        val updatedClips = cur.clips.map { clip ->
            if (clip.id == targetId) {
                clip.copy(cropRatio = cropRatio)
            } else clip
        }
        commitProjectChange(cur.copy(clips = updatedClips))
        setFeedback("Recorte do clipe: $cropRatio")
    }

    fun updateClipTransition(transitionName: String?, durationMs: Long? = null, clipId: String? = null) {
        val cur = _uiState.value.currentProject ?: return
        val targetId = clipId ?: _uiState.value.selectedClipId ?: return
        val updatedClips = cur.clips.map {
            if (it.id == targetId) {
                it.copy(
                    transition = transitionName,
                    transitionDurationMs = durationMs ?: it.transitionDurationMs
                )
            } else it
        }
        commitProjectChange(cur.copy(clips = updatedClips))
        setFeedback(if (transitionName != null) "Transição '$transitionName' aplicada" else "Transição removida")
    }

    fun updateClipTransitionDuration(durationMs: Long, clipId: String? = null) {
        val cur = _uiState.value.currentProject ?: return
        val targetId = clipId ?: _uiState.value.selectedClipId ?: return
        val updatedClips = cur.clips.map {
            if (it.id == targetId) it.copy(transitionDurationMs = durationMs.coerceAtLeast(100L)) else it
        }
        commitProjectChange(cur.copy(clips = updatedClips))
    }

    fun setAspectRatio(aspectRatio: AspectRatio) {
        val cur = _uiState.value.currentProject ?: return
        commitProjectChange(cur.copy(aspectRatio = aspectRatio))
    }

    // ---------------- TEXT OVERLAY MANAGEMENT ----------------

    fun selectTextOverlay(id: String?) {
        _uiState.update {
            it.copy(
                selectedTextId = id,
                selectedStickerId = if (id != null) null else it.selectedStickerId,
                selectedClipId = if (id != null) null else it.selectedClipId,
                selectedAudioTrackId = if (id != null) null else it.selectedAudioTrackId
            )
        }
    }

    fun addTextOverlay(text: String, colorHex: String = "#FFFFFF") {
        if (text.isBlank()) return
        val cur = _uiState.value.currentProject ?: return
        val newText = TextOverlayItem(
            id = "txt_" + UUID.randomUUID().toString().take(6),
            text = text,
            startTimeMs = _uiState.value.currentPositionMs,
            durationMs = 4000L,
            colorHex = colorHex
        )
        commitProjectChange(cur.copy(texts = cur.texts + newText))
        _uiState.update { it.copy(selectedTextId = newText.id) }
        setFeedback("Texto adicionado")
    }

    fun addTextOverlayItem(item: TextOverlayItem) {
        val cur = _uiState.value.currentProject ?: return
        commitProjectChange(cur.copy(texts = cur.texts + item))
        _uiState.update { it.copy(selectedTextId = item.id) }
        setFeedback("Texto adicionado")
    }

    fun applyTextTemplate(template: com.example.template.TextTemplate, customText: String? = null) {
        val cur = _uiState.value.currentProject ?: return
        val newItem = template.createOverlayItem(
            customText = customText,
            startTimeMs = _uiState.value.currentPositionMs,
            durationMs = 4000L
        )
        commitProjectChange(cur.copy(texts = cur.texts + newItem))
        _uiState.update { it.copy(selectedTextId = newItem.id) }
        setFeedback("Template '${template.name}' aplicado")
    }

    fun updateTextOverlayPosition(id: String, posX: Float, posY: Float) {
        val cur = _uiState.value.currentProject ?: return
        val updated = cur.texts.map {
            if (it.id == id) it.copy(posX = posX.coerceIn(0.05f, 0.95f), posY = posY.coerceIn(0.05f, 0.95f)) else it
        }
        commitProjectChange(cur.copy(texts = updated), registerUndo = false)
    }

    fun updateTextOverlayScale(id: String, scale: Float) {
        val cur = _uiState.value.currentProject ?: return
        val updated = cur.texts.map {
            if (it.id == id) it.copy(scale = scale.coerceIn(0.3f, 3.5f)) else it
        }
        commitProjectChange(cur.copy(texts = updated))
    }

    fun updateTextOverlayRotation(id: String, rotation: Float) {
        val cur = _uiState.value.currentProject ?: return
        val updated = cur.texts.map {
            if (it.id == id) it.copy(rotation = rotation) else it
        }
        commitProjectChange(cur.copy(texts = updated))
    }

    fun updateTextOverlayTiming(id: String, startTimeMs: Long, durationMs: Long) {
        val cur = _uiState.value.currentProject ?: return
        val updated = cur.texts.map {
            if (it.id == id) it.copy(
                startTimeMs = startTimeMs.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(200L)
            ) else it
        }
        commitProjectChange(cur.copy(texts = updated))
    }

    fun updateTextOverlayContent(id: String, newText: String) {
        val cur = _uiState.value.currentProject ?: return
        val updated = cur.texts.map {
            if (it.id == id) it.copy(text = newText) else it
        }
        commitProjectChange(cur.copy(texts = updated))
    }

    fun updateTextOverlayStyle(
        id: String,
        colorHex: String? = null,
        bgHex: String? = null,
        clearBg: Boolean = false,
        fontSizeSp: Float? = null,
        alignment: String? = null,
        fontFamily: String? = null,
        strokeColorHex: String? = null,
        strokeWidth: Float? = null,
        shadowColorHex: String? = null,
        shadowRadius: Float? = null
    ) {
        val cur = _uiState.value.currentProject ?: return
        val updated = cur.texts.map {
            if (it.id == id) {
                it.copy(
                    colorHex = colorHex ?: it.colorHex,
                    bgHex = if (clearBg) null else (bgHex ?: it.bgHex),
                    fontSizeSp = fontSizeSp ?: it.fontSizeSp,
                    alignment = alignment ?: it.alignment,
                    fontFamily = fontFamily ?: it.fontFamily,
                    strokeColorHex = strokeColorHex ?: it.strokeColorHex,
                    strokeWidth = strokeWidth ?: it.strokeWidth,
                    shadowColorHex = shadowColorHex ?: it.shadowColorHex,
                    shadowRadius = shadowRadius ?: it.shadowRadius
                )
            } else it
        }
        commitProjectChange(cur.copy(texts = updated))
    }

    fun updateTextOverlayAnimation(
        id: String,
        animationIn: String? = null,
        animationOut: String? = null,
        textAnimationMode: String? = null,
        animationDurationMs: Long? = null
    ) {
        val cur = _uiState.value.currentProject ?: return
        val updated = cur.texts.map {
            if (it.id == id) {
                it.copy(
                    animationIn = animationIn ?: it.animationIn,
                    animationOut = animationOut ?: it.animationOut,
                    textAnimationMode = textAnimationMode ?: it.textAnimationMode,
                    animationDurationMs = animationDurationMs ?: it.animationDurationMs
                )
            } else it
        }
        commitProjectChange(cur.copy(texts = updated))
    }

    fun removeTextOverlay(id: String) {
        val cur = _uiState.value.currentProject ?: return
        val updated = cur.texts.filterNot { it.id == id }
        commitProjectChange(cur.copy(texts = updated))
        if (_uiState.value.selectedTextId == id) {
            _uiState.update { it.copy(selectedTextId = null) }
        }
        setFeedback("Texto removido")
    }

    // ---------------- STICKER & GIF MANAGEMENT ----------------

    fun selectSticker(id: String?) {
        _uiState.update {
            it.copy(
                selectedStickerId = id,
                selectedTextId = if (id != null) null else it.selectedTextId,
                selectedClipId = if (id != null) null else it.selectedClipId,
                selectedAudioTrackId = if (id != null) null else it.selectedAudioTrackId
            )
        }
    }

    fun addSticker(sticker: StickerItem) {
        val cur = _uiState.value.currentProject ?: return
        val updated = cur.stickers + sticker
        commitProjectChange(cur.copy(stickers = updated))
        _uiState.update { it.copy(selectedStickerId = sticker.id) }
        setFeedback(if (sticker.isGif) "GIF adicionado" else "Sticker adicionado")
    }

    fun importOverlayMedia(uri: String, isVideo: Boolean, name: String = if (isVideo) "Vídeo Sobreposto" else "Foto Sobreposta") {
        val cur = _uiState.value.currentProject ?: return
        val currentPlayhead = _uiState.value.currentPositionMs
        val newSticker = StickerItem(
            id = "overlay_" + UUID.randomUUID().toString().take(6),
            uri = uri,
            name = name,
            isVideo = isVideo,
            startTimeMs = currentPlayhead,
            durationMs = if (isVideo) 5000L else 4000L,
            posX = 0.5f,
            posY = 0.5f,
            scale = 0.8f
        )
        val updated = cur.stickers + newSticker
        commitProjectChange(cur.copy(stickers = updated))
        _uiState.update { it.copy(selectedStickerId = newSticker.id) }
        setFeedback(if (isVideo) "Vídeo adicionado como camada" else "Foto adicionada como camada")
    }

    fun updateStickerPosition(id: String, posX: Float, posY: Float) {
        val cur = _uiState.value.currentProject ?: return
        val updated = cur.stickers.map {
            if (it.id == id) it.copy(posX = posX.coerceIn(0.05f, 0.95f), posY = posY.coerceIn(0.05f, 0.95f)) else it
        }
        commitProjectChange(cur.copy(stickers = updated), registerUndo = false)
    }

    fun updateStickerScale(id: String, scale: Float) {
        val cur = _uiState.value.currentProject ?: return
        val updated = cur.stickers.map {
            if (it.id == id) it.copy(scale = scale.coerceIn(0.3f, 3.5f)) else it
        }
        commitProjectChange(cur.copy(stickers = updated))
    }

    fun updateStickerRotation(id: String, rotation: Float) {
        val cur = _uiState.value.currentProject ?: return
        val updated = cur.stickers.map {
            if (it.id == id) it.copy(rotation = rotation) else it
        }
        commitProjectChange(cur.copy(stickers = updated))
    }

    fun updateStickerTiming(id: String, startTimeMs: Long, durationMs: Long) {
        val cur = _uiState.value.currentProject ?: return
        val updated = cur.stickers.map {
            if (it.id == id) it.copy(
                startTimeMs = startTimeMs.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(200L)
            ) else it
        }
        commitProjectChange(cur.copy(stickers = updated))
    }

    fun updateStickerAnimation(
        id: String,
        animationIn: String? = null,
        animationOut: String? = null,
        animationDurationMs: Long? = null
    ) {
        val cur = _uiState.value.currentProject ?: return
        val updated = cur.stickers.map {
            if (it.id == id) {
                it.copy(
                    animationIn = animationIn ?: it.animationIn,
                    animationOut = animationOut ?: it.animationOut,
                    animationDurationMs = animationDurationMs ?: it.animationDurationMs
                )
            } else it
        }
        commitProjectChange(cur.copy(stickers = updated))
    }

    fun removeSticker(id: String) {
        val cur = _uiState.value.currentProject ?: return
        val updated = cur.stickers.filterNot { it.id == id }
        commitProjectChange(cur.copy(stickers = updated))
        if (_uiState.value.selectedStickerId == id) {
            _uiState.update { it.copy(selectedStickerId = null) }
        }
        setFeedback("Sobreposição removida")
    }

    fun importOverlayMediaUri(uri: android.net.Uri) {
        val cur = _uiState.value.currentProject ?: return
        viewModelScope.launch {
            try {
                val stored = storageManager.copyUriToProjectMedia(cur.id, uri)
                val metadata = metadataExtractor.extractMetadata(cur.id, stored.file, stored.mimeType)
                val isVideo = metadata.mediaType == MediaType.VIDEO
                val durationMs = if (isVideo) metadata.durationMs.coerceAtLeast(1000L) else 4000L
                val newOverlay = StickerItem(
                    id = "overlay_" + UUID.randomUUID().toString().take(6),
                    uri = uri.toString(),
                    localPath = stored.file.absolutePath,
                    name = stored.originalName.ifBlank { if (isVideo) "Vídeo Sobreposto" else "Foto Sobreposta" },
                    isGif = stored.mimeType.contains("gif", ignoreCase = true),
                    isVideo = isVideo,
                    startTimeMs = _uiState.value.currentPositionMs,
                    durationMs = durationMs,
                    posX = 0.5f,
                    posY = 0.5f,
                    scale = 0.75f,
                    rotation = 0f
                )
                val updatedProject = cur.copy(stickers = cur.stickers + newOverlay)
                commitProjectChange(updatedProject)
                _uiState.update { it.copy(selectedStickerId = newOverlay.id) }
                setFeedback(if (isVideo) "Vídeo adicionado como sobreposição" else "Foto adicionada como sobreposição")
            } catch (e: Exception) {
                setFeedback("Erro ao importar sobreposição: ${e.message}")
            }
        }
    }

    fun splitStickerAtPlayhead(stickerId: String? = null) {
        val cur = _uiState.value.currentProject ?: return
        val targetId = stickerId ?: _uiState.value.selectedStickerId ?: return
        val playhead = _uiState.value.currentPositionMs
        val (updated, newId) = TimelineUtils.splitStickerAtPlayhead(cur.stickers, targetId, playhead)
        if (newId != null) {
            commitProjectChange(cur.copy(stickers = updated))
            _uiState.update { it.copy(selectedStickerId = newId) }
            setFeedback("Sobreposição dividida")
        } else {
            setFeedback("Posição inválida para dividir sobreposição")
        }
    }

    fun duplicateSticker(stickerId: String? = null) {
        val cur = _uiState.value.currentProject ?: return
        val targetId = stickerId ?: _uiState.value.selectedStickerId ?: return
        val (updated, newId) = TimelineUtils.duplicateSticker(cur.stickers, targetId)
        if (newId != null) {
            commitProjectChange(cur.copy(stickers = updated))
            _uiState.update { it.copy(selectedStickerId = newId) }
            setFeedback("Sobreposição duplicada")
        }
    }

    fun splitTextOverlayAtPlayhead(textId: String? = null) {
        val cur = _uiState.value.currentProject ?: return
        val targetId = textId ?: _uiState.value.selectedTextId ?: return
        val playhead = _uiState.value.currentPositionMs
        val (updated, newId) = TimelineUtils.splitTextOverlayAtPlayhead(cur.texts, targetId, playhead)
        if (newId != null) {
            commitProjectChange(cur.copy(texts = updated))
            _uiState.update { it.copy(selectedTextId = newId) }
            setFeedback("Texto dividido")
        } else {
            setFeedback("Posição inválida para dividir texto")
        }
    }

    fun duplicateTextOverlay(textId: String? = null) {
        val cur = _uiState.value.currentProject ?: return
        val targetId = textId ?: _uiState.value.selectedTextId ?: return
        val (updated, newId) = TimelineUtils.duplicateTextOverlay(cur.texts, targetId)
        if (newId != null) {
            commitProjectChange(cur.copy(texts = updated))
            _uiState.update { it.copy(selectedTextId = newId) }
            setFeedback("Texto duplicado")
        }
    }

    fun duplicateAudioTrack(trackId: String? = null) {
        val cur = _uiState.value.currentProject ?: return
        val targetId = trackId ?: _uiState.value.selectedAudioTrackId ?: return
        val (updated, newId) = TimelineUtils.duplicateAudioTrack(cur.audios, targetId)
        if (newId != null) {
            commitProjectChange(cur.copy(audios = updated))
            _uiState.update { it.copy(selectedAudioTrackId = newId) }
            val newTrack = updated.find { it.id == newId }
            if (newTrack != null) loadWaveformForTrack(newTrack)
            setFeedback("Áudio duplicado")
        }
    }

    // ---------------- MULTI-TRACK AUDIO MANAGEMENT ----------------

    fun selectAudioTrack(id: String?) {
        _uiState.update {
            it.copy(
                selectedAudioTrackId = id,
                selectedClipId = if (id != null) null else it.selectedClipId,
                selectedTextId = if (id != null) null else it.selectedTextId,
                selectedStickerId = if (id != null) null else it.selectedStickerId
            )
        }
    }

    fun addAudioTrack(track: AudioTrackItem) {
        val cur = _uiState.value.currentProject ?: return
        val updatedAudios = cur.audios + track
        commitProjectChange(cur.copy(audios = updatedAudios))
        _uiState.update { it.copy(selectedAudioTrackId = track.id) }
        loadWaveformForTrack(track)
        setFeedback("Áudio '${track.name}' adicionado")
    }

    fun removeAudioTrack(id: String) {
        val cur = _uiState.value.currentProject ?: return
        val updatedAudios = cur.audios.filterNot { it.id == id }
        val newSelected = if (_uiState.value.selectedAudioTrackId == id) {
            updatedAudios.firstOrNull()?.id
        } else {
            _uiState.value.selectedAudioTrackId
        }
        commitProjectChange(cur.copy(audios = updatedAudios))
        _uiState.update { it.copy(selectedAudioTrackId = newSelected) }
        setFeedback("Faixa de áudio removida")
    }

    fun updateAudioTrackVolume(trackId: String, volume: Float) {
        val cur = _uiState.value.currentProject ?: return
        val updatedAudios = cur.audios.map {
            if (it.id == trackId) TimelineUtils.setAudioTrackVolume(it, volume) else it
        }
        val isMuted = updatedAudios.firstOrNull { it.id == trackId }?.isMuted ?: false
        playerManager.audioSyncManager.updateTrackVolumeAndMute(trackId, volume, isMuted)
        commitProjectChange(cur.copy(audios = updatedAudios))
    }

    fun toggleAudioTrackMute(trackId: String) {
        val cur = _uiState.value.currentProject ?: return
        val updatedAudios = cur.audios.map {
            if (it.id == trackId) TimelineUtils.toggleAudioTrackMute(it) else it
        }
        val track = updatedAudios.firstOrNull { it.id == trackId }
        if (track != null) {
            playerManager.audioSyncManager.updateTrackVolumeAndMute(trackId, track.volume, track.isMuted)
        }
        commitProjectChange(cur.copy(audios = updatedAudios))
        setFeedback(if (track?.isMuted == true) "Faixa silenciada" else "Faixa ativada")
    }

    fun updateAudioTrackPosition(trackId: String, newTimelineStartMs: Long) {
        val cur = _uiState.value.currentProject ?: return
        val updatedAudios = cur.audios.map {
            if (it.id == trackId) TimelineUtils.moveAudioTrackTimelineStart(it, newTimelineStartMs) else it
        }
        commitProjectChange(cur.copy(audios = updatedAudios))
    }

    fun updateAudioTrackTrim(trackId: String, trimStartMs: Long, trimEndMs: Long) {
        val cur = _uiState.value.currentProject ?: return
        val updatedAudios = cur.audios.map {
            if (it.id == trackId) {
                TimelineUtils.applyAudioTrim(it, trimStartMs, trimEndMs) ?: it
            } else it
        }
        commitProjectChange(cur.copy(audios = updatedAudios))
    }

    fun splitAudioTrackAtPlayhead(trackId: String? = null) {
        val cur = _uiState.value.currentProject ?: return
        val targetId = trackId ?: _uiState.value.selectedAudioTrackId ?: cur.audios.firstOrNull()?.id ?: return
        val playhead = _uiState.value.currentPositionMs
        val (updatedTracks, newTrackId) = TimelineUtils.splitAudioTrackAtPlayhead(cur.audios, targetId, playhead)
        if (newTrackId != null) {
            commitProjectChange(cur.copy(audios = updatedTracks))
            _uiState.update { it.copy(selectedAudioTrackId = newTrackId) }
            val newTrack = updatedTracks.firstOrNull { it.id == newTrackId }
            if (newTrack != null) {
                loadWaveformForTrack(newTrack)
            }
            setFeedback("Áudio dividido no playhead")
        } else {
            setFeedback("Posição inválida para dividir o áudio")
        }
    }

    fun loadWaveformsForProject(project: ProjectItem) {
        project.audios.forEach { track ->
            loadWaveformForTrack(track)
        }
    }

    fun loadWaveformForTrack(track: AudioTrackItem) {
        if (_uiState.value.waveforms.containsKey(track.id)) return
        viewModelScope.launch {
            val samples = waveformGenerator.getWaveform(track.localPath, sampleCount = 60)
            _uiState.update {
                it.copy(waveforms = it.waveforms + (track.id to samples))
            }
        }
    }

    fun toggleVFX(vfx: VFXEffectItem) {
        val cur = _uiState.value.currentProject ?: return
        val exists = cur.activeVFX.any { it.id == vfx.id }
        val updated = if (exists) {
            cur.activeVFX.filterNot { it.id == vfx.id }
        } else {
            val startMs = _uiState.value.currentPositionMs
            val newVfx = vfx.copy(startTimeMs = startMs, durationMs = 3000L)
            cur.activeVFX + newVfx
        }
        commitProjectChange(cur.copy(activeVFX = updated))
        setFeedback(if (!exists) "Efeito '${vfx.name}' ativado" else "Efeito '${vfx.name}' desativado")
    }

    fun updateVfxIntensity(vfxId: String, intensity: Float) {
        val cur = _uiState.value.currentProject ?: return
        val updated = cur.activeVFX.map {
            if (it.id == vfxId) it.copy(intensity = intensity.coerceIn(0f, 100f)) else it
        }
        commitProjectChange(cur.copy(activeVFX = updated))
    }

    fun clearAllVFX() {
        val cur = _uiState.value.currentProject ?: return
        if (cur.activeVFX.isEmpty()) return
        commitProjectChange(cur.copy(activeVFX = emptyList()))
        setFeedback("Todos os efeitos foram desativados")
    }

    fun generateCaptions(language: String) {
        val cur = _uiState.value.currentProject ?: return
        val generated = listOf(
            SubtitleSegmentItem("sub1", "Bem-vindos ao meu vídeo!", 1000L, 3500L),
            SubtitleSegmentItem("sub2", "Hoje vamos explorar lugares incríveis.", 4000L, 8000L),
            SubtitleSegmentItem("sub3", "Não se esqueça de curtir e compartilhar!", 8500L, 12000L)
        )
        commitProjectChange(cur.copy(subtitles = generated))
        setFeedback("Legendas geradas em $language com IA")
    }

    fun generateAutoCaptions(language: String = "pt-BR") {
        generateCaptions(language)
    }

    fun updateSubtitle(id: String, newText: String) {
        val cur = _uiState.value.currentProject ?: return
        val updated = cur.subtitles.map {
            if (it.id == id) it.copy(text = newText) else it
        }
        commitProjectChange(cur.copy(subtitles = updated))
    }

    fun deleteSubtitle(id: String) {
        val cur = _uiState.value.currentProject ?: return
        commitProjectChange(cur.copy(subtitles = cur.subtitles.filterNot { it.id == id }))
    }

    private fun updateCurrentProject(project: ProjectItem) {
        _uiState.update { state ->
            val updatedProjects = state.projects.map {
                if (it.id == project.id) project else it
            }
            state.copy(currentProject = project, projects = updatedProjects)
        }
        playerManager.setClipsAndAudios(
            project.clips,
            project.audios,
            project.texts,
            project.stickers,
            project.activeVFX
        )
        playerManager.setLayerMuteState(project.isVideoMuted, project.isAudioMuted)
        loadWaveformsForProject(project)
        viewModelScope.launch {
            repository.saveProject(project)
        }
    }

    fun release() {
        playerManager.release()
    }

    override fun onCleared() {
        super.onCleared()
        release()
    }

    fun startExport(options: ExportOptions) {
        exportJob?.cancel()
        val current = _uiState.value.currentProject ?: return

        val resolution = com.example.export.ExportResolution.fromLabel(options.resolution)
        val exportConfig = com.example.export.VideoExportConfig(
            resolution = resolution,
            aspectRatio = current.aspectRatio,
            fps = options.frameRate,
            quality = options.quality,
            removeWatermark = options.removeWatermark || _uiState.value.isPremiumUser
        )

        _uiState.update {
            it.copy(
                isExporting = true,
                exportProgress = 0f,
                exportSuccess = false,
                exportStatusMessage = "Iniciando exportação...",
                selectedExportOptions = options
            )
        }

        exportJob = viewModelScope.launch {
            try {
                val result = exportManager.exportProject(
                    project = current,
                    config = exportConfig,
                    onProgress = { progress ->
                        _uiState.update {
                            it.copy(
                                exportProgress = progress.progress,
                                exportStatusMessage = progress.message
                            )
                        }
                    }
                )

                if (result.success && result.outputFile != null) {
                    val path = result.outputFile.absolutePath
                    repository.saveLastExportedPath(current.id, path)
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            exportSuccess = true,
                            exportProgress = 1.0f,
                            lastExportedFile = result.outputFile.name,
                            lastExportedFilePath = path,
                            exportStatusMessage = "Vídeo MP4 salvo com sucesso!"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            exportSuccess = false,
                            feedbackMessage = result.errorMessage ?: "Falha ao exportar vídeo"
                        )
                    }
                }
            } catch (c: kotlinx.coroutines.CancellationException) {
                _uiState.update { it.copy(isExporting = false, exportProgress = 0f, exportStatusMessage = null) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportSuccess = false,
                        feedbackMessage = "Erro ao exportar: ${e.message}"
                    )
                }
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        _uiState.update { it.copy(isExporting = false, exportProgress = 0f, exportStatusMessage = null) }
        setFeedback("Exportação cancelada.")
    }

    fun resetExportState() {
        _uiState.update { it.copy(exportSuccess = false, isExporting = false, exportProgress = 0f, exportStatusMessage = null) }
    }

    fun subscribePremium() {
        _uiState.update { it.copy(isPremiumUser = true) }
        setFeedback("Parabéns! Boti Pro ativado com sucesso.")
    }

    fun setPremiumUser(isPremium: Boolean = true) {
        _uiState.update { it.copy(isPremiumUser = isPremium) }
        if (isPremium) {
            setFeedback("Parabéns! Boti Pro ativado com sucesso.")
        }
    }

    fun openVideoInExternalPlayer(context: android.content.Context, filePath: String) {
        val file = java.io.File(filePath)
        if (!file.exists()) {
            setFeedback("Arquivo de vídeo não encontrado.")
            return
        }
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = android.content.Intent.createChooser(intent, "Abrir vídeo com...").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            setFeedback("Não foi possível abrir o player externo: ${e.message}")
        }
    }

    fun shareVideo(context: android.content.Context, filePath: String) {
        val file = java.io.File(filePath)
        if (!file.exists()) {
            setFeedback("Arquivo de vídeo não encontrado.")
            return
        }
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = android.content.Intent.createChooser(intent, "Compartilhar vídeo").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            setFeedback("Erro ao compartilhar vídeo: ${e.message}")
        }
    }

    fun setFeedback(msg: String) {
        _uiState.update { it.copy(feedbackMessage = msg) }
        viewModelScope.launch {
            delay(3000)
            _uiState.update { if (it.feedbackMessage == msg) it.copy(feedbackMessage = null) else it }
        }
    }

    /**
     * Calcula o tamanho real do cache de arquivos temporários do aplicativo em background (Dispatchers.IO).
     */
    fun calculateAppCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val cacheDir = app.cacheDir
            val extCacheDir = app.externalCacheDir
            val exportsDir = File(app.filesDir, "exports")

            var totalBytes = calculateDirSize(cacheDir)
            if (extCacheDir != null) {
                totalBytes += calculateDirSize(extCacheDir)
            }
            if (exportsDir.exists()) {
                totalBytes += calculateDirSize(exportsDir)
            }

            val formatted = when {
                totalBytes <= 0L -> "0 MB"
                totalBytes < 1024L * 1024L -> String.format("%.1f KB", totalBytes / 1024.0)
                else -> String.format("%.1f MB", totalBytes / (1024.0 * 1024.0))
            }
            _cacheSizeMb.value = formatted
        }
    }

    private fun calculateDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return size
    }

    /**
     * Limpa os arquivos temporários reais do cache do aplicativo e recalcula o tamanho.
     */
    fun clearAppCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            deleteDirContents(app.cacheDir)
            app.externalCacheDir?.let { deleteDirContents(it) }

            calculateAppCacheSize()
            setFeedback("Cache temporário limpo com sucesso!")
        }
    }

    private fun deleteDirContents(dir: File?) {
        if (dir == null || !dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                deleteDirContents(file)
                file.delete()
            } else {
                file.delete()
            }
        }
    }

    /**
     * Carrega modelos da camada de TemplateRepository simulando consumo de API.
     */
    fun loadTemplates() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val list = templateRepository.getTemplates()
                _templates.value = list
            } catch (e: Exception) {
                android.util.Log.w("EditorViewModel", "Falha ao carregar templates: ${e.message}")
            }
        }
    }
}

// ---------------- DEEP COPY EXTENSION ----------------

fun ProjectItem.deepCopy(): ProjectItem {
    return this.copy(
        clips = this.clips.map { it.copy() },
        audios = this.audios.map { it.copy() },
        texts = this.texts.map { it.copy() },
        stickers = this.stickers.map { it.copy() },
        subtitles = this.subtitles.map { it.copy() },
        activeVFX = this.activeVFX.map { it.copy() },
        transitions = this.transitions.map { it.copy() }
    )
}
