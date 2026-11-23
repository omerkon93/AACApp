package com.kon.myaacapp

import android.content.Context
import android.content.ContextWrapper
import com.kon.myaacapp.service.audio.AudioRecordingService
import com.kon.myaacapp.service.audio.PcmAudioRecorder
import com.kon.myaacapp.service.audio.RecordingFailureReason
import com.kon.myaacapp.service.audio.RecordingResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.File
import java.nio.file.Files

class AudioRecordingServiceTest {

    private lateinit var tempFilesDir: File
    private lateinit var testContext: Context

    @Before
    fun setup() {
        tempFilesDir = Files.createTempDirectory("audio_service_test").toFile()
        testContext = object : ContextWrapper(mock(Context::class.java)) {
            override fun getFilesDir(): File = tempFilesDir
            override fun getApplicationContext(): Context = this
        }
    }

    @After
    fun tearDown() {
        tempFilesDir.deleteRecursively()
    }

    @Test
    fun `silent recording returns Silent and writes no output or temp files`() = runBlocking {
        val fakeRecorder = FakePcmAudioRecorder(
            chunks = listOf(ShortArray(1024) { 0 }, ShortArray(1024) { 250 }),
        )
        val service = AudioRecordingService(testContext) { fakeRecorder }

        assertTrue(service.startRecording("tile_1", "he"))
        delay(50)
        val result = service.stopRecording()

        assertEquals(RecordingResult.Silent, result)
        val audioFiles = File(tempFilesDir, "audio_tiles/he").listFiles().orEmpty()
        assertTrue("Expected no audio or tmp files after silent recording", audioFiles.isEmpty())
    }

    @Test
    fun `audible recording writes verified wav file and canPlayAudio returns true`() = runBlocking {
        val audibleChunk = ShortArray(2048) { idx ->
            if (idx % 2 == 0) 6000 else -6000
        }
        val fakeRecorder = FakePcmAudioRecorder(chunks = listOf(audibleChunk))
        val service = AudioRecordingService(testContext) { fakeRecorder }

        assertTrue(service.startRecording("apple", "he"))
        delay(50)
        val result = service.stopRecording()

        assertTrue(result is RecordingResult.Success)
        val savedPath = (result as RecordingResult.Success).filePath
        val savedFile = File(savedPath)
        assertTrue(savedFile.exists())
        assertTrue(savedFile.length() > AudioRecordingService.WAV_HEADER_SIZE_BYTES)
        assertTrue(service.isValidWavFile(savedFile))
        assertTrue(service.canPlayAudio(savedPath))
    }

    @Test
    fun `silent replacement leaves previous working wav file intact`() = runBlocking {
        val workingFile = File(tempFilesDir, "audio_tiles/he/audio_existing.wav").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(128) { 0x11 }.also {
                it[0] = 'R'.code.toByte(); it[1] = 'I'.code.toByte(); it[2] = 'F'.code.toByte(); it[3] = 'F'.code.toByte()
                it[8] = 'W'.code.toByte(); it[9] = 'A'.code.toByte(); it[10] = 'V'.code.toByte(); it[11] = 'E'.code.toByte()
            })
        }
        val originalBytes = workingFile.readBytes()

        val silentRecorder = FakePcmAudioRecorder(chunks = listOf(ShortArray(512) { 0 }))
        val service = AudioRecordingService(testContext) { silentRecorder }

        assertTrue(service.startRecording("existing", "he"))
        delay(50)
        val result = service.stopRecording()

        assertEquals(RecordingResult.Silent, result)
        assertTrue(workingFile.exists())
        assertArrayEquals(originalBytes, workingFile.readBytes())
        assertTrue(service.canPlayAudio(workingFile.absolutePath))
    }

    @Test
    fun `cancelled recording removes temp files and returns Cancelled`() = runBlocking {
        val audibleChunk = ShortArray(1024) { 4000 }
        val fakeRecorder = FakePcmAudioRecorder(chunks = listOf(audibleChunk), repeatForever = true)
        val service = AudioRecordingService(testContext) { fakeRecorder }

        assertTrue(service.startRecording("tile_cancel", "en"))
        delay(50)
        service.cancelRecording()

        val audioFiles = File(tempFilesDir, "audio_tiles/en").listFiles().orEmpty()
        assertTrue(audioFiles.isEmpty())
    }

    @Test
    fun `initialization failure returns false from startRecording`() = runBlocking {
        val failingRecorder = FakePcmAudioRecorder(initBufferSize = null)
        val service = AudioRecordingService(testContext) { failingRecorder }

        assertFalse(service.startRecording("tile_fail", "he"))
        val stopResult = service.stopRecording()
        assertEquals(
            RecordingResult.Failed(RecordingFailureReason.NOT_RECORDING),
            stopResult,
        )
    }

    @Test
    fun `canPlayAudio rejects missing empty and corrupt wav files`() {
        val service = AudioRecordingService(testContext)

        assertFalse(service.canPlayAudio(File(tempFilesDir, "missing.wav").absolutePath))

        val emptyFile = File(tempFilesDir, "empty.wav").apply { writeBytes(ByteArray(0)) }
        assertFalse(service.canPlayAudio(emptyFile.absolutePath))

        val truncatedFile = File(tempFilesDir, "truncated.wav").apply {
            writeBytes(byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte()))
        }
        assertFalse(service.canPlayAudio(truncatedFile.absolutePath))

        val nonWavContentFile = File(tempFilesDir, "corrupt.wav").apply {
            writeBytes(ByteArray(100) { 'X'.code.toByte() })
        }
        assertFalse(service.canPlayAudio(nonWavContentFile.absolutePath))
    }

    private class FakePcmAudioRecorder(
        private val chunks: List<ShortArray> = emptyList(),
        private val initBufferSize: Int? = 1024,
        private val repeatForever: Boolean = false,
    ) : PcmAudioRecorder {
        private var index = 0

        override fun initialize(sampleRate: Int, channelConfig: Int, audioFormat: Int): Int? =
            initBufferSize

        override fun start(): Boolean = initBufferSize != null

        override fun read(buffer: ShortArray, offset: Int, size: Int): Int {
            if (chunks.isEmpty()) {
                Thread.sleep(5)
                return 0
            }
            if (index >= chunks.size) {
                if (repeatForever) {
                    index = 0
                } else {
                    Thread.sleep(5)
                    return 0
                }
            }
            val source = chunks[index++]
            val count = minOf(size, source.size)
            System.arraycopy(source, 0, buffer, offset, count)
            return count
        }

        override fun stopAndRelease() = Unit
    }
}
