package com.kon.myaacapp

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

class AudioRecordingService(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var audioRecord: AudioRecord? = null

    // OPTIMIZATION: @Volatile ensures the IO thread instantly sees when the Main thread stops recording
    @Volatile
    private var isRecording = false
    private var recordingJob: Job? = null

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val SILENCE_THRESHOLD = 1500
    }

    private fun resolveAudioPath(audioUri: String): String {
        return if (audioUri.startsWith("/") || audioUri.startsWith("content://") || audioUri.startsWith("file://")) {
            audioUri
        } else {
            File(context.filesDir, audioUri).absolutePath
        }
    }

    @SuppressLint("MissingPermission")
    fun startRecording(tileId: String, languageCode: String): String? {
        val normalizedLang = LocaleHelper.normalize(languageCode)
        val outputDir = File(context.filesDir, "audio_tiles/$normalizedLang")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val outputFile = File(outputDir, "audio_$tileId.wav")

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if ((bufferSize == AudioRecord.ERROR) || (bufferSize == AudioRecord.ERROR_BAD_VALUE)) {
            Log.e("AudioRecordingService", "Invalid buffer size")
            return null
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize,
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("AudioRecordingService", "AudioRecord initialization failed")
            return null
        }

        audioRecord?.startRecording()
        isRecording = true

        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            // OPTIMIZATION: Replaced MutableList<Short> with a pure primitive array.
            // This eliminates 100% of object boxing (converting primitive short -> Short Object),
            // saving massive amounts of memory and preventing GC thrashing during long recordings.
            var audioData = ShortArray(bufferSize * 10)
            var totalShorts = 0
            val readBuffer = ShortArray(bufferSize)

            while (isRecording) {
                val readCount = audioRecord?.read(readBuffer, 0, bufferSize) ?: 0
                if (readCount > 0) {
                    // Amortized O(1) array doubling to handle infinite recording length
                    if (totalShorts + readCount > audioData.size) {
                        audioData = audioData.copyOf(audioData.size * 2)
                    }
                    System.arraycopy(readBuffer, 0, audioData, totalShorts, readCount)
                    totalShorts += readCount
                }
            }

            processAndSaveAudio(audioData, totalShorts, outputFile)
        }

        return outputFile.absolutePath
    }

    suspend fun stopRecording() {
        isRecording = false
        audioRecord?.apply {
            if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                stop()
            }
            release()
        }
        audioRecord = null
        recordingJob?.join()
        recordingJob = null
    }

    // Adjusted signature to accept our new memory-optimized primitive array
    private fun processAndSaveAudio(rawShorts: ShortArray, totalSize: Int, outputFile: File) {
        if (totalSize == 0) return

        var startIdx = 0
        while (startIdx < totalSize && kotlin.math.abs(rawShorts[startIdx].toInt()) < SILENCE_THRESHOLD) {
            startIdx++
        }

        var endIdx = totalSize - 1
        while (endIdx > startIdx && kotlin.math.abs(rawShorts[endIdx].toInt()) < SILENCE_THRESHOLD) {
            endIdx--
        }

        if (startIdx >= endIdx) {
            Log.w("AudioRecordingService", "Recording was all silence")
            return
        }

        val trimmedSize = endIdx - startIdx + 1

        try {
            FileOutputStream(outputFile).use { fos ->
                val totalAudioLen = (trimmedSize * 2).toLong()
                val totalDataLen = totalAudioLen + 36

                writeWavHeader(fos, totalAudioLen, totalDataLen)

                // OPTIMIZATION: Bitwise chunk writing.
                // Instead of allocating `ByteBuffer.allocate(2048)` repeatedly, we use a single
                // primitive ByteArray and bit-shift the data. This writes straight to disk at maximum speed.
                val buffer = ByteArray(4096)
                var bufIndex = 0

                for (i in startIdx..endIdx) {
                    val sample = rawShorts[i].toInt()
                    buffer[bufIndex++] = (sample and 0x00FF).toByte()         // Little Endian Low
                    buffer[bufIndex++] = ((sample and 0xFF00) shr 8).toByte() // Little Endian High

                    if (bufIndex == 4096) {
                        fos.write(buffer)
                        bufIndex = 0
                    }
                }

                // Flush remainder
                if (bufIndex > 0) {
                    fos.write(buffer, 0, bufIndex)
                }
            }
            Log.d("AudioRecordingService", "Saved trimmed WAV to ${outputFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("AudioRecordingService", "Failed to save WAV file", e)
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

    fun playRecording(audioUri: String) {
        try {
            // FIX: Ensure the old player is stopped AND released, fixing native memory leaks.
            stopPlayback()
            val actualPath = resolveAudioPath(audioUri)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(actualPath)
                // OPTIMIZATION: Non-blocking preparation. This instantly returns the thread
                // back to the UI, while Android loads the audio file in the hardware background.
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun playRecordingSuspend(path: String) = suspendCancellableCoroutine { continuation ->
        stopPlayback()
        val player = MediaPlayer()
        mediaPlayer = player
        try {
            val actualPath = resolveAudioPath(path)
            player.setDataSource(actualPath)
            player.setOnCompletionListener {
                stopPlayback()
                if (continuation.isActive) continuation.resume(Unit)
            }
            player.setOnErrorListener { _, what, extra ->
                Log.e("AudioRecordingService", "MediaPlayer error: $what, $extra")
                stopPlayback()
                if (continuation.isActive) continuation.resume(Unit)
                true
            }
            // OPTIMIZATION: Same non-blocking async pattern applied to the suspend version
            player.setOnPreparedListener { it.start() }
            player.prepareAsync()
        } catch (e: Exception) {
            Log.e("AudioRecordingService", "playRecordingSuspend() failed", e)
            stopPlayback()
            if (continuation.isActive) continuation.resume(Unit)
        }

        continuation.invokeOnCancellation {
            stopPlayback()
        }
    }

    suspend fun speakSentence(
        sentence: List<CombinedTile>,
        ttsHelper: TextToSpeechHelper,
        tileService: AACTileService
    ) {
        for (tile in sentence) {
            if (tile.audioUri != null) {
                playRecordingSuspend(tile.audioUri!!)
            } else {
                val text = tileService.getTTSText(tile)
                ttsHelper.speakSuspend(text)
            }
            delay(150.milliseconds)
        }
    }

    fun stopPlayback() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            // FIX: Release actually destroys the C++ layer audio decoder.
            release()
        }
        mediaPlayer = null
    }

    fun deleteRecording(path: String) {
        // OPTIMIZATION: Fire-and-forget deletion on the IO thread so UI isn't blocked.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val actualPath = resolveAudioPath(path)
                val file = File(actualPath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.e("AudioRecordingService", "Failed to delete file", e)
            }
        }
    }
}