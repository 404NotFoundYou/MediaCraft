package com.mediacraft.app.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mediacraft.app.BuildConfig
import com.mediacraft.app.update.AppUpdate
import com.mediacraft.app.update.InstalledVersion
import com.mediacraft.app.update.UpdateCheckResult
import com.mediacraft.app.update.UpdateClient
import com.mediacraft.app.update.UpdateDownloadProgress
import com.mediacraft.app.update.UpdateJson
import com.mediacraft.app.update.VerifiedApk
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class UpdatePhase { IDLE, CHECKING, CURRENT, NO_RELEASE, AVAILABLE, DOWNLOADING, VERIFYING, READY, ERROR }

data class UpdateUiState(
    val dialogVisible: Boolean = false,
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val current: InstalledVersion = InstalledVersion(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong()),
    val update: AppUpdate? = null,
    val progress: UpdateDownloadProgress? = null,
    val verifiedPath: String? = null,
    val message: String? = null,
)

sealed interface UpdateUiEvent {
    data object RequestUnknownSources : UpdateUiEvent
    data class LaunchInstaller(val apk: VerifiedApk) : UpdateUiEvent
}

class UpdateViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val client: UpdateClient,
) : AndroidViewModel(application) {
    private val encodedRestoredUpdate = savedStateHandle.get<String>(UPDATE_JSON_KEY)
    private val restoredUpdateResult = encodedRestoredUpdate?.let { encoded -> runCatching { UpdateJson.decodeUpdate(encoded) } }
    private val restoredUpdate = restoredUpdateResult?.getOrNull()
    private val restoredIsStale = restoredUpdate?.versionCode?.let { it <= BuildConfig.VERSION_CODE.toLong() } == true
    private val savedVerifiedPath = savedStateHandle.get<String>(VERIFIED_PATH_KEY)
    private val restoredPath = savedVerifiedPath?.takeIf { File(it).isFile }
    private val interruptedDownload = savedStateHandle.get<Boolean>(DOWNLOADING_KEY) == true
    private val restoredPhase = savedStateHandle.get<String>(LAST_PHASE_KEY)?.let { runCatching { UpdatePhase.valueOf(it) }.getOrNull() }
    private val restoredMessage = savedStateHandle.get<String>(LAST_MESSAGE_KEY)
    private val restoreError = when {
        restoredUpdateResult?.isFailure == true -> "缓存的更新信息损坏，请重新检查更新。"
        savedVerifiedPath != null && restoredPath == null -> "已下载的 APK 不存在，请重新下载。"
        else -> null
    }

    private val mutableState = MutableStateFlow(
        UpdateUiState(
            dialogVisible = savedStateHandle.get<Boolean>(DIALOG_VISIBLE_KEY) == true,
            phase = when {
                restoredIsStale -> UpdatePhase.CURRENT
                interruptedDownload || restoreError != null -> UpdatePhase.ERROR
                restoredUpdate != null && restoredPath != null -> UpdatePhase.READY
                restoredPhase in setOf(UpdatePhase.CURRENT, UpdatePhase.NO_RELEASE, UpdatePhase.AVAILABLE, UpdatePhase.ERROR) -> requireNotNull(restoredPhase)
                restoredUpdate != null -> UpdatePhase.AVAILABLE
                else -> UpdatePhase.IDLE
            },
            update = restoredUpdate,
            verifiedPath = if (restoredIsStale) null else restoredPath,
            message = when {
                restoredIsStale -> "当前安装版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) 已不低于缓存版本，无需安装。"
                interruptedDownload -> "下载因应用重建而中断，请重新下载。"
                restoreError != null -> restoreError
                restoredUpdate != null && restoredPath != null -> "已恢复下载记录，安装前将重新校验 APK。"
                else -> restoredMessage
            },
        ),
    )
    val state: StateFlow<UpdateUiState> = mutableState.asStateFlow()

    private val eventChannel = Channel<UpdateUiEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private var workJob: Job? = null

    init {
        if (restoredIsStale) {
            savedStateHandle.remove<String>(UPDATE_JSON_KEY)
            savedStateHandle.remove<String>(VERIFIED_PATH_KEY)
            savedStateHandle[DOWNLOADING_KEY] = false
            rememberStatus(UpdatePhase.CURRENT, "当前安装版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) 已不低于缓存版本，无需安装。")
            mutableState.update { it.copy(update = null) }
        }
    }

    fun openAndCheck() {
        savedStateHandle[DIALOG_VISIBLE_KEY] = true
        mutableState.update { it.copy(dialogVisible = true) }
        checkForUpdates()
    }

    fun openDialog() { savedStateHandle[DIALOG_VISIBLE_KEY] = true; mutableState.update { it.copy(dialogVisible = true) } }
    fun closeDialog() { savedStateHandle[DIALOG_VISIBLE_KEY] = false; mutableState.update { it.copy(dialogVisible = false) } }

    fun checkForUpdates() {
        if (workJob?.isActive == true) return
        workJob = viewModelScope.launch {
            clearRememberedStatus()
            mutableState.update { it.copy(phase = UpdatePhase.CHECKING, progress = null, message = null) }
            try {
                when (val result = client.check(mutableState.value.current, Build.VERSION.SDK_INT)) {
                    is UpdateCheckResult.Current -> mutableState.update {
                        clearSavedUpdate()
                        val message = currentVersionMessage(result.latest)
                        rememberStatus(UpdatePhase.CURRENT, message)
                        it.copy(phase = UpdatePhase.CURRENT, update = null, verifiedPath = null, message = message)
                    }
                    is UpdateCheckResult.Available -> {
                        saveUpdate(result.update)
                        clearVerifiedPath()
                        rememberStatus(UpdatePhase.AVAILABLE, null)
                        mutableState.update { it.copy(phase = UpdatePhase.AVAILABLE, update = result.update, verifiedPath = null, message = null) }
                    }
                    is UpdateCheckResult.NoRelease -> {
                        clearSavedUpdate()
                        rememberStatus(UpdatePhase.NO_RELEASE, result.message)
                        mutableState.update { it.copy(phase = UpdatePhase.NO_RELEASE, update = null, verifiedPath = null, message = result.message) }
                    }
                    is UpdateCheckResult.Unsupported -> {
                        clearSavedUpdate()
                        rememberStatus(UpdatePhase.ERROR, result.message)
                        mutableState.update { it.copy(phase = UpdatePhase.ERROR, update = null, verifiedPath = null, message = result.message) }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val message = "检查更新失败：${error.describe()}"
                rememberStatus(UpdatePhase.ERROR, message)
                mutableState.update { it.copy(phase = UpdatePhase.ERROR, message = message) }
            }
        }
    }

    fun download() {
        if (workJob?.isActive == true) return
        val update = mutableState.value.update ?: run {
            mutableState.update { it.copy(phase = UpdatePhase.ERROR, message = "没有可下载的更新，请重新检查。") }
            return
        }
        workJob = viewModelScope.launch {
            savedStateHandle[DOWNLOADING_KEY] = true
            mutableState.update { it.copy(phase = UpdatePhase.DOWNLOADING, progress = UpdateDownloadProgress(0, update.asset.size), message = null) }
            try {
                val verified = client.download(update) { progress ->
                    mutableState.update { state -> state.copy(progress = progress) }
                }
                saveVerified(verified)
                mutableState.update {
                    it.copy(phase = UpdatePhase.READY, update = verified.update, progress = null, verifiedPath = verified.file.absolutePath, message = "下载和校验完成。安装前会再次校验。")
                }
            } catch (cancelled: CancellationException) {
                rememberStatus(UpdatePhase.AVAILABLE, "下载已取消。")
                mutableState.update { it.copy(phase = UpdatePhase.AVAILABLE, progress = null, message = "下载已取消。") }
                throw cancelled
            } catch (error: Throwable) {
                clearVerifiedPath()
                val message = "下载或校验失败：${error.describe()}"
                rememberStatus(UpdatePhase.ERROR, message)
                mutableState.update { it.copy(phase = UpdatePhase.ERROR, progress = null, message = message) }
            } finally {
                savedStateHandle[DOWNLOADING_KEY] = false
            }
        }
    }

    fun cancelDownload() {
        if (mutableState.value.phase == UpdatePhase.DOWNLOADING) workJob?.cancel()
    }

    fun requestInstall() {
        if (workJob?.isActive == true) return
        verifyThenContinue(fromSettings = false)
    }

    fun onUnknownSourcesReturned() {
        if (mutableState.value.verifiedPath == null) return
        verifyThenContinue(fromSettings = true)
    }

    fun onSettingsLaunchFailed(error: Throwable) {
        val message = "无法打开未知来源安装设置：${error.describe()}"
        rememberStatus(UpdatePhase.ERROR, message)
        mutableState.update { it.copy(phase = UpdatePhase.ERROR, message = message) }
    }

    fun onInstallerLaunchFailed(error: Throwable) {
        val message = "无法打开系统安装器：${error.describe()}"
        rememberStatus(UpdatePhase.ERROR, message)
        mutableState.update { it.copy(phase = UpdatePhase.ERROR, message = message) }
    }

    private fun verifyThenContinue(fromSettings: Boolean) {
        val update = mutableState.value.update
        val path = mutableState.value.verifiedPath
        if (update == null || path == null) {
            mutableState.update { it.copy(phase = UpdatePhase.ERROR, message = "已下载的更新记录不完整，请重新下载。") }
            return
        }
        workJob = viewModelScope.launch {
            mutableState.update { it.copy(phase = UpdatePhase.VERIFYING, message = "正在重新校验安装包") }
            try {
                val verified = client.verify(File(path), update)
                saveVerified(verified)
                val canInstall = getApplication<Application>().packageManager.canRequestPackageInstalls()
                if (canInstall) {
                    mutableState.update { it.copy(phase = UpdatePhase.READY, message = "校验通过，正在打开系统安装器") }
                    eventChannel.send(UpdateUiEvent.LaunchInstaller(verified))
                } else if (fromSettings) {
                    rememberStatus(UpdatePhase.READY, "尚未允许此来源安装应用。可再次点击安装。")
                    mutableState.update { it.copy(phase = UpdatePhase.READY, message = "尚未允许此来源安装应用。可再次点击安装。") }
                } else {
                    rememberStatus(UpdatePhase.READY, "需要先允许 MediaCraft 安装此来源的应用。")
                    mutableState.update { it.copy(phase = UpdatePhase.READY, message = "需要先允许 MediaCraft 安装此来源的应用。") }
                    eventChannel.send(UpdateUiEvent.RequestUnknownSources)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                clearVerifiedPath()
                val message = "安装前校验失败：${error.describe()}。请重新下载。"
                rememberStatus(UpdatePhase.ERROR, message)
                mutableState.update { it.copy(phase = UpdatePhase.ERROR, verifiedPath = null, message = message) }
            }
        }
    }

    private fun saveUpdate(update: AppUpdate) {
        savedStateHandle[UPDATE_JSON_KEY] = UpdateJson.encodeUpdate(update)
    }

    private fun saveVerified(verified: VerifiedApk) {
        saveUpdate(verified.update)
        savedStateHandle[VERIFIED_PATH_KEY] = verified.file.absolutePath
        savedStateHandle[DOWNLOADING_KEY] = false
        rememberStatus(UpdatePhase.READY, "下载和校验完成。安装前会再次校验。")
    }

    private fun clearVerifiedPath() {
        savedStateHandle.remove<String>(VERIFIED_PATH_KEY)
        savedStateHandle[DOWNLOADING_KEY] = false
    }

    private fun clearSavedUpdate() {
        savedStateHandle.remove<String>(UPDATE_JSON_KEY)
        clearVerifiedPath()
    }

    private fun rememberStatus(phase: UpdatePhase, message: String?) {
        savedStateHandle[LAST_PHASE_KEY] = phase.name
        if (message == null) savedStateHandle.remove<String>(LAST_MESSAGE_KEY) else savedStateHandle[LAST_MESSAGE_KEY] = message
    }

    private fun clearRememberedStatus() {
        savedStateHandle.remove<String>(LAST_PHASE_KEY)
        savedStateHandle.remove<String>(LAST_MESSAGE_KEY)
    }

    private fun Throwable.describe(): String = message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName

    private fun currentVersionMessage(latest: InstalledVersion): String =
        if (latest.code < mutableState.value.current.code) {
            "当前安装版本 ${mutableState.value.current.name} (${mutableState.value.current.code}) 高于 GitHub 正式发布 ${latest.name} (${latest.code})"
        } else {
            "当前已是最新版本 ${mutableState.value.current.name} (${mutableState.value.current.code})"
        }

    internal companion object {
        const val UPDATE_JSON_KEY = "update_json"
        const val VERIFIED_PATH_KEY = "update_verified_path"
        const val DOWNLOADING_KEY = "update_downloading"
        const val DIALOG_VISIBLE_KEY = "update_dialog_visible"
        const val LAST_PHASE_KEY = "update_last_phase"
        const val LAST_MESSAGE_KEY = "update_last_message"
    }
}
