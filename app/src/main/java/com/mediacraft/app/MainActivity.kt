package com.mediacraft.app

import android.Manifest
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.core.content.FileProvider
import com.mediacraft.app.data.MediaRepository
import com.mediacraft.app.data.OperationRepository
import com.mediacraft.app.domain.DuplicateScanner
import com.mediacraft.app.domain.RenamePlanner
import com.mediacraft.app.ui.MediaCraftApp
import com.mediacraft.app.ui.MediaCraftViewModel
import com.mediacraft.app.ui.UiEvent
import com.mediacraft.app.ui.UpdateUiEvent
import com.mediacraft.app.ui.UpdateViewModel
import com.mediacraft.app.update.GitHubUpdateClient
import java.io.File
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val mediaRepository by lazy { MediaRepository(applicationContext) }
    private val operationRepository by lazy { OperationRepository(applicationContext, mediaRepository) }

    private val viewModel: MediaCraftViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return MediaCraftViewModel(
                    application = application as Application,
                    savedStateHandle = extras.createSavedStateHandle(),
                    scanner = DuplicateScanner(mediaRepository),
                    renamePlanner = RenamePlanner(),
                    mediaRepository = mediaRepository,
                    operationRepository = operationRepository,
                ) as T
            }
        }
    }

    private val updateViewModel: UpdateViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return UpdateViewModel(
                    application = application as Application,
                    savedStateHandle = extras.createSavedStateHandle(),
                    client = GitHubUpdateClient(applicationContext),
                ) as T
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.refreshPermissionCoverage()
    }

    private val folderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            viewModel.setFolderUri(uri.toString())
        }
    }

    private val authorizationLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onAuthorizationResult(result.resultCode == RESULT_OK)
    }

    private val unknownSourcesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updateViewModel.onUnknownSourcesReturned()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissionCoverage()
        })
        lifecycleScope.launch {
            viewModel.events.collect { event ->
                when (event) {
                    is UiEvent.RequestAuthorization -> {
                        runCatching {
                            val request = IntentSenderRequest.Builder(viewModel.authorizationIntent(event.operation)).build()
                            authorizationLauncher.launch(request)
                        }.onFailure {
                            viewModel.onAuthorizationLaunchFailed(it)
                        }
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                updateViewModel.events.collect { event ->
                    when (event) {
                        UpdateUiEvent.RequestUnknownSources -> openUnknownSourcesSettings()
                        is UpdateUiEvent.LaunchInstaller -> openInstaller(event.apk.file)
                    }
                }
            }
        }
        setContent {
            MediaCraftApp(
                viewModel = viewModel,
                updateViewModel = updateViewModel,
                onRequestPermissions = { photos, videos -> permissionLauncher.launch(requiredPermissions(photos, videos)) },
                onPickFolder = { folderLauncher.launch(null) },
                onClearFolder = { viewModel.setFolderUri(null) },
            )
        }
    }

    private fun openUnknownSourcesSettings() {
        runCatching {
            val intent = Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName"),
            )
            unknownSourcesLauncher.launch(intent)
        }.onFailure(updateViewModel::onSettingsLaunchFailed)
    }

    private fun openInstaller(file: File) {
        runCatching {
            val updatesDirectory = File(cacheDir, "updates").canonicalFile
            val apk = file.canonicalFile
            require(apk.isFile && apk.parentFile == updatesDirectory) { "安装包不在受信更新目录" }
            val uri = FileProvider.getUriForFile(this, "$packageName.updates", apk)
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
            )
        }.onFailure(updateViewModel::onInstallerLaunchFailed)
    }

    private fun requiredPermissions(photos: Boolean, videos: Boolean): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> buildList {
            if (photos) add(Manifest.permission.READ_MEDIA_IMAGES)
            if (videos) add(Manifest.permission.READ_MEDIA_VIDEO)
            add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        }.toTypedArray()
        Build.VERSION.SDK_INT >= 33 -> buildList {
            if (photos) add(Manifest.permission.READ_MEDIA_IMAGES)
            if (videos) add(Manifest.permission.READ_MEDIA_VIDEO)
        }.toTypedArray()
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}
