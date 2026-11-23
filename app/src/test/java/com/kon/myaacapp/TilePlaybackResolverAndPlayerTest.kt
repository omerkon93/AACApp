package com.kon.myaacapp

import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.model.CreateTileRequest
import com.kon.myaacapp.domain.model.TileDefinition
import com.kon.myaacapp.domain.model.TileLayoutState
import com.kon.myaacapp.domain.model.TileType
import com.kon.myaacapp.domain.repository.TileRepository
import com.kon.myaacapp.domain.usecase.tile.AddTileUseCase
import com.kon.myaacapp.domain.usecase.tile.ObserveTilesUseCase
import com.kon.myaacapp.domain.usecase.tile.UpdateTileUseCase
import com.kon.myaacapp.service.audio.AudioClipPlayer
import com.kon.myaacapp.service.audio.SpeechSynthesizer
import com.kon.myaacapp.service.audio.TilePlaybackDecision
import com.kon.myaacapp.service.audio.TilePlaybackPath
import com.kon.myaacapp.service.audio.TilePlaybackResolver
import com.kon.myaacapp.service.audio.TileSpeechPlayer
import com.kon.myaacapp.ui.editor.TileEditorAction
import com.kon.myaacapp.ui.editor.TileEditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TilePlaybackResolverAndPlayerTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `custom recording takes precedence over tts when tilesUseTts is enabled`() {
        val decision = TilePlaybackResolver.resolve(
            ttsText = "שלום",
            audioUri = "/data/user/0/app/files/audio_tiles/he/custom.wav",
            isCustomAudio = true,
            tilesUseTts = true,
            canPlayAudio = { true },
        )

        assertTrue(decision is TilePlaybackDecision.PlayAudio)
        assertEquals("/data/user/0/app/files/audio_tiles/he/custom.wav", (decision as TilePlaybackDecision.PlayAudio).audioUri)
    }

    @Test
    fun `bundled audio respects tilesUseTts enabled by speaking tts`() {
        val decision = TilePlaybackResolver.resolve(
            ttsText = "עצור",
            audioUri = "audio_tiles/he/audio_stop.wav",
            isCustomAudio = false,
            tilesUseTts = true,
            canPlayAudio = { true },
        )

        assertEquals(TilePlaybackDecision.SpeakTts(text = "עצור", usedFallback = false), decision)
    }

    @Test
    fun `bundled audio plays recording when tilesUseTts is disabled`() {
        val decision = TilePlaybackResolver.resolve(
            ttsText = "עצור",
            audioUri = "audio_tiles/he/audio_stop.wav",
            isCustomAudio = false,
            tilesUseTts = false,
            canPlayAudio = { true },
        )

        assertTrue(decision is TilePlaybackDecision.PlayAudio)
        assertEquals("audio_tiles/he/audio_stop.wav", (decision as TilePlaybackDecision.PlayAudio).audioUri)
    }

    @Test
    fun `missing recording falls back to tts when ttsText is available`() {
        val fakeAudio = FakeAudioClipPlayer(playablePaths = emptySet())
        val fakeTts = FakeSpeechSynthesizer()
        val player = TileSpeechPlayer(fakeAudio, fakeTts)

        val path = player.speakTile(
            tileId = "tile_custom",
            ttsText = "מים",
            audioUri = "/missing/audio.wav",
            isCustomAudio = true,
            tilesUseTts = true,
        )

        assertEquals(TilePlaybackPath.TTS, path)
        assertEquals(listOf("מים"), fakeTts.spokenTexts)
        assertTrue(fakeAudio.playedPaths.isEmpty())
        val event = player.lastPlaybackEvent.value!!
        assertEquals(TilePlaybackPath.TTS, event.path)
        assertTrue(event.usedFallback)
        assertFalse(event.fileExists)
    }

    @Test
    fun `async mediaplayer failure triggers tts fallback and updates playback telemetry`() {
        val fakeAudio = FakeAudioClipPlayer(
            playablePaths = setOf("/corrupt/audio.wav"),
            failAsyncOnPlay = true,
        )
        val fakeTts = FakeSpeechSynthesizer()
        val player = TileSpeechPlayer(fakeAudio, fakeTts)

        player.speakTile(
            tileId = "tile_corrupt",
            ttsText = "תפוח",
            audioUri = "/corrupt/audio.wav",
            isCustomAudio = true,
            tilesUseTts = true,
        )

        assertEquals(listOf("/corrupt/audio.wav"), fakeAudio.playedPaths)
        assertEquals(listOf("תפוח"), fakeTts.spokenTexts)
        val event = player.lastPlaybackEvent.value!!
        assertEquals(TilePlaybackPath.TTS, event.path)
        assertTrue(event.usedFallback)
    }

    @Test
    fun `suspend playback falls back to tts when suspend audio fails`() = runTest {
        val fakeAudio = FakeAudioClipPlayer(
            playablePaths = setOf("/corrupt/audio.wav"),
            suspendResult = false,
        )
        val fakeTts = FakeSpeechSynthesizer()
        val player = TileSpeechPlayer(fakeAudio, fakeTts)

        val path = player.speakTileSuspend(
            tileId = "tile_1",
            ttsText = "לחם",
            audioUri = "/corrupt/audio.wav",
            isCustomAudio = true,
            tilesUseTts = true,
        )

        assertEquals(TilePlaybackPath.TTS, path)
        assertEquals(listOf("לחם"), fakeTts.spokenTexts)
        assertTrue(player.lastPlaybackEvent.value!!.usedFallback)
    }

    @Test
    fun `tile editor preserves bundled vs custom audio flag across label edits`() = runTest {
        val repo = FakeTileRepository()
        val vm = TileEditorViewModel(
            addTileUseCase = AddTileUseCase(repo),
            updateTileUseCase = UpdateTileUseCase(repo),
            observeTilesUseCase = ObserveTilesUseCase(repo),
        )

        val starterBundledTile = CombinedTile(
            definition = TileDefinition(
                id = "stop",
                label = "עצור",
                ttsText = "עצור",
                audioUri = "audio_tiles/he/audio_stop.wav",
                isCustomAudio = false,
                type = TileType.BASIC,
                languageCode = "he",
            ),
            layoutState = TileLayoutState(tileId = "stop", parentId = null, cellIndex = 0),
        )

        vm.initialize(
            existingTile = starterBundledTile,
            initialCellIndex = 0,
            currentParentId = null,
            languageCode = "he",
        )
        vm.onAction(TileEditorAction.LabelChanged("עצור עכשיו"))
        vm.onAction(TileEditorAction.SaveClicked)
        advanceUntilIdle()

        val savedLabelEditOnly = repo.updatedTiles.last()
        assertEquals("audio_tiles/he/audio_stop.wav", savedLabelEditOnly.audioUri)
        assertFalse(savedLabelEditOnly.isCustomAudio)

        vm.onAction(TileEditorAction.AudioUriChanged("/data/files/audio_tiles/he/custom_stop.wav"))
        vm.onAction(TileEditorAction.SaveClicked)
        advanceUntilIdle()

        val savedWithCustomAudio = repo.updatedTiles.last()
        assertEquals("/data/files/audio_tiles/he/custom_stop.wav", savedWithCustomAudio.audioUri)
        assertTrue(savedWithCustomAudio.isCustomAudio)

        vm.onAction(TileEditorAction.AudioUriChanged(null))
        vm.onAction(TileEditorAction.SaveClicked)
        advanceUntilIdle()

        val savedDeletedAudio = repo.updatedTiles.last()
        assertNull(savedDeletedAudio.audioUri)
        assertFalse(savedDeletedAudio.isCustomAudio)
    }

    private class FakeAudioClipPlayer(
        private val playablePaths: Set<String>,
        private val failAsyncOnPlay: Boolean = false,
        private val suspendResult: Boolean = true,
    ) : AudioClipPlayer {
        val playedPaths = mutableListOf<String>()

        override fun canPlayAudio(audioUri: String): Boolean = audioUri in playablePaths

        override fun playRecording(audioUri: String, onError: (() -> Unit)?) {
            playedPaths += audioUri
            if (failAsyncOnPlay) {
                onError?.invoke()
            }
        }

        override suspend fun playRecordingSuspend(path: String): Boolean {
            playedPaths += path
            return suspendResult && canPlayAudio(path)
        }

        override fun stopPlayback() = Unit
    }

    private class FakeSpeechSynthesizer : SpeechSynthesizer {
        val spokenTexts = mutableListOf<String>()

        override fun speak(text: String) {
            spokenTexts += text
        }

        override suspend fun speakSuspend(text: String) {
            spokenTexts += text
        }

        override fun stop() = Unit
    }

    private class FakeTileRepository : TileRepository {
        val updatedTiles = mutableListOf<CombinedTile>()
        override val baseDefinitions: StateFlow<List<TileDefinition>> = MutableStateFlow(emptyList())
        override suspend fun loadAllDefinitions() = Unit
        override suspend fun reload() = Unit
        override fun getCombinedTiles(parentId: String?, langCode: String): Flow<List<CombinedTile>> = flowOf(emptyList())
        override fun getAllDefinitionsAsCombinedTiles(langCode: String): Flow<List<CombinedTile>> = flowOf(emptyList())
        override suspend fun addTile(request: CreateTileRequest) = Unit
        override suspend fun updateTile(tile: CombinedTile) {
            updatedTiles += tile
        }
        override suspend fun deleteTile(tile: CombinedTile) = Unit
        override suspend fun updateTileIndex(tileId: String, parentId: String?, newIndex: Int) = Unit
        override suspend fun updateTileVisibility(tileId: String, parentId: String?, isHidden: Boolean) = Unit
        override suspend fun swapTilesByIndex(parentId: String?, fromIndex: Int, toIndex: Int) = Unit
        override suspend fun attachTileToCategory(tileId: String, parentId: String?, langCode: String, cellIndex: Int?) = Unit
        override suspend fun removeTileFromCategory(tileId: String, parentId: String?) = Unit
        override suspend fun incrementClickCount(id: String, parentId: String?) = Unit
        override fun observeUsageEvents(startTime: Long, endTime: Long) = flowOf(emptyList<com.kon.myaacapp.domain.model.TileUsageEvent>())
    }
}
