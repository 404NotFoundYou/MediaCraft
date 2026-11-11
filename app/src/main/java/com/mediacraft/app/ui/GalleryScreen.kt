package com.mediacraft.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediacraft.app.domain.MediaItem
import com.mediacraft.app.domain.MediaKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun GalleryScreen(
    state: MediaCraftUiState,
    viewModel: MediaCraftViewModel,
    onRequestPermissions: (Boolean, Boolean) -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val wide = screenWidth > 900
    val columns = when { wide -> 4; screenWidth > 520 -> 3; else -> 2 }
    val recentMedia = remember(state.media, state.scanScope.album) {
        state.media.filter { state.scanScope.album == null || it.relativePath == state.scanScope.album }
            .sortedWith(compareByDescending<MediaItem> { it.dateTakenMillis.takeIf { date -> date > 0 } ?: it.dateAddedMillis }.thenBy { it.id })
            .take(24)
    }
    val openDuplicates = { viewModel.selectPage(MainPage.DEDUPLICATE) }
    val scan = {
        if (state.permissionCoverage == PermissionCoverage.DENIED) onRequestPermissions(state.scanScope.photos, state.scanScope.videos)
        else { openDuplicates(); if (!state.scanning) viewModel.startScan() }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize().testTag("gallery-grid"),
        contentPadding = PaddingValues(start = if (wide) 28.dp else 16.dp, end = if (wide) 28.dp else 16.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    GalleryHero(state, scan, Modifier.weight(1.35f))
                    GalleryOverviewCard(state, Modifier.weight(0.65f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    GalleryHero(state, scan)
                    GalleryOverviewCard(state)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = 14.dp, bottom = 2.dp)) {
                Text("最近媒体", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("按拍摄时间排列 · 共 ${state.media.count { state.scanScope.album == null || it.relativePath == state.scanScope.album }} 项", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 5.dp))
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(state.scanScope.photos && state.scanScope.videos, { viewModel.updateScope(photos = true, videos = true) }, { Text("全部", fontSize = 12.sp) }, enabled = !state.operationPending, shape = CircleShape)
                    FilterChip(state.scanScope.photos && !state.scanScope.videos, { viewModel.updateScope(photos = true, videos = false) }, { Text("照片", fontSize = 12.sp) }, enabled = !state.operationPending, shape = CircleShape)
                    FilterChip(!state.scanScope.photos && state.scanScope.videos, { viewModel.updateScope(photos = false, videos = true) }, { Text("视频", fontSize = 12.sp) }, enabled = !state.operationPending, shape = CircleShape)
                }
            }
        }
        if (recentMedia.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(color = Color.White, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color(0xFFE1E9E3))) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.PhotoLibrary, null, tint = Green, modifier = Modifier.size(34.dp))
                        Text(if (state.permissionCoverage == PermissionCoverage.DENIED) "先授权你的媒体" else "当前范围内暂无媒体", fontWeight = FontWeight.Bold)
                        Text(if (state.permissionCoverage == PermissionCoverage.DENIED) "只读取 Android 已授权的照片和视频" else "试试切换媒体类型或在设置中调整访问范围", color = Muted, fontSize = 12.sp)
                        if (state.permissionCoverage == PermissionCoverage.DENIED) Button({ onRequestPermissions(state.scanScope.photos, state.scanScope.videos) }, shape = RoundedCornerShape(10.dp)) { Text("授权媒体") }
                    }
                }
            }
        } else {
            items(recentMedia, key = { it.id }) { media -> GalleryTile(media) { onOpenDetail(media) } }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { DuplicateSummary(state, openDuplicates) }
        state.message?.let { message -> item(span = { GridItemSpan(maxLineSpan) }) { MessageCard(message) } }
    }
}

@Composable
private fun GalleryHero(state: MediaCraftUiState, onScan: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth().testTag("gallery-hero"), color = Green, shape = RoundedCornerShape(24.dp), shadowElevation = 3.dp) {
        Box {
            Surface(Modifier.size(230.dp).offset(x = 70.dp, y = (-85).dp).align(Alignment.TopEnd), shape = CircleShape, color = Color.White.copy(alpha = 0.04f), border = BorderStroke(22.dp, Color.White.copy(alpha = 0.04f))) {}
            Column(Modifier.padding(21.dp)) {
                Text("你的本机影像", color = Color(0xFFC8E8D1), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
                Text("从浏览开始，再决定整理什么。", color = Color.White, fontSize = 25.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
                Text("先看照片和视频，再用重复项扫描找出可以复核的副本。所有处理都在设备上完成。", color = Color(0xFFDCEFE3), fontSize = 13.sp, lineHeight = 20.sp)
                Button(onScan, Modifier.padding(top = 21.dp), enabled = !state.operationPending, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Green), shape = RoundedCornerShape(11.dp), contentPadding = PaddingValues(horizontal = 15.dp, vertical = 11.dp)) {
                    Text(if (state.scanning) "查看扫描进度" else "扫描重复项", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.size(8.dp))
                    Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(17.dp))
                }
                Row(Modifier.padding(top = 15.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, null, tint = Color(0xFFC9EAD5), modifier = Modifier.size(14.dp))
                    Text("当前范围：${when { state.scanScope.folderUri != null -> "指定文件夹"; state.permissionCoverage == PermissionCoverage.DENIED -> "尚未授权媒体"; state.permissionCoverage == PermissionCoverage.PARTIAL -> "部分已授权媒体"; else -> "已授权的${if (state.scanScope.photos && state.scanScope.videos) "照片和视频" else if (state.scanScope.photos) "照片" else "视频"}" }}", color = Color(0xFFC9EAD5), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun GalleryOverviewCard(state: MediaCraftUiState, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth().testTag("gallery-overview"), color = Color.White, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, Color(0xFFE1E9E3)), shadowElevation = 1.dp) {
        Column(Modifier.padding(18.dp)) {
            Text("影像概览 · ${SimpleDateFormat("yyyy年M月", Locale.CHINA).format(Date())}", fontSize = 12.sp, color = Muted, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GalleryStat("照片", state.media.count { it.kind == MediaKind.PHOTO }.toString(), Modifier.weight(1f))
                GalleryStat("视频", state.media.count { it.kind == MediaKind.VIDEO }.toString(), Modifier.weight(1f))
                GalleryStat("重复组", state.groups.size.toString(), Modifier.weight(1f))
            }
            Text(if (state.permissionCoverage == PermissionCoverage.DENIED) "授权后显示本机媒体" else "当前授权范围 · ${state.albums.size} 个相册", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 13.dp))
        }
    }
}

@Composable
private fun GalleryStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = Color(0xFFEFF7F1), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(value, color = Green, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(label, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun GalleryTile(media: MediaItem, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(15.dp)).clickable(onClick = onClick).semantics { contentDescription = media.displayName }.testTag("gallery-tile")) {
        MediaThumbnail(media, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)), startY = 160f)))
        Text(media.displayName.substringBeforeLast('.'), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 10.dp, bottom = 9.dp))
        if (media.kind == MediaKind.VIDEO) {
            Surface(Modifier.align(Alignment.TopEnd).padding(9.dp).size(26.dp), shape = CircleShape, color = Color.Black.copy(alpha = 0.48f)) {
                Icon(Icons.Default.PlayArrow, "视频", tint = Color.White, modifier = Modifier.padding(5.dp))
            }
        }
    }
}

@Composable
private fun DuplicateSummary(state: MediaCraftUiState, onReview: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(top = 15.dp).testTag("duplicate-summary"), color = Color.White, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color(0xFFE1E9E3)), shadowElevation = 1.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("重复项工作台", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(if (state.scanning) "正在扫描当前授权范围" else if (state.groups.isEmpty()) "扫描后在这里查看候选组" else "发现 ${state.groups.size} 组 · 等待逐组复核", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Surface(color = Color(0xFFFFF1DD), shape = RoundedCornerShape(50)) { Text("人工确认", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = Color(0xFFA6681B), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
            state.groups.firstOrNull()?.let { group ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { group.items.take(2).forEach { MediaThumbnail(it, Modifier.size(54.dp).clip(RoundedCornerShape(9.dp))) } }
                    Column(Modifier.weight(1f)) {
                        Text(group.items.first().displayName, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(group.reason, color = Green, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Button(onReview, shape = RoundedCornerShape(10.dp)) { Text(if (state.groups.isEmpty()) "查看重复项" else "去复核", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
