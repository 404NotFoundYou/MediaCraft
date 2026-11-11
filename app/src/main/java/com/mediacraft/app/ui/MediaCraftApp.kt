package com.mediacraft.app.ui

import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
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
import com.mediacraft.app.data.MediaLocationRepository
import com.mediacraft.app.data.PlaceNameRepository
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlin.math.max

internal val Green = Color(0xFF24694E)
internal val PaleGreen = Color(0xFFDCEEE3)
internal val Ink = Color(0xFF20352D)
internal val Muted = Color(0xFF74827B)

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
    var detailMedia by remember { mutableStateOf<MediaItem?>(null) }
    val appContext = LocalContext.current.applicationContext
    val locationRepository = remember(appContext) { MediaLocationRepository(appContext) }
    val placeNameRepository = remember(appContext) { PlaceNameRepository(appContext) }
    LaunchedEffect(Unit) { viewModel.refreshPermissionCoverage() }
    LaunchedEffect(state.page, state.permissionCoverage, state.scanScope) {
        if (state.permissionCoverage != PermissionCoverage.DENIED) viewModel.refreshMedia()
    }
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Green, onPrimary = Color.White, primaryContainer = PaleGreen, onPrimaryContainer = Ink, secondary = Green, secondaryContainer = PaleGreen, onSecondaryContainer = Green, background = Color(0xFFF4F7F4), surface = Color.White, onSurface = Ink, onSurfaceVariant = Muted, outline = Color(0xFFE1E9E3), outlineVariant = Color(0xFFE1E9E3)),
        shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(24.dp)),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFEEF5F0), Color(0xFFF8FAF8))))) {
            val wide = maxWidth > 900.dp
            Row(Modifier.fillMaxSize()) {
                if (wide) AppSidebar(state.page, viewModel::selectPage)
                Scaffold(
                    modifier = Modifier.weight(1f),
                    containerColor = Color.Transparent,
                    topBar = { AppHeader(state.page, viewModel::selectPage, updateViewModel::openAndCheck) },
                    bottomBar = { if (!wide) FloatingNavigation(state.page, viewModel::selectPage) },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        when (state.page) {
                            MainPage.GALLERY -> GalleryScreen(state, viewModel, onRequestPermissions, onOpenDetail = { detailMedia = it })
                            MainPage.DEDUPLICATE -> DeduplicateScreen(state, viewModel, onRequestPermissions, onCompare = { comparing = it })
                            MainPage.RENAME -> PrototypeRenameScreen(state, viewModel)
                            MainPage.HISTORY -> PrototypeHistoryScreen(state, viewModel)
                            MainPage.SETTINGS -> PrototypeSettingsScreen(state, viewModel, onRequestPermissions, onPickFolder, onClearFolder)
                        }
                        state.busyMessage?.let { BusyOverlay(it) }
                    }
                }
            }
        }
        comparing?.let { group ->
            if (group.kind == DuplicateKind.VIDEO_VERSION) VideoComparisonDialog(group, onDismiss = { comparing = null })
            else PhotoComparisonDialog(group, onDismiss = { comparing = null })
        }
        detailMedia?.let { media ->
            MediaDetailSheet(media, state.groups.count { group -> group.items.any { it.id == media.id } }, locationRepository, placeNameRepository, onDismiss = { detailMedia = null }, onReviewDuplicates = { detailMedia = null; viewModel.selectPage(MainPage.DEDUPLICATE) })
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
private fun AppHeader(page: MainPage, onSelect: (MainPage) -> Unit, onCheckUpdate: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text("MEDIACRAFT · LOCAL GALLERY", color = Green, fontSize = 10.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.ExtraBold)
            Text(pageLabel(page), color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
        }
        Box {
            Surface(shape = RoundedCornerShape(13.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE1E9E3))) {
                IconButton({ menuExpanded = true }, Modifier.size(42.dp)) { Icon(Icons.Default.MoreHoriz, "更多", tint = Green) }
            }
            DropdownMenu(menuExpanded, { menuExpanded = false }) {
                DropdownMenuItem(text = { Text("活动记录") }, leadingIcon = { Icon(Icons.Default.History, null) }, onClick = { menuExpanded = false; onSelect(MainPage.HISTORY) })
                DropdownMenuItem(text = { Text("检查更新") }, leadingIcon = { Icon(Icons.Default.SystemUpdate, null) }, onClick = { menuExpanded = false; onCheckUpdate() })
            }
        }
        Surface(onClick = { onSelect(MainPage.SETTINGS) }, shape = RoundedCornerShape(13.dp), color = Green, modifier = Modifier.size(42.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("MC", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        }
    }
}

@Composable
private fun FloatingNavigation(selected: MainPage, onSelect: (MainPage) -> Unit) {
    Surface(Modifier.navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = 0.96f), border = BorderStroke(1.dp, Color(0xFFE1E9E3)), shadowElevation = 8.dp) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(MainPage.GALLERY, MainPage.DEDUPLICATE, MainPage.RENAME, MainPage.SETTINGS).forEach { page ->
                Surface(onClick = { onSelect(page) }, color = if (page == selected) PaleGreen else Color.Transparent, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f).testTag("nav-${page.name.lowercase()}")) {
                    Column(Modifier.padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(pageIcon(page), null, tint = if (page == selected) Green else Muted, modifier = Modifier.size(20.dp))
                        Text(pageLabel(page), fontSize = 11.sp, color = if (page == selected) Green else Muted, fontWeight = if (page == selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppSidebar(selected: MainPage, onSelect: (MainPage) -> Unit) {
    Surface(Modifier.width(250.dp).fillMaxSize(), color = Color.White.copy(alpha = 0.72f)) {
        Column(Modifier.statusBarsPadding().padding(horizontal = 18.dp, vertical = 26.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = Green, modifier = Modifier.size(36.dp)) { Box(contentAlignment = Alignment.Center) { Text("✦", color = Color.White, fontSize = 20.sp) } }
                Column { Text("MediaCraft", fontWeight = FontWeight.Bold, fontSize = 17.sp); Text("本机影像 · 安全整理", color = Muted, fontSize = 11.sp) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                MainPage.entries.forEach { page ->
                    Surface(onClick = { onSelect(page) }, shape = RoundedCornerShape(12.dp), color = if (selected == page) PaleGreen else Color.Transparent) {
                        Row(Modifier.fillMaxWidth().padding(13.dp), horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(pageIcon(page), null, tint = Green, modifier = Modifier.size(22.dp))
                            Text(pageLabel(page), color = if (selected == page) Green else Muted, fontSize = 14.sp, fontWeight = if (selected == page) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Surface(color = Color(0xFFEFF7F1), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFD8EADC))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("▣ 本机处理", color = Green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("扫描和摘要计算均在本机完成，不上传照片或视频。", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
                }
            }
        }
    }
}

private fun pageLabel(page: MainPage) = when (page) {
    MainPage.GALLERY -> "图库"
    MainPage.DEDUPLICATE -> "重复项"
    MainPage.RENAME -> "重命名"
    MainPage.HISTORY -> "活动记录"
    MainPage.SETTINGS -> "设置"
}

private fun pageIcon(page: MainPage) = when (page) {
    MainPage.GALLERY -> Icons.Default.PhotoLibrary
    MainPage.DEDUPLICATE -> Icons.Default.Compare
    MainPage.RENAME -> Icons.Default.TextFields
    MainPage.HISTORY -> Icons.Default.History
    MainPage.SETTINGS -> Icons.Default.Settings
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
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("dedup-list"), contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ScreenHeading("重复项工作台", "重复项", "确定性重复与疑似同内容版本分开展示")
            Surface(color = Color.White, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color(0xFFE1E9E3)), shadowElevation = 1.dp) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("扫描范围", fontSize = 17.sp, fontWeight = FontWeight.Bold)
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
                if (group.kind != DuplicateKind.EXACT) AssistChip(
                    { onCompare(group) },
                    { Text("对比") },
                    leadingIcon = { Icon(Icons.Default.Compare, null) },
                    modifier = Modifier.semantics { contentDescription = "对比 ${group.items.joinToString { it.displayName }}" },
                )
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
internal fun PermissionCard(coverage: PermissionCoverage, folderSelected: Boolean = false, onRequest: () -> Unit) {
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
internal fun AlbumPicker(albums: List<String>, selected: String?, enabled: Boolean, onSelect: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("相册范围", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected == null, { onSelect(null) }, { Text("全部相册") }, enabled = enabled)
            albums.forEach { album -> FilterChip(selected == album, { onSelect(album) }, { Text(album.ifBlank { "未分类" }, maxLines = 1) }, enabled = enabled) }
        }
    }
}

@Composable
internal fun MediaThumbnail(media: MediaItem, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
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
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = media.displayName, modifier = Modifier.fillMaxSize(), contentScale = contentScale) }
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

@Composable internal fun ScreenHeading(eyebrow: String, title: String, subtitle: String) = Column(Modifier.padding(vertical = 16.dp)) { Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = Green); Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted) }
@Composable internal fun MessageCard(message: String) = Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5E8))) { Text(message, Modifier.fillMaxWidth().padding(12.dp)) }
@Composable private fun BusyOverlay(message: String) = Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)), contentAlignment = Alignment.Center) { Card { Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(24.dp)); Spacer(Modifier.size(12.dp)); Text(message) } } }

private fun kindLabel(kind: DuplicateKind) = when (kind) { DuplicateKind.EXACT -> "完全重复"; DuplicateKind.PHOTO_VERSION -> "疑似同内容 · 照片版本"; DuplicateKind.VIDEO_VERSION -> "疑似同内容 · 视频版本" }
internal fun operationLabel(type: OperationType) = when (type) { OperationType.RENAME -> "批量重命名"; OperationType.TRASH -> "移入回收站"; OperationType.RESTORE -> "恢复文件"; OperationType.UNDO_RENAME -> "恢复原名" }
internal fun statusLabel(status: OperationStatus) = when (status) { OperationStatus.PENDING -> "等待核对"; OperationStatus.SUCCESS -> "成功"; OperationStatus.FAILED -> "失败"; OperationStatus.SKIPPED -> "跳过"; OperationStatus.CANCELLED -> "取消" }
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
internal fun formatBytes(bytes: Long): String = when { bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0); bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0); bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0); else -> "$bytes B" }
