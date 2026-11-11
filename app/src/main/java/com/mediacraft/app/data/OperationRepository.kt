package com.mediacraft.app.data

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore
import androidx.room.withTransaction
import com.mediacraft.app.domain.DuplicateKind
import com.mediacraft.app.domain.MediaItem
import com.mediacraft.app.domain.OperationItemResult
import com.mediacraft.app.domain.OperationRecord
import com.mediacraft.app.domain.OperationSpec
import com.mediacraft.app.domain.OperationStatus
import com.mediacraft.app.domain.OperationType
import com.mediacraft.app.domain.normalizedMediaName
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class PreparedOperation(val id: String, val type: OperationType, val uris: List<String>)

class OperationRepository(
    context: Context,
    private val media: MediaRepository,
) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val database = OperationDatabase.get(appContext)
    private val dao = database.operations()

    val records: Flow<List<OperationRecord>> = dao.observeAll().map { stored ->
        stored.map { it.toRecord() }
    }

    suspend fun prepare(
        type: OperationType,
        specs: List<OperationSpec>,
        sourceId: String? = null,
    ): PreparedOperation = withContext(Dispatchers.IO) {
        require(specs.isNotEmpty()) { "没有可执行的媒体项" }
        require(specs.size <= MAX_ITEMS) { "单次最多处理 $MAX_ITEMS 项，请分批执行" }
        val errors = specs.map { it.validationError }.toMutableList()
        markGlobalConflicts(type, specs, errors)
        val pendingIds = specs.mapTo(hashSetOf()) { it.media.id }
        val preparedSpecs = specs.mapIndexed { index, spec ->
            if (errors[index] != null) return@mapIndexed spec.copy(validationError = errors[index])
            runCatching {
                when (type) {
                    OperationType.RENAME, OperationType.UNDO_RENAME -> validateRename(spec)
                    OperationType.TRASH -> validateTrash(spec, pendingIds)
                    OperationType.RESTORE -> validateRestore(spec)
                }
            }.getOrElse { error -> spec.copy(validationError = error.userMessage()) }
        }
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        database.withTransaction {
            dao.insertOperation(OperationEntity(id, type.name, now, sourceId))
            dao.insertItems(preparedSpecs.mapIndexed { index, spec ->
                val error = spec.validationError
                OperationItemEntity(
                    id,
                    index,
                    OperationJson.encode(spec),
                    if (error == null) OperationStatus.PENDING.name else OperationStatus.FAILED.name,
                    error ?: "等待系统授权",
                )
            })
        }
        PreparedOperation(id, type, preparedSpecs.filter { it.validationError == null }.map { it.media.id })
    }

    fun authorization(operation: PreparedOperation): IntentSender {
        require(operation.uris.isNotEmpty()) { "授权列表不能为空" }
        val uris = operation.uris.map(Uri::parse)
        val request: PendingIntent = when (operation.type) {
            OperationType.RENAME, OperationType.UNDO_RENAME -> MediaStore.createWriteRequest(resolver, uris)
            OperationType.TRASH -> MediaStore.createTrashRequest(resolver, uris, true)
            OperationType.RESTORE -> MediaStore.createTrashRequest(resolver, uris, false)
        }
        return request.intentSender
    }

    suspend fun complete(id: String, approved: Boolean): OperationRecord = withContext(Dispatchers.IO) {
        val stored = dao.get(id) ?: error("操作计划不存在：$id")
        if (!approved) {
            stored.items.filter { it.status == OperationStatus.PENDING.name }.forEach { item ->
                val spec = OperationJson.decode(item.specJson)
                val applied = runCatching {
                    checkApplied(OperationType.valueOf(stored.operation.type), spec)
                }.getOrElse { error ->
                    OperationStatus.FAILED to "用户取消授权后的状态核对失败：${error.userMessage()}"
                }
                dao.updateItem(
                    id,
                    item.position,
                    applied?.first?.name ?: OperationStatus.CANCELLED.name,
                    applied?.second ?: "用户取消或拒绝了系统授权，核对后未发现操作生效",
                )
            }
            return@withContext dao.get(id)!!.toRecord()
        }

        val ordered = stored.items.sortedBy(OperationItemEntity::position)
        for ((offset, item) in ordered.withIndex()) {
            if (item.status != OperationStatus.PENDING.name) continue
            try {
                val spec = OperationJson.decode(item.specJson)
                val result = when (OperationType.valueOf(stored.operation.type)) {
                    OperationType.RENAME, OperationType.UNDO_RENAME -> executeRename(spec)
                    OperationType.TRASH -> verifyTrashState(spec, expectedTrashed = true)
                    OperationType.RESTORE -> verifyTrashState(spec, expectedTrashed = false)
                }
                dao.updateItem(id, item.position, result.first.name, result.second)
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) {
                    dao.updateItem(id, item.position, OperationStatus.PENDING.name, "执行被中断，当前项等待重新对账")
                    ordered.drop(offset + 1).filter { it.status == OperationStatus.PENDING.name }.forEach { remaining ->
                        dao.updateItem(id, remaining.position, OperationStatus.PENDING.name, "执行被中断，等待重新对账")
                    }
                }
                throw cancelled
            } catch (error: Exception) {
                dao.updateItem(id, item.position, OperationStatus.FAILED.name, "执行失败：${error.userMessage()}")
            }
        }
        dao.get(id)!!.toRecord()
    }

    suspend fun undoSpecs(record: OperationRecord): List<OperationSpec> = withContext(Dispatchers.IO) {
        record.items.filter { it.status == OperationStatus.SUCCESS }.map { result ->
            val original = result.spec
            when (record.type) {
                OperationType.RENAME, OperationType.UNDO_RENAME -> {
                    val current = media.find(original.media.id)
                        ?: return@map original.copy(validationError = "媒体已不存在，无法恢复原名")
                    when {
                        normalizedMediaName(current.displayName) != normalizedMediaName(original.targetName.orEmpty()) -> original.copy(
                            media = current,
                            validationError = "当前名称与操作记录不一致，无法恢复原名",
                        )
                        original.digest != null && media.digest(current) != original.digest -> original.copy(
                            media = current,
                            validationError = "媒体内容已变化，无法恢复原名",
                        )
                        media.existingNames(current)
                            .asSequence()
                            .filter { normalizedMediaName(it) != normalizedMediaName(current.displayName) }
                            .any { normalizedMediaName(it) == normalizedMediaName(original.media.displayName) } -> original.copy(
                            media = current,
                            validationError = "原名称已被占用：${original.media.displayName}",
                        )
                        else -> OperationSpec(current, targetName = original.media.displayName, digest = original.digest)
                    }
                }
                OperationType.TRASH -> {
                    val current = media.find(original.media.id, includeTrashed = true)
                    when {
                        media.isTrashed(original.media.id) != true -> original.copy(validationError = "媒体已不在回收站，无法恢复")
                        current == null -> original.copy(validationError = "回收站媒体已不存在，无法恢复")
                        original.digest != null && media.digest(current) != original.digest -> original.copy(
                            media = current,
                            validationError = "回收站媒体内容已变化，无法恢复",
                        )
                        else -> OperationSpec(current, digest = original.digest)
                    }
                }
                OperationType.RESTORE -> {
                    original.copy(validationError = "恢复记录不能直接反向回收；请重新扫描并确认保留项")
                }
            }
        }
    }

    suspend fun reconcilePending(excludeId: String? = null) = withContext(Dispatchers.IO) {
        dao.pending().filterNot { it.operation.id == excludeId }.forEach { operation ->
            val type = OperationType.valueOf(operation.operation.type)
            operation.items.filter { it.status == OperationStatus.PENDING.name }.forEach { item ->
                val spec = runCatching { OperationJson.decode(item.specJson) }.getOrElse { error ->
                    dao.updateItem(operation.operation.id, item.position, OperationStatus.FAILED.name, "计划损坏：${error.userMessage()}")
                    return@forEach
                }
                val reconciled = runCatching {
                    when (type) {
                        OperationType.RENAME, OperationType.UNDO_RENAME -> reconcileRename(spec)
                        OperationType.TRASH -> reconcileTrash(spec, true)
                        OperationType.RESTORE -> reconcileTrash(spec, false)
                    }
                }.getOrElse { OperationStatus.FAILED to "对账失败：${it.userMessage()}" }
                dao.updateItem(operation.operation.id, item.position, reconciled.first.name, reconciled.second)
            }
        }
    }

    private fun markGlobalConflicts(
        type: OperationType,
        specs: List<OperationSpec>,
        errors: MutableList<String?>,
    ) {
        fun mark(index: Int, message: String) {
            if (errors[index] == null) errors[index] = message
        }

        specs.withIndex().groupBy { it.value.media.id }.values.filter { it.size > 1 }.forEach { duplicates ->
            duplicates.forEach { mark(it.index, "同一媒体不能在计划中重复出现") }
        }

        if (type == OperationType.RENAME || type == OperationType.UNDO_RENAME) {
            specs.withIndex()
                .filter { it.value.targetName != null }
                .groupBy { it.value.media.directoryKey to normalizedMediaName(it.value.targetName.orEmpty()) }
                .values
                .filter { it.size > 1 }
                .forEach { conflicts ->
                    val name = conflicts.first().value.targetName.orEmpty()
                    conflicts.forEach { mark(it.index, "同一目录存在重复目标名称：$name") }
                }

            specs.forEachIndexed { index, spec ->
                val target = spec.targetName ?: return@forEachIndexed
                specs.forEachIndexed { ownerIndex, owner ->
                    if (index != ownerIndex && spec.media.directoryKey == owner.media.directoryKey &&
                        normalizedMediaName(target) == normalizedMediaName(owner.media.displayName)
                    ) {
                        mark(index, "首版不支持交换、循环或占用批内现有名称：$target")
                        mark(ownerIndex, "当前名称被同批其他项作为目标：$target")
                    }
                }
            }
        }

        if (type == OperationType.TRASH) {
            val positions = specs.withIndex().associateBy { it.value.media.id }
            specs.forEachIndexed { index, spec ->
                val keeperId = spec.keeper?.id ?: return@forEachIndexed
                positions[keeperId]?.let { keeper ->
                    mark(index, "保留项也在本批待回收列表中：${keeper.value.media.displayName}")
                    mark(keeper.index, "该项被同批其他组设为保留项，不能回收")
                }
            }
        }
    }

    private suspend fun checkApplied(
        type: OperationType,
        spec: OperationSpec,
    ): Pair<OperationStatus, String>? {
        return when (type) {
            OperationType.RENAME, OperationType.UNDO_RENAME -> {
                if (normalizedMediaName(spec.targetName.orEmpty()) == normalizedMediaName(spec.media.displayName)) {
                    null
                } else {
                    val current = media.find(spec.media.id)
                    if (current == null ||
                        normalizedMediaName(current.displayName) != normalizedMediaName(spec.targetName.orEmpty())
                    ) {
                        null
                    } else if (spec.digest != null && media.digest(current) != spec.digest) {
                        OperationStatus.FAILED to "系统授权被取消，但名称已变化且内容摘要不一致"
                    } else {
                        OperationStatus.SUCCESS to "系统回调为取消，但核对确认重命名已经生效"
                    }
                }
            }
            OperationType.TRASH -> {
                if (media.isTrashed(spec.media.id) != true) null else verifyTrashState(spec, true)
            }
            OperationType.RESTORE -> {
                if (media.isTrashed(spec.media.id) != false) null else verifyTrashState(spec, false)
            }
        }
    }

    private suspend fun validateRename(spec: OperationSpec): OperationSpec {
        val target = requireNotNull(spec.targetName) { "重命名缺少目标名称" }
        validateName(target)
        val current = requireCurrent(spec.media, includeTrashed = false)
        require(normalizedMediaName(target.substringAfterLast('.', "")) == normalizedMediaName(current.displayName.substringAfterLast('.', ""))) {
            "不能修改扩展名：${current.displayName}"
        }
        if (normalizedMediaName(target) != normalizedMediaName(current.displayName)) {
            require(media.existingNames(current).none { normalizedMediaName(it) == normalizedMediaName(target) }) {
                "目标名称已存在：$target"
            }
        }
        return spec.copy(media = current, digest = spec.digest ?: media.digest(current))
    }

    private suspend fun validateTrash(spec: OperationSpec, pendingIds: Set<String>): OperationSpec {
        val current = requireCurrent(spec.media, includeTrashed = false)
        val keeperSnapshot = requireNotNull(spec.keeper) { "待回收项缺少保留文件" }
        require(keeperSnapshot.id != current.id) { "保留项不能与待回收项相同" }
        require(keeperSnapshot.id !in pendingIds) { "保留项也在本批待回收列表中：${keeperSnapshot.displayName}" }
        val keeper = requireCurrent(keeperSnapshot, includeTrashed = false)
        require(media.isTrashed(keeper.id) == false) { "保留文件已进入回收站" }
        val digest = spec.digest ?: media.digest(current)
        if (spec.duplicateKind == DuplicateKind.EXACT) {
            val keeperDigest = media.digest(keeper)
            require(digest == keeperDigest && media.byteEquals(current, keeper)) { "完全重复内容复核失败" }
        }
        return spec.copy(media = current, keeper = keeper, digest = digest)
    }

    private suspend fun validateRestore(spec: OperationSpec): OperationSpec {
        require(media.isTrashed(spec.media.id) == true) { "媒体当前不在回收站：${spec.media.displayName}" }
        val current = media.find(spec.media.id, includeTrashed = true)
            ?: throw IllegalArgumentException("回收站媒体不存在：${spec.media.displayName}")
        requireSameIdentity(spec.media, current)
        if (spec.digest != null) require(media.digest(current) == spec.digest) { "回收站媒体内容已发生变化" }
        return spec.copy(media = current)
    }

    private suspend fun executeRename(spec: OperationSpec): Pair<OperationStatus, String> {
        val target = requireNotNull(spec.targetName)
        val before = media.find(spec.media.id) ?: return OperationStatus.FAILED to "媒体已不存在"
        if (!sameIdentity(spec.media, before)) return OperationStatus.SKIPPED to "媒体在授权后发生变化"
        if (normalizedMediaName(before.displayName) == normalizedMediaName(target)) {
            val unchanged = spec.digest == null || media.digest(before) == spec.digest
            return if (unchanged) OperationStatus.SUCCESS to "名称已经更新并核对内容一致"
            else OperationStatus.FAILED to "名称已更新，但内容摘要不一致"
        }
        if (media.existingNames(before).asSequence()
                .filter { normalizedMediaName(it) != normalizedMediaName(before.displayName) }
                .any { normalizedMediaName(it) == normalizedMediaName(target) }
        ) {
            return OperationStatus.SKIPPED to "目标名称已被占用"
        }
        val beforeDigest = media.digest(before)
        if (spec.digest != null && beforeDigest != spec.digest) return OperationStatus.SKIPPED to "内容在授权后发生变化"
        val updated = resolver.update(
            Uri.parse(before.id),
            ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, target) },
            null,
            null,
        )
        if (updated != 1) return OperationStatus.FAILED to "系统未更新该媒体名称"
        val after = media.find(before.id) ?: return OperationStatus.FAILED to "更新后无法重新读取媒体"
        if (normalizedMediaName(after.displayName) != normalizedMediaName(target)) {
            return OperationStatus.FAILED to "系统返回成功，但名称核对失败"
        }
        if (after.size != before.size || media.digest(after) != beforeDigest) {
            return OperationStatus.FAILED to "重命名后内容摘要或大小发生变化"
        }
        return OperationStatus.SUCCESS to "已重命名并核对内容一致"
    }

    private suspend fun verifyTrashState(spec: OperationSpec, expectedTrashed: Boolean): Pair<OperationStatus, String> {
        val actual = media.isTrashed(spec.media.id)
            ?: return OperationStatus.FAILED to "系统确认后无法找到媒体"
        if (actual != expectedTrashed) {
            return OperationStatus.FAILED to if (expectedTrashed) "媒体未进入系统回收站" else "媒体仍在系统回收站"
        }
        val current = media.find(spec.media.id, includeTrashed = true)
            ?: return OperationStatus.FAILED to "状态变化后无法读取媒体"
        if (!sameContentIdentity(spec.media, current)) return OperationStatus.FAILED to "媒体身份或内容标识发生变化"
        if (spec.digest != null && media.digest(current) != spec.digest) {
            return OperationStatus.FAILED to "媒体内容摘要发生变化"
        }
        return OperationStatus.SUCCESS to if (expectedTrashed) "已核对进入系统回收站" else "已核对移出系统回收站"
    }

    private suspend fun reconcileRename(spec: OperationSpec): Pair<OperationStatus, String> {
        val current = media.find(spec.media.id) ?: return OperationStatus.FAILED to "中断后媒体不存在"
        return when {
            normalizedMediaName(current.displayName) == normalizedMediaName(spec.targetName.orEmpty()) &&
                (spec.digest == null || media.digest(current) == spec.digest) ->
                OperationStatus.SUCCESS to "中断后对账：名称和内容均已生效"
            normalizedMediaName(current.displayName) == normalizedMediaName(spec.media.displayName) ->
                OperationStatus.FAILED to "中断后对账：操作未执行，不会自动重放"
            else -> OperationStatus.FAILED to "中断后对账：名称处于其他状态，不会自动重放"
        }
    }

    private suspend fun reconcileTrash(spec: OperationSpec, expected: Boolean): Pair<OperationStatus, String> {
        val actual = media.isTrashed(spec.media.id)
        if (actual != expected) return OperationStatus.FAILED to "中断后状态与计划不一致，不会自动重放"
        val current = media.find(spec.media.id, includeTrashed = true)
            ?: return OperationStatus.FAILED to "中断后媒体不存在"
        if (!sameContentIdentity(spec.media, current)) return OperationStatus.FAILED to "中断后媒体身份已变化"
        if (spec.digest != null && media.digest(current) != spec.digest) {
            return OperationStatus.FAILED to "中断后媒体摘要已变化"
        }
        return OperationStatus.SUCCESS to if (expected) "中断后对账：已进入回收站" else "中断后对账：已恢复"
    }

    private suspend fun requireCurrent(snapshot: MediaItem, includeTrashed: Boolean): MediaItem {
        val current = media.find(snapshot.id, includeTrashed)
            ?: throw IllegalArgumentException("媒体不存在或不可访问：${snapshot.displayName}")
        requireSameIdentity(snapshot, current)
        return current
    }

    private fun requireSameIdentity(expected: MediaItem, actual: MediaItem) {
        require(sameIdentity(expected, actual)) { "媒体已发生变化：${expected.displayName}" }
    }

    private fun sameIdentity(expected: MediaItem, actual: MediaItem): Boolean =
        expected.id == actual.id &&
            expected.size == actual.size &&
            expected.dateModifiedSeconds == actual.dateModifiedSeconds &&
            (expected.generation == 0L || actual.generation == 0L || expected.generation == actual.generation)

    private fun sameContentIdentity(expected: MediaItem, actual: MediaItem): Boolean =
        expected.id == actual.id && expected.size == actual.size

    private fun validateName(name: String) {
        require(name.isNotBlank()) { "目标名称不能为空" }
        require(name != "." && name != "..") { "目标名称无效" }
        require(name.length <= 255) { "目标名称过长" }
        require(name.none { it == '/' || it == '\\' || it.code < 32 }) { "目标名称包含路径分隔符或控制字符" }
    }

    private fun StoredOperation.toRecord(): OperationRecord = OperationRecord(
        id = operation.id,
        type = OperationType.valueOf(operation.type),
        createdAtMillis = operation.createdAtMillis,
        items = items.sortedBy(OperationItemEntity::position).map { item ->
            OperationItemResult(
                OperationJson.decode(item.specJson),
                OperationStatus.valueOf(item.status),
                item.message,
            )
        },
        sourceId = operation.sourceId,
    )

    private fun Throwable.userMessage(): String = message?.takeIf(String::isNotBlank) ?: javaClass.simpleName

    companion object {
        private const val MAX_ITEMS = 200
    }
}
