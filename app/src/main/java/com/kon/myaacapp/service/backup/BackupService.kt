package com.kon.myaacapp.service.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.room.withTransaction
import com.kon.myaacapp.core.locale.LocaleHelper
import com.kon.myaacapp.data.local.AACDatabase
import com.kon.myaacapp.data.local.entity.AACTile
import com.kon.myaacapp.data.local.entity.TileClickEvent
import com.kon.myaacapp.data.local.entity.TilePlacement
import com.kon.myaacapp.data.repository.AACRepository
import com.kon.myaacapp.data.repository.BoardSettingsSnapshot
import com.kon.myaacapp.data.repository.ExportedBoardSettings
import com.kon.myaacapp.data.repository.SettingsRepository
import com.kon.myaacapp.domain.model.TileDefinition
import com.kon.myaacapp.domain.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

enum class RestoreApplyStep {
    BEFORE_FILE_SWAP,
    AFTER_FILE_SWAP,
    AFTER_ROOM_CLEAR,
    AFTER_SETTINGS_APPLIED,
    AFTER_RELOAD,
}

enum class BackupMediaType {
    IMAGE,
    AUDIO,
}

data class MissingMediaReference(
    val tileId: String,
    val languageCode: String,
    val mediaType: BackupMediaType,
    val reference: String,
)

data class RestoreReport(
    val success: Boolean,
    val formatVersion: Int? = null,
    val restoredSettings: Boolean = false,
    val missingMedia: List<MissingMediaReference> = emptyList(),
)

@Serializable
data class BackupManifest(
    val formatVersion: Int = BackupService.CURRENT_BACKUP_FORMAT_VERSION,
    val exportedAtEpochMs: Long = 0L,
    val settings: ExportedBoardSettings? = null,
)

class BackupService(
    private val context: Context,
    private val repository: AACRepository,
    private val database: AACDatabase = AACDatabase.getDatabase(context),
    internal val settingsRepository: SettingsRepository = SettingsRepository(context),
) {
    private val operationMutex = Mutex()

    private val validationJson = Json {
        ignoreUnknownKeys = true
    }

    private val snapshotJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val restoreWorkDir: File
        get() = File(context.filesDir, RESTORE_WORK_DIR)

    private val stagingDir: File
        get() = File(restoreWorkDir, STAGING_DIR)

    private val rollbackDir: File
        get() = File(restoreWorkDir, ROLLBACK_DIR)

    private val journalFile: File
        get() = File(restoreWorkDir, JOURNAL_FILE)

    /**
     * Exports the app's internal storage directories and explicit board settings allowlist
     * to a versioned portable ZIP archive.
     */
    suspend fun exportDatabase(contentResolver: ContentResolver, uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                try {
                    val activeBoardLanguage = resolveActiveBoardLanguageForExport()
                    val exportedSettings = settingsRepository.readExportedBoardSettings(
                        boardLanguageCodeOverride = activeBoardLanguage,
                    )
                    val writtenEntries = HashSet<String>()
                    val extraMediaToWrite = LinkedHashMap<String, File>()

                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        ZipOutputStream(outputStream.buffered(BUFFER_SIZE)).use { zos ->
                            val baseDir = context.filesDir

                            baseDir.walkTopDown()
                                .filter { it.isFile }
                                .forEach { file ->
                                    val relativePath =
                                        file.relativeTo(baseDir).path.replace("\\", "/")

                                    if (relativePath.endsWith(".tmp", ignoreCase = true) ||
                                        relativePath.endsWith(".bak", ignoreCase = true)
                                    ) {
                                        return@forEach
                                    }

                                    when {
                                        relativePath.startsWith("profiles/") ||
                                            relativePath.startsWith("audio_tiles/") ||
                                            relativePath.startsWith("images/") -> {
                                            writeFileEntryToZip(
                                                zos = zos,
                                                entryName = relativePath,
                                                sourceFile = file,
                                                writtenEntries = writtenEntries,
                                            )
                                        }

                                        relativePath.startsWith("tiles/") -> {
                                            writeTileEntryToZip(
                                                zos = zos,
                                                entryName = relativePath,
                                                sourceFile = file,
                                                writtenEntries = writtenEntries,
                                                extraMediaToWrite = extraMediaToWrite,
                                            )
                                        }
                                    }
                                }

                            extraMediaToWrite.forEach { (entryName, externalFile) ->
                                if (entryName !in writtenEntries && externalFile.isFile) {
                                    writeFileEntryToZip(
                                        zos = zos,
                                        entryName = entryName,
                                        sourceFile = externalFile,
                                        writtenEntries = writtenEntries,
                                    )
                                }
                            }

                            val manifest = BackupManifest(
                                formatVersion = CURRENT_BACKUP_FORMAT_VERSION,
                                exportedAtEpochMs = System.currentTimeMillis(),
                                settings = exportedSettings,
                            )
                            if (writtenEntries.add(BACKUP_MANIFEST_FILE)) {
                                zos.putNextEntry(ZipEntry(BACKUP_MANIFEST_FILE))
                                zos.write(
                                    snapshotJson.encodeToString(manifest).toByteArray(Charsets.UTF_8),
                                )
                                zos.closeEntry()
                            }
                        }
                    } ?: return@withLock false
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Export failed", e)
                    false
                }
            }
        }

    /**
     * Recovers an interrupted restore if the app died during commit or rollback.
     * Should be invoked during startup before repositories reload active board data.
     */
    suspend fun recoverIncompleteRestoreIfNeeded(): Boolean =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                if (!restoreWorkDir.exists()) {
                    return@withLock true
                }

                val state = readJournalState()
                if (state == JournalState.COMMITTING || state == JournalState.ROLLING_BACK) {
                    Log.w(TAG, "Recovering interrupted restore in state: $state")
                    val recovered = rollbackFromSnapshotLocked()
                    if (recovered) {
                        restoreWorkDir.deleteRecursively()
                    }
                    recovered
                } else {
                    restoreWorkDir.deleteRecursively()
                    true
                }
            }
        }

    /**
     * Restores the app's internal storage and exported board settings from a ZIP file
     * using staging, validation, rollback snapshotting, and a journaled commit.
     */
    suspend fun importDatabase(
        contentResolver: ContentResolver,
        uri: Uri,
        onApplyStep: (suspend (RestoreApplyStep) -> Unit)? = null,
        onCommittedReload: (suspend () -> Unit)? = null,
        onRollbackReload: (suspend () -> Unit)? = null,
    ): Boolean =
        importDatabaseWithReport(
            contentResolver = contentResolver,
            uri = uri,
            onApplyStep = onApplyStep,
            onCommittedReload = onCommittedReload,
            onRollbackReload = onRollbackReload,
        ).success

    suspend fun importDatabaseWithReport(
        contentResolver: ContentResolver,
        uri: Uri,
        onApplyStep: (suspend (RestoreApplyStep) -> Unit)? = null,
        onCommittedReload: (suspend () -> Unit)? = null,
        onRollbackReload: (suspend () -> Unit)? = null,
    ): RestoreReport =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                try {
                    val staged = contentResolver.openInputStream(uri)?.use { inputStream ->
                        stageAndValidateZip(
                            inputStream = inputStream,
                            preserveProfiles = false,
                        )
                    }
                    if (staged == null) {
                        cleanupStagingOnly()
                        return@withLock RestoreReport(success = false)
                    }

                    commitStagedRestoreLocked(
                        staged = staged,
                        onApplyStep = onApplyStep,
                        onCommittedReload = onCommittedReload,
                        onRollbackReload = onRollbackReload,
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Import failed", e)
                    cleanupStagingOnly()
                    RestoreReport(success = false)
                }
            }
        }

    suspend fun importFromAssets(fileName: String): Boolean =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                if (!fileName.endsWith(".zip")) {
                    return@withLock false
                }

                try {
                    val staged = context.assets.open(fileName).use { inputStream ->
                        stageAndValidateZip(
                            inputStream = inputStream,
                            preserveProfiles = true,
                        )
                    }
                    if (staged == null) {
                        cleanupStagingOnly()
                        return@withLock false
                    }

                    withContext(NonCancellable) {
                        resolveAndRewriteStagedMedia(staged.payloadDir)
                        val targetDirs = ASSET_RESTORE_DIRS
                        targetDirs.forEach { dirName ->
                            val liveDir = File(context.filesDir, dirName)
                            if (liveDir.exists() && !liveDir.deleteRecursively()) {
                                throw IOException("Failed to clear $dirName before asset import")
                            }
                            val stagedDir = File(staged.payloadDir, dirName)
                            if (stagedDir.exists()) {
                                moveOrCopyDirectory(stagedDir, liveDir)
                            }
                        }

                        repository.clearAllStatistics()
                        repository.deleteAllTilesFromRoom()
                        writeFileAtomically(
                            targetFile = File(context.filesDir, STARTER_DATA_VERSION_FILE),
                            content = CURRENT_STARTER_DATA_VERSION.toString(),
                        )
                        restoreWorkDir.deleteRecursively()
                        true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Import from assets failed", e)
                    cleanupStagingOnly()
                    false
                }
            }
        }

    /**
     * Updates bundled starter dictionaries and missing bundled audio files on app upgrade
     * without overwriting user profiles, placements, custom vocabulary, recordings, images,
     * or usage statistics.
     */
    suspend fun syncStarterDataFromAssetsIfNeeded(
        fileName: String = "initial_data.zip",
        targetVersion: Int = CURRENT_STARTER_DATA_VERSION,
    ): Boolean =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                if (!fileName.endsWith(".zip")) {
                    return@withLock false
                }

                val versionFile = File(context.filesDir, STARTER_DATA_VERSION_FILE)
                val currentVersion = if (versionFile.exists()) {
                    versionFile.readText().trim().toIntOrNull() ?: 0
                } else {
                    0
                }
                if (currentVersion >= targetVersion) {
                    return@withLock true
                }

                val tempZip = File(context.cacheDir, "starter_sync_${System.nanoTime()}.zip")
                try {
                    context.assets.open(fileName).use { inputStream ->
                        tempZip.outputStream().buffered(BUFFER_SIZE).use { output ->
                            inputStream.copyTo(output)
                        }
                    }

                    val filesCanonicalPrefix = context.filesDir.canonicalPath + File.separator
                    val bundledDictionaries = LinkedHashMap<String, List<TileDefinition>>()

                    ZipFile(tempZip).use { zipFile ->
                        val entries = zipFile.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            val normalizedName = entry.name.replace('\\', '/').removePrefix("/")
                            if (entry.isDirectory || normalizedName.isEmpty()) {
                                continue
                            }

                            when {
                                normalizedName.startsWith("$AUDIO_TILES_DIR/") -> {
                                    val destAudio = File(context.filesDir, normalizedName)
                                    val destCanonical = runCatching { destAudio.canonicalPath }.getOrNull()
                                    if (destCanonical == null || !destCanonical.startsWith(filesCanonicalPrefix)) {
                                        throw SecurityException("Zip entry ${entry.name} is outside target directory")
                                    }
                                    if (!destAudio.exists()) {
                                        destAudio.parentFile?.mkdirs()
                                        zipFile.getInputStream(entry).buffered(BUFFER_SIZE).use { input ->
                                            destAudio.outputStream().buffered(BUFFER_SIZE).use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                    }
                                }

                                STARTER_DICT_PATH_REGEX.matches(normalizedName) -> {
                                    val raw = zipFile.getInputStream(entry).bufferedReader().use {
                                        it.readText().removePrefix("\uFEFF")
                                    }
                                    bundledDictionaries[normalizedName] =
                                        validationJson.decodeFromString<List<TileDefinition>>(raw)
                                }
                            }
                        }
                    }

                    bundledDictionaries.forEach { (relativeDictPath, bundledDefs) ->
                        val resolvedBundledDefs = bundledDefs.map { definition ->
                            val resolvedAudio = resolveBundledMediaAgainstLiveFiles(
                                rawUri = definition.audioUri,
                                mediaDirName = AUDIO_TILES_DIR,
                            )
                            val resolvedImage = resolveBundledMediaAgainstLiveFiles(
                                rawUri = definition.imageUri,
                                mediaDirName = IMAGES_DIR,
                            )
                            definition.copy(
                                audioUri = resolvedAudio,
                                imageUri = resolvedImage,
                                isCustomAudio = if (resolvedAudio == null) false else definition.isCustomAudio,
                            )
                        }

                        val destDictFile = File(context.filesDir, relativeDictPath)
                        val existingDefs = if (destDictFile.exists()) {
                            runCatching {
                                validationJson.decodeFromString<List<TileDefinition>>(
                                    destDictFile.readText().removePrefix("\uFEFF"),
                                )
                            }.getOrDefault(emptyList())
                        } else {
                            emptyList()
                        }

                        val mergedDefs = (existingDefs + resolvedBundledDefs)
                            .associateBy { it.languageCode to it.id }
                            .values
                            .toList()

                        writeFileAtomically(
                            targetFile = destDictFile,
                            content = snapshotJson.encodeToString(mergedDefs),
                        )
                    }

                    writeFileAtomically(
                        targetFile = versionFile,
                        content = targetVersion.toString(),
                    )
                    true
                } catch (e: Exception) {
                    Log.w(TAG, "Starter data sync from $fileName failed", e)
                    false
                } finally {
                    tempZip.delete()
                }
            }
        }

    private fun resolveBundledMediaAgainstLiveFiles(
        rawUri: String?,
        mediaDirName: String,
    ): String? {
        val trimmed = rawUri?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (isRemoteOrResourceUri(trimmed)) {
            return trimmed
        }
        val normalized = normalizeLocalUriString(trimmed)
        val candidates = buildArchivedMediaCandidates(normalized, mediaDirName)
        val liveMediaRoot = File(context.filesDir, mediaDirName)
        val liveMediaPrefix = liveMediaRoot.canonicalPath + File.separator

        for (candidateRelPath in candidates) {
            val liveFile = File(context.filesDir, candidateRelPath)
            val liveCanonical = runCatching { liveFile.canonicalPath }.getOrNull() ?: continue
            if (liveCanonical.startsWith(liveMediaPrefix) && liveFile.isFile) {
                return liveFile.absolutePath
            }
        }
        return null
    }

    /**
     * Test-only helper that stages a valid backup, snapshots rollback state, writes COMMITTING,
     * executes apply steps up to [interruptAt], and returns without running in-process rollback
     * so startup recovery after process death can be exercised deterministically.
     */
    @VisibleForTesting
    internal suspend fun interruptApplyForTest(
        contentResolver: ContentResolver,
        uri: Uri,
        interruptAt: RestoreApplyStep,
    ): Boolean =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val staged = contentResolver.openInputStream(uri)?.use { inputStream ->
                    stageAndValidateZip(
                        inputStream = inputStream,
                        preserveProfiles = false,
                    )
                }
                if (staged == null) {
                    cleanupStagingOnly()
                    return@withLock false
                }

                resolveAndRewriteStagedMedia(staged.payloadDir)
                prepareRollbackSnapshotLocked()
                writeJournalState(JournalState.COMMITTING)

                if (interruptAt == RestoreApplyStep.BEFORE_FILE_SWAP) {
                    return@withLock true
                }

                applyStagedDirectories(staged.payloadDir)

                if (interruptAt == RestoreApplyStep.AFTER_FILE_SWAP) {
                    return@withLock true
                }

                clearRoomTablesInTransaction()

                if (interruptAt == RestoreApplyStep.AFTER_ROOM_CLEAR) {
                    return@withLock true
                }

                applyRestoredSettingsIfPresent(staged)
                true
            }
        }

    private fun writeFileEntryToZip(
        zos: ZipOutputStream,
        entryName: String,
        sourceFile: File,
        writtenEntries: MutableSet<String>,
    ) {
        if (!writtenEntries.add(entryName)) return
        try {
            val entry = ZipEntry(entryName)
            zos.putNextEntry(entry)
            sourceFile.inputStream().buffered(BUFFER_SIZE).use { it.copyTo(zos) }
            zos.closeEntry()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add $entryName to zip", e)
        }
    }

    private fun writeTileEntryToZip(
        zos: ZipOutputStream,
        entryName: String,
        sourceFile: File,
        writtenEntries: MutableSet<String>,
        extraMediaToWrite: MutableMap<String, File>,
    ) {
        if (!sourceFile.extension.equals("json", ignoreCase = true)) {
            writeFileEntryToZip(zos, entryName, sourceFile, writtenEntries)
            return
        }

        val raw = sourceFile.readText().removePrefix("\uFEFF")
        val definitions = runCatching {
            validationJson.decodeFromString<List<TileDefinition>>(raw)
        }.getOrNull()

        if (definitions == null) {
            writeFileEntryToZip(zos, entryName, sourceFile, writtenEntries)
            return
        }

        val portableDefinitions = definitions.map { definition ->
            definition.copy(
                imageUri = relativizeMediaUriForExport(
                    rawUri = definition.imageUri,
                    mediaDirName = IMAGES_DIR,
                    extraMediaToWrite = extraMediaToWrite,
                ),
                audioUri = relativizeMediaUriForExport(
                    rawUri = definition.audioUri,
                    mediaDirName = AUDIO_TILES_DIR,
                    extraMediaToWrite = extraMediaToWrite,
                ),
            )
        }

        if (!writtenEntries.add(entryName)) return
        zos.putNextEntry(ZipEntry(entryName))
        zos.write(snapshotJson.encodeToString(portableDefinitions).toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun relativizeMediaUriForExport(
        rawUri: String?,
        mediaDirName: String,
        extraMediaToWrite: MutableMap<String, File>,
    ): String? {
        val trimmed = rawUri?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (isRemoteOrResourceUri(trimmed)) {
            return trimmed
        }

        val pathCandidate = normalizeLocalUriString(trimmed)
        val mediaRoot = File(context.filesDir, mediaDirName)
        val mediaCanonicalPrefix = mediaRoot.canonicalPath + File.separator
        val asFile = if (pathCandidate.startsWith("/")) {
            File(pathCandidate)
        } else {
            File(context.filesDir, pathCandidate)
        }

        val canonicalFile = runCatching { asFile.canonicalPath }.getOrNull()
        if (canonicalFile != null && canonicalFile.startsWith(mediaCanonicalPrefix)) {
            val rel = canonicalFile.removePrefix(mediaCanonicalPrefix).replace("\\", "/")
            if (rel.isNotEmpty() && !hasTraversalSegment(rel)) {
                return "$mediaDirName/$rel"
            }
        }

        val marker = "/$mediaDirName/"
        val markerIndex = pathCandidate.lastIndexOf(marker)
        if (markerIndex >= 0) {
            val rel = pathCandidate.substring(markerIndex + 1)
            if (rel.length > mediaDirName.length + 1 && !hasTraversalSegment(rel)) {
                return rel
            }
        }

        if (pathCandidate.startsWith("$mediaDirName/") && !hasTraversalSegment(pathCandidate)) {
            return pathCandidate
        }

        if (asFile.isFile) {
            val uniqueHash = Integer.toHexString(pathCandidate.hashCode())
            val entryName = "$mediaDirName/external_${uniqueHash}_${asFile.name}"
            extraMediaToWrite.putIfAbsent(entryName, asFile)
            return entryName
        }

        return trimmed
    }

    private fun stageAndValidateZip(
        inputStream: InputStream,
        preserveProfiles: Boolean,
    ): StagedPayload? {
        stagingDir.deleteRecursively()
        if (!stagingDir.mkdirs()) {
            throw IOException("Failed to create staging directory")
        }

        val stagedZip = File(stagingDir, "candidate.zip")
        val payloadDir = File(stagingDir, "payload")
        if (!payloadDir.mkdirs()) {
            throw IOException("Failed to create staging payload directory")
        }

        val copiedBytes = stagedZip.outputStream().buffered(BUFFER_SIZE).use { output ->
            inputStream.copyTo(output)
        }
        if (copiedBytes <= 0L) {
            Log.e(TAG, "Backup archive stream was empty")
            return null
        }

        val payloadCanonicalPrefix = payloadDir.canonicalPath + File.separator
        var extractedFileCount = 0

        ZipFile(stagedZip).use { zipFile ->
            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val normalizedName = entry.name.replace('\\', '/').removePrefix("/")
                if (normalizedName.isEmpty()) {
                    continue
                }

                if (preserveProfiles && normalizedName.startsWith("profiles/")) {
                    continue
                }

                val targetFile = File(payloadDir, normalizedName)
                val entryCanonicalPath = targetFile.canonicalPath
                if (entryCanonicalPath != payloadDir.canonicalPath &&
                    !entryCanonicalPath.startsWith(payloadCanonicalPrefix)
                ) {
                    throw SecurityException("Zip entry ${entry.name} is outside of the target directory")
                }

                if (entry.isDirectory || normalizedName.endsWith("/")) {
                    if (!targetFile.exists() && !targetFile.mkdirs()) {
                        throw IOException("Failed to create staged directory ${targetFile.absolutePath}")
                    }
                } else {
                    targetFile.parentFile?.let { parent ->
                        if (!parent.exists() && !parent.mkdirs()) {
                            throw IOException("Failed to create staged parent directory ${parent.absolutePath}")
                        }
                    }

                    zipFile.getInputStream(entry).buffered(BUFFER_SIZE).use { entryInput ->
                        targetFile.outputStream().buffered(BUFFER_SIZE).use { entryOutput ->
                            entryInput.copyTo(entryOutput)
                        }
                    }
                    extractedFileCount++
                }
            }
        }

        if (extractedFileCount == 0) {
            Log.e(TAG, "Backup archive contained no files")
            return null
        }

        return validateStagedPayload(payloadDir, preserveProfiles)
    }

    private fun validateStagedPayload(
        payloadDir: File,
        preserveProfiles: Boolean,
    ): StagedPayload? {
        val manifestFile = File(payloadDir, BACKUP_MANIFEST_FILE)
        val parsedManifest = if (manifestFile.exists()) {
            val rawManifest = manifestFile.readText().removePrefix("\uFEFF")
            val manifest = runCatching {
                validationJson.decodeFromString<BackupManifest>(rawManifest)
            }.getOrElse { error ->
                Log.e(TAG, "Invalid backup manifest JSON", error)
                return null
            }

            if (manifest.formatVersion !in 1..CURRENT_BACKUP_FORMAT_VERSION) {
                Log.e(TAG, "Unsupported backup format version: ${manifest.formatVersion}")
                return null
            }
            manifest
        } else {
            null
        }

        val restoredProfileIds = linkedSetOf<String>()
        if (!preserveProfiles) {
            val profilesDir = File(payloadDir, "profiles")
            val profileFiles = profilesDir.listFiles { file ->
                file.isFile && file.extension.equals("json", ignoreCase = true)
            }?.sortedBy { it.name }.orEmpty()

            if (profileFiles.isEmpty()) {
                Log.e(TAG, "Backup archive has no profile JSON files")
                return null
            }

            profileFiles.forEach { profileFile ->
                val raw = profileFile.readText().removePrefix("\uFEFF")
                val parsed = validationJson.decodeFromString<UserProfile>(raw)
                if (parsed.profileId.isBlank() || parsed.profileName.isBlank()) {
                    Log.e(TAG, "Invalid profile metadata in ${profileFile.name}")
                    return null
                }
                restoredProfileIds += parsed.profileId
            }
        }

        val tilesDir = File(payloadDir, "tiles")
        if (!tilesDir.exists() || !tilesDir.isDirectory) {
            Log.e(TAG, "Backup archive is missing tiles directory")
            return null
        }

        val tileJsonFiles = tilesDir.walkTopDown()
            .filter { file ->
                file.isFile && file.extension.equals("json", ignoreCase = true)
            }
            .toList()

        if (tileJsonFiles.isEmpty()) {
            Log.e(TAG, "Backup archive has no tile dictionary JSON files")
            return null
        }

        tileJsonFiles.forEach { tileFile ->
            val raw = tileFile.readText().removePrefix("\uFEFF")
            validationJson.decodeFromString<List<TileDefinition>>(raw)
        }

        return StagedPayload(
            payloadDir = payloadDir,
            manifest = parsedManifest,
            restoredProfileIds = restoredProfileIds,
        )
    }

    private fun resolveAndRewriteStagedMedia(payloadDir: File): List<MissingMediaReference> {
        val tilesDir = File(payloadDir, "tiles")
        if (!tilesDir.exists()) return emptyList()

        val missingMedia = mutableListOf<MissingMediaReference>()
        val tileJsonFiles = tilesDir.walkTopDown()
            .filter { file ->
                file.isFile && file.extension.equals("json", ignoreCase = true)
            }
            .toList()

        tileJsonFiles.forEach { tileFile ->
            val raw = tileFile.readText().removePrefix("\uFEFF")
            val definitions = validationJson.decodeFromString<List<TileDefinition>>(raw)
            var changed = false

            val rewrittenDefinitions = definitions.map { definition ->
                val resolvedImage = resolveSingleMediaReference(
                    rawUri = definition.imageUri,
                    mediaDirName = IMAGES_DIR,
                    mediaType = BackupMediaType.IMAGE,
                    tile = definition,
                    payloadDir = payloadDir,
                    missingMedia = missingMedia,
                )
                val resolvedAudio = resolveSingleMediaReference(
                    rawUri = definition.audioUri,
                    mediaDirName = AUDIO_TILES_DIR,
                    mediaType = BackupMediaType.AUDIO,
                    tile = definition,
                    payloadDir = payloadDir,
                    missingMedia = missingMedia,
                )

                val resolvedIsCustomAudio = if (resolvedAudio == null) {
                    false
                } else {
                    definition.isCustomAudio
                }

                if (
                    resolvedImage != definition.imageUri ||
                    resolvedAudio != definition.audioUri ||
                    resolvedIsCustomAudio != definition.isCustomAudio
                ) {
                    changed = true
                    definition.copy(
                        imageUri = resolvedImage,
                        audioUri = resolvedAudio,
                        isCustomAudio = resolvedIsCustomAudio,
                    )
                } else {
                    definition
                }
            }

            if (changed) {
                tileFile.writeText(snapshotJson.encodeToString(rewrittenDefinitions))
            }
        }

        return missingMedia
    }

    private fun resolveSingleMediaReference(
        rawUri: String?,
        mediaDirName: String,
        mediaType: BackupMediaType,
        tile: TileDefinition,
        payloadDir: File,
        missingMedia: MutableList<MissingMediaReference>,
    ): String? {
        val trimmed = rawUri?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (isRemoteOrResourceUri(trimmed)) {
            return trimmed
        }

        val normalized = normalizeLocalUriString(trimmed)
        val candidates = buildArchivedMediaCandidates(normalized, mediaDirName)
        val stagedMediaRoot = File(payloadDir, mediaDirName)
        val stagedMediaPrefix = stagedMediaRoot.canonicalPath + File.separator

        for (candidateRelPath in candidates) {
            val stagedFile = File(payloadDir, candidateRelPath)
            val stagedCanonical = runCatching { stagedFile.canonicalPath }.getOrNull() ?: continue
            if (stagedCanonical.startsWith(stagedMediaPrefix) && stagedFile.isFile) {
                return File(context.filesDir, candidateRelPath).absolutePath
            }
        }

        Log.w(
            TAG,
            "Missing $mediaType media in backup archive for tile '${tile.id}' (${tile.languageCode}): $rawUri",
        )
        missingMedia += MissingMediaReference(
            tileId = tile.id,
            languageCode = tile.languageCode,
            mediaType = mediaType,
            reference = rawUri,
        )
        return null
    }

    private fun buildArchivedMediaCandidates(
        normalizedPath: String,
        mediaDirName: String,
    ): List<String> {
        val candidates = mutableListOf<String>()

        fun addIfSafe(relPath: String) {
            val clean = relPath.removePrefix("/")
            if (clean.startsWith("$mediaDirName/") &&
                clean.length > mediaDirName.length + 1 &&
                !hasTraversalSegment(clean) &&
                clean !in candidates
            ) {
                candidates += clean
            }
        }

        if (normalizedPath.startsWith("$mediaDirName/")) {
            addIfSafe(normalizedPath)
        } else if (normalizedPath.startsWith("files/$mediaDirName/")) {
            addIfSafe(normalizedPath.removePrefix("files/"))
        }

        val marker = "/$mediaDirName/"
        val markerIndex = normalizedPath.lastIndexOf(marker)
        if (markerIndex >= 0) {
            addIfSafe(normalizedPath.substring(markerIndex + 1))
        }

        if (!normalizedPath.startsWith("/") && !normalizedPath.startsWith("$mediaDirName/")) {
            addIfSafe("$mediaDirName/$normalizedPath")
        }

        return candidates
    }

    private suspend fun commitStagedRestoreLocked(
        staged: StagedPayload,
        onApplyStep: (suspend (RestoreApplyStep) -> Unit)?,
        onCommittedReload: (suspend () -> Unit)?,
        onRollbackReload: (suspend () -> Unit)?,
    ): RestoreReport = withContext(NonCancellable + Dispatchers.IO) {
        try {
            val missingMedia = resolveAndRewriteStagedMedia(staged.payloadDir)

            prepareRollbackSnapshotLocked()
            writeJournalState(JournalState.COMMITTING)

            onApplyStep?.invoke(RestoreApplyStep.BEFORE_FILE_SWAP)
            applyStagedDirectories(staged.payloadDir)
            onApplyStep?.invoke(RestoreApplyStep.AFTER_FILE_SWAP)

            clearRoomTablesInTransaction()
            onApplyStep?.invoke(RestoreApplyStep.AFTER_ROOM_CLEAR)

            val restoredSettings = applyRestoredSettingsIfPresent(staged)
            onApplyStep?.invoke(RestoreApplyStep.AFTER_SETTINGS_APPLIED)

            onCommittedReload?.invoke()
            onApplyStep?.invoke(RestoreApplyStep.AFTER_RELOAD)

            writeJournalState(JournalState.COMMITTED)
            restoreWorkDir.deleteRecursively()

            RestoreReport(
                success = true,
                formatVersion = staged.manifest?.formatVersion ?: LEGACY_BACKUP_FORMAT_VERSION,
                restoredSettings = restoredSettings,
                missingMedia = missingMedia,
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Restore commit failed; rolling back to pre-restore state", t)
            val rolledBack = rollbackFromSnapshotLocked()
            if (rolledBack) {
                runCatching {
                    if (onRollbackReload != null) {
                        onRollbackReload.invoke()
                    } else {
                        repository.reload()
                    }
                }.onFailure { reloadError ->
                    Log.e(TAG, "Failed to reload repositories after rollback", reloadError)
                }
                restoreWorkDir.deleteRecursively()
            }
            RestoreReport(success = false)
        }
    }

    private suspend fun applyRestoredSettingsIfPresent(staged: StagedPayload): Boolean {
        val exportedSettings = staged.manifest?.settings ?: return false

        val isFreeSlotLocked = settingsRepository.readFreeCustomProfileLocked()
        val existingSelectedCustomId = settingsRepository.selectedCustomProfileIdFlow.first()

        val requestedActiveId = exportedSettings.activeProfileId
        val resolvedActiveId: String?
        val resolvedSelectedCustomId: String?

        if (isFreeSlotLocked) {
            // Preserve the installation's locked Free choice across backup imports.
            resolvedSelectedCustomId = existingSelectedCustomId
            resolvedActiveId = when {
                requestedActiveId == "default" && "default" in staged.restoredProfileIds -> "default"
                !existingSelectedCustomId.isNullOrBlank() &&
                    existingSelectedCustomId in staged.restoredProfileIds -> existingSelectedCustomId
                "default" in staged.restoredProfileIds -> "default"
                else -> null
            }
        } else {
            resolvedActiveId = when {
                !requestedActiveId.isNullOrBlank() && requestedActiveId in staged.restoredProfileIds -> {
                    requestedActiveId
                }

                "default" in staged.restoredProfileIds -> "default"
                else -> staged.restoredProfileIds.firstOrNull()
            }

            resolvedSelectedCustomId = when {
                !resolvedActiveId.isNullOrBlank() &&
                    resolvedActiveId != "default" &&
                    resolvedActiveId in staged.restoredProfileIds -> {
                    resolvedActiveId
                }

                !exportedSettings.selectedCustomProfileId.isNullOrBlank() &&
                    exportedSettings.selectedCustomProfileId != "default" &&
                    exportedSettings.selectedCustomProfileId in staged.restoredProfileIds -> {
                    exportedSettings.selectedCustomProfileId
                }

                else -> staged.restoredProfileIds.firstOrNull { it != "default" }
            }
        }

        val restoredBoardLanguage = exportedSettings.resolvedBoardLanguageCode()
        if (resolvedActiveId != null) {
            syncRestoredActiveProfileLanguageOnDisk(
                profileId = resolvedActiveId,
                boardLanguageCode = restoredBoardLanguage,
                forceSyncFromManifest = staged.manifest.formatVersion <= 2 ||
                    exportedSettings.boardLanguageCode != null,
            )
        }

        settingsRepository.applyExportedBoardSettings(
            settings = exportedSettings,
            resolvedActiveProfileId = resolvedActiveId,
            resolvedSelectedCustomProfileId = resolvedSelectedCustomId,
        )
        return true
    }

    private suspend fun resolveActiveBoardLanguageForExport(): String {
        val activeFromRepo = repository.activeProfile.value
            ?.activeLanguageCode
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (activeFromRepo != null) {
            return LocaleHelper.normalize(activeFromRepo)
        }

        val activeProfileId = settingsRepository.activeProfileIdFlow.first()
        val profilesDir = File(context.filesDir, "profiles")
        val profileFiles = profilesDir.listFiles { file ->
            file.isFile && file.extension.equals("json", ignoreCase = true)
        }?.sortedBy { it.name }.orEmpty()

        val profiles = profileFiles.mapNotNull { file ->
            runCatching {
                validationJson.decodeFromString<UserProfile>(file.readText().removePrefix("\uFEFF"))
            }.getOrNull()
        }
        val matchedProfile = profiles.find { it.profileId == activeProfileId }
            ?: profiles.find { it.profileId == "default" }
            ?: profiles.firstOrNull()

        val diskLanguage = matchedProfile?.activeLanguageCode?.trim()?.takeIf { it.isNotEmpty() }
        return LocaleHelper.normalize(diskLanguage ?: settingsRepository.languageCodeFlow.first())
    }

    private fun syncRestoredActiveProfileLanguageOnDisk(
        profileId: String,
        boardLanguageCode: String,
        forceSyncFromManifest: Boolean,
    ) {
        val profilesDir = File(context.filesDir, "profiles")
        val profileFiles = profilesDir.listFiles { file ->
            file.isFile && file.extension.equals("json", ignoreCase = true)
        }.orEmpty()

        profileFiles.forEach { file ->
            val parsed = runCatching {
                validationJson.decodeFromString<UserProfile>(file.readText().removePrefix("\uFEFF"))
            }.getOrNull() ?: return@forEach

            if (parsed.profileId == profileId) {
                val shouldUpdate = parsed.activeLanguageCode.isBlank() ||
                    (
                        forceSyncFromManifest &&
                            LocaleHelper.normalize(parsed.activeLanguageCode) != boardLanguageCode
                        )
                if (shouldUpdate) {
                    val updated = parsed.copy(activeLanguageCode = boardLanguageCode)
                    writeFileAtomically(file, snapshotJson.encodeToString(updated))
                }
                return
            }
        }
    }

    private suspend fun prepareRollbackSnapshotLocked() {
        rollbackDir.deleteRecursively()
        val rollbackFilesDir = File(rollbackDir, ROLLBACK_FILES_DIR)
        if (!rollbackFilesDir.mkdirs()) {
            throw IOException("Failed to create rollback files directory")
        }

        val existingDirs = mutableListOf<String>()
        FULL_RESTORE_DIRS.forEach { dirName ->
            val liveDir = File(context.filesDir, dirName)
            if (liveDir.exists()) {
                existingDirs += dirName
                val backupTarget = File(rollbackFilesDir, dirName)
                if (!liveDir.copyRecursively(backupTarget, overwrite = true)) {
                    throw IOException("Failed to snapshot live directory $dirName")
                }
            }
        }

        val settingsSnapshot = settingsRepository.readRollbackSettingsSnapshot()
        val roomSnapshot = database.withTransaction {
            val dao = database.aacTileDao()
            RoomRestoreSnapshot(
                tiles = dao.getAllTilesSync(),
                placements = dao.getAllPlacements(),
                clickEvents = dao.getAllClickEventsSync(),
            )
        }

        val metadata = RollbackMetadata(
            existingDirs = existingDirs,
            activeProfileId = settingsSnapshot.activeProfileId,
            settingsSnapshot = settingsSnapshot,
        )

        writeFileAtomically(
            targetFile = File(rollbackDir, ROLLBACK_METADATA_FILE),
            content = snapshotJson.encodeToString(metadata),
        )
        writeFileAtomically(
            targetFile = File(rollbackDir, ROOM_SNAPSHOT_FILE),
            content = snapshotJson.encodeToString(roomSnapshot),
        )
    }

    private suspend fun rollbackFromSnapshotLocked(): Boolean =
        withContext(NonCancellable + Dispatchers.IO) {
            try {
                if (!rollbackDir.exists()) {
                    Log.e(TAG, "Rollback directory missing; cannot recover snapshot")
                    return@withContext false
                }

                writeJournalState(JournalState.ROLLING_BACK)

                val metadataFile = File(rollbackDir, ROLLBACK_METADATA_FILE)
                val metadata = if (metadataFile.exists()) {
                    snapshotJson.decodeFromString<RollbackMetadata>(metadataFile.readText())
                } else {
                    RollbackMetadata(
                        existingDirs = FULL_RESTORE_DIRS.filter { dirName ->
                            File(rollbackDir, "$ROLLBACK_FILES_DIR/$dirName").exists()
                        },
                        activeProfileId = null,
                        settingsSnapshot = null,
                    )
                }

                val rollbackFilesDir = File(rollbackDir, ROLLBACK_FILES_DIR)
                FULL_RESTORE_DIRS.forEach { dirName ->
                    val liveDir = File(context.filesDir, dirName)
                    if (liveDir.exists() && !liveDir.deleteRecursively()) {
                        throw IOException("Failed to clear partial $dirName during rollback")
                    }
                    if (metadata.existingDirs.contains(dirName)) {
                        val backupDir = File(rollbackFilesDir, dirName)
                        if (backupDir.exists() && !backupDir.copyRecursively(liveDir, overwrite = true)) {
                            throw IOException("Failed to restore $dirName during rollback")
                        }
                    }
                }

                val roomSnapshotFile = File(rollbackDir, ROOM_SNAPSHOT_FILE)
                if (roomSnapshotFile.exists()) {
                    val roomSnapshot = snapshotJson.decodeFromString<RoomRestoreSnapshot>(
                        roomSnapshotFile.readText(),
                    )
                    database.withTransaction {
                        val dao = database.aacTileDao()
                        dao.deleteAllClickEvents()
                        dao.deleteAllPlacements()
                        dao.deleteAllTiles()

                        if (roomSnapshot.tiles.isNotEmpty()) {
                            dao.insertTiles(roomSnapshot.tiles)
                        }
                        if (roomSnapshot.placements.isNotEmpty()) {
                            dao.insertPlacements(roomSnapshot.placements)
                        }
                        if (roomSnapshot.clickEvents.isNotEmpty()) {
                            dao.insertClickEvents(roomSnapshot.clickEvents)
                        }
                    }
                }

                val snapshot = metadata.settingsSnapshot
                if (snapshot != null) {
                    settingsRepository.restoreSettingsSnapshot(snapshot)
                } else {
                    metadata.activeProfileId?.let { previousActiveId ->
                        settingsRepository.updateActiveProfileId(previousActiveId)
                    }
                }

                true
            } catch (t: Throwable) {
                Log.e(TAG, "Rollback failed", t)
                false
            }
        }

    private fun applyStagedDirectories(
        payloadDir: File,
    ) {
        FULL_RESTORE_DIRS.forEach { dirName ->
            val liveDir = File(context.filesDir, dirName)
            if (liveDir.exists() && !liveDir.deleteRecursively()) {
                throw IOException("Failed to clear live directory $dirName")
            }

            val stagedDir = File(payloadDir, dirName)
            if (stagedDir.exists()) {
                moveOrCopyDirectory(stagedDir, liveDir)
            }
        }
    }

    private suspend fun clearRoomTablesInTransaction() {
        database.withTransaction {
            val dao = database.aacTileDao()
            dao.deleteAllClickEvents()
            dao.deleteAllPlacements()
            dao.deleteAllTiles()
        }
    }

    private fun moveOrCopyDirectory(source: File, target: File) {
        if (source.renameTo(target)) {
            return
        }
        if (!source.copyRecursively(target, overwrite = true)) {
            throw IOException("Failed to copy ${source.absolutePath} to ${target.absolutePath}")
        }
        source.deleteRecursively()
    }

    private fun cleanupStagingOnly() {
        if (!restoreWorkDir.exists()) return
        val state = readJournalState()
        if (state != JournalState.COMMITTING && state != JournalState.ROLLING_BACK) {
            restoreWorkDir.deleteRecursively()
        } else {
            stagingDir.deleteRecursively()
        }
    }

    private fun writeJournalState(state: JournalState) {
        if (!restoreWorkDir.exists() && !restoreWorkDir.mkdirs()) {
            throw IOException("Failed to create restore work directory")
        }
        writeFileAtomically(journalFile, state.name)
    }

    private fun readJournalState(): JournalState? {
        if (!journalFile.exists()) return null
        return runCatching {
            JournalState.valueOf(journalFile.readText().trim())
        }.getOrNull()
    }

    private fun writeFileAtomically(targetFile: File, content: String) {
        targetFile.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) {
                throw IOException("Failed to create parent directory for ${targetFile.name}")
            }
        }
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        FileOutputStream(tempFile).use { fos ->
            fos.write(content.toByteArray(Charsets.UTF_8))
            fos.flush()
            runCatching { fos.fd.sync() }
        }
        if (targetFile.exists() && !targetFile.delete()) {
            throw IOException("Failed to replace ${targetFile.name}")
        }
        if (!tempFile.renameTo(targetFile)) {
            throw IOException("Failed to rename ${tempFile.name} to ${targetFile.name}")
        }
    }

    private fun normalizeLocalUriString(raw: String): String {
        val withoutScheme = if (raw.startsWith("file://", ignoreCase = true)) {
            runCatching { Uri.parse(raw).path }.getOrNull() ?: raw.removePrefix("file://")
        } else {
            raw
        }
        return withoutScheme.replace("\\", "/")
    }

    private fun isRemoteOrResourceUri(value: String): Boolean {
        return value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("android.resource://", ignoreCase = true)
    }

    private fun hasTraversalSegment(path: String): Boolean {
        return path.split('/').any { segment -> segment == ".." }
    }

    private data class StagedPayload(
        val payloadDir: File,
        val manifest: BackupManifest?,
        val restoredProfileIds: Set<String>,
    )

    private enum class JournalState {
        COMMITTING,
        ROLLING_BACK,
        COMMITTED,
    }

    @Serializable
    private data class RollbackMetadata(
        val existingDirs: List<String>,
        val activeProfileId: String? = null,
        val settingsSnapshot: BoardSettingsSnapshot? = null,
    )

    @Serializable
    private data class RoomRestoreSnapshot(
        val tiles: List<AACTile>,
        val placements: List<TilePlacement>,
        val clickEvents: List<TileClickEvent>,
    )

    companion object {
        const val CURRENT_BACKUP_FORMAT_VERSION = 3
        const val LEGACY_BACKUP_FORMAT_VERSION = 1
        const val CURRENT_STARTER_DATA_VERSION = 3
        const val BACKUP_MANIFEST_FILE = "backup_manifest.json"

        private const val TAG = "BackupService"
        private const val BUFFER_SIZE = 65536
        private const val STARTER_DATA_VERSION_FILE = "starter_data_version.txt"
        private const val RESTORE_WORK_DIR = "restore_work"
        private const val STAGING_DIR = "staging"
        private const val ROLLBACK_DIR = "rollback"
        private const val ROLLBACK_FILES_DIR = "files"
        private const val JOURNAL_FILE = "journal.txt"
        private const val ROLLBACK_METADATA_FILE = "metadata.json"
        private const val ROOM_SNAPSHOT_FILE = "room_snapshot.json"
        private const val IMAGES_DIR = "images"
        private const val AUDIO_TILES_DIR = "audio_tiles"
        private val STARTER_DICT_PATH_REGEX =
            Regex("^tiles/[^/]+/default_dictionary\\.json$")

        private val FULL_RESTORE_DIRS = listOf(
            "profiles",
            "tiles",
            AUDIO_TILES_DIR,
            IMAGES_DIR,
        )

        private val ASSET_RESTORE_DIRS = listOf(
            "tiles",
            AUDIO_TILES_DIR,
            IMAGES_DIR,
        )
    }
}
