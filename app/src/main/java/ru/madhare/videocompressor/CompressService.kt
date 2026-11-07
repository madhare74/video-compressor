package ru.madhare.videocompressor

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.StatFs
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.ExportException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@UnstableApi
class CompressService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var lastNotify = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val first = notification("Подготовка…", -1f, ongoing = true)
        if (Build.VERSION.SDK_INT >= 29) {
            ServiceCompat.startForeground(this, NOTIF_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, first)
        }

        if (intent?.action == ACTION_CANCEL) {
            CompressionManager.requestCancel()
            if (job?.isActive != true) finish()
            return START_NOT_STICKY
        }

        if (job?.isActive != true) {
            CompressionManager.cancelHandler = { job?.cancel() }
            job = scope.launch {
                try {
                    runQueue()
                } finally {
                    CompressionManager.cancelHandler = null
                    withContext(NonCancellable) {
                        CompressionManager.cancelQueued()
                        finish()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun finish() {
        val (done, total) = CompressionManager.summary()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (total > 0) notifyResult("Сжатие завершено", "Обработано $done из $total")
        stopSelf()
    }

    private suspend fun runQueue() {
        while (true) {
            val item = CompressionManager.takeNext() ?: break
            process(item)
        }
    }

    private suspend fun process(item: VideoItem) {
        val manager = CompressionManager
        val tmp = File(cacheDir, "vc_${item.id}.mp4")
        try {
            val plan = Planner.plan(item.level, item.width, item.height, item.videoBps, item.hevc)
            val needed = Planner.estimateSize(plan, item.durationMs) * 13 / 10 + 50_000_000L
            val free = StatFs(cacheDir.absolutePath).availableBytes
            if (free < needed) {
                manager.update(item.id) { it.copy(status = Status.Failed, message = "Недостаточно места на телефоне") }
                return
            }

            val (doneBefore, total) = manager.summary()
            Compressor.compress(this, item.uri, plan, item.hevc, item.height > item.width, tmp) { p ->
                manager.update(item.id) { it.copy(progress = p) }
                val now = System.currentTimeMillis()
                if (now - lastNotify > 1000) {
                    lastNotify = now
                    notifyProgress("${doneBefore + 1} из $total · ${item.name}", p)
                }
            }

            val outSize = tmp.length()
            if (item.sizeBytes > 0 && outSize >= item.sizeBytes) {
                manager.update(item.id) {
                    it.copy(
                        status = Status.Skipped, progress = 1f, resultSize = outSize,
                        message = "Результат не меньше оригинала, файл не сохранён",
                    )
                }
                return
            }
            val uri = Saver.save(this, tmp, Naming.compressedName(item.name), item.dateTakenMs)
            manager.update(item.id) {
                it.copy(status = Status.Done, progress = 1f, resultUri = uri, resultSize = outSize, message = null)
            }
        } catch (e: CancellationException) {
            manager.update(item.id) { it.copy(status = Status.Cancelled, message = "Отменено") }
            throw e
        } catch (t: Throwable) {
            manager.update(item.id) { it.copy(status = Status.Failed, message = errorText(t)) }
        } finally {
            withContext(NonCancellable) { tmp.delete() }
        }
    }

    private fun errorText(t: Throwable): String {
        val e = t as? ExportException
        return when {
            e != null && (e.errorCode == ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
                e.errorCode == ExportException.ERROR_CODE_DECODER_INIT_FAILED) ->
                "Формат видео не поддерживается кодером этого телефона"
            e != null && (e.errorCode == ExportException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                e.errorCode == ExportException.ERROR_CODE_IO_NO_PERMISSION) ->
                "Видео недоступно или повреждено"
            e != null && e.errorCode == ExportException.ERROR_CODE_IO_UNSPECIFIED ->
                "Ошибка чтения или записи файла"
            else -> "Не удалось сжать видео"
        }
    }

    // ---------- Уведомления ----------

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Сжатие видео", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun notification(text: String, progress: Float, ongoing: Boolean): Notification {
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, CompressService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Сжатие видео")
            .setContentText(text)
            .setContentIntent(contentIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
        if (ongoing) {
            b.addAction(0, "Отменить", cancel)
            if (progress >= 0f) b.setProgress(100, (progress * 100).toInt(), false)
            else b.setProgress(0, 0, true)
        }
        return b.build()
    }

    private fun notifyProgress(text: String, progress: Float) {
        if (!canNotify()) return
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(text, progress, true))
    }

    private fun notifyResult(title: String, text: String) {
        if (!canNotify()) return
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(RESULT_ID, n)
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "compress"
        private const val NOTIF_ID = 101
        private const val RESULT_ID = 102
        const val ACTION_CANCEL = "ru.madhare.videocompressor.CANCEL"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, CompressService::class.java))
        }
    }
}
