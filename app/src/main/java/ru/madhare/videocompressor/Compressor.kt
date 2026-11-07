package ru.madhare.videocompressor

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@UnstableApi
object Compressor {

    /** Кодирует [uri] в [out]. Отмена корутины останавливает кодирование. */
    suspend fun compress(
        context: Context,
        uri: Uri,
        plan: Plan,
        hevc: Boolean,
        out: File,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.Main) {
        val app = context.applicationContext
        suspendCancellableCoroutine<Unit> { cont ->
            val encoderFactory = DefaultEncoderFactory.Builder(app)
                .setRequestedVideoEncoderSettings(
                    VideoEncoderSettings.Builder()
                        .setBitrate(plan.videoBitrate)
                        .setBitrateMode(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
                        .build()
                )
                .setRequestedAudioEncoderSettings(
                    AudioEncoderSettings.Builder().setBitrate(plan.audioBitrate).build()
                )
                .setEnableFallback(true)
                .build()

            val transformer = Transformer.Builder(app)
                .setVideoMimeType(if (hevc) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoderFactory)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        if (cont.isActive) cont.resumeWithException(exportException)
                    }
                })
                .build()

            val videoEffects = mutableListOf<Effect>()
            plan.targetShortSide?.let { videoEffects += Presentation.createForShortSide(it) }
            videoEffects += FrameDropEffect.createDefaultFrameDropEffect(30f)

            val edited = EditedMediaItem.Builder(MediaItem.fromUri(uri))
                .setEffects(Effects(emptyList(), videoEffects))
                .build()

            val handler = Handler(Looper.getMainLooper())
            val holder = ProgressHolder()
            val poll = object : Runnable {
                override fun run() {
                    if (!cont.isActive) return
                    if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress((holder.progress / 100f).coerceIn(0f, 1f))
                    }
                    handler.postDelayed(this, 400)
                }
            }

            cont.invokeOnCancellation { handler.post { transformer.cancel() } }
            try {
                transformer.start(edited, out.absolutePath)
                handler.postDelayed(poll, 400)
            } catch (t: Throwable) {
                if (cont.isActive) cont.resumeWithException(t)
            }
        }
    }
}

object Saver {
    const val FOLDER = "Video Compressor"
    private const val MIME = "video/mp4"

    /** Копирует готовый файл в галерею (Movies/Video Compressor) и возвращает его Uri. */
    fun save(context: Context, tmp: File, desiredName: String, dateTakenMs: Long?): Uri {
        val cr = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= 29)
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        return if (Build.VERSION.SDK_INT >= 29) {
            val relative = "${Environment.DIRECTORY_MOVIES}/$FOLDER/"
            val name = Naming.unique(desiredName) { exists(context, collection, it, relative) }
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, MIME)
                put(MediaStore.Video.Media.RELATIVE_PATH, relative)
                put(MediaStore.Video.Media.IS_PENDING, 1)
                dateTakenMs?.let { put(MediaStore.Video.Media.DATE_TAKEN, it) }
            }
            val uri = cr.insert(collection, values) ?: error("Не удалось создать файл в галерее")
            try {
                cr.openOutputStream(uri)!!.use { os -> tmp.inputStream().use { it.copyTo(os) } }
                val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                cr.update(uri, done, null, null)
            } catch (t: Throwable) {
                try { cr.delete(uri, null, null) } catch (_: Throwable) {}
                throw t
            }
            uri
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), FOLDER)
            dir.mkdirs()
            val name = Naming.unique(desiredName) { File(dir, it).exists() }
            val file = File(dir, name)
            tmp.copyTo(file, overwrite = false)
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, MIME)
                @Suppress("DEPRECATION")
                put(MediaStore.Video.Media.DATA, file.absolutePath)
                put(MediaStore.Video.Media.SIZE, file.length())
                put(MediaStore.Video.Media.DATE_TAKEN, dateTakenMs ?: System.currentTimeMillis())
            }
            val uri = cr.insert(collection, values) ?: run {
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(MIME), null)
                error("Не удалось добавить файл в галерею")
            }
            uri
        }
    }

    private fun exists(context: Context, collection: Uri, name: String, relative: String): Boolean {
        context.contentResolver.query(
            collection, arrayOf(MediaStore.Video.Media._ID),
            "${MediaStore.Video.Media.DISPLAY_NAME}=? AND ${MediaStore.Video.Media.RELATIVE_PATH}=?",
            arrayOf(name, relative), null,
        )?.use { return it.moveToFirst() }
        return false
    }

    /**
     * Пытается получить MediaStore-Uri оригинала (для запроса на удаление). Возвращает null,
     * если видео выбрано не из локальной галереи (например, из облака).
     */
    fun mediaStoreUri(uri: Uri): Uri? {
        if (uri.authority == MediaStore.AUTHORITY) {
            val segs = uri.pathSegments
            // content://media/picker/<user>/<authority>/media/<id>
            if (segs.size >= 5 && segs[0] == "picker" && segs[2] == "com.android.providers.media.photopicker") {
                val id = segs.last().toLongOrNull() ?: return null
                return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            }
            return uri
        }
        return null
    }
}
