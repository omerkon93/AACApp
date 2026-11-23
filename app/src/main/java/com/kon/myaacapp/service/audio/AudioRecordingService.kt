package com.kon.myaacapp.service.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import com.kon.myaacapp.core.locale.LocaleHelper
import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.service.AACTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

enum class RecordingFailureReason {
    NOT_RECORDING,
    ALREADY_RECORDING,
    INITIALIZATION_FAILED,
    IO_ERROR,
    INVALID_OUTPUT,
}

sealed interface RecordingResult {
    data class Success(val filePath: String) : RecordingResult
    data object Silent : RecordingResult
    data object Cancelled : RecordingResult
    data class Failed(
        val reason: RecordingFailureReason,
        val cause: Throwable? = null,
    ) : RecordingResult
}

interface PcmAudioRecorder {
    fun initialize(sampleRate: Int, channelConfig: Int, audioFormat: Int): Int?
    fun start(): Boolean
    fun read(buffer: ShortArray, offset: Int, size: Int): Int
    fun stopAndRelease()
}

private class AndroidPcmRecorder : PcmAudioRecorder {
    private var audioRecord: AudioRecord? = null

    @SuppressLint("MissingPermission")
    override fun initialize(sampleRate: Int, channelConfig: Int, audioFormat: Int): Int? {
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize <= 0) {
            return null
        }

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize,
            )
        } catch (e: Exception) {
            Log.e("AudioRecordingService", "Failed to construct AudioRecord", e)
            return null
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { record.release() }
            return null
        }

        audioRecord = record
        return bufferSize
    }

    override fun start(): Boolean {
        val record = audioRecord ?: return false
        return try {
            record.startRecording()
            true
        } catch (e: Exception) {
            Log.e("AudioRecordingService", "AudioRecord.startRecording() failed", e)
            false
        }
    }

    override fun read(buffer: ShortArray, offset: Int, size: Int): Int {
        return audioRecord?.read(buffer, offset, size) ?: -1
    }

    override fun stopAndRelease() {
        val record = audioRecord
        audioRecord = null
        record?.apply {
            runCatching {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
            }
            runCatching { release() }
        }
    }
}

class AudioRecordingService(
    private val context: Context,
    private val pcmRecorderFactory: () -> PcmAudioRecorder = { AndroidPcmRecorder() },
) : AudioClipPlayer {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var mediaPlayer: MediaPlayer? = null
    private var activeRecorder: PcmAudioRecorder? = null

    @Volatile
    private var isRecording = false

    @Volatile
    private var isCancelled = false

    private var recordingDeferred: Deferred<RecordingResult>? = null
    private var pendingTempFile: File? = null
    private var pendingOutputFile: File? = null

    companion object {
        private const val TAG = "AudioRecordingService"
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val SILENCE_THRESHOLD = 1500
        internal const val WAV_HEADER_SIZE_BYTES = 44L
    }

    private fun resolveAudioPath(audioUri: String): String {
        return when {
            audioUri.startsWith("file://") ->
                runCatching { java.net.URI(audioUri).path }.getOrNull()
                    ?: audioUri.removePrefix("file://")
            audioUri.startsWith("/") || audioUri.startsWith("content://") || File(audioUri).isAbsolute -> audioUri
            else -> File(context.filesDir, audioUri).absolutePath
        }
    }

    private fun resolveLocalFile(audioUri: String): File? {
        val trimmed = audioUri.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("content://")) {
            return null
        }
        val path = resolveAudioPath(trimmed)
        return File(path)
    }

    override fun canPlayAudio(audioUri: String): Boolean {
        val trimmed = audioUri.trim()
        if (trimmed.isEmpty()) return false

        if (trimmed.startsWith("content://")) {
            return runCatching {
                context.contentResolver.openAssetFileDescriptor(Uri.parse(trimmed), "r")?.use {
                    true
                } ?: false
            }.getOrDefault(false)
        }

        val file = resolveLocalFile(trimmed) ?: return false
        if (!file.exists() || !file.isFile || !file.canRead() || file.length() <= 0L) {
            return false
        }

        if (file.extension.equals("wav", ignoreCase = true)) {
            return isValidWavFile(file)
        }

        return true
    }

    internal fun isValidWavFile(file: File): Boolean {
        if (!file.exists() || !file.isFile || !file.canRead() || file.length() <= WAV_HEADER_SIZE_BYTES) {
            return false
        }
        return runCatching {
            val header = ByteArray(12)
            FileInputStream(file).use { input ->
                val read = input.read(header)
                if (read < 12) return@use false
                header[0] == 'R'.code.toByte() &&
                    header[1] == 'I'.code.toByte() &&
                    header[2] == 'F'.code.toByte() &&
                    header[3] == 'F'.code.toByte() &&
                    header[8] == 'W'.code.toByte() &&
                    header[9] == 'A'.code.toByte() &&
                    header[10] == 'V'.code.toByte() &&
                    header[11] == 'E'.code.toByte()
            }
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun startRecording(tileId: String, languageCode: String): Boolean {
        if (isRecording || recordingDeferred?.isActive == true) {
            Log.w(TAG, "startRecording called while a recording is already active")
            return false
        }

        val normalizedLang = LocaleHelper.normalize(languageCode)
        val outputDir = File(context.filesDir, "audio_tiles/$normalizedLang")
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            Log.e(TAG, "Failed to create audio output directory: ${outputDir.absolutePath}")
            return false
        }

        val sanitizedId = tileId
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .ifBlank { "tile" }
        val uniqueSuffix = UUID.randomUUID().toString().take(8)
        val outputFile = File(outputDir, "audio_${sanitizedId}_$uniqueSuffix.wav")
        val tempFile = File(outputDir, ".recording_${sanitizedId}_$uniqueSuffix.wav.tmp")

        val recorder = pcmRecorderFactory()
        val bufferSize = recorder.initialize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == null || bufferSize <= 0) {
            Log.e(TAG, "AudioRecord initialization failed")
            recorder.stopAndRelease()
            return false
        }

        if (!recorder.start()) {
            Log.e(TAG, "AudioRecord start failed")
            recorder.stopAndRelease()
            return false
        }

        activeRecorder = recorder
        pendingTempFile = tempFile
        pendingOutputFile = outputFile
        isCancelled = false
        isRecording = true

        recordingDeferred = serviceScope.async {
            var audioData = ShortArray(bufferSize * 10)
            var totalShorts = 0
            val readBuffer = ShortArray(bufferSize)

            try {
                while (isRecording && !isCancelled) {
                    val readCount = recorder.read(readBuffer, 0, bufferSize)
                    if (readCount > 0) {
                        if (totalShorts + readCount > audioData.size) {
                            val newSize = maxOf(audioData.size * 2, totalShorts + readCount)
                            audioData = audioData.copyOf(newSize)
                        }
                        System.arraycopy(readBuffer, 0, audioData, totalShorts, readCount)
                        totalShorts += readCount
                    } else if (readCount < 0) {
                        break
                    }
                }
            } finally {
                recorder.stopAndRelease()
            }

            if (isCancelled) {
                runCatching { tempFile.delete() }
                runCatching { outputFile.delete() }
                RecordingResult.Cancelled
            } else {
                saveTrimmedWav(
                    rawShorts = audioData,
                    totalSize = totalShorts,
                    tempFile = tempFile,
                    outputFile = outputFile,
                )
            }
        }

        return true
    }

    suspend fun stopRecording(): RecordingResult {
        val deferred = recordingDeferred
            ?: return RecordingResult.Failed(RecordingFailureReason.NOT_RECORDING)

        isRecording = false
        val result = try {
            withContext(Dispatchers.IO) {
                deferred.await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "stopRecording failed", e)
            cleanupPendingRecordingFiles()
            RecordingResult.Failed(RecordingFailureReason.IO_ERROR, e)
        } finally {
            activeRecorder?.stopAndRelease()
            activeRecorder = null
            recordingDeferred = null
            pendingTempFile = null
            pendingOutputFile = null
        }

        return result
    }

    suspend fun cancelRecording() {
        isCancelled = true
        isRecording = false
        activeRecorder?.stopAndRelease()
        val deferred = recordingDeferred
        if (deferred != null) {
            runCatching {
                withContext(Dispatchers.IO) {
                    deferred.await()
                }
            }
        }
        cleanupPendingRecordingFiles()
        activeRecorder = null
        recordingDeferred = null
        pendingTempFile = null
        pendingOutputFile = null
    }

    private fun cleanupPendingRecordingFiles() {
        runCatching { pendingTempFile?.delete() }
        runCatching { pendingOutputFile?.delete() }
    }

    internal fun saveTrimmedWav(
        rawShorts: ShortArray,
        totalSize: Int,
        tempFile: File,
        outputFile: File,
    ): RecordingResult {
        if (totalSize <= 0) {
            runCatching { tempFile.delete() }
            return RecordingResult.Silent
        }

        var startIdx = 0
        while (startIdx < totalSize && abs(rawShorts[startIdx].toInt()) < SILENCE_THRESHOLD) {
            startIdx++
        }

        var endIdx = totalSize - 1
        while (endIdx > startIdx && abs(rawShorts[endIdx].toInt()) < SILENCE_THRESHOLD) {
            endIdx--
        }

        if (startIdx >= endIdx) {
            Log.w(TAG, "Recording was all silence")
            runCatching { tempFile.delete() }
            return RecordingResult.Silent
        }

        val trimmedSize = endIdx - startIdx + 1

        return try {
            tempFile.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) {
                    return RecordingResult.Failed(RecordingFailureReason.IO_ERROR)
                }
            }

            FileOutputStream(tempFile).use { fos ->
                val totalAudioLen = (trimmedSize * 2).toLong()
                val totalDataLen = totalAudioLen + 36

                writeWavHeader(fos, totalAudioLen, totalDataLen)

                val buffer = ByteArray(4096)
                var bufIndex = 0

                for (i in startIdx..endIdx) {
                    val sample = rawShorts[i].toInt()
                    buffer[bufIndex++] = (sample and 0x00FF).toByte()
                    buffer[bufIndex++] = ((sample and 0xFF00) shr 8).toByte()

                    if (bufIndex == 4096) {
                        fos.write(buffer)
                        bufIndex = 0
                    }
                }

                if (bufIndex > 0) {
                    fos.write(buffer, 0, bufIndex)
                }
                fos.fd.sync()
            }

            if (!isValidWavFile(tempFile)) {
                runCatching { tempFile.delete() }
                return RecordingResult.Failed(RecordingFailureReason.INVALID_OUTPUT)
            }

            if (outputFile.exists() && !outputFile.delete()) {
                runCatching { tempFile.delete() }
                return RecordingResult.Failed(RecordingFailureReason.IO_ERROR)
            }

            val moved = tempFile.renameTo(outputFile) || run {
                tempFile.copyTo(outputFile, overwrite = true)
                tempFile.delete()
                true
            }

            if (!moved || !isValidWavFile(outputFile)) {
                runCatching { tempFile.delete() }
                runCatching { outputFile.delete() }
                return RecordingResult.Failed(RecordingFailureReason.INVALID_OUTPUT)
            }

            Log.d(TAG, "Saved verified WAV to ${outputFile.absolutePath} (${outputFile.length()} bytes)")
            RecordingResult.Success(outputFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save WAV file", e)
            runCatching { tempFile.delete() }
            runCatching { outputFile.delete() }
            RecordingResult.Failed(RecordingFailureReason.IO_ERROR, e)
        }
    }

    private fun writeWavHeader(
        out: OutputStream,
        totalAudioLen: Long,
        totalDataLen: Long,
    ) {
        val channels = 1
        val byteRate = (SAMPLE_RATE * channels * 16 / 8).toLong()
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xffL).toByte(); header[5] = (totalDataLen shr 8 and 0xffL).toByte()
        header[6] = (totalDataLen shr 16 and 0xffL).toByte(); header[7] = (totalDataLen shr 24 and 0xffL).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0; header[22] = channels.toByte(); header[23] = 0
        header[24] = (SAMPLE_RATE.toLong() and 0xffL).toByte(); header[25] = (SAMPLE_RATE.toLong() shr 8 and 0xffL).toByte()
        header[26] = (SAMPLE_RATE.toLong() shr 16 and 0xffL).toByte(); header[27] = (SAMPLE_RATE.toLong() shr 24 and 0xffL).toByte()
        header[28] = (byteRate and 0xffL).toByte(); header[29] = (byteRate shr 8 and 0xffL).toByte()
        header[30] = (byteRate shr 16 and 0xffL).toByte(); header[31] = (byteRate shr 24 and 0xffL).toByte()
        header[32] = (channels * 16 / 8).toByte(); header[33] = 0; header[34] = 16; header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xffL).toByte(); header[41] = (totalAudioLen shr 8 and 0xffL).toByte()
        header[42] = (totalAudioLen shr 16 and 0xffL).toByte(); header[43] = (totalAudioLen shr 24 and 0xffL).toByte()
        out.write(header, 0, 44)
    }

    override fun playRecording(audioUri: String, onError: (() -> Unit)?) {
        if (!canPlayAudio(audioUri)) {
            Log.w(TAG, "Audio file missing or unreadable: $audioUri")
            stopPlayback()
            onError?.invoke()
            return
        }

        try {
            stopPlayback()
            val player = MediaPlayer()
            mediaPlayer = player
            var errorReported = false
            val reportError = {
                if (!errorReported) {
                    errorReported = true
                    stopPlayback()
                    onError?.invoke()
                }
            }

            player.setOnCompletionListener {
                stopPlayback()
            }
            player.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error: $what, $extra for $audioUri")
                reportError()
                true
            }
            if (audioUri.startsWith("content://")) {
                player.setDataSource(context, Uri.parse(audioUri))
            } else {
                player.setDataSource(resolveAudioPath(audioUri))
            }
            player.setOnPreparedListener {
                try {
                    it.start()
                } catch (e: Exception) {
                    Log.e(TAG, "MediaPlayer start() failed", e)
                    reportError()
                }
            }
            player.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "playRecording() failed for $audioUri", e)
            stopPlayback()
            onError?.invoke()
        }
    }

    override suspend fun playRecordingSuspend(path: String): Boolean {
        if (!canPlayAudio(path)) {
            Log.w(TAG, "Suspend playback skipped; file missing or unreadable: $path")
            stopPlayback()
            return false
        }

        return suspendCancellableCoroutine { continuation ->
            stopPlayback()
            val player = MediaPlayer()
            mediaPlayer = player
            try {
                player.setOnCompletionListener {
                    stopPlayback()
                    if (continuation.isActive) continuation.resume(true)
                }
                player.setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: $what, $extra for $path")
                    stopPlayback()
                    if (continuation.isActive) continuation.resume(false)
                    true
                }
                if (path.startsWith("content://")) {
                    player.setDataSource(context, Uri.parse(path))
                } else {
                    player.setDataSource(resolveAudioPath(path))
                }
                player.setOnPreparedListener {
                    try {
                        it.start()
                    } catch (e: Exception) {
                        Log.e(TAG, "MediaPlayer start() failed", e)
                        stopPlayback()
                        if (continuation.isActive) continuation.resume(false)
                    }
                }
                player.prepareAsync()
            } catch (e: Exception) {
                Log.e(TAG, "playRecordingSuspend() failed for $path", e)
                stopPlayback()
                if (continuation.isActive) continuation.resume(false)
            }

            continuation.invokeOnCancellation {
                stopPlayback()
            }
        }
    }

    suspend fun speakSentence(
        sentence: List<CombinedTile>,
        ttsHelper: TextToSpeechHelper,
        tileService: AACTileService,
        tilesUseTts: Boolean = false,
        speechPlayer: TileSpeechPlayer = TileSpeechPlayer(this, ttsHelper),
    ) {
        for (tile in sentence) {
            speechPlayer.speakTileSuspend(
                tileId = tile.id,
                ttsText = tileService.getTTSText(tile),
                audioUri = tile.audioUri,
                isCustomAudio = tile.isCustomAudio,
                tilesUseTts = tilesUseTts,
            )
            delay(150.milliseconds)
        }
    }

    override fun stopPlayback() {
        val player = mediaPlayer
        mediaPlayer = null
        player?.apply {
            runCatching {
                if (isPlaying) {
                    stop()
                }
            }
            runCatching {
                release()
            }
        }
    }

    fun deleteRecording(path: String) {
        serviceScope.launch {
            try {
                val file = resolveLocalFile(path)
                if (file != null && file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete file", e)
            }
        }
    }
}
