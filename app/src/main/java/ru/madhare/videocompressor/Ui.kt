package ru.madhare.videocompressor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.isSpecified

data class UiState(
    val items: List<VideoItem>,
    val level: Level,
    val hevc: Boolean,
    val loading: Int,
)

data class Actions(
    val onPick: () -> Unit = {},
    val onLevel: (Level) -> Unit = {},
    val onHevc: (Boolean) -> Unit = {},
    val onRemove: (Long) -> Unit = {},
    val onCompress: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onOpen: (VideoItem) -> Unit = {},
    val onShare: (VideoItem) -> Unit = {},
    val onDeleteOriginals: () -> Unit = {},
    val onClear: () -> Unit = {},
)

private const val NBSP = " "

/** Короткая подпись в одну строку: если не помещается, шрифт уменьшается. */
@Composable
fun OneLineText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
) {
    var scale by remember(text) { mutableFloatStateOf(1f) }
    val base = if (style.fontSize.isSpecified) style.fontSize else 14.sp
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontWeight = fontWeight,
        style = style.copy(fontSize = base * scale, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        onTextLayout = { if (it.didOverflowWidth && scale > 0.5f) scale *= 0.92f },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(state: UiState, actions: Actions) {
    var showSettings by remember { mutableStateOf(false) }
    val busy = state.items.any { it.status == Status.Queued || it.status == Status.Running }
    val anyDone = state.items.any { it.status == Status.Done }
    val retryable = state.items.count {
        it.status == Status.Ready || it.status == Status.Failed ||
            it.status == Status.Cancelled || it.status == Status.Skipped
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item("header") {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text("Video Compressor", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "Выберите видео, уровень сжатия и нажмите «Сжать». Готовые файлы появятся в галерее.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Настройки")
                        }
                    }
                }
                item("pick") {
                    OutlinedButton(
                        onClick = actions.onPick,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { OneLineText("Выбрать видео", style = MaterialTheme.typography.labelLarge) }
                }
                if (state.loading > 0) {
                    item("loading") {
                        Text(
                            "Загружаю список: ещё ${state.loading}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item("levels") {
                    LevelPicker(state, enabled = !busy, onLevel = actions.onLevel)
                }
                if (state.items.isEmpty() && state.loading == 0) {
                    item("empty") {
                        Text(
                            "Список пуст. Нажмите «Выбрать видео», можно сразу несколько.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.items, key = { it.id }) { item ->
                    VideoCard(item, state.level, state.hevc, busy, actions)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (busy) {
                    val total = state.items.count { it.status != Status.Ready }
                    val finished = state.items.count {
                        it.status == Status.Done || it.status == Status.Skipped ||
                            it.status == Status.Failed || it.status == Status.Cancelled
                    }
                    Text(
                        "Готово $finished из $total",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = actions.onCancel, modifier = Modifier.fillMaxWidth()) {
                        OneLineText("Отменить", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Button(
                        onClick = actions.onCompress,
                        enabled = retryable > 0,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OneLineText(
                            if (retryable > 0) "Сжать ($retryable)" else "Сжать",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    if (anyDone || state.items.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (anyDone) {
                                TextButton(onClick = actions.onDeleteOriginals) {
                                    OneLineText("Удалить оригиналы", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            if (state.items.isNotEmpty()) {
                                TextButton(onClick = actions.onClear) {
                                    OneLineText("Очистить список", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Настройки") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Кодек H.265 (HEVC)", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Файл примерно на треть меньше при том же качестве, но открывается не везде.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = state.hevc, onCheckedChange = actions.onHevc)
                    }
                    Text(
                        "Папка сохранения: Movies/${Saver.FOLDER}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) {
                    OneLineText("Готово", style = MaterialTheme.typography.labelLarge)
                }
            },
        )
    }
}

@Composable
private fun LevelPicker(state: UiState, enabled: Boolean, onLevel: (Level) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Level.entries.forEach { level ->
            val selected = state.level == level
            val total = state.items.sumOf { it.sizeBytes }
            val est = state.items.sumOf {
                Planner.estimateSize(
                    Planner.plan(level, it.width, it.height, it.videoBps, state.hevc), it.durationMs,
                )
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(enabled = enabled) { onLevel(level) },
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected, onClick = { onLevel(level) }, enabled = enabled)
                    Column(Modifier.weight(1f)) {
                        Text(level.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            level.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.items.isNotEmpty() && total > 0) {
                            Text(
                                "≈${NBSP}${Fmt.size(est)} из ${Fmt.size(total)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VideoCard(item: VideoItem, level: Level, hevc: Boolean, busy: Boolean, actions: Actions) {
    val shown = if (item.status == Status.Ready) level else item.level
    val shownHevc = if (item.status == Status.Ready) hevc else item.hevc
    val estimate = Planner.estimateSize(
        Planner.plan(shown, item.width, item.height, item.videoBps, shownHevc), item.durationMs,
    )
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .size(width = 88.dp, height = 66.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    item.thumb?.let {
                        Image(
                            bitmap = it.asImageBitmap(), contentDescription = null,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${Fmt.resolution(item.width, item.height)} · ${Fmt.duration(item.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        Fmt.size(item.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.status != Status.Running && !busy) {
                    IconButton(onClick = { actions.onRemove(item.id) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Убрать из списка")
                    }
                }
            }

            when (item.status) {
                Status.Running -> LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                )
                else -> SizeBar(original = item.sizeBytes, result = when (item.status) {
                    Status.Done, Status.Skipped -> item.resultSize
                    else -> estimate
                }, dim = item.status != Status.Done)
            }

            val line = when (item.status) {
                Status.Ready -> "≈${NBSP}${Fmt.size(estimate)} после сжатия"
                Status.Queued -> "В очереди"
                Status.Running -> "Сжимаю: ${(item.progress * 100).toInt()}${NBSP}%"
                Status.Done -> "${Fmt.size(item.sizeBytes)} → ${Fmt.size(item.resultSize)} · ${Fmt.saved(item.sizeBytes, item.resultSize)}"
                else -> item.message ?: ""
            }
            if (line.isNotEmpty()) {
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (item.status == Status.Done) FontWeight.SemiBold else FontWeight.Normal,
                    color = when (item.status) {
                        Status.Failed -> MaterialTheme.colorScheme.error
                        Status.Done -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            if (item.status == Status.Done && item.resultUri != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { actions.onOpen(item) }) {
                        OneLineText("Открыть", style = MaterialTheme.typography.labelLarge)
                    }
                    OutlinedButton(onClick = { actions.onShare(item) }) {
                        OneLineText("Поделиться", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** Полоса «было → стало»: серая — оригинал, цветная — результат (или оценка). */
@Composable
private fun SizeBar(original: Long, result: Long, dim: Boolean) {
    val fraction = if (original > 0) (result.toFloat() / original).coerceIn(0.03f, 1f) else 1f
    val color = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .clip(RoundedCornerShape(4.dp))
                .background(if (dim) color.copy(alpha = 0.45f) else color),
        )
    }
}
