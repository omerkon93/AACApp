package com.kon.myaacapp

import android.content.Context
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed class DownloadStatus {
    object Idle : DownloadStatus()
    data class Downloading(val progress: Int) : DownloadStatus()
    object Installing : DownloadStatus()
    object Success : DownloadStatus()
    data class Error(val message: String) : DownloadStatus()
}

class LanguageDownloadHelper(context: Context) {

    private val splitInstallManager: SplitInstallManager = SplitInstallManagerFactory.create(context)
    
    private val _downloadStatus = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val downloadStatus: StateFlow<DownloadStatus> = _downloadStatus.asStateFlow()

    private val onCompleteCallbacks = mutableMapOf<Int, (Boolean) -> Unit>()

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
                _downloadStatus.value = DownloadStatus.Idle // Reset to Idle on success
                onCompleteCallbacks.remove(sessionId)?.invoke(true)
            }
            SplitInstallSessionStatus.FAILED -> {
                _downloadStatus.value = DownloadStatus.Error("Installation failed: ${state.errorCode()}")
                onCompleteCallbacks.remove(sessionId)?.invoke(false)
            }
            SplitInstallSessionStatus.CANCELED -> {
                _downloadStatus.value = DownloadStatus.Idle
                onCompleteCallbacks.remove(sessionId)?.invoke(false)
            }
            SplitInstallSessionStatus.PENDING -> {
                // Just wait
            }
            else -> {
                // Handle other statuses if necessary
            }
        }
    }

    init {
        splitInstallManager.registerListener(listener)
    }

    fun unregister() {
        splitInstallManager.unregisterListener(listener)
    }

    fun isLanguageInstalled(languageCode: String): Boolean {
        val normalizedCode = LocaleHelper.forSplitInstall(languageCode)
        return splitInstallManager.installedLanguages.contains(normalizedCode) || 
               splitInstallManager.installedLanguages.contains(languageCode)
    }

    fun downloadLanguage(languageCode: String, onComplete: (Boolean) -> Unit) {
        if (isLanguageInstalled(languageCode)) {
            onComplete(true)
            return
        }

        val normalizedCode = LocaleHelper.forSplitInstall(languageCode)
        val request = SplitInstallRequest.newBuilder()
            .addLanguage(Locale.forLanguageTag(normalizedCode))
            .build()

        splitInstallManager.startInstall(request)
            .addOnSuccessListener { sessionId ->
                onCompleteCallbacks[sessionId] = onComplete
            }
            .addOnFailureListener { exception ->
                _downloadStatus.value = DownloadStatus.Error(exception.message ?: "Unknown error")
                onComplete(false)
            }
    }
}
