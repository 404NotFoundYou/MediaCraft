package com.mediacraft.app.ui

import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.mediacraft.app.domain.DuplicateGroup
import com.mediacraft.app.domain.DuplicateKind
import com.mediacraft.app.domain.MediaItem
import com.mediacraft.app.domain.MediaKind
import com.mediacraft.app.domain.OperationRecord
import com.mediacraft.app.domain.OperationStatus
import com.mediacraft.app.domain.OperationType
import com.mediacraft.app.domain.RenameMode
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlin.math.max

private val Green = Color(0xFF24694E)
private val PaleGreen = Color(0xFFEAF3ED)
private val Ink = Color(0xFF20352D)
private val Muted = Color(0xFF66796F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaCraftApp(
    viewModel: MediaCraftViewModel,
    updateViewModel: UpdateViewModel,
    onRequestPermissions: (photos: Boolean, videos: Boolean) -> Unit,
    onPickFolder: () -> Unit,
    onClearFolder: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    var comparing by remember { mutableStateOf<DuplicateGroup?>(null) }
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(primary = Green, onPrimary = Color.White, primaryContainer = PaleGreen, onPrimaryContainer = Ink, secondary = Green, secondaryContainer = PaleGreen, onSecondaryContainer = Green, surface = Color(0xFFFAFCF9), onSurface = Ink),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Column { Text("MediaCraft", fontWeight = FontWeight.SemiBold); Text("本机影像，轻松整理", style = MaterialTheme.typography.labelSmall, color = Muted) } },
                    actions = {
                        IconButton(updateViewModel::openAndCheck) { Icon(Icons.Default.SystemUpdate, contentDescription = "检查更新", tint = Green) }
                        Icon(Icons.Default.Shield, contentDescription = null, tint = Green)
                        Text("媒体本机处理", style = MaterialTheme.typography.labelSmall, color = Green, modifier = Modifier.padding(end = 12.dp))
                    },
                )
            },
            bottomBar = {
                BottomAppBar(containerColor = Color.White) {
                    MainPage.entries.forEach { page ->
                        NavigationBarItem(
                            selected = state.page == page,
                            onClick = { viewModel.selectPage(page) },
                            icon = { Icon(when (page) { MainPage.DEDUPLICATE -> Icons.Default.Search; MainPage.RENAME -> Icons.Default.TextFields; MainPage.HISTORY -> Icons.Default.History; MainPage.SETTINGS -> Icons.Default.Settings }, null) },
                            label = { Text(when (page) { MainPage.DEDUPLICATE -> "去重"; MainPage.RENAME -> "重命名"; MainPage.HISTORY -> "记录"; MainPage.SETTINGS -> "设置" }) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).background(Color(0xFFFAFCF9))) {
                when (state.page) {
                    MainPage.DEDUPLICATE -> DeduplicateScreen(state, viewModel, onRequestPermissions, onCompare = { comparing = it })
                    MainPage.RENAME -> RenameScreen(state, viewModel)
                    MainPage.HISTORY -> HistoryScreen(state, viewModel)
                    MainPage.SETTINGS -> SettingsScreen(state, viewModel, onRequestPermissions, onPickFolder, onClearFolder)
                }
                state.busyMessage?.let { BusyOverlay(it) }
            }
        }
        comparing?.let { group ->
            if (group.kind == DuplicateKind.VIDEO_VERSION) VideoComparisonDialog(group, onDismiss = { comparing = null })
            else PhotoComparisonDialog(group, onDismiss = { comparing = null })
        }
        if (state.awaitingSystemAuthorization) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("等待系统确认") },
                text = { Text("若 Android 确认界面仍在显示，请在系统界面完成操作；若界面已消失，可取消等待并核对媒体实际状态。") },
                confirmButton = { OutlinedButton(viewModel::cancelPendingAuthorization) { Text("取消等待并核对") } },
            )
        }
        UpdateDialog(updateState, updateViewModel)
    }
}

@Composable
private fun DeduplicateScreen(
    state: MediaCraftUiState,
    viewModel: MediaCraftViewModel,
    onRequestPermissions: (Boolean, Boolean) -> Unit,
    onCompare: (DuplicateGroup) -> Unit,
) {
    var category by rememberSaveable { mutableStateOf<DuplicateKind?>(null) }
    var confirmVisible by remember { mutableStateOf(false) }
    val selected = viewModel.selectedTrashGroups()
    val visibleGroups = state.groups.filter { category == null || it.kind == category }
    val canMutateMedia = !state.operationPending && state.scanScope.folderUri == null
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("dedup-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ScreenHeading("整理，从少一份重复开始", "查找重复影像", "完全重复会精确核对；同内容版本需要你复核。")
            PermissionCard(state.permissionCoverage, folderSelected = state.scanScope.folderUri != null) { onRequestPermissions(state.scanScope.photos, state.scanScope.videos) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(state.scanScope.photos, { viewModel.updateScope(photos = !state.scanScope.photos) }, { Text("照片") }, enabled = !state.operationPending)
                FilterChip(state.scanScope.videos, { viewModel.updateScope(videos = !state.scanScope.videos) }, { Text("视频") }, enabled = !state.operationPending)
            }
            AlbumPicker(state.albums, state.scanScope.album, enabled = !state.operationPending) { viewModel.updateScope(album = it) }
            if (state.scanning) {
                val progress = state.scanProgress
                if (progress != null && progress.total > 0) LinearProgressIndicator(progress = { progress.processed.toFloat() / max(progress.total, 1) }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(progress?.let { "${it.phase} · ${it.processed}/${it.total} · 跳过 ${it.skipped}" } ?: "正在扫描", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(viewModel::cancelScan, Modifier.fillMaxWidth()) { Text("取消扫描") }
            } else {
                Button(viewModel::startScan, Modifier.fillMaxWidth(), enabled = !state.operationPending && state.permissionCoverage != PermissionCoverage.DENIED && (state.scanScope.photos || state.scanScope.videos)) {
                    Icon(Icons.Default.Search, null); Spacer(Modifier.size(8.dp)); Text(if (state.groups.isEmpty()) "开始扫描" else "重新扫描")
                }
            }
            state.message?.let { MessageCard(it) }
            state.issues.takeIf { it.isNotEmpty() }?.let { issues ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5E8))) { Column(Modifier.padding(12.dp)) { Text("有 ${issues.size} 项未能处理", fontWeight = FontWeight.Medium); issues.take(5).forEach { Text("• ${it.message}", style = MaterialTheme.typography.bodySmall) }; if (issues.size > 5) Text("另有 ${issues.size - 5} 项", style = MaterialTheme.typography.bodySmall) } }
            }
        }
        if (state.groups.isNotEmpty()) {
            item {
                Text("扫描结果", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(null to "全部", DuplicateKind.EXACT to "完全重复", DuplicateKind.PHOTO_VERSION to "照片版本", DuplicateKind.VIDEO_VERSION to "视频版本").forEach { (kind, label) ->
                        FilterChip(category == kind, { category = kind }, { Text(label) })
                    }
                }
            }
            items(visibleGroups, key = { it.id }) { group ->
                DuplicateGroupCard(group, state.reviews[group.id] ?: GroupReview(), viewModel, onCompare, enabled = canMutateMedia)
            }
            item {
                val count = selected.sumOf { (group, _) -> group.items.size - 1 }
                val bytes = selected.sumOf { (group, review) -> group.items.filterNot { it.id == review.keeperId }.sumOf { it.size } }
                Card(colors = CardDefaults.cardColors(containerColor = PaleGreen)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("待移入回收站", style = MaterialTheme.typography.labelMedium); Text("$count 个 · ${formatBytes(bytes)}", fontWeight = FontWeight.SemiBold) }
                        Button({ confirmVisible = true }, enabled = count > 0 && canMutateMedia) { Text("复核并处理") }
                    }
                }
                if (state.scanScope.folderUri != null) {
                    Text("指定文件夹扫描当前支持安全预览和对比；修改或移入回收站请切换回全局相册授权。", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                Text("推荐不会自动删除。移入回收站后不立即宣称释放空间。", style = MaterialTheme.typography.bodySmall, color = Muted)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    if (confirmVisible) TrashConfirmationDialog(selected, onDismiss = { confirmVisible = false }, onConfirm = { confirmVisible = false; viewModel.prepareTrash() })
}

@Composable
private fun DuplicateGroupCard(group: DuplicateGroup, review: GroupReview, viewModel: MediaCraftViewModel, onCompare: (DuplicateGroup) -> Unit, enabled: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(kindLabel(group.kind), fontWeight = FontWeight.SemiBold)
                if (group.kind != DuplicateKind.EXACT) AssistChip({ onCompare(group) }, { Text("对比") }, leadingIcon = { Icon(Icons.Default.Compare, null) })
            }
            Surface(color = if (group.recommendedId == null) Color(0xFFFFF5E8) else PaleGreen, shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    Text(if (group.recommendedId == null) "无法可靠推荐" else "建议保留", color = if (group.recommendedId == null) Color(0xFF945C12) else Green, fontWeight = FontWeight.SemiBold)
                    Text(group.reason, style = MaterialTheme.typography.bodySmall)
                }
            }
            group.items.forEach { media ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MediaThumbnail(media, Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).clickable { if (group.kind != DuplicateKind.EXACT) onCompare(group) })
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        if (media.id == group.recommendedId) Text("建议保留", color = Green, style = MaterialTheme.typography.labelSmall)
                        Text(media.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text(mediaDetails(media), style = MaterialTheme.typography.bodySmall, color = Muted)
                        Text(media.relativePath, style = MaterialTheme.typography.labelSmall, color = Muted)
                    }
                    RadioButton(
                        selected = review.keeperId == media.id,
                        onClick = { viewModel.chooseKeeper(group.id, media.id) },
                        enabled = enabled,
                        modifier = Modifier.semantics { contentDescription = "保留 ${media.displayName}" },
                    )
                }
            }
            Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { viewModel.chooseKeeper(group.id, null) }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(review.keeperId == null, { viewModel.chooseKeeper(group.id, null) }, enabled = enabled, modifier = Modifier.semantics { contentDescription = "${kindLabel(group.kind)} 全部保留" }); Text("全部保留，跳过这一组")
            }
            group.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = Color(0xFF945C12)) }
            Row(Modifier.fillMaxWidth().clickable(enabled = enabled && review.keeperId != null) { viewModel.confirmContent(group.id, !review.contentConfirmed) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(review.contentConfirmed, { viewModel.confirmContent(group.id, it) }, enabled = enabled && review.keeperId != null, modifier = Modifier.semantics { contentDescription = if (group.kind == DuplicateKind.EXACT) "核对 ${group.items.joinToString { it.displayName }} 的保留项" else "确认 ${group.items.joinToString { it.displayName }} 内容一致" })
                Text(if (group.kind == DuplicateKind.EXACT) "已核对保留项和待处理副本" else "已对比并确认内容一致")
            }
            if (review.keeperId != null && !review.contentConfirmed) Text("更换保留项后需重新确认。未确认不会进入处理计划。", style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}

@Composable
private fun RenameScreen(state: MediaCraftUiState, viewModel: MediaCraftViewModel) {
    var prefix by rememberSaveable { mutableStateOf("旅行") }
    var includeDate by rememberSaveable { mutableStateOf(true) }
    var digits by rememberSaveable { mutableStateOf(3) }
    fun applyVisualTemplate() {
        val template = buildString { append(prefix.ifBlank { "Media" }); if (includeDate) append("_{date}"); append("_{seq:$digits}") }
        viewModel.updateRenameOptions(template = template)
    }
    val visibleMedia = state.media.filter { state.scanScope.album == null || it.relativePath == state.scanScope.album }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("rename-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeading("给照片和视频，一个好找的名字", "批量重命名", "选择真实媒体，预览锁定后再授权执行。") }
        if (state.permissionCoverage == PermissionCoverage.DENIED) item { MessageCard("需要媒体读取权限才能选择文件") }
        if (state.scanScope.folderUri != null) item { MessageCard("指定文件夹扫描当前仅支持去重预览，重命名请切换回全局相册授权") }
        item {
            Text("选择文件 · ${state.selectedRenameIds.size} 项", fontWeight = FontWeight.SemiBold)
            if (state.media.size > 200) Text("单次最多执行 200 项；列表不会自动截断。", style = MaterialTheme.typography.bodySmall, color = Color(0xFF945C12))
        }
        items(visibleMedia, key = { "select-${it.id}" }) { media ->
            Row(Modifier.fillMaxWidth().clickable(enabled = !state.operationPending && state.scanScope.folderUri == null) { viewModel.toggleRenameSelection(media.id) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = media.id in state.selectedRenameIds,
                    onCheckedChange = { viewModel.toggleRenameSelection(media.id) },
                    enabled = !state.operationPending && state.scanScope.folderUri == null,
                    modifier = Modifier.semantics { contentDescription = "选择 ${media.displayName}" },
                )
                MediaThumbnail(media, Modifier.size(48.dp).clip(RoundedCornerShape(7.dp)))
                Column(Modifier.weight(1f).padding(start = 9.dp)) { Text(media.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(media.relativePath, style = MaterialTheme.typography.labelSmall, color = Muted) }
            }
        }
        item {
            HorizontalDivider()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(state.renameOptions.mode == RenameMode.CUSTOM, { viewModel.updateRenameOptions(mode = RenameMode.CUSTOM) }, { Text("自定义命名") }, enabled = !state.operationPending)
                FilterChip(state.renameOptions.mode == RenameMode.RANDOM, { viewModel.updateRenameOptions(mode = RenameMode.RANDOM) }, { Text("随机命名") }, enabled = !state.operationPending)
            }
            if (state.renameOptions.mode == RenameMode.CUSTOM) {
                OutlinedTextField(prefix, { prefix = it; applyVisualTemplate() }, label = { Text("前缀") }, modifier = Modifier.fillMaxWidth(), enabled = !state.operationPending)
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(includeDate, { includeDate = it; applyVisualTemplate() }, enabled = !state.operationPending); Text("加入拍摄日期（缺失时使用添加日期）") }
                Text("序号位数")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..6).forEach { value -> FilterChip(digits == value, { digits = value; applyVisualTemplate() }, { Text(value.toString()) }, enabled = !state.operationPending) }
                }
                OutlinedTextField(state.renameOptions.template, { viewModel.updateRenameOptions(template = it) }, label = { Text("模板") }, supportingText = { Text("支持 {date}、{seq:1}–{seq:6}、{original}") }, modifier = Modifier.fillMaxWidth(), enabled = !state.operationPending)
            } else Text("将生成 MC_ + 10 位随机字符，预览后名称锁定。", style = MaterialTheme.typography.bodySmall, color = Muted)
            Button(viewModel::createRenamePreview, Modifier.fillMaxWidth(), enabled = !state.operationPending && state.scanScope.folderUri == null && state.selectedRenameIds.isNotEmpty()) { Text(if (state.renamePlanLocked) "重新生成预览" else "生成名称预览") }
        }
        state.renamePlan?.let { plan ->
            item { Text("名称预览${if (state.renamePlanLocked) " · 已锁定" else ""}", fontWeight = FontWeight.SemiBold); plan.error?.let { MessageCard(it) } }
            items(plan.items, key = { "preview-${it.media.id}" }) { item ->
                Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                    Text(item.media.displayName, style = MaterialTheme.typography.bodySmall, color = Muted)
                    Text("↳ ${item.newName}", color = if (item.error == null) Green else MaterialTheme.colorScheme.error)
                    item.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    item.warnings.forEach { Text(it, color = Color(0xFF945C12), style = MaterialTheme.typography.bodySmall) }
                    if (item.unchanged) Text("无需更改", style = MaterialTheme.typography.labelSmall, color = Muted)
                    HorizontalDivider(Modifier.padding(top = 7.dp))
                }
            }
            item { Button(viewModel::prepareRename, Modifier.fillMaxWidth(), enabled = !state.operationPending && plan.canExecute && state.renamePlanLocked) { Text("确认并请求系统授权") }; Text("不改变扩展名、画质、拍摄时间和所在相册。", style = MaterialTheme.typography.bodySmall, color = Muted); Spacer(Modifier.height(16.dp)) }
        }
        state.message?.let { item { MessageCard(it) } }
    }
}

@Composable
private fun HistoryScreen(state: MediaCraftUiState, viewModel: MediaCraftViewModel) {
    val records = state.records
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("history-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeading("每一次整理，都有迹可循", "操作记录", "查看逐项结果，或尝试恢复。") }
        if (records.isEmpty()) item { Text("还没有操作记录", color = Muted) }
        items(records, key = { it.id }) { record -> OperationRecordCard(record, viewModel, enabled = !state.operationPending) }
        item { Text("恢复取决于媒体仍存在、身份可核对、权限允许且原名称未被占用。", style = MaterialTheme.typography.bodySmall, color = Muted); Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun OperationRecordCard(record: OperationRecord, viewModel: MediaCraftViewModel, enabled: Boolean) {
    var expanded by rememberSaveable(record.id) { mutableStateOf(false) }
    val success = record.items.count { it.status == OperationStatus.SUCCESS }
    val failed = record.items.count { it.status == OperationStatus.FAILED }
    val cancelled = record.items.count { it.status == OperationStatus.CANCELLED }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(operationLabel(record.type), fontWeight = FontWeight.SemiBold)
            Text(DateFormat.getDateTimeInstance().format(Date(record.createdAtMillis)), style = MaterialTheme.typography.labelSmall, color = Muted)
            Text("成功 $success · 失败 $failed · 取消 $cancelled")
            if (!expanded) record.items.filter { it.status != OperationStatus.SUCCESS }.take(5).forEach { Text("${it.spec.media.displayName}：${it.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            TextButton({ expanded = !expanded }) { Text(if (expanded) "收起逐项结果" else "展开逐项结果（${record.items.size}）") }
            if (expanded) record.items.forEach { item ->
                HorizontalDivider()
                Text(item.spec.media.displayName + (item.spec.targetName?.let { " → $it" } ?: ""), style = MaterialTheme.typography.bodySmall)
                Text("${statusLabel(item.status)}：${item.message}", style = MaterialTheme.typography.bodySmall, color = if (item.status == OperationStatus.SUCCESS) Green else MaterialTheme.colorScheme.error)
                item.spec.keeper?.let { Text("保留：${it.displayName}", style = MaterialTheme.typography.labelSmall, color = Muted) }
            }
            val skipped = record.items.count { it.status == OperationStatus.SKIPPED }
            if (skipped > 0) Text("跳过 $skipped", color = Color(0xFF945C12))
            if (success > 0 && record.type in setOf(OperationType.TRASH, OperationType.RENAME)) {
                OutlinedButton({ viewModel.prepareUndo(record) }, Modifier.fillMaxWidth(), enabled = enabled) { Text(if (record.type == OperationType.TRASH) "尝试恢复文件" else "尝试恢复原名") }
            }
        }
    }
}

@Composable
private fun PermissionCard(coverage: PermissionCoverage, folderSelected: Boolean = false, onRequest: () -> Unit) {
    val (title, detail) = when {
        folderSelected && coverage == PermissionCoverage.FULL -> "已获得文件夹访问权限" to "将递归扫描所选文件夹中的照片和视频。"
        coverage == PermissionCoverage.FULL -> "已获得完整媒体权限" to "可扫描已选照片和视频类型。"
        coverage == PermissionCoverage.PARTIAL -> "仅能访问部分媒体" to "结果只覆盖当前授权项目。可重新选择更多媒体。"
        else -> "需要媒体访问权限" to "MediaCraft 只在本机读取你授权的照片和视频。"
    }
    Card(colors = CardDefaults.cardColors(containerColor = if (coverage == PermissionCoverage.FULL) PaleGreen else Color(0xFFFFF5E8))) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Shield, null, tint = Green); Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { Text(title, fontWeight = FontWeight.Medium); Text(detail, style = MaterialTheme.typography.bodySmall) }
            if (coverage != PermissionCoverage.FULL) OutlinedButton(onRequest) { Text(if (coverage == PermissionCoverage.PARTIAL) "重新选择" else "授权") }
        }
    }
}

@Composable
private fun AlbumPicker(albums: List<String>, selected: String?, enabled: Boolean, onSelect: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("相册范围", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected == null, { onSelect(null) }, { Text("全部相册") }, enabled = enabled)
            albums.forEach { album -> FilterChip(selected == album, { onSelect(album) }, { Text(album.ifBlank { "未分类" }, maxLines = 1) }, enabled = enabled) }
        }
    }
}

@Composable
private fun MediaThumbnail(media: MediaItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(media.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var failed by remember(media.id) { mutableStateOf(false) }
    LaunchedEffect(media.id, media.dateModifiedSeconds) {
        try {
            bitmap = withContext(Dispatchers.IO) { context.contentResolver.loadThumbnail(Uri.parse(media.id), android.util.Size(256, 256), null) }
            failed = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            bitmap = null
            failed = true
        }
    }
    Box(modifier.background(PaleGreen), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = media.displayName, modifier = Modifier.fillMaxSize()) }
            ?: Icon(if (media.kind == MediaKind.PHOTO) Icons.Default.Image else Icons.Default.PlayCircle, contentDescription = if (failed) "${media.displayName} 预览读取失败" else null, tint = Green)
    }
}

@Composable
private fun PhotoComparisonDialog(group: DuplicateGroup, onDismiss: () -> Unit) {
    var leftIndex by remember(group.id) { mutableStateOf(0) }
    var rightIndex by remember(group.id) { mutableStateOf(if (group.items.size > 1) 1 else 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onDismiss) { Text("完成对比") } },
        dismissButton = { IconButton(onDismiss) { Icon(Icons.Default.Close, "关闭") } },
        title = { Text("照片放大对比") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ComparisonSelectors(group.items, leftIndex, rightIndex, onLeft = { selected -> leftIndex = selected; if (rightIndex == selected) rightIndex = group.items.indices.first { it != selected } }, onRight = { selected -> rightIndex = selected; if (leftIndex == selected) leftIndex = group.items.indices.first { it != selected } })
                PhotoComparisonPane(group.items[leftIndex])
                PhotoComparisonPane(group.items[rightIndex])
                Text("放大像素不代表真实清晰度。请核对构图、编辑和插值差异。", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        },
    )
}

@Composable
private fun VideoComparisonDialog(group: DuplicateGroup, onDismiss: () -> Unit) {
    var leftIndex by remember(group.id) { mutableStateOf(0) }
    var rightIndex by remember(group.id) { mutableStateOf(if (group.items.size > 1) 1 else 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { OutlinedButton(onDismiss) { Text("关闭并释放播放器") } },
        title = { Text("视频同步对比") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ComparisonSelectors(group.items, leftIndex, rightIndex, onLeft = { selected -> leftIndex = selected; if (rightIndex == selected) rightIndex = group.items.indices.first { it != selected } }, onRight = { selected -> rightIndex = selected; if (leftIndex == selected) leftIndex = group.items.indices.first { it != selected } })
                key(group.items[leftIndex].id, group.items[rightIndex].id) {
                    VideoPairComparison(group.items[leftIndex], group.items[rightIndex])
                }
            }
        },
    )
}

@Composable
private fun ComparisonSelectors(items: List<MediaItem>, leftIndex: Int, rightIndex: Int, onLeft: (Int) -> Unit, onRight: (Int) -> Unit) {
    Text("左侧版本", style = MaterialTheme.typography.labelMedium)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEachIndexed { index, item -> FilterChip(leftIndex == index, { onLeft(index) }, { Text(item.displayName, maxLines = 1) }, enabled = index != rightIndex) }
    }
    Text("右侧版本", style = MaterialTheme.typography.labelMedium)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEachIndexed { index, item -> FilterChip(rightIndex == index, { onRight(index) }, { Text(item.displayName, maxLines = 1) }, enabled = index != leftIndex) }
    }
}

@Composable
private fun PhotoComparisonPane(media: MediaItem) {
    val context = LocalContext.current
    var bitmap by remember(media.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var scale by remember(media.id) { mutableStateOf(1f) }
    var offsetX by remember(media.id) { mutableStateOf(0f) }
    var offsetY by remember(media.id) { mutableStateOf(0f) }
    var loadError by remember(media.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(media.id) {
        try {
            bitmap = withContext(Dispatchers.IO) {
                val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(media.id))
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.setTargetSampleSize(max(1, max(info.size.width, info.size.height) / 2048))
                }
            }
            loadError = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            bitmap = null
            loadError = error.message ?: error::class.java.simpleName
        }
    }
    Text(media.displayName, fontWeight = FontWeight.Medium)
    Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black).pointerInput(media.id) {
        detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 6f); offsetX += pan.x; offsetY += pan.y }
    }, contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), media.displayName, Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)) }
            ?: Text(loadError?.let { "预览读取失败：$it" } ?: "正在加载预览", color = Color.White, modifier = Modifier.padding(12.dp))
    }
    Text(mediaDetails(media), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun VideoPairComparison(left: MediaItem, right: MediaItem) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val pair = listOf(left, right)
    val players = remember(left.id, right.id) { pair.mapIndexed { index, media -> ExoPlayer.Builder(context).build().apply { setMediaItem(PlayerMediaItem.fromUri(Uri.parse(media.id))); volume = if (index == 0) 1f else 0f; prepare() } } }
    var playing by remember(left.id, right.id) { mutableStateOf(false) }
    var position by remember(left.id, right.id) { mutableStateOf(0L) }
    var duration by remember(left.id, right.id) { mutableStateOf(1L) }
    var audibleIndex by remember(left.id, right.id) { mutableStateOf(0) }
    var playerError by remember(left.id, right.id) { mutableStateOf<String?>(null) }
    DisposableEffect(players, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { playerError = "播放器错误：${error.message ?: error.errorCodeName}"; playing = false; players.forEach { it.pause() } }
        }
        players.forEach { it.addListener(listener) }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) { playing = false; players.forEach { it.pause() } }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); players.forEach { it.removeListener(listener); it.release() } }
    }
    pair.forEachIndexed { index, media ->
        Text(media.displayName, fontWeight = FontWeight.Medium)
        AndroidView(factory = { PlayerView(it).apply { player = players[index]; useController = false } }, modifier = Modifier.fillMaxWidth().height(190.dp))
        Text(mediaDetails(media), style = MaterialTheme.typography.bodySmall)
    }
    playerError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(audibleIndex == 0, { audibleIndex = 0 }, { Text("听第一份") })
        FilterChip(audibleIndex == 1, { audibleIndex = 1 }, { Text("听第二份") })
    }
    LaunchedEffect(players, audibleIndex) { players.forEachIndexed { index, player -> player.volume = if (index == audibleIndex) 1f else 0f } }
    Slider(value = position.coerceAtMost(duration).toFloat(), onValueChange = { value -> position = value.toLong(); players.forEach { it.seekTo(position) } }, valueRange = 0f..duration.coerceAtLeast(1L).toFloat())
    Button({ if (!playing && position >= duration - 100L) position = 0L; playing = !playing }, Modifier.fillMaxWidth()) { Text(if (playing) "暂停" else "同步播放") }
    Text("共享播放、暂停和进度，可切换收听每一份声音。切到后台会自动暂停。", style = MaterialTheme.typography.bodySmall, color = Muted)
    LaunchedEffect(players, playing) {
        if (playing) players.forEach { it.seekTo(position); it.play() }
        else players.forEach { it.pause() }
        while (true) {
            val knownDuration = players.map { it.duration }.filter { it > 0 }.minOrNull()
            if (knownDuration != null) duration = knownDuration
            if (playing) {
                position = players.firstOrNull()?.currentPosition ?: position
                if (players.all { it.playbackState == Player.STATE_READY }) {
                    players.drop(1).filter { kotlin.math.abs(it.currentPosition - position) > 150L }.forEach { it.seekTo(position) }
                }
                if (players.firstOrNull()?.playbackState == Player.STATE_ENDED) playing = false
            }
            delay(250)
        }
    }
}

@Composable
private fun TrashConfirmationDialog(selected: List<Pair<DuplicateGroup, GroupReview>>, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val exact = selected.count { it.first.kind == DuplicateKind.EXACT }
    val versions = selected.size - exact
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认移入回收站") },
        confirmButton = { Button(onConfirm) { Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.size(6.dp)); Text("请求系统确认") } },
        dismissButton = { OutlinedButton(onDismiss) { Text("返回检查") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("完全重复 $exact 组 · 人工确认版本 $versions 组")
                selected.forEach { (group, review) ->
                    val keeper = group.items.first { it.id == review.keeperId }
                    Text(kindLabel(group.kind), fontWeight = FontWeight.SemiBold)
                    Text("保留：${keeper.displayName}", color = Green)
                    group.items.filterNot { it.id == keeper.id }.forEach { Text("移入回收站：${it.displayName}") }
                    HorizontalDivider()
                }
                Text("接下来由 Android 显示系统授权。授权通过后仍会逐项核对实际结果。", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        },
    )
}

@Composable
private fun SettingsScreen(
    state: MediaCraftUiState,
    viewModel: MediaCraftViewModel,
    onRequestPermissions: (Boolean, Boolean) -> Unit,
    onPickFolder: () -> Unit,
    onClearFolder: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeading("管理访问范围", "设置", "先授权媒体访问，再选择全局相册或指定文件夹进行去重。")
            PermissionCard(state.permissionCoverage, folderSelected = state.scanScope.folderUri != null) { onRequestPermissions(state.scanScope.photos, state.scanScope.videos) }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("扫描范围", fontWeight = FontWeight.SemiBold)
                    Text("媒体类型", style = MaterialTheme.typography.labelMedium, color = Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.scanScope.photos,
                            onClick = { viewModel.updateScope(photos = !state.scanScope.photos) },
                            label = { Text("照片") },
                            enabled = !state.operationPending,
                        )
                        FilterChip(
                            selected = state.scanScope.videos,
                            onClick = { viewModel.updateScope(videos = !state.scanScope.videos) },
                            label = { Text("视频") },
                            enabled = !state.operationPending,
                        )
                    }
                    Text("扫描来源", style = MaterialTheme.typography.labelMedium, color = Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = onPickFolder, enabled = !state.operationPending) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("选择文件夹")
                        }
                        if (state.scanScope.folderUri != null) {
                            TextButton(onClick = onClearFolder, enabled = !state.operationPending) { Text("恢复全局") }
                        }
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
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = PaleGreen)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("隐私与安全", fontWeight = FontWeight.SemiBold)
                    Text("扫描和摘要计算均在本机完成，不上传照片或视频。应用只读取 Android 授权范围内的媒体。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable private fun ScreenHeading(eyebrow: String, title: String, subtitle: String) = Column(Modifier.padding(vertical = 16.dp)) { Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = Green); Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted) }
@Composable private fun MessageCard(message: String) = Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5E8))) { Text(message, Modifier.fillMaxWidth().padding(12.dp)) }
@Composable private fun BusyOverlay(message: String) = Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)), contentAlignment = Alignment.Center) { Card { Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(24.dp)); Spacer(Modifier.size(12.dp)); Text(message) } } }

private fun kindLabel(kind: DuplicateKind) = when (kind) { DuplicateKind.EXACT -> "完全重复"; DuplicateKind.PHOTO_VERSION -> "疑似同内容 · 照片版本"; DuplicateKind.VIDEO_VERSION -> "疑似同内容 · 视频版本" }
private fun operationLabel(type: OperationType) = when (type) { OperationType.RENAME -> "批量重命名"; OperationType.TRASH -> "移入回收站"; OperationType.RESTORE -> "恢复文件"; OperationType.UNDO_RENAME -> "恢复原名" }
private fun statusLabel(status: OperationStatus) = when (status) { OperationStatus.PENDING -> "等待核对"; OperationStatus.SUCCESS -> "成功"; OperationStatus.FAILED -> "失败"; OperationStatus.SKIPPED -> "跳过"; OperationStatus.CANCELLED -> "取消" }
private fun mediaDetails(media: MediaItem): String = buildString {
    append("${media.width} × ${media.height}")
    if (media.kind == MediaKind.PHOTO) append(" · ${"%.1f".format(media.pixels / 1_000_000.0)} MP")
    media.video?.let { video ->
        append(" · %d:%02d".format(media.durationMillis / 60_000, media.durationMillis / 1000 % 60))
        append(" · ${video.codec ?: "编码未知"}")
        video.bitrate?.let { append(" · ${"%.1f".format(it / 1_000_000.0)} Mbps") }
        video.frameRate?.let { append(" · ${"%.1f".format(it)} fps") }
        append(" · ${video.dynamicRange ?: "色彩范围未知"}")
        append(video.bitDepth?.let { " · $it bit" } ?: " · 色深未知")
        append(" · ${video.audioSignature ?: "音轨未知"}")
    }
    append(" · ${formatBytes(media.size)}")
}
private fun formatBytes(bytes: Long): String = when { bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0); bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0); bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0); else -> "$bytes B" }
