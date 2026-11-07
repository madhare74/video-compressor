package ru.madhare.videocompressor

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

enum class Status { Ready, Queued, Running, Done, Skipped, Failed, Cancelled }

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    /** Битрейт видеодорожки (приблизительно), бит/с; 0 — неизвестен. */
    val videoBps: Long,
    val dateTakenMs: Long?,
    val thumb: Bitmap? = null,
    val status: Status = Status.Ready,
    val progress: Float = 0f,
    val level: Level = Level.MIN,
    val hevc: Boolean = false,
    val resultUri: Uri? = null,
    val resultSize: Long = 0L,
    val message: String? = null,
)

/** Общее состояние очереди; живёт, пока жив процесс (сервис и экран работают в одном процессе). */
object CompressionManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var nextId = 1L

    private val _items = MutableStateFlow<List<VideoItem>>(emptyList())
    val items: StateFlow<List<VideoItem>> = _items.asStateFlow()

    private val _level = MutableStateFlow(Level.MIN)
    val level: StateFlow<Level> = _level.asStateFlow()

    private val _hevc = MutableStateFlow(false)
    val hevc: StateFlow<Boolean> = _hevc.asStateFlow()

    private val _loading = MutableStateFlow(0)
    val loading: StateFlow<Int> = _loading.asStateFlow()

    /** Регистрируется сервисом на время работы, чтобы кнопка «Отменить» на экране могла остановить очередь. */
    @Volatile
    var cancelHandler: (() -> Unit)? = null

    fun setLevel(level: Level) { _level.value = level }
    fun setHevc(value: Boolean) { _hevc.value = value }

    fun isBusy(): Boolean = _items.value.any { it.status == Status.Queued || it.status == Status.Running }

    fun add(context: Context, uris: List<Uri>) {
        val app = context.applicationContext
        val known = _items.value.map { it.uri }.toSet()
        val fresh = uris.filter { it !in known }
        if (fresh.isEmpty()) return
        _loading.update { it + fresh.size }
        scope.launch {
            for (uri in fresh) {
                try {
                    val item = MetaReader.read(app, uri, nextId++)
                    _items.update { it + item }
                } catch (_: Throwable) {
                    // Нечитаемый файл просто пропускаем.
                } finally {
                    _loading.update { it - 1 }
                }
            }
        }
    }

    fun remove(id: Long) {
        _items.update { list -> list.filterNot { it.id == id && it.status != Status.Running } }
    }

    fun clear() {
        _items.update { list -> list.filter { it.status == Status.Queued || it.status == Status.Running } }
    }

    /** Ставит в очередь всё, что ещё не сжато. Возвращает число поставленных видео. */
    fun queueAll(): Int {
        var n = 0
        val lvl = _level.value
        val hv = _hevc.value
        _items.update { list ->
            list.map {
                if (it.status == Status.Ready || it.status == Status.Failed ||
                    it.status == Status.Cancelled || it.status == Status.Skipped
                ) {
                    n++
                    it.copy(status = Status.Queued, progress = 0f, message = null, level = lvl, hevc = hv)
                } else it
            }
        }
        return n
    }

    /** Берёт следующее видео из очереди и помечает его выполняемым. */
    @Synchronized
    fun takeNext(): VideoItem? {
        val next = _items.value.firstOrNull { it.status == Status.Queued } ?: return null
        update(next.id) { it.copy(status = Status.Running, progress = 0f) }
        return _items.value.first { it.id == next.id }
    }

    fun update(id: Long, block: (VideoItem) -> VideoItem) {
        _items.update { list -> list.map { if (it.id == id) block(it) else it } }
    }

    fun cancelQueued() {
        _items.update { list ->
            list.map {
                if (it.status == Status.Queued) it.copy(status = Status.Cancelled, message = "Отменено") else it
            }
        }
    }

    fun requestCancel() {
        cancelQueued()
        cancelHandler?.invoke()
    }

    fun summary(): Pair<Int, Int> {
        val list = _items.value.filter { it.status != Status.Ready }
        val finished = list.count { it.status == Status.Done || it.status == Status.Skipped || it.status == Status.Failed || it.status == Status.Cancelled }
        return finished to list.size
    }
}

object MetaReader {
    fun read(context: Context, uri: Uri, id: Long): VideoItem {
        val (name, size) = queryNameAndSize(context.contentResolver, uri)
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            fun num(key: Int): Long = r.extractMetadata(key)?.toLongOrNull() ?: 0L
            var w = num(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toInt()
            var h = num(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toInt()
            val rotation = num(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION).toInt()
            if (rotation == 90 || rotation == 270) { val t = w; w = h; h = t }
            val duration = num(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val totalBps = num(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            val videoBps = when {
                totalBps > 0 -> maxOf(totalBps - 128_000L, totalBps * 8 / 10)
                size > 0 && duration > 0 -> maxOf(size * 8000L / duration - 128_000L, 0L)
                else -> 0L
            }
            val date = parseDate(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE))
            val thumb = try {
                val atUs = minOf(1_000_000L, duration * 500L)
                r.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let(::scaleThumb)
            } catch (_: Throwable) { null }
            return VideoItem(
                id = id, uri = uri, name = name, durationMs = duration, sizeBytes = size,
                width = w, height = h, videoBps = videoBps, dateTakenMs = date, thumb = thumb,
            )
        } finally {
            try { r.release() } catch (_: Throwable) {}
        }
    }

    private fun scaleThumb(src: Bitmap): Bitmap {
        val maxSide = 320
        val scale = maxSide.toFloat() / maxOf(src.width, src.height)
        if (scale >= 1f) return src
        return Bitmap.createScaledBitmap(src, (src.width * scale).toInt().coerceAtLeast(1), (src.height * scale).toInt().coerceAtLeast(1), true)
    }

    private fun queryNameAndSize(cr: ContentResolver, uri: Uri): Pair<String, Long> {
        var name = "video"
        var size = 0L
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
            }
        }
        return name to size
    }

    private fun parseDate(raw: String?): Long? {
        if (raw == null || raw.length < 15) return null
        return try {
            val f = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US)
            f.timeZone = TimeZone.getTimeZone("UTC")
            f.parse(raw.substring(0, 15))?.time
        } catch (_: Throwable) { null }
    }
}
