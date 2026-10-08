package com.example.export

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.yield
import java.io.File
import java.nio.ByteBuffer

/**
 * Pipeline central de codificação MP4 de alta performance.
 * Orquestra o VideoFrameRenderer, o MediaCodec (H.264 / video/avc), o AudioExportRenderer e o MediaMuxer.
 */
class Mp4EncoderPipeline(
    private val context: Context,
    private val config: VideoExportConfig
) {

    private val width = config.width
    private val height = config.height
    private val fps = config.fps
    private val videoBitrate = config.videoBitrate

    /**
     * Valida se as dimensões solicitadas são suportadas pelo hardware/sistema operacional.
     */
    fun validateResolutionSupport() {
        try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val format = MediaFormat.createVideoFormat("video/avc", width, height)
            val encoderName = codecList.findEncoderForFormat(format)
            if (encoderName == null) {
                // Tenta instanciar diretamente para checar suporte explícito
                val encoder = MediaCodec.createEncoderByType("video/avc")
                val caps = encoder.codecInfo.getCapabilitiesForType("video/avc")
                encoder.release()
                val isSupported = caps.videoCapabilities?.isSizeSupported(width, height) ?: false
                if (!isSupported) {
                    throw ExportException("Resolução ${config.resolution.label} (${width}x${height}) não suportada pelo codificador de vídeo deste dispositivo.")
                }
            }
        } catch (e: ExportException) {
            throw e
        } catch (_: Exception) {
            // Em ambientes de teste sem lista completa de hardware, prossegue
        }
    }

    /**
     * Executa o processo completo de renderização, codificação e muxing no arquivo MP4 de saída.
     */
    suspend fun encode(
        timeline: ExportTimeline,
        outputFile: File,
        frameRenderer: VideoFrameRenderer,
        audioRenderer: AudioExportRenderer,
        onProgress: (VideoExportProgress) -> Unit
    ): VideoExportResult {
        validateResolutionSupport()

        val totalFrames = timeline.totalFrames
        val totalDurationMs = timeline.totalDurationMs
        val frameDurationUs = (1_000_000L / fps)

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val maxInputSize = width * height * 3 / 2

        // 1. Configura codificador H.264
        val videoFormat = MediaFormat.createVideoFormat("video/avc", width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
            setInteger(MediaFormat.KEY_BIT_RATE, videoBitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // Keyframe a cada 1 segundo
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxInputSize)
        }

        val videoEncoder = MediaCodec.createEncoderByType("video/avc")
        videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        videoEncoder.start()

        val audioFormat = audioRenderer.createAudioMediaFormat()

        var videoTrackIndex = -1
        var audioTrackIndex = -1
        var isMuxerStarted = false

        val videoBufferInfo = MediaCodec.BufferInfo()
        val targetBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val argbPixels = IntArray(width * height)
        val yuvBuffer = ByteArray(width * height * 3 / 2)

        try {
            // Determina as tracks no Muxer antes do início
            videoTrackIndex = muxer.addTrack(videoFormat)
            audioTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()
            isMuxerStarted = true

            // 2. Loop de Renderização e Codificação de Quadros de Vídeo
            for (frameIndex in 0 until totalFrames) {
                yield() // Permite cancelamento cooperativo de coroutines

                val timestampMs = timeline.getTimeMsForFrame(frameIndex)
                val presentationTimeUs = frameIndex * frameDurationUs

                // Renderiza o frame no bitmap
                val snapshot = timeline.evaluateAt(timestampMs)
                frameRenderer.render(snapshot, targetBitmap)

                // Converte Bitmap para NV12 (YUV420SemiPlanar)
                targetBitmap.getPixels(argbPixels, 0, width, 0, 0, width, height)
                argbToNv12(argbPixels, width, height, yuvBuffer)

                // Envia buffer para o VideoEncoder
                feedEncoder(
                    encoder = videoEncoder,
                    data = yuvBuffer,
                    presentationTimeUs = presentationTimeUs,
                    isEos = (frameIndex == totalFrames - 1)
                )

                // Drena pacotes prontos do VideoEncoder para o Muxer
                drainEncoder(
                    encoder = videoEncoder,
                    bufferInfo = videoBufferInfo,
                    muxer = muxer,
                    trackIndex = videoTrackIndex,
                    isEosExpected = (frameIndex == totalFrames - 1)
                )

                // Emite progresso de vídeo (0% .. 80%)
                val videoProgress = (frameIndex.toFloat() / totalFrames.coerceAtLeast(1L)) * 0.80f
                onProgress(
                    VideoExportProgress(
                        phase = ExportPhase.RENDERING_FRAMES,
                        progress = videoProgress,
                        currentFrame = frameIndex + 1,
                        totalFrames = totalFrames,
                        currentTimestampMs = timestampMs,
                        totalDurationMs = totalDurationMs,
                        message = "Renderizando quadros: ${frameIndex + 1} de $totalFrames (${(videoProgress * 100).toInt()}%)"
                    )
                )
            }

            // Drena quaisquer buffers pendentes do encoder de vídeo
            drainEncoder(
                encoder = videoEncoder,
                bufferInfo = videoBufferInfo,
                muxer = muxer,
                trackIndex = videoTrackIndex,
                isEosExpected = true
            )

            // 3. Processamento e Codificação do Áudio (80% .. 98%)
            onProgress(
                VideoExportProgress(
                    phase = ExportPhase.PROCESSING_AUDIO,
                    progress = 0.82f,
                    currentFrame = totalFrames,
                    totalFrames = totalFrames,
                    totalDurationMs = totalDurationMs,
                    message = "Mixando e codificando áudio AAC..."
                )
            )

            audioRenderer.processAndEncodeAudio(
                project = timeline.project,
                totalDurationMs = totalDurationMs,
                muxer = muxer,
                audioTrackIndex = audioTrackIndex,
                onProgress = { audioFraction ->
                    val totalProgress = 0.80f + (audioFraction * 0.18f)
                    onProgress(
                        VideoExportProgress(
                            phase = ExportPhase.PROCESSING_AUDIO,
                            progress = totalProgress,
                            currentFrame = totalFrames,
                            totalFrames = totalFrames,
                            totalDurationMs = totalDurationMs,
                            message = "Finalizando áudio: ${(audioFraction * 100).toInt()}%"
                        )
                    )
                }
            )

            // 4. Finalização
            onProgress(
                VideoExportProgress(
                    phase = ExportPhase.MUXING_MP4,
                    progress = 0.99f,
                    currentFrame = totalFrames,
                    totalFrames = totalFrames,
                    totalDurationMs = totalDurationMs,
                    message = "Finalizando container MP4..."
                )
            )

        } catch (c: CancellationException) {
            outputFile.delete()
            throw c
        } catch (e: Throwable) {
            outputFile.delete()
            throw ExportException("Falha na codificação do vídeo MP4: ${e.message}", e)
        } finally {
            try {
                videoEncoder.stop()
            } catch (_: Exception) {}
            try {
                videoEncoder.release()
            } catch (_: Exception) {}

            try {
                if (isMuxerStarted) {
                    muxer.stop()
                }
            } catch (_: Exception) {}
            try {
                muxer.release()
            } catch (_: Exception) {}

            try {
                if (!targetBitmap.isRecycled) {
                    targetBitmap.recycle()
                }
            } catch (_: Exception) {}
        }

        val fileSize = outputFile.length()
        return VideoExportResult(
            success = true,
            outputFile = outputFile,
            durationMs = totalDurationMs,
            fileSizeBytes = fileSize,
            width = width,
            height = height,
            fps = fps
        )
    }

    private fun feedEncoder(
        encoder: MediaCodec,
        data: ByteArray,
        presentationTimeUs: Long,
        isEos: Boolean
    ) {
        val inputIndex = encoder.dequeueInputBuffer(10_000L)
        if (inputIndex >= 0) {
            val inputBuffer = encoder.getInputBuffer(inputIndex) ?: return
            inputBuffer.clear()
            val bytesToWrite = kotlin.math.min(inputBuffer.remaining(), data.size)
            inputBuffer.put(data, 0, bytesToWrite)
            val flags = if (isEos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
            encoder.queueInputBuffer(inputIndex, 0, bytesToWrite, presentationTimeUs, flags)
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        muxer: MediaMuxer,
        trackIndex: Int,
        isEosExpected: Boolean
    ) {
        while (true) {
            val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
            if (outputIndex >= 0) {
                val outputBuffer = encoder.getOutputBuffer(outputIndex) ?: break

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    bufferInfo.size = 0
                }

                if (bufferInfo.size > 0) {
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                }

                encoder.releaseOutputBuffer(outputIndex, false)

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!isEosExpected) break else break
            } else {
                break
            }
        }
    }

    /**
     * Converte pixels ARGB para formato YUV420SemiPlanar (NV12).
     */
    private fun argbToNv12(argb: IntArray, width: Int, height: Int, outNv12: ByteArray) {
        val frameSize = width * height
        var yIndex = 0
        var uvIndex = frameSize

        for (j in 0 until height) {
            for (i in 0 until width) {
                val pixel = argb[j * width + i]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                // Y calculation (ITU-R BT.601)
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                outNv12[yIndex++] = y.coerceIn(16, 235).toByte()

                // U and V calculations for even coordinates
                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    outNv12[uvIndex++] = u.coerceIn(16, 240).toByte()
                    outNv12[uvIndex++] = v.coerceIn(16, 240).toByte()
                }
            }
        }
    }
}
