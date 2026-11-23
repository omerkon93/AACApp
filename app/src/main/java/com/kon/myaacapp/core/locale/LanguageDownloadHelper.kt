package com.kon.myaacapp.core.locale

import android.content.Context
import android.content.res.Configuration
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import com.kon.myaacapp.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

sealed class DownloadStatus {
    object Idle : DownloadStatus()
    data class Downloading(val progress: Int) : DownloadStatus()
    object Installing : DownloadStatus()
    object Success : DownloadStatus()
    data class Error(val message: String) : DownloadStatus()
}

class LanguageDownloadHelper(
    private val context: Context,
    private val installTimeoutMs: Long = DEFAULT_INSTALL_TIMEOUT_MS,
    private val localResourceChecker: (Context, String) -> Boolean = ::defaultHasBundledResources,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {

    private val splitInstallManager: SplitInstallManager =
        SplitInstallManagerFactory.create(context)

    private val _downloadStatus = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val downloadStatus: StateFlow<DownloadStatus> = _downloadStatus.asStateFlow()

    private val onCompleteCallbacks = ConcurrentHashMap<Int, PendingDownload>()
    private val activeTimeouts = ConcurrentHashMap<Int, Job>()

    private data class PendingDownload(
        val completed: AtomicBoolean,
        val timeoutJob: Job?,
        val callback: (Boolean) -> Unit,
    ) {
        fun finish(success: Boolean) {
            if (completed.compareAndSet(false, true)) {
                timeoutJob?.cancel()
                callback(success)
            }
        }
    }

    private val listener = SplitInstallStateUpdatedListener { state ->
        val sessionId = state.sessionId()
        when (state.status()) {
            SplitInstallSessionStatus.DOWNLOADING -> {
                val totalBytes = state.totalBytesToDownload()
                val progress = if (totalBytes > 0) {
                    ((100 * state.bytesDownloaded()) / totalBytes).toInt()
                } else 0
                _downloadStatus.value = DownloadStatus.Downloading(progress)
            }

            SplitInstallSessionStatus.INSTALLING -> {
                _downloadStatus.value = DownloadStatus.Installing
            }

            SplitInstallSessionStatus.INSTALLED -> {
                _downloadStatus.value = DownloadStatus.Success
                activeTimeouts.remove(sessionId)?.cancel()
                onCompleteCallbacks.remove(sessionId)?.finish(true)
            }

            SplitInstallSessionStatus.FAILED -> {
                _downloadStatus.value =
                    DownloadStatus.Error("Installation failed: ${state.errorCode()}")
                activeTimeouts.remove(sessionId)?.cancel()
                onCompleteCallbacks.remove(sessionId)?.finish(false)
            }

            SplitInstallSessionStatus.CANCELED -> {
                _downloadStatus.value = DownloadStatus.Error("Language download was cancelled")
                activeTimeouts.remove(sessionId)?.cancel()
                onCompleteCallbacks.remove(sessionId)?.finish(false)
            }

            SplitInstallSessionStatus.PENDING -> {
                // Handled by listener and timeout guard
            }

            else -> {
                // Ignore intermediate or unknown statuses; timeout guard protects against hangs
            }
        }
    }

    init {
        splitInstallManager.registerListener(listener)
    }

    fun unregister() {
        splitInstallManager.unregisterListener(listener)
        activeTimeouts.values.forEach { it.cancel() }
        activeTimeouts.clear()
        onCompleteCallbacks.clear()
        scope.cancel()
    }

    fun clearError() {
        if (_downloadStatus.value is DownloadStatus.Error) {
            _downloadStatus.value = DownloadStatus.Idle
        }
    }

    fun isLanguageInstalled(languageCode: String): Boolean {
        val normalizedCode = LocaleHelper.forSplitInstall(languageCode)
        val installedLanguages = runCatching {
            splitInstallManager.installedLanguages
        }.getOrDefault(emptySet())

        return installedLanguages.contains(normalizedCode) ||
            installedLanguages.contains(languageCode) ||
            localResourceChecker(context, languageCode)
    }

    fun downloadLanguage(
        languageCode: String,
        onComplete: (Boolean) -> Unit,
    ) {
        val normalizedCode = LocaleHelper.forSplitInstall(languageCode)

        if (isLanguageInstalled(normalizedCode)) {
            _downloadStatus.value = DownloadStatus.Success
            onComplete(true)
            return
        }

        _downloadStatus.value = DownloadStatus.Downloading(0)

        val completedFlag = AtomicBoolean(false)
        val trackedSessionId = AtomicInteger(NO_SESSION_ID)

        val timeoutJob = if (installTimeoutMs > 0L) {
            scope.launch {
                delay(installTimeoutMs.milliseconds)
                if (completedFlag.compareAndSet(false, true)) {
                    val sessionId = trackedSessionId.get()
                    if (sessionId != NO_SESSION_ID) {
                        onCompleteCallbacks.remove(sessionId)
                        activeTimeouts.remove(sessionId)
                        runCatching { splitInstallManager.cancelInstall(sessionId) }
                    }
                    _downloadStatus.value = DownloadStatus.Error(TIMEOUT_ERROR_MESSAGE)
                    onComplete(false)
                }
            }
        } else {
            null
        }

        val pending = PendingDownload(
            completed = completedFlag,
            timeoutJob = timeoutJob,
            callback = onComplete,
        )

        val request = SplitInstallRequest.newBuilder()
            .addLanguage(Locale.forLanguageTag(normalizedCode))
            .build()

        splitInstallManager.startInstall(request)
            .addOnSuccessListener { sessionId ->
                if (completedFlag.get()) {
                    runCatching { splitInstallManager.cancelInstall(sessionId) }
                    return@addOnSuccessListener
                }

                trackedSessionId.set(sessionId)
                onCompleteCallbacks[sessionId] = pending
                if (timeoutJob != null) {
                    activeTimeouts[sessionId] = timeoutJob
                }

                /*
                 * The INSTALLED event may arrive before the callback is added.
                 * Check the current session state to close that race.
                 */
                splitInstallManager
                    .getSessionState(sessionId)
                    .addOnSuccessListener { state ->
                        if (state.status() == SplitInstallSessionStatus.INSTALLED) {
                            _downloadStatus.value = DownloadStatus.Success
                            activeTimeouts.remove(sessionId)?.cancel()
                            onCompleteCallbacks.remove(sessionId)?.finish(true)
                        } else if (state.status() == SplitInstallSessionStatus.FAILED) {
                            _downloadStatus.value =
                                DownloadStatus.Error("Installation failed: ${state.errorCode()}")
                            activeTimeouts.remove(sessionId)?.cancel()
                            onCompleteCallbacks.remove(sessionId)?.finish(false)
                        }
                    }
            }
            .addOnFailureListener { exception ->
                timeoutJob?.cancel()
                if (completedFlag.compareAndSet(false, true)) {
                    _downloadStatus.value = DownloadStatus.Error(
                        exception.message ?: UNAVAILABLE_ERROR_MESSAGE
                    )
                    onComplete(false)
                }
            }
    }

    companion object {
        const val DEFAULT_INSTALL_TIMEOUT_MS = 10_000L
        private const val NO_SESSION_ID = -1
        private const val TIMEOUT_ERROR_MESSAGE =
            "Language download timed out while offline or Play Store was unavailable."
        private const val UNAVAILABLE_ERROR_MESSAGE =
            "Required language resources are unavailable."

        fun defaultHasBundledResources(
            context: Context,
            languageCode: String,
        ): Boolean {
            val normalized = LocaleHelper.normalize(languageCode)
            if (normalized != "he" && normalized != "en") {
                return false
            }

            return runCatching {
                val resources = context.resources ?: return@runCatching false
                val targetLocale = Locale.forLanguageTag(normalized)
                val config = Configuration(resources.configuration).apply {
                    setLocale(targetLocale)
                }
                val localizedContext =
                    context.createConfigurationContext(config) ?: return@runCatching false
                val localizedLabel = localizedContext.resources.getString(R.string.language)
                when (normalized) {
                    "en" -> localizedLabel == "Language" ||
                        localizedLabel.none { it in '\u0590'..'\u05FF' }

                    "he" -> localizedLabel == "שפה" ||
                        localizedLabel.any { it in '\u0590'..'\u05FF' }

                    else -> false
                }
            }.getOrDefault(false)
        }
    }
}
