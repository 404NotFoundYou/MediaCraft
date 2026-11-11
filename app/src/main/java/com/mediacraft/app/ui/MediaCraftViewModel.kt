package com.mediacraft.app.ui

import android.app.Application
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mediacraft.app.data.MediaRepository
import com.mediacraft.app.data.OperationRepository
import com.mediacraft.app.data.PreparedOperation
import com.mediacraft.app.domain.DuplicateGroup
import com.mediacraft.app.domain.DuplicateKind
import com.mediacraft.app.domain.DuplicateScanner
import com.mediacraft.app.domain.MediaIssue
import com.mediacraft.app.domain.MediaItem
import com.mediacraft.app.domain.MediaKind
import com.mediacraft.app.domain.OperationRecord
import com.mediacraft.app.domain.OperationSpec
import com.mediacraft.app.domain.OperationType
import com.mediacraft.app.domain.RenameMode
import com.mediacraft.app.domain.RenameOptions
import com.mediacraft.app.domain.RenamePlan
import com.mediacraft.app.domain.RenamePlanner
import com.mediacraft.app.domain.ScanProgress
import com.mediacraft.app.domain.ScanScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MainPage { DEDUPLICATE, RENAME, HISTORY }
enum class PermissionCoverage { FULL, PARTIAL, DENIED }

data class GroupReview(
    val keeperId: String? = null,
    val contentConfirmed: Boolean = false,
)

data class MediaCraftUiState(
    val page: MainPage = MainPage.DEDUPLICATE,
    val permissionCoverage: PermissionCoverage = PermissionCoverage.DENIED,
    val scanScope: ScanScope = ScanScope(),
    val albums: List<String> = emptyList(),
    val media: List<MediaItem> = emptyList(),
    val groups: List<DuplicateGroup> = emptyList(),
    val reviews: Map<String, GroupReview> = emptyMap(),
    val scanProgress: ScanProgress? = null,
    val scanning: Boolean = false,
    val issues: List<MediaIssue> = emptyList(),
    val selectedRenameIds: Set<String> = emptySet(),
    val renameOptions: RenameOptions = RenameOptions(),
    val renamePlan: RenamePlan? = null,
    val renamePlanLocked: Boolean = false,
    val busyMessage: String? = null,
    val operationPending: Boolean = false,
    val awaitingSystemAuthorization: Boolean = false,
    val message: String? = null,
    val records: List<OperationRecord> = emptyList(),
)

sealed interface UiEvent {
    data class RequestAuthorization(val operation: PreparedOperation) : UiEvent
}

class MediaCraftViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val scanner: DuplicateScanner,
    private val renamePlanner: RenamePlanner,
    private val mediaRepository: MediaRepository,
    private val operationRepository: OperationRepository,
) : AndroidViewModel(application) {
    private val restoredAuthorization = restorePendingAuthorization(savedStateHandle)
    private val mutableState = MutableStateFlow(
        MediaCraftUiState(
            operationPending = restoredAuthorization != null,
            awaitingSystemAuthorization = restoredAuthorization != null,
            message = restoredAuthorization?.let { "系统确认仍在等待结果；若确认界面已消失，请取消等待并核对实际状态。" },
        ),
    )
    private val mutableRecords = MutableStateFlow<List<OperationRecord>>(emptyList())
    val state: StateFlow<MediaCraftUiState> = combine(mutableState, mutableRecords) { state, records ->
        state.copy(records = records)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MediaCraftUiState())

    private val eventChannel = Channel<UiEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private var scanJob: Job? = null
    private var awaitingAuthorization: PreparedOperation? = restoredAuthorization
    private var mediaRefreshToken: Long = 0

    init {
        refreshPermissionCoverage()
        viewModelScope.launch {
            operationRepository.records.collect { mutableRecords.value = it }
        }
        viewModelScope.launch {
            try {
                operationRepository.reconcilePending(excludeId = awaitingAuthorization?.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                showError("恢复未完成操作失败", error)
            }
        }
    }

    fun selectPage(page: MainPage) = update { it.copy(page = page, message = null) }

    fun refreshPermissionCoverage() {
        val context = getApplication<Application>()
        val scope = mutableState.value.scanScope
        val coverage = when {
            Build.VERSION.SDK_INT >= 34 -> {
                val images = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                val videos = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                val selected = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
                val requestedGranted = (!scope.photos || images) && (!scope.videos || videos)
                when {
                    requestedGranted && (scope.photos || scope.videos) -> PermissionCoverage.FULL
                    images || videos || selected -> PermissionCoverage.PARTIAL
                    else -> PermissionCoverage.DENIED
                }
            }
            Build.VERSION.SDK_INT >= 33 -> {
                val images = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                val videos = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                val requestedGranted = (!scope.photos || images) && (!scope.videos || videos)
                when {
                    requestedGranted && (scope.photos || scope.videos) -> PermissionCoverage.FULL
                    images || videos -> PermissionCoverage.PARTIAL
                    else -> PermissionCoverage.DENIED
                }
            }
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED -> PermissionCoverage.FULL
            else -> PermissionCoverage.DENIED
        }
        update { state ->
            if (state.permissionCoverage == coverage) state.copy(permissionCoverage = coverage)
            else state.copy(
                permissionCoverage = coverage,
                groups = emptyList(),
                reviews = emptyMap(),
                renamePlan = null,
                renamePlanLocked = false,
                message = if (state.permissionCoverage != PermissionCoverage.DENIED) "媒体授权范围已变化，请重新扫描或生成预览" else state.message,
            )
        }
        if (coverage != PermissionCoverage.DENIED) refreshMedia()
    }

    fun updateScope(photos: Boolean? = null, videos: Boolean? = null, album: String? = mutableState.value.scanScope.album) {
        if (mutableState.value.operationPending) return
        scanJob?.cancel()
        val current = mutableState.value.scanScope
        val next = current.copy(photos = photos ?: current.photos, videos = videos ?: current.videos, album = album)
        update { state ->
            val visibleIds = state.media.filter { media ->
                (media.kind == MediaKind.PHOTO && next.photos || media.kind == MediaKind.VIDEO && next.videos) &&
                    (next.album == null || media.relativePath == next.album)
            }.mapTo(mutableSetOf()) { it.id }
            state.copy(
                scanScope = next,
                groups = emptyList(),
                reviews = emptyMap(),
                scanProgress = null,
                selectedRenameIds = state.selectedRenameIds.intersect(visibleIds),
                renamePlan = null,
                renamePlanLocked = false,
                message = null,
            )
        }
        refreshPermissionCoverage()
    }

    fun refreshMedia() {
        if (mutableState.value.permissionCoverage == PermissionCoverage.DENIED) return
        val requestedScope = mutableState.value.scanScope
        val listingScope = requestedScope.copy(album = null)
        val token = ++mediaRefreshToken
        viewModelScope.launch {
            try {
                val listing = mediaRepository.list(listingScope)
                if (token != mediaRefreshToken) return@launch
                val currentScope = mutableState.value.scanScope
                if (currentScope.photos != requestedScope.photos || currentScope.videos != requestedScope.videos) return@launch
                val albums = listing.items.map { it.relativePath }.filter { it.isNotBlank() }.distinct().sorted()
                update { current ->
                    val previousIdentity = current.media.associate { it.id to Triple(it.generation, it.dateModifiedSeconds, it.size) }
                    val newIdentity = listing.items.associate { it.id to Triple(it.generation, it.dateModifiedSeconds, it.size) }
                    val mediaChanged = current.media.isNotEmpty() && previousIdentity != newIdentity
                    current.copy(
                        media = listing.items,
                        albums = albums,
                        issues = listing.issues,
                        selectedRenameIds = current.selectedRenameIds.intersect(listing.items.mapTo(mutableSetOf()) { it.id }),
                        groups = if (mediaChanged) emptyList() else current.groups,
                        reviews = if (mediaChanged) emptyMap() else current.reviews,
                        renamePlan = if (mediaChanged) null else current.renamePlan,
                        renamePlanLocked = if (mediaChanged) false else current.renamePlanLocked,
                        message = if (mediaChanged && current.message == null) "媒体范围或内容已变化，请重新扫描或生成预览" else current.message,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (token == mediaRefreshToken) showError("读取媒体失败", error)
            }
        }
    }

    fun startScan() {
        if (mutableState.value.operationPending) {
            update { it.copy(message = "已有操作正在等待系统处理") }
            return
        }
        if (mutableState.value.permissionCoverage == PermissionCoverage.DENIED) {
            update { it.copy(message = "请先授权访问照片和视频") }
            return
        }
        scanJob?.cancel()
        val scopeSnapshot = mutableState.value.scanScope
        scanJob = viewModelScope.launch {
            update { it.copy(scanning = true, groups = emptyList(), reviews = emptyMap(), scanProgress = ScanProgress("准备扫描", 0, 0), issues = emptyList(), message = null) }
            try {
                val result = scanner.scan(scopeSnapshot) { progress ->
                    update { state -> state.copy(scanProgress = progress) }
                }
                if (mutableState.value.scanScope != scopeSnapshot) return@launch
                val reviews = result.groups.associate { group ->
                    group.id to GroupReview(keeperId = group.recommendedId, contentConfirmed = false)
                }
                update {
                    it.copy(
                        groups = result.groups,
                        reviews = reviews,
                        issues = result.issues,
                        scanning = false,
                        scanProgress = null,
                        message = if (result.groups.isEmpty()) "当前授权范围内未发现重复候选" else null,
                    )
                }
            } catch (cancelled: CancellationException) {
                update { it.copy(scanning = false, scanProgress = null, message = "扫描已取消") }
                throw cancelled
            } catch (error: Throwable) {
                update { it.copy(scanning = false, scanProgress = null) }
                showError("扫描失败", error)
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
    }

    fun chooseKeeper(groupId: String, keeperId: String?) {
        if (mutableState.value.operationPending) return
        update { state ->
            state.copy(reviews = state.reviews + (groupId to GroupReview(keeperId = keeperId, contentConfirmed = false)))
        }
    }

    fun confirmContent(groupId: String, confirmed: Boolean) {
        if (mutableState.value.operationPending) return
        update { state ->
            val review = state.reviews[groupId] ?: GroupReview()
            state.copy(reviews = state.reviews + (groupId to review.copy(contentConfirmed = confirmed && review.keeperId != null)))
        }
    }

    fun selectedTrashGroups(): List<Pair<DuplicateGroup, GroupReview>> = mutableState.value.groups.mapNotNull { group ->
        val review = mutableState.value.reviews[group.id] ?: return@mapNotNull null
        if (review.keeperId == null || !review.contentConfirmed) null else group to review
    }

    fun prepareTrash() {
        val selected = selectedTrashGroups()
        val specs = selected.flatMap { (group, review) ->
            val keeper = group.items.firstOrNull { it.id == review.keeperId } ?: return@flatMap emptyList()
            group.items.filterNot { it.id == keeper.id }.map { media ->
                OperationSpec(media = media, keeper = keeper, duplicateKind = group.kind)
            }
        }
        prepareOperation(OperationType.TRASH, specs)
    }

    fun toggleRenameSelection(id: String) {
        if (mutableState.value.operationPending) return
        update { state ->
            val selected = state.selectedRenameIds.toMutableSet()
            if (!selected.add(id)) selected.remove(id)
            state.copy(selectedRenameIds = selected, renamePlan = null, renamePlanLocked = false, message = null)
        }
    }

    fun updateRenameOptions(mode: RenameMode? = null, template: String? = null, start: Int? = null) {
        if (mutableState.value.operationPending) return
        update { state ->
            val options = state.renameOptions.copy(
                mode = mode ?: state.renameOptions.mode,
                template = template ?: state.renameOptions.template,
                start = start ?: state.renameOptions.start,
            )
            state.copy(renameOptions = options, renamePlan = null, renamePlanLocked = false, message = null)
        }
    }

    fun createRenamePreview() {
        if (mutableState.value.operationPending) {
            update { it.copy(message = "已有操作正在处理，请等待完成") }
            return
        }
        val snapshot = mutableState.value
        val selected = snapshot.media.filter { it.id in snapshot.selectedRenameIds }
        val plan = runCatching { renamePlanner.create(selected, snapshot.media, snapshot.renameOptions) }
            .getOrElse {
                showError("生成重命名预览失败", it)
                return
            }
        update { it.copy(renamePlan = plan, renamePlanLocked = true) }
    }

    fun prepareRename() {
        val state = mutableState.value
        val plan = state.renamePlan
        if (!state.renamePlanLocked || plan == null || !plan.canExecute) {
            update { it.copy(message = "请先生成并确认有效预览") }
            return
        }
        val specs = plan.items.filterNot { it.unchanged }.map { OperationSpec(media = it.media, targetName = it.newName) }
        prepareOperation(OperationType.RENAME, specs)
    }

    fun prepareUndo(record: OperationRecord) {
        if (mutableState.value.operationPending) {
            update { it.copy(message = "已有操作正在处理，请等待完成") }
            return
        }
        viewModelScope.launch {
            update { it.copy(busyMessage = "正在检查可恢复项目", operationPending = true, message = null) }
            try {
                val specs = operationRepository.undoSpecs(record)
                val type = if (record.type == OperationType.TRASH) OperationType.RESTORE else OperationType.UNDO_RENAME
                prepareOperationNow(type, specs, record.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                update { it.copy(operationPending = false) }
                showError("准备撤销失败", error)
            } finally {
                update { it.copy(busyMessage = null) }
            }
        }
    }

    private fun prepareOperation(type: OperationType, specs: List<OperationSpec>, sourceId: String? = null) {
        if (mutableState.value.operationPending) {
            update { it.copy(message = "已有操作正在处理，请等待完成") }
            return
        }
        if (specs.isEmpty()) {
            update { it.copy(message = "没有可处理的项目") }
            return
        }
        if (specs.size > 200) {
            update { it.copy(message = "一次最多处理 200 项；当前 ${specs.size} 项，请分批选择。不会自动截断。") }
            return
        }
        viewModelScope.launch {
            update { it.copy(busyMessage = "正在核对 ${specs.size} 个项目", operationPending = true, message = null) }
            try {
                prepareOperationNow(type, specs, sourceId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                update { it.copy(operationPending = false) }
                showError("准备操作失败", error)
            } finally {
                update { it.copy(busyMessage = null) }
            }
        }
    }

    private suspend fun prepareOperationNow(type: OperationType, specs: List<OperationSpec>, sourceId: String?) {
        if (specs.size > 200) {
            update { it.copy(message = "一次最多处理 200 项；当前 ${specs.size} 项，请分批选择。不会自动截断。") }
            return
        }
        val prepared = operationRepository.prepare(type, specs, sourceId)
        if (prepared.uris.isEmpty()) {
            completePrepared(prepared, approved = true)
            return
        }
        awaitingAuthorization = prepared
        savePendingAuthorization(prepared)
        update { it.copy(operationPending = true, awaitingSystemAuthorization = true, message = "正在等待 Android 系统确认") }
        eventChannel.send(UiEvent.RequestAuthorization(prepared))
    }

    fun onAuthorizationResult(approved: Boolean) {
        val pending = awaitingAuthorization ?: run {
            update { it.copy(message = "授权结果已过期，请重新提交") }
            return
        }
        awaitingAuthorization = null
        clearPendingAuthorization()
        update { it.copy(awaitingSystemAuthorization = false) }
        viewModelScope.launch { completePrepared(pending, approved) }
    }

    fun authorizationIntent(operation: PreparedOperation): IntentSender = operationRepository.authorization(operation)

    fun onAuthorizationLaunchFailed(error: Throwable) {
        val pending = awaitingAuthorization ?: run {
            update { it.copy(operationPending = false, awaitingSystemAuthorization = false, message = "无法打开系统确认：${error.message ?: error::class.java.simpleName}") }
            clearPendingAuthorization()
            return
        }
        awaitingAuthorization = null
        clearPendingAuthorization()
        update { it.copy(awaitingSystemAuthorization = false) }
        viewModelScope.launch {
            completePrepared(pending, approved = false, messagePrefix = "无法打开系统确认：${error.message ?: error::class.java.simpleName}。已核对计划状态")
        }
    }

    fun cancelPendingAuthorization() {
        val pending = awaitingAuthorization ?: run {
            clearPendingAuthorization()
            update { it.copy(operationPending = false, awaitingSystemAuthorization = false, message = "没有等待中的系统确认") }
            return
        }
        awaitingAuthorization = null
        clearPendingAuthorization()
        update { it.copy(awaitingSystemAuthorization = false) }
        viewModelScope.launch {
            completePrepared(pending, approved = false, messagePrefix = "已取消等待并核对实际状态")
        }
    }

    private suspend fun completePrepared(pending: PreparedOperation, approved: Boolean, messagePrefix: String? = null) {
        update { it.copy(busyMessage = "正在核对系统处理结果", operationPending = true, message = null) }
        try {
            val record = operationRepository.complete(pending.id, approved)
            val success = record.items.count { it.status.name == "SUCCESS" }
            val failed = record.items.count { it.status.name == "FAILED" }
            val skipped = record.items.count { it.status.name == "SKIPPED" }
            val cancelled = record.items.count { it.status.name == "CANCELLED" }
            update {
                it.copy(
                    busyMessage = null,
                    operationPending = false,
                    awaitingSystemAuthorization = false,
                    message = listOfNotNull(messagePrefix, "处理完成：成功 $success，失败 $failed，跳过 $skipped，取消 $cancelled").joinToString("；"),
                    renamePlan = null,
                    renamePlanLocked = false,
                )
            }
            refreshMedia()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            update { it.copy(busyMessage = null, operationPending = false, awaitingSystemAuthorization = false) }
            showError("核对操作结果失败", error)
        }
    }

    private fun savePendingAuthorization(operation: PreparedOperation) {
        savedStateHandle[PENDING_ID_KEY] = operation.id
        savedStateHandle[PENDING_TYPE_KEY] = operation.type.name
        savedStateHandle[PENDING_URIS_KEY] = ArrayList(operation.uris)
    }

    private fun clearPendingAuthorization() {
        savedStateHandle.remove<String>(PENDING_ID_KEY)
        savedStateHandle.remove<String>(PENDING_TYPE_KEY)
        savedStateHandle.remove<ArrayList<String>>(PENDING_URIS_KEY)
    }

    private fun showError(prefix: String, error: Throwable) {
        val detail = error.message?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName
        update { it.copy(message = "$prefix：$detail") }
    }

    private inline fun update(transform: (MediaCraftUiState) -> MediaCraftUiState) {
        mutableState.update(transform)
    }

    internal companion object {
        const val PENDING_ID_KEY = "pending_authorization_id"
        const val PENDING_TYPE_KEY = "pending_authorization_type"
        const val PENDING_URIS_KEY = "pending_authorization_uris"

        fun restorePendingAuthorization(handle: SavedStateHandle): PreparedOperation? {
            val id = handle.get<String>(PENDING_ID_KEY)?.takeIf { it.isNotBlank() } ?: return null
            val type = handle.get<String>(PENDING_TYPE_KEY)?.let { runCatching { OperationType.valueOf(it) }.getOrNull() } ?: return null
            val uris = handle.get<ArrayList<String>>(PENDING_URIS_KEY)?.toList() ?: return null
            return PreparedOperation(id, type, uris)
        }
    }
}
