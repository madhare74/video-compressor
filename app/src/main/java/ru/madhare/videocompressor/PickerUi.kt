package ru.madhare.videocompressor

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class PickerState(
    val videos: List<GalleryVideo>,
    val selected: Set<Long>,
    val onlyNew: Boolean,
    val hasAccess: Boolean,
    val loading: Boolean,
)

data class PickerActions(
    val onToggle: (Long) -> Unit = {},
    val onOnlyNew: (Boolean) -> Unit = {},
    val onSelectAllNew: () -> Unit = {},
    val onClearSelection: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onGrant: () -> Unit = {},
    val onSystemPicker: () -> Unit = {},
)

private val fullSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PickerScreen(
    state: PickerState,
    actions: PickerActions,
    thumb: (suspend (GalleryVideo) -> Bitmap?)? = null,
) {
    val shown = visibleVideos(state.videos, state.onlyNew)
    val newCount = state.videos.count(::isNew)
    val selectedCount = state.selected.size

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(116.dp),
                modifier = Modifier.weight(1f).testTag("grid"),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = fullSpan) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OneLineText("Выбор видео", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Сжатые ранее видео помечены. Новые можно выбрать одним нажатием.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.hasAccess) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Только несжатые",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Switch(checked = state.onlyNew, onCheckedChange = actions.onOnlyNew)
                            }
                            OutlinedButton(
                                onClick = actions.onSelectAllNew,
                                enabled = newCount > 0,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                OneLineText(
                                    "Выбрать все несжатые ($newCount)",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state.hasAccess && selectedCount > 0) {
                                TextButton(onClick = actions.onClearSelection) {
                                    OneLineText("Снять выбор", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            TextButton(onClick = actions.onSystemPicker) {
                                OneLineText("Системная галерея", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                if (!state.hasAccess) {
                    item(span = fullSpan) {
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Нужен доступ к видео", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Чтобы показать список видео с пометками, разрешите приложению доступ к видео на телефоне. На Android 14 и новее выберите «Разрешить все».",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(onClick = actions.onGrant, modifier = Modifier.fillMaxWidth()) {
                                    OneLineText("Разрешить доступ", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                } else {
                    if (state.loading) {
                        item(span = fullSpan) {
                            Text(
                                "Загружаю список видео…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else if (shown.isEmpty()) {
                        item(span = fullSpan) {
                            Text(
                                if (state.onlyNew) "Несжатых видео нет. Выключите фильтр, чтобы увидеть все."
                                else "На телефоне нет видео.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(shown, key = { it.id }) { v ->
                        VideoTile(v, v.id in state.selected, thumb) { actions.onToggle(v.id) }
                    }
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
                Button(
                    onClick = actions.onAdd,
                    enabled = selectedCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OneLineText(
                        if (selectedCount > 0) "Добавить ($selectedCount)" else "Добавить",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                TextButton(onClick = actions.onCancel) {
                    OneLineText("Назад", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun VideoTile(
    v: GalleryVideo,
    selected: Boolean,
    thumb: (suspend (GalleryVideo) -> Bitmap?)?,
    onClick: () -> Unit,
) {
    val bmp by produceState<Bitmap?>(null, v.id) { value = thumb?.invoke(v) }
    val shape = MaterialTheme.shapes.small
    val dim = !isNew(v)
    Column(
        Modifier.clip(shape).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier),
        ) {
            bmp?.let {
                Image(
                    bitmap = it.asImageBitmap(), contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = if (dim) 0.45f else 1f,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            val badge = when {
                v.ownResult -> "Результат"
                v.compressed != null -> "Сжато ${v.compressed.name.lowercase()}"
                else -> null
            }
            if (badge != null) {
                Badge(badge, Modifier.align(Alignment.TopStart).padding(4.dp))
            }
            Badge(Fmt.duration(v.durationMs), Modifier.align(Alignment.BottomStart).padding(4.dp))
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓", color = MaterialTheme.colorScheme.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(
            v.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        OneLineText(
            Fmt.size(v.sizeBytes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Badge(text: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.small, color = Color.Black.copy(alpha = 0.62f)) {
        OneLineText(
            text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = Color.White,
        )
    }
}
