package com.example.data.audio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.sqrt

class WaveformGenerator(private val context: Context) {

    private val memoryCache = ConcurrentHashMap<String, List<Float>>()
    private val cacheDir = File(context.cacheDir, "waveforms").apply { mkdirs() }

    suspend fun getWaveform(localPath: String, sampleCount: Int = 80): List<Float> = withContext(Dispatchers.IO) {
        if (localPath.isBlank()) return@withContext emptyList()
        val file = File(localPath)
        if (!file.exists() || file.length() == 0L) {
            return@withContext emptyList()
        }

        val cacheKey = "${file.name}_${file.length()}_${file.lastModified()}_$sampleCount"
        memoryCache[cacheKey]?.let { return@withContext it }

        // Check disk cache
        val diskCacheFile = File(cacheDir, "${cacheKey.hashCode()}.wave")
        if (diskCacheFile.exists()) {
            try {
                val samples = readFromDiskCache(diskCacheFile)
                if (samples.isNotEmpty()) {
                    memoryCache[cacheKey] = samples
                    return@withContext samples
                }
            } catch (_: Exception) {
                diskCacheFile.delete()
            }
        }

        // Extract real amplitude from media file
        val extracted = extractAmplitudesFromFile(file, sampleCount)
        memoryCache[cacheKey] = extracted

        try {
            writeToDiskCache(diskCacheFile, extracted)
        } catch (_: Exception) {}

        extracted
    }

    private fun extractAmplitudesFromFile(file: File, sampleCount: Int): List<Float> {
        val extractor = MediaExtractor()
        val rawAmplitudes = FloatArray(sampleCount) { 0.1f }

        try {
            extractor.setDataSource(file.absolutePath)
            var audioTrackIndex = -1
            var durationUs = 0L

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationUs = format.getLong(MediaFormat.KEY_DURATION)
                    }
                    break
                }
            }

            if (audioTrackIndex == -1) {
                extractor.release()
                return generateFallbackBaseline(file, sampleCount)
            }

            extractor.selectTrack(audioTrackIndex)
            val buffer = ByteBuffer.allocate(8192)

            if (durationUs > 0) {
                val stepUs = durationUs / sampleCount
                for (s in 0 until sampleCount) {
                    val targetUs = s * stepUs
                    extractor.seekTo(targetUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                    var sumEnergy = 0.0
                    var samplesRead = 0

                    for (p in 0 until 4) {
                        buffer.clear()
                        val bytesRead = extractor.readSampleData(buffer, 0)
                        if (bytesRead <= 0) break

                        var packetSum = 0.0
                        for (b in 0 until bytesRead step 2) {
                            val byteVal = buffer.get(b).toInt()
                            packetSum += (byteVal * byteVal)
                        }
                        sumEnergy += sqrt(packetSum / max(1, bytesRead / 2))
                        samplesRead++
                        if (!extractor.advance()) break
                    }

                    val avgRms = if (samplesRead > 0) sumEnergy / samplesRead else 5.0
                    rawAmplitudes[s] = avgRms.toFloat()
                }
            } else {
                var s = 0
                while (s < sampleCount) {
                    buffer.clear()
                    val bytesRead = extractor.readSampleData(buffer, 0)
                    if (bytesRead <= 0) break
                    var packetSum = 0.0
                    for (b in 0 until bytesRead step 2) {
                        val byteVal = buffer.get(b).toInt()
                        packetSum += (byteVal * byteVal)
                    }
                    rawAmplitudes[s] = sqrt(packetSum / max(1, bytesRead / 2)).toFloat()
                    s++
                    if (!extractor.advance()) break
                }
            }
            extractor.release()
        } catch (_: Exception) {
            try { extractor.release() } catch (_: Exception) {}
            return generateFallbackBaseline(file, sampleCount)
        }

        // Normalize amplitudes to [0.12f .. 1.0f]
        val maxAmp = rawAmplitudes.maxOrNull() ?: 1.0f
        val minAmp = rawAmplitudes.minOrNull() ?: 0.0f
        val range = (maxAmp - minAmp).coerceAtLeast(0.001f)

        return rawAmplitudes.map {
            val normalized = ((it - minAmp) / range).coerceIn(0f, 1f)
            (0.12f + (normalized * 0.88f)).coerceIn(0.12f, 1.0f)
        }
    }

    private fun generateFallbackBaseline(file: File, sampleCount: Int): List<Float> {
        val size = file.length()
        val seed = (file.name.hashCode() xor size.toInt()).toLong()
        val random = java.util.Random(seed)
        return List(sampleCount) {
            0.15f + (random.nextFloat() * 0.5f)
        }
    }

    private fun readFromDiskCache(file: File): List<Float> {
        DataInputStream(BufferedInputStream(FileInputStream(file))).use { dis ->
            val count = dis.readInt()
            val list = ArrayList<Float>(count)
            for (i in 0 until count) {
                list.add(dis.readFloat())
            }
            return list
        }
    }

    private fun writeToDiskCache(file: File, data: List<Float>) {
        DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { dos ->
            dos.writeInt(data.size)
            for (value in data) {
                dos.writeFloat(value)
            }
        }
    }

    fun invalidateCache(localPath: String) {
        val file = File(localPath)
        val prefix = "${file.name}_"
        memoryCache.keys.removeIf { it.startsWith(prefix) }
        val diskCacheFile = File(cacheDir, "${localPath.hashCode()}.wave")
        if (diskCacheFile.exists()) {
            diskCacheFile.delete()
        }
    }

    fun clearAllCache() {
        memoryCache.clear()
        cacheDir.listFiles()?.forEach { it.delete() }
    }
}
