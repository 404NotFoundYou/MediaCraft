package com.mediacraft.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun UpdateDialog(state: UpdateUiState, viewModel: UpdateViewModel) {
    if (!state.dialogVisible) return
    AlertDialog(
        onDismissRequest = viewModel::closeDialog,
        title = { Text("MediaCraft 更新") },
        dismissButton = { TextButton(viewModel::closeDialog) { Text("关闭") } },
        confirmButton = {
            when (state.phase) {
                UpdatePhase.IDLE, UpdatePhase.CURRENT, UpdatePhase.NO_RELEASE, UpdatePhase.ERROR -> Button(viewModel::checkForUpdates) {
                    Icon(Icons.Default.Refresh, null); Text("检查更新", Modifier.padding(start = 6.dp))
                }
                UpdatePhase.AVAILABLE -> Button(viewModel::download) {
                    Icon(Icons.Default.Download, null); Text("下载 APK", Modifier.padding(start = 6.dp))
                }
                UpdatePhase.DOWNLOADING -> OutlinedButton(viewModel::cancelDownload) { Text("取消下载") }
                UpdatePhase.READY -> Button(viewModel::requestInstall) {
                    Icon(Icons.Default.InstallMobile, null); Text("安装更新", Modifier.padding(start = 6.dp))
                }
                UpdatePhase.CHECKING, UpdatePhase.VERIFYING -> Unit
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("当前版本 ${state.current.name} (${state.current.code})", style = MaterialTheme.typography.bodySmall)
                Text("媒体处理始终在本机；只有手动检查更新时才访问 GitHub。", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                when (state.phase) {
                    UpdatePhase.IDLE -> Text("点击“检查更新”后才会连接 GitHub。")
                    UpdatePhase.CHECKING -> WorkingRow("正在检查 GitHub Release")
                    UpdatePhase.CURRENT -> Text(state.message.orEmpty())
                    UpdatePhase.NO_RELEASE -> Text(state.message.orEmpty())
                    UpdatePhase.ERROR -> Text(state.message.orEmpty(), color = MaterialTheme.colorScheme.error)
                    UpdatePhase.AVAILABLE -> {
                        UpdateDetails(state)
                        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    UpdatePhase.DOWNLOADING -> {
                        UpdateDetails(state)
                        val progress = state.progress
                        if (progress != null && progress.total > 0) {
                            LinearProgressIndicator(progress = { (progress.downloaded.toDouble() / progress.total).coerceIn(0.0, 1.0).toFloat() }, modifier = Modifier.fillMaxWidth())
                            Text("${formatUpdateBytes(progress.downloaded)} / ${formatUpdateBytes(progress.total)}", style = MaterialTheme.typography.bodySmall)
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                    UpdatePhase.VERIFYING -> WorkingRow(state.message ?: "正在重新校验安装包")
                    UpdatePhase.READY -> {
                        UpdateDetails(state)
                        Text(state.message ?: "APK 已校验，等待安装。")
                        Text("安装由 Android 系统界面确认；MediaCraft 不会静默安装。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
    )
}

@Composable
private fun UpdateDetails(state: UpdateUiState) {
    val update = state.update ?: return
    Text("发现版本 ${update.versionName} (${update.versionCode})", fontWeight = FontWeight.SemiBold)
    Text("安装包 ${update.asset.name} · ${formatUpdateBytes(update.asset.size)}", style = MaterialTheme.typography.bodySmall)
    if (update.notes.isNotBlank()) {
        Text("更新说明", fontWeight = FontWeight.Medium)
        Text(update.notes)
    }
}

@Composable
private fun WorkingRow(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator()
        Text(label)
    }
}

private fun formatUpdateBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
