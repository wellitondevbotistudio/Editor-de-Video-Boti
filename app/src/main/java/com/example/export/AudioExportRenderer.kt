package com.example.export

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import com.example.model.AudioTrackItem
import com.example.model.MediaClip
import com.example.model.MediaType
import com.example.model.ProjectItem
import com.example.util.TimelineUtils
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * Renderizador e codificador de áudio multifaixas para exportação.
 * Extrai e decodifica amostras reais 16-bit PCM de arquivos e vídeos via [MediaExtractor] e [MediaCodec],
 * realizando mixagem precisa multicanal (44.1kHz estéreo) com volume, trim, mute e codificação AAC real.
 */
class AudioExportRenderer(
    private val context: Context,
    private val config: VideoExportConfig
) {
    companion object {
        private const val TAG = "AudioExportRenderer"
    }

    private val sampleRate = config.audioSampleRate
    private val channelCount = config.audioChannels
    private val bitRate = config.audioBitrate

    // Cache de amostras PCM decodificadas para cada fonte de mídia (áudio ou vídeo)
    private val decodedPcmCache = mutableMapOf<String, ShortArray>()

    /**
     * Processa, mixa e codifica o áudio do projeto diretamente no [MediaMuxer].
     */
    fun processAndEncodeAudio(
        project: ProjectItem,
        totalDurationMs: Long,
        muxer: MediaMuxer,
        audioTrackIndex: Int,
        onProgress: (Float) -> Unit
    ) {
        // Pré-carrega/decodifica fontes de áudio reais necessárias
        preloadAudioSources(project)

        val encoder = MediaCodec.createEncoderByType("audio/mp4a-latm")
        val audioFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        encoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val bufferInfo = MediaCodec.BufferInfo()
        val totalSamples = ((totalDurationMs.toDouble() / 1000.0) * sampleRate).toLong()

        // Cada frame PCM = 1024 amostras por canal * 2 canais * 2 bytes = 4096 bytes
        val samplesPerChunk = 1024
        val bytesPerChunk = samplesPerChunk * channelCount * 2
        val chunkDurationUs = (samplesPerChunk.toDouble() / sampleRate * 1_000_000).toLong()

        var currentSample = 0L
        var presentationTimeUs = 0L
        var isInputEos = false

        val pcmBuffer = ByteBuffer.allocateDirect(bytesPerChunk).order(ByteOrder.LITTLE_ENDIAN)
        val shortBuffer = ShortArray(samplesPerChunk * channelCount)

        try {
            while (true) {
                // 1. Alimenta o encoder com dados PCM mixados
                if (!isInputEos) {
                    val inputBufferIndex = encoder.dequeueInputBuffer(10_000L)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = encoder.getInputBuffer(inputBufferIndex) ?: continue
                        inputBuffer.clear()

                        if (currentSample >= totalSamples) {
                            // Fim do áudio (EOS)
                            encoder.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                0,
                                presentationTimeUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            isInputEos = true
                        } else {
                            val currentTimelineMs = (currentSample.toDouble() / sampleRate * 1000.0).toLong()

                            // Mixa as faixas no buffer de amostras
                            mixPcmChunkAt(
                                project = project,
                                timelineTimeMs = currentTimelineMs,
                                samplesCount = samplesPerChunk,
                                outShorts = shortBuffer
                            )

                            pcmBuffer.clear()
                            for (s in shortBuffer) {
                                pcmBuffer.putShort(s)
                            }
                            pcmBuffer.flip()

                            val toWrite = kotlin.math.min(inputBuffer.remaining(), pcmBuffer.remaining())
                            val oldLimit = pcmBuffer.limit()
                            pcmBuffer.limit(pcmBuffer.position() + toWrite)
                            inputBuffer.put(pcmBuffer)
                            pcmBuffer.limit(oldLimit)

                            encoder.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                toWrite,
                                presentationTimeUs,
                                0
                            )

                            currentSample += samplesPerChunk
                            presentationTimeUs += chunkDurationUs

                            val audioProgress = (currentSample.toFloat() / totalSamples.coerceAtLeast(1L)).coerceIn(0f, 1f)
                            onProgress(audioProgress)
                        }
                    }
                }

                // 2. Coleta pacotes AAC codificados e grava no Muxer
                val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                if (outputBufferIndex >= 0) {
                    val outputBuffer = encoder.getOutputBuffer(outputBufferIndex) ?: continue

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(audioTrackIndex, outputBuffer, bufferInfo)
                    }

                    encoder.releaseOutputBuffer(outputBufferIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                } else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER && isInputEos) {
                    break
                }
            }
        } finally {
            try {
                encoder.stop()
                encoder.release()
            } catch (_: Exception) {}
            decodedPcmCache.clear()
        }
    }

    /**
     * Prepara o MediaFormat de áudio para ser adicionado ao Muxer antes do start().
     */
    fun createAudioMediaFormat(): MediaFormat {
        return MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            val csd0 = ByteBuffer.wrap(byteArrayOf(0x12.toByte(), 0x10.toByte()))
            setByteBuffer("csd-0", csd0)
        }
    }

    /**
     * Decodifica antecipadamente para PCM bruto as fontes de áudio ativas do projeto.
     */
    private fun preloadAudioSources(project: ProjectItem) {
        // Faixas de áudio
        if (!project.isAudioMuted) {
            project.audios.forEach { track ->
                if (!track.isMuted && track.volume > 0f) {
                    val pathOrUri = when {
                        track.localPath.isNotBlank() -> track.localPath
                        track.uri.isNotBlank() -> track.uri
                        else -> null
                    }
                    if (pathOrUri != null && !decodedPcmCache.containsKey(track.id)) {
                        decodeAudioSourceToPcm(pathOrUri)?.let { pcm ->
                            decodedPcmCache[track.id] = pcm
                        }
                    }
                }
            }
        }

        // Áudio dos clipes de vídeo
        if (!project.isVideoMuted) {
            project.clips.forEach { clip ->
                if (clip.type == MediaType.VIDEO && !clip.isMuted && clip.volume > 0f) {
                    val pathOrUri = when {
                        clip.localPath.isNotBlank() -> clip.localPath
                        clip.uri.isNotBlank() -> clip.uri
                        else -> null
                    }
                    if (pathOrUri != null && !decodedPcmCache.containsKey(clip.id)) {
                        decodeAudioSourceToPcm(pathOrUri)?.let { pcm ->
                            decodedPcmCache[clip.id] = pcm
                        }
                    }
                }
            }
        }
    }

    /**
     * Decodifica um arquivo de mídia real para amostras PCM estéreo de 16 bits usando MediaExtractor e MediaCodec.
     */
    private fun decodeAudioSourceToPcm(sourcePathOrUri: String): ShortArray? {
        val extractor = MediaExtractor()
        return try {
            if (sourcePathOrUri.startsWith("content://") || sourcePathOrUri.startsWith("file://")) {
                extractor.setDataSource(context, Uri.parse(sourcePathOrUri), null)
            } else {
                val f = File(sourcePathOrUri)
                if (!f.exists() || f.length() == 0L) {
                    extractor.release()
                    return null
                }
                extractor.setDataSource(f.absolutePath)
            }

            var audioTrackIndex = -1
            var trackFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    trackFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || trackFormat == null) {
                extractor.release()
                return null
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: return null
            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(trackFormat, null, null, 0)
            decoder.start()

            val srcChannels = if (trackFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            val decodedList = ArrayList<Short>()
            val bufferInfo = MediaCodec.BufferInfo()
            var inputEos = false
            var outputEos = false
            val timeout = 8_000L

            while (!outputEos) {
                if (!inputEos) {
                    val inIdx = decoder.dequeueInputBuffer(timeout)
                    if (inIdx >= 0) {
                        val inBuf = decoder.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEos = true
                            } else {
                                decoder.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIdx = decoder.dequeueOutputBuffer(bufferInfo, timeout)
                if (outIdx >= 0) {
                    val outBuf = decoder.getOutputBuffer(outIdx)
                    if (outBuf != null && bufferInfo.size > 0) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        outBuf.order(ByteOrder.LITTLE_ENDIAN)

                        val shortView = outBuf.asShortBuffer()
                        val pcmChunk = ShortArray(shortView.remaining())
                        shortView.get(pcmChunk)

                        if (srcChannels == 1) {
                            for (sample in pcmChunk) {
                                decodedList.add(sample)
                                decodedList.add(sample)
                            }
                        } else {
                            for (sample in pcmChunk) {
                                decodedList.add(sample)
                            }
                        }
                    }
                    decoder.releaseOutputBuffer(outIdx, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputEos = true
                    }
                } else if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER && inputEos) {
                    break
                }
            }

            try {
                decoder.stop()
                decoder.release()
            } catch (_: Exception) {}
            extractor.release()

            Log.i(TAG, "PCM decodificado com sucesso (${decodedList.size} amostras) de $sourcePathOrUri")
            decodedList.toShortArray()
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível extrair PCM de $sourcePathOrUri: ${e.message}")
            try { extractor.release() } catch (_: Exception) {}
            null
        }
    }

    /**
     * Mixa amostras de áudio para um bloco de tempo a partir dos buffers PCM decodificados reais.
     */
    private fun mixPcmChunkAt(
        project: ProjectItem,
        timelineTimeMs: Long,
        samplesCount: Int,
        outShorts: ShortArray
    ) {
        java.util.Arrays.fill(outShorts, 0.toShort())

        val mixAccumulatorL = IntArray(samplesCount)
        val mixAccumulatorR = IntArray(samplesCount)

        // 1. Faixas de áudio externas
        if (!project.isAudioMuted) {
            project.audios.forEach { track ->
                if (!track.isMuted && track.volume > 0f) {
                    if (TimelineUtils.isAudioActiveAtTimelinePosition(track, timelineTimeMs)) {
                        val pcmData = decodedPcmCache[track.id]
                        if (pcmData != null && pcmData.isNotEmpty()) {
                            val offsetMs = timelineTimeMs - track.timelineStartMs + track.trimStartMs
                            val effectiveMs = offsetMs.coerceAtLeast(0L)
                            val startSampleIndex = ((effectiveMs * sampleRate) / 1000L).toInt() * 2 // estéreo
                            val vol = track.volume.coerceIn(0f, 1f)

                            for (i in 0 until samplesCount) {
                                val idxL = startSampleIndex + i * 2
                                val idxR = idxL + 1
                                if (idxR < pcmData.size) {
                                    mixAccumulatorL[i] += (pcmData[idxL] * vol).toInt()
                                    mixAccumulatorR[i] += (pcmData[idxR] * vol).toInt()
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Áudio embutido dos clipes de vídeo principais
        if (!project.isVideoMuted) {
            var accumulatedStartMs = 0L
            for (clip in project.clips) {
                val clipDuration = TimelineUtils.calculateClipTimelineDuration(clip)
                val clipEndMs = accumulatedStartMs + clipDuration

                if (timelineTimeMs in accumulatedStartMs until clipEndMs) {
                    if (clip.type == MediaType.VIDEO && !clip.isMuted && clip.volume > 0f) {
                        val pcmData = decodedPcmCache[clip.id]
                        if (pcmData != null && pcmData.isNotEmpty()) {
                            val offsetInClipMs = timelineTimeMs - accumulatedStartMs + clip.trimStartMs
                            val effectiveMs = (offsetInClipMs * clip.speed).toLong().coerceAtLeast(0L)
                            val startSampleIndex = ((effectiveMs * sampleRate) / 1000L).toInt() * 2
                            val vol = clip.volume.coerceIn(0f, 1f)

                            for (i in 0 until samplesCount) {
                                val idxL = startSampleIndex + i * 2
                                val idxR = idxL + 1
                                if (idxR < pcmData.size) {
                                    mixAccumulatorL[i] += (pcmData[idxL] * vol).toInt()
                                    mixAccumulatorR[i] += (pcmData[idxR] * vol).toInt()
                                }
                            }
                        }
                    }
                    break
                }
                accumulatedStartMs = clipEndMs
            }
        }

        // Converte acumulador para 16-bit com clipping limiter
        for (i in 0 until samplesCount) {
            val sampleL = mixAccumulatorL[i].coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            val sampleR = mixAccumulatorR[i].coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            outShorts[i * 2] = sampleL.toShort()
            outShorts[i * 2 + 1] = sampleR.toShort()
        }
    }
}
