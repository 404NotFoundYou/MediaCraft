package com.mediacraft.app.ui

import android.content.Intent
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mediacraft.app.data.MediaLocationRepository
import com.mediacraft.app.data.PlaceNameRepository
import com.mediacraft.app.domain.MediaItem
import com.mediacraft.app.domain.MediaKind
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

private val DetailGreen = Color(0xFF24694E)
private val DetailInk = Color(0xFF20352D)
private val DetailMuted = Color(0xFF74827B)
private val PreviewBackground = Color(0xFF152A25)

@Composable
internal fun MediaDetailSheet(
    media: MediaItem,
    duplicateCount: Int,
    locationRepository: MediaLocationRepository,
    placeNameRepository: PlaceNameRepository,
    onDismiss: () -> Unit,
    onReviewDuplicates: () -> Unit,
) {
    val wide = LocalConfiguration.current.screenWidthDp >= 700
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x990E211A))
                .padding(if (wide) 28.dp else 0.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = if (wide) Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.9f) else Modifier.fillMaxSize(),
                shape = if (wide) RoundedCornerShape(24.dp) else RoundedCornerShape(0.dp),
                color = Color.White,
                shadowElevation = if (wide) 24.dp else 0.dp,
            ) {
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        DetailPreview(media, onDismiss, Modifier.weight(1.15f).fillMaxHeight())
                        DetailInformation(
                            media,
                            duplicateCount,
                            locationRepository,
                            placeNameRepository,
                            onReviewDuplicates,
                            Modifier.weight(0.85f).fillMaxHeight(),
                        )
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        DetailPreview(media, onDismiss, Modifier.fillMaxWidth().height(280.dp))
                        DetailInformation(
                            media,
                            duplicateCount,
                            locationRepository,
                            placeNameRepository,
                            onReviewDuplicates,
                            Modifier.fillMaxWidth().weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailPreview(media: MediaItem, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.background(PreviewBackground)) {
        if (media.kind == MediaKind.VIDEO) {
            DetailVideoPlayer(media, Modifier.fillMaxSize())
        } else {
            DetailPhoto(media, Modifier.fillMaxSize().padding(24.dp))
        }
        Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = 0.38f),
        ) {
            Column(Modifier.padding(horizontal = 11.dp, vertical = 7.dp)) {
                Text("媒体详情", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (media.kind == MediaKind.VIDEO) "视频 · 本机媒体" else "照片 · 本机媒体",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).background(Color.Black.copy(alpha = 0.38f), RoundedCornerShape(50)),
        ) {
            Icon(Icons.Default.Close, contentDescription = "关闭媒体详情", tint = Color.White)
            Spacer(Modifier.width(4.dp))
            Text("关闭详情", color = Color.White)
        }
    }
}

@Composable
private fun DetailPhoto(media: MediaItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(media.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var scale by remember(media.id) { mutableStateOf(1f) }
    var offsetX by remember(media.id) { mutableStateOf(0f) }
    var offsetY by remember(media.id) { mutableStateOf(0f) }
    var loadError by remember(media.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(media.id, media.dateModifiedSeconds) {
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
    Box(
        modifier.pointerInput(media.id) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 6f)
                offsetX += pan.x
                offsetY += pan.y
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                it.asImageBitmap(),
                contentDescription = media.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                ),
            )
        } ?: Text(
            loadError?.let { "预览读取失败：$it" } ?: "正在加载预览",
            color = Color.White,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun DetailVideoPlayer(media: MediaItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var playerError by remember(media.id) { mutableStateOf<String?>(null) }
    val player = remember(media.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(PlayerMediaItem.fromUri(Uri.parse(media.id)))
            prepare()
        }
    }
    DisposableEffect(player, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playerError = "视频播放失败：${error.message ?: error.errorCodeName}"
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) player.pause()
        }
        player.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player; useController = true } },
            modifier = Modifier.fillMaxSize(),
        )
        playerError?.let {
            Text(it, color = Color.White, modifier = Modifier.background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(8.dp)).padding(12.dp))
        }
    }
}

@Composable
private fun DetailInformation(
    media: MediaItem,
    duplicateCount: Int,
    locationRepository: MediaLocationRepository,
    placeNameRepository: PlaceNameRepository,
    onReviewDuplicates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                media.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = DetailInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(media.relativePath.ifBlank { "本机媒体" }, style = MaterialTheme.typography.bodySmall, color = DetailMuted)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailField("尺寸", "${media.width} × ${media.height}", Modifier.weight(1f))
            DetailField("文件大小", formatBytes(media.size), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailField("格式", media.mimeType.substringAfter('/').uppercase(Locale.ROOT), Modifier.weight(1f))
            DetailField("媒体类型", if (media.kind == MediaKind.VIDEO) "视频" else "照片", Modifier.weight(1f))
        }

        DetailMetaRow(
            "拍摄时间",
            if (media.dateTakenMillis > 0) detailDate(media.dateTakenMillis) else "未记录",
        )
        DetailMetaRow(
            "重复项状态",
            if (duplicateCount > 0) "可能存在 $duplicateCount 个版本" else "尚未发现重复候选，扫描后复核",
            valueColor = if (duplicateCount > 0) DetailGreen else DetailMuted,
        )
        media.video?.let { video ->
            DetailMetaRow("视频参数", listOfNotNull(video.codec, video.frameRate?.let { "%.1f fps".format(it) }).ifEmpty { listOf("未知") }.joinToString(" · "))
        }

        MediaLocationSection(media, locationRepository, placeNameRepository)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Button(
                onClick = onReviewDuplicates,
                modifier = Modifier.weight(1f),
            ) {
                Text("去重复项复核")
            }
            OutlinedButton(
                onClick = { shareMedia(context, media) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text("分享")
            }
        }
    }
}

@Composable
private fun DetailField(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(13.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE1E9E3))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = DetailMuted)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = DetailInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DetailMetaRow(label: String, value: String, valueColor: Color = DetailInk) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = DetailMuted)
        Spacer(Modifier.width(16.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = valueColor, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun shareMedia(context: android.content.Context, media: MediaItem) {
    val uri = Uri.parse(media.id)
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = media.mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(sendIntent, "分享 ${media.displayName}"))
}

private fun detailDate(timestampMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestampMillis))
