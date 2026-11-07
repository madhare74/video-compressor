package ru.madhare.videocompressor

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi

@UnstableApi
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { App() } }
    }
}

private const val MAX_PICK = 50

@UnstableApi
@Composable
private fun App() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showPicker by remember { mutableStateOf(false) }
    val items by CompressionManager.items.collectAsStateWithLifecycle()
    val level by CompressionManager.level.collectAsStateWithLifecycle()
    val hevc by CompressionManager.hevc.collectAsStateWithLifecycle()
    val loading by CompressionManager.loading.collectAsStateWithLifecycle()

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    fun startQueue() {
        if (CompressionManager.queueAll() > 0) CompressService.start(context)
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK),
    ) { uris ->
        if (uris.isNotEmpty()) {
            CompressionManager.add(context, uris)
            showPicker = false
        }
    }

    val videoPermission =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
        else Manifest.permission.READ_EXTERNAL_STORAGE
    fun hasVideoAccess() =
        ContextCompat.checkSelfPermission(context, videoPermission) == PackageManager.PERMISSION_GRANTED
    var hasAccess by remember { mutableStateOf(hasVideoAccess()) }
    val accessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { hasAccess = hasVideoAccess() }

    var gallery by remember { mutableStateOf<List<GalleryVideo>>(emptyList()) }
    var galleryLoading by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var onlyNew by remember { mutableStateOf(true) }

    LaunchedEffect(showPicker, hasAccess) {
        if (showPicker && hasAccess) {
            galleryLoading = true
            gallery = withContext(Dispatchers.IO) { Gallery.query(context) }
            galleryLoading = false
        }
    }
    BackHandler(enabled = showPicker) { showPicker = false }

    val thumbLoader: suspend (GalleryVideo) -> Bitmap? = { v ->
        withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= 29) context.contentResolver.loadThumbnail(v.uri, Size(256, 256), null)
                else @Suppress("DEPRECATION") MediaStore.Video.Thumbnails.getThumbnail(
                    context.contentResolver, v.id, MediaStore.Video.Thumbnails.MINI_KIND, null,
                )
            } catch (_: Throwable) { null }
        }
    }

    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val storageDenied = result[Manifest.permission.WRITE_EXTERNAL_STORAGE] == false
        if (storageDenied) toast("Без доступа к памяти нельзя сохранить видео в галерею") else startQueue()
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { /* результат смотреть не нужно: галерея обновится сама */ }

    val actions = remember {
        Actions(
            onPick = {
                hasAccess = hasVideoAccess()
                selected = emptySet()
                showPicker = true
            },
            onLevel = CompressionManager::setLevel,
            onHevc = CompressionManager::setHevc,
            onRemove = CompressionManager::remove,
            onCompress = {
                val need = mutableListOf<String>()
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) need += Manifest.permission.POST_NOTIFICATIONS
                if (Build.VERSION.SDK_INT <= 28 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                ) need += Manifest.permission.WRITE_EXTERNAL_STORAGE
                if (need.isEmpty()) startQueue() else permissions.launch(need.toTypedArray())
            },
            onCancel = CompressionManager::requestCancel,
            onOpen = { item -> item.resultUri?.let { openVideo(context, it, Intent.ACTION_VIEW) } },
            onShare = { item -> item.resultUri?.let { openVideo(context, it, Intent.ACTION_SEND) } },
            onClear = CompressionManager::clear,
            onDeleteOriginals = {
                val done = CompressionManager.items.value.filter { it.status == Status.Done }
                val mediaUris = done.mapNotNull { Saver.mediaStoreUri(it.uri) }
                if (mediaUris.isEmpty()) {
                    toast("Эти оригиналы нельзя удалить из приложения. Удалите их в галерее.")
                } else if (Build.VERSION.SDK_INT >= 30) {
                    val sender = MediaStore.createDeleteRequest(context.contentResolver, mediaUris).intentSender
                    deleteLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build())
                } else {
                    var removed = 0
                    mediaUris.forEach {
                        try { removed += context.contentResolver.delete(it, null, null) } catch (_: Throwable) {}
                    }
                    toast(if (removed > 0) "Удалено оригиналов: $removed" else "Не удалось удалить оригиналы. Удалите их в галерее.")
                }
            },
        )
    }

    if (showPicker) {
        PickerScreen(
            state = PickerState(gallery, selected, onlyNew, hasAccess, galleryLoading),
            actions = PickerActions(
                onToggle = { id -> selected = if (id in selected) selected - id else selected + id },
                onOnlyNew = { onlyNew = it },
                onSelectAllNew = { selected = selected + gallery.filter(::isNew).map { it.id } },
                onClearSelection = { selected = emptySet() },
                onAdd = {
                    val uris = gallery.filter { it.id in selected }.map { it.uri }
                    if (uris.isNotEmpty()) CompressionManager.add(context, uris)
                    showPicker = false
                },
                onCancel = { showPicker = false },
                onGrant = { accessLauncher.launch(videoPermission) },
                onSystemPicker = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                },
            ),
            thumb = thumbLoader,
        )
    } else {
        MainScreen(UiState(items, level, hevc, loading), actions)
    }
}

private fun openVideo(context: android.content.Context, uri: Uri, action: String) {
    try {
        val intent = if (action == Intent.ACTION_SEND) {
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                "Поделиться видео",
            )
        } else {
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Не нашлось приложения для этого действия", Toast.LENGTH_LONG).show()
    }
}
