package com.kon.myaacapp

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.IOException

class AudioRecordingService(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentRecordingFile: File? = null

    /**
     * Starts recording audio for a specific tile ID.
     * @return The absolute path to the recorded file if successful, null otherwise.
     */
    fun startRecording(tileId: String): String? {
        val outputDir = File(context.filesDir, "audio_tiles")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val outputFile = File(outputDir, "audio_$tileId.m4a")
        currentRecordingFile = outputFile

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputFile.absolutePath)

            try {
                prepare()
                start()
            } catch (e: IOException) {
                Log.e("AudioRecordingService", "prepare() failed", e)
                return null
            } catch (e: IllegalStateException) {
                Log.e("AudioRecordingService", "start() failed", e)
                return null
            }
        }
        return outputFile.absolutePath
    }

    fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioRecordingService", "stopRecording() failed", e)
        } finally {
            mediaRecorder = null
        }
    }

    fun playRecording(path: String) {
        stopPlayback()
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(path)
                prepare()
                start()
            } catch (e: IOException) {
                Log.e("AudioRecordingService", "playRecording() failed", e)
            }
        }
    }

    fun stopPlayback() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
    }

    fun deleteRecording(path: String) {
        val file = File(path)
        if (file.exists()) {
            file.delete()
        }
    }
}
