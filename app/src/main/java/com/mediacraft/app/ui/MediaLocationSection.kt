package com.mediacraft.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mediacraft.app.data.MediaLocationRepository
import com.mediacraft.app.data.PlaceNameRepository
import com.mediacraft.app.domain.GeoCoordinates
import com.mediacraft.app.domain.LocationReadResult
import com.mediacraft.app.domain.MediaItem
import com.mediacraft.app.domain.PlaceName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

@Composable
internal fun MediaLocationSection(
    media: MediaItem,
    locationRepository: MediaLocationRepository,
    placeNameRepository: PlaceNameRepository,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var readAttempt by remember(media.id) { mutableStateOf(0) }
    var location by remember(media.id) { mutableStateOf<LocationReadResult?>(null) }
    var permissionDenied by remember(media.id) { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val activity = context.findActivity()
        permissionDenied = !granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_MEDIA_LOCATION)
        readAttempt++
    }
    DisposableEffect(lifecycleOwner, media.id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) readAttempt++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(media.id, media.generation, media.dateModifiedSeconds, readAttempt) {
        location = null
        location = locationRepository.read(media)
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().testTag("media-location"),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("拍摄位置", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            when (val result = location) {
                null -> Text("正在读取媒体位置信息…", style = MaterialTheme.typography.bodySmall)
                LocationReadResult.Missing -> Text("未记录位置", style = MaterialTheme.typography.bodySmall)
                LocationReadResult.PermissionRequired -> {
                    Text("需要允许读取媒体中的 GPS 信息；不会获取设备当前定位。", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton({
                        if (permissionDenied) {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                        } else {
                            launcher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
                        }
                    }) { Text(if (permissionDenied) "前往应用权限设置" else "允许读取媒体位置") }
                }
                is LocationReadResult.Failure -> {
                    Text(result.message, style = MaterialTheme.typography.bodySmall)
                    TextButton({ readAttempt++ }) { Text("重新读取位置") }
                }
                is LocationReadResult.Found -> key(result.coordinates) { LocationDetails(result.coordinates, placeNameRepository) }
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun LocationDetails(coordinates: GeoCoordinates, repository: PlaceNameRepository) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var place by remember { mutableStateOf<PlaceName?>(null) }
    var querying by remember { mutableStateOf(true) }
    var queryError by remember { mutableStateOf<String?>(null) }
    var queryAttempt by remember { mutableStateOf(0) }
    var showMap by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var openError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(queryAttempt) {
        querying = true
        queryError = null
        try {
            place = withTimeout(8_000) { repository.resolve(coordinates) }
            if (place == null) queryError = "暂时无法识别地点，坐标仍可使用。"
        } catch (_: TimeoutCancellationException) {
            queryError = "地名查询超时，坐标仍可使用。"
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            queryError = "地名查询失败，请检查网络或系统地理编码服务。"
        } finally {
            querying = false
        }
    }
    if (querying) Text("正在查询地名…", style = MaterialTheme.typography.bodySmall)
    place?.let { resolved ->
        Text(resolved.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("media-place-name"))
        resolved.address?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
    queryError?.let { message ->
        Text(message, style = MaterialTheme.typography.bodySmall)
        TextButton({ queryAttempt++ }, enabled = !querying) { Text("重新查询地名") }
    }
    Text(coordinates.dmsText(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("media-coordinates"))
    TextButton({ clipboard.setText(AnnotatedString("${coordinates.latitude}, ${coordinates.longitude}")); copied = true }) {
        Text(if (copied) "坐标已复制" else "复制坐标")
    }
    if (showMap) {
        LocationOnlineMap(coordinates)
    } else {
        OutlinedButton({ showMap = true }, Modifier.fillMaxWidth()) { Text("加载拍摄位置地图") }
    }
    TextButton({
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(locationMapUrl(coordinates))))
        } catch (_: android.content.ActivityNotFoundException) {
            openError = "没有可打开地图的应用或浏览器。"
        }
    }) { Text("在外部地图查看") }
    openError?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    Text(
        "地名查询可能由系统联网发送坐标；加载地图会向 OpenStreetMap 发送坐标。不发送照片或视频。",
        style = MaterialTheme.typography.labelSmall,
    )
}

internal fun locationMapUrl(coordinates: GeoCoordinates): String =
    "https://www.openstreetmap.org/?mlat=${coordinates.latitude}&mlon=${coordinates.longitude}#map=16/${coordinates.latitude}/${coordinates.longitude}"

internal fun locationMapEmbedUrl(coordinates: GeoCoordinates): String {
    val west = (coordinates.longitude - 0.008).coerceAtLeast(-180.0)
    val east = (coordinates.longitude + 0.008).coerceAtMost(180.0)
    val south = (coordinates.latitude - 0.006).coerceAtLeast(-90.0)
    val north = (coordinates.latitude + 0.006).coerceAtMost(90.0)
    return "https://www.openstreetmap.org/export/embed.html?bbox=$west,$south,$east,$north&layer=mapnik&marker=${coordinates.latitude},${coordinates.longitude}"
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LocationOnlineMap(coordinates: GeoCoordinates) {
    val context = LocalContext.current
    var loading by remember(coordinates) { mutableStateOf(true) }
    var failed by remember(coordinates) { mutableStateOf(false) }
    var attempt by remember(coordinates) { mutableStateOf(0) }
    if (failed) {
        Text("地图加载失败，地名和坐标仍可使用。", style = MaterialTheme.typography.bodySmall)
        TextButton({ failed = false; loading = true; attempt++ }) { Text("重新加载地图") }
        return
    }
    val webView = remember(context, coordinates, attempt) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.url.scheme != "https" || request.url.host != "www.openstreetmap.org"

                override fun onPageFinished(view: WebView, url: String) { loading = false }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) { failed = true; loading = false }
                }

                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                    if (request.isForMainFrame) { failed = true; loading = false }
                }
            }
            contentDescription = "媒体拍摄位置地图"
            loadUrl(locationMapEmbedUrl(coordinates))
        }
    }
    DisposableEffect(webView) {
        onDispose { webView.stopLoading(); webView.destroy() }
    }
    LaunchedEffect(webView) {
        delay(15_000)
        if (loading) { failed = true; loading = false }
    }
    if (loading) Text("地图加载中…", style = MaterialTheme.typography.bodySmall)
    AndroidView(factory = { webView }, modifier = Modifier.fillMaxWidth().height(210.dp))
    Text("© OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall)
}
