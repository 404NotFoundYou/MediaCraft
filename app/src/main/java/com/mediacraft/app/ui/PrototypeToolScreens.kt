package com.mediacraft.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mediacraft.app.domain.OperationRecord
import com.mediacraft.app.domain.OperationStatus
import com.mediacraft.app.domain.OperationType
import com.mediacraft.app.domain.RenameMode
import java.text.DateFormat
import java.util.Date

private val PanelLine = Color(0xFFE1E9E3)
private val Warning = Color(0xFF945C12)
private val PanelShape = RoundedCornerShape(20.dp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PrototypeRenameScreen(state: MediaCraftUiState, viewModel: MediaCraftViewModel) {
    var prefix by rememberSaveable { mutableStateOf("旅行") }
    var includeDate by rememberSaveable { mutableStateOf(true) }
    var digits by rememberSaveable { mutableStateOf(3) }
    fun applyVisualTemplate() {
        val template = buildString {
            append(prefix.ifBlank { "Media" })
            if (includeDate) append("_{date}")
            append("_{seq:$digits}")
        }
        viewModel.updateRenameOptions(template = template)
    }
    val visibleMedia = state.media.filter { state.scanScope.album == null || it.relativePath == state.scanScope.album }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("rename-list"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeading("给照片和视频，一个好找的名字", "批量重命名", "选择真实媒体，预览锁定后再授权执行。") }
        if (state.permissionCoverage == PermissionCoverage.DENIED) item { MessageCard("需要媒体读取权限才能选择文件") }
        if (state.scanScope.folderUri != null) item { MessageCard("指定文件夹扫描当前仅支持去重预览，重命名请切换回全局相册授权") }

        item {
            ToolPanel {
                Text("选择文件 · ${state.selectedRenameIds.size} 项", fontWeight = FontWeight.SemiBold, color = Ink)
                if (state.media.size > 200) Text("单次最多执行 200 项；列表不会自动截断。", style = MaterialTheme.typography.bodySmall, color = Warning)
            }
        }
        items(visibleMedia, key = { "select-${it.id}" }) { media ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, PanelLine),
            ) {
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !state.operationPending && state.scanScope.folderUri == null) {
                        viewModel.toggleRenameSelection(media.id)
                    }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = media.id in state.selectedRenameIds,
                        onCheckedChange = { viewModel.toggleRenameSelection(media.id) },
                        enabled = !state.operationPending && state.scanScope.folderUri == null,
                        modifier = Modifier.semantics { contentDescription = "选择 ${media.displayName}" },
                    )
                    MediaThumbnail(media, Modifier.size(52.dp).clip(RoundedCornerShape(9.dp)))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(media.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Ink)
                        Text(media.relativePath, style = MaterialTheme.typography.labelSmall, color = Muted)
                    }
                }
            }
        }

        item {
            ToolPanel {
                Text("选择命名方式", fontWeight = FontWeight.SemiBold, color = Ink)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(state.renameOptions.mode == RenameMode.CUSTOM, { viewModel.updateRenameOptions(mode = RenameMode.CUSTOM) }, { Text("自定义命名") }, enabled = !state.operationPending)
                    FilterChip(state.renameOptions.mode == RenameMode.RANDOM, { viewModel.updateRenameOptions(mode = RenameMode.RANDOM) }, { Text("随机命名") }, enabled = !state.operationPending)
                }
                if (state.renameOptions.mode == RenameMode.CUSTOM) {
                    OutlinedTextField(prefix, { prefix = it; applyVisualTemplate() }, label = { Text("前缀") }, modifier = Modifier.fillMaxWidth(), enabled = !state.operationPending)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(includeDate, { includeDate = it; applyVisualTemplate() }, enabled = !state.operationPending)
                        Text("加入拍摄日期（缺失时使用添加日期）")
                    }
                    Text("序号位数", style = MaterialTheme.typography.labelMedium, color = Muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..6).forEach { value ->
                            FilterChip(digits == value, { digits = value; applyVisualTemplate() }, { Text(value.toString()) }, enabled = !state.operationPending)
                        }
                    }
                    OutlinedTextField(
                        state.renameOptions.template,
                        { viewModel.updateRenameOptions(template = it) },
                        label = { Text("模板") },
                        supportingText = { Text("支持 {date}、{seq:1}–{seq:6}、{original}") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.operationPending,
                    )
                } else {
                    Text("将生成 MC_ + 10 位随机字符，预览后名称锁定。", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                Button(
                    viewModel::createRenamePreview,
                    Modifier.fillMaxWidth(),
                    enabled = !state.operationPending && state.scanScope.folderUri == null && state.selectedRenameIds.isNotEmpty(),
                ) { Text(if (state.renamePlanLocked) "重新生成预览" else "生成名称预览") }
            }
        }

        state.renamePlan?.let { plan ->
            item {
                ToolPanel {
                    Text("名称预览${if (state.renamePlanLocked) " · 已锁定" else ""}", fontWeight = FontWeight.SemiBold, color = Ink)
                    plan.error?.let { MessageCard(it) }
                    plan.items.forEach { item ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Text(item.media.displayName, style = MaterialTheme.typography.bodySmall, color = Muted)
                            Text("↳ ${item.newName}", color = if (item.error == null) Green else MaterialTheme.colorScheme.error)
                            item.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            item.warnings.forEach { Text(it, color = Warning, style = MaterialTheme.typography.bodySmall) }
                            if (item.unchanged) Text("无需更改", style = MaterialTheme.typography.labelSmall, color = Muted)
                            HorizontalDivider(Modifier.padding(top = 8.dp), color = PanelLine)
                        }
                    }
                    Button(
                        viewModel::prepareRename,
                        Modifier.fillMaxWidth(),
                        enabled = !state.operationPending && plan.canExecute && state.renamePlanLocked,
                    ) { Text("确认并请求系统授权") }
                    Text("不改变扩展名、画质、拍摄时间和所在相册。", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }
        state.message?.let { item { MessageCard(it) } }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
internal fun PrototypeHistoryScreen(state: MediaCraftUiState, viewModel: MediaCraftViewModel) {
    val records = state.records
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("history-list"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeading("每一次整理，都有迹可循", "操作记录", "查看逐项结果，或尝试恢复。") }
        if (records.isEmpty()) item { ToolPanel { Text("还没有操作记录", color = Muted) } }
        items(records, key = { it.id }) { record -> PrototypeOperationRecordCard(record, viewModel, enabled = !state.operationPending) }
        item {
            Text("恢复取决于媒体仍存在、身份可核对、权限允许且原名称未被占用。", style = MaterialTheme.typography.bodySmall, color = Muted)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PrototypeOperationRecordCard(record: OperationRecord, viewModel: MediaCraftViewModel, enabled: Boolean) {
    var expanded by rememberSaveable(record.id) { mutableStateOf(false) }
    val success = record.items.count { it.status == OperationStatus.SUCCESS }
    val failed = record.items.count { it.status == OperationStatus.FAILED }
    val cancelled = record.items.count { it.status == OperationStatus.CANCELLED }
    ToolPanel {
        Text(operationLabel(record.type), fontWeight = FontWeight.SemiBold, color = Ink)
        Text(DateFormat.getDateTimeInstance().format(Date(record.createdAtMillis)), style = MaterialTheme.typography.labelSmall, color = Muted)
        Text("成功 $success · 失败 $failed · 取消 $cancelled")
        if (!expanded) {
            record.items.filter { it.status != OperationStatus.SUCCESS }.take(5).forEach {
                Text("${it.spec.media.displayName}：${it.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        TextButton({ expanded = !expanded }) { Text(if (expanded) "收起逐项结果" else "展开逐项结果（${record.items.size}）") }
        if (expanded) record.items.forEach { item ->
            HorizontalDivider(color = PanelLine)
            Text(item.spec.media.displayName + (item.spec.targetName?.let { " → $it" } ?: ""), style = MaterialTheme.typography.bodySmall)
            Text(
                "${statusLabel(item.status)}：${item.message}",
                style = MaterialTheme.typography.bodySmall,
                color = if (item.status == OperationStatus.SUCCESS) Green else MaterialTheme.colorScheme.error,
            )
            item.spec.keeper?.let { Text("保留：${it.displayName}", style = MaterialTheme.typography.labelSmall, color = Muted) }
        }
        val skipped = record.items.count { it.status == OperationStatus.SKIPPED }
        if (skipped > 0) Text("跳过 $skipped", color = Warning)
        if (success > 0 && record.type in setOf(OperationType.TRASH, OperationType.RENAME)) {
            OutlinedButton({ viewModel.prepareUndo(record) }, Modifier.fillMaxWidth(), enabled = enabled) {
                Text(if (record.type == OperationType.TRASH) "尝试恢复文件" else "尝试恢复原名")
            }
        }
    }
}

@Composable
internal fun PrototypeSettingsScreen(
    state: MediaCraftUiState,
    viewModel: MediaCraftViewModel,
    onRequestPermissions: (Boolean, Boolean) -> Unit,
    onPickFolder: () -> Unit,
    onClearFolder: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ScreenHeading("管理访问范围", "设置", "先授权媒体访问，再选择全局相册或指定文件夹进行去重。")
            PermissionCard(state.permissionCoverage, folderSelected = state.scanScope.folderUri != null) {
                onRequestPermissions(state.scanScope.photos, state.scanScope.videos)
            }
        }
        item {
            ToolPanel {
                Text("扫描范围", fontWeight = FontWeight.SemiBold, color = Ink)
                Text("媒体类型", style = MaterialTheme.typography.labelMedium, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(state.scanScope.photos, { viewModel.updateScope(photos = !state.scanScope.photos) }, { Text("照片") }, enabled = !state.operationPending)
                    FilterChip(state.scanScope.videos, { viewModel.updateScope(videos = !state.scanScope.videos) }, { Text("视频") }, enabled = !state.operationPending)
                }
                Text("扫描来源", style = MaterialTheme.typography.labelMedium, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onPickFolder, enabled = !state.operationPending) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("选择文件夹")
                    }
                    if (state.scanScope.folderUri != null) TextButton(onClick = onClearFolder, enabled = !state.operationPending) { Text("恢复全局") }
                }
                Text(
                    when {
                        state.scanScope.folderUri != null -> "当前：已选文件夹（包含子文件夹）"
                        state.scanScope.album != null -> "当前相册：${state.scanScope.album}"
                        else -> "当前：所有已授权媒体文件夹"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                if (state.scanScope.folderUri == null) {
                    AlbumPicker(state.albums, state.scanScope.album, enabled = !state.operationPending) { viewModel.updateScope(album = it) }
                }
            }
        }
        item {
            Card(shape = PanelShape, colors = CardDefaults.cardColors(containerColor = PaleGreen)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("隐私与安全", fontWeight = FontWeight.SemiBold, color = Green)
                    Text(
                        "扫描和摘要计算均在本机完成，不上传照片或视频。应用只读取 Android 授权范围内的媒体。地名查询可能由系统联网发送坐标；加载在线地图会向 OpenStreetMap 发送坐标。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun ToolPanel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = PanelShape,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, PanelLine),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
