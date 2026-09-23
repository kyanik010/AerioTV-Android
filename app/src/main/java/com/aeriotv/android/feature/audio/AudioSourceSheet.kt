package com.aeriotv.android.feature.audio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun AudioSourceSheet(
    manager: AudioSourceManager,
    onDismiss: () -> Unit,
    videoPositionProvider: () -> Long = { 0L },
) {
    val channels by manager.channels.collectAsState()
    val selected by manager.selected.collectAsState()
    val syncMs by manager.syncMs.collectAsState()
    val savedUrl by manager.url.collectAsState()
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(savedUrl, channels.isEmpty()) {
        if (savedUrl.isNotBlank() && channels.isEmpty() && !loading) {
            loading = true
            error = manager.loadPlaylist(savedUrl).exceptionOrNull()?.message
            loading = false
        }
        manager.restoreSelected()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.MusicNote, null)
                Text("مصدر الصوت")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                Text(
                    selected?.name ?: "لا يوجد مصدر صوت محدد",
                    style = MaterialTheme.typography.titleMedium,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = selected != null && !loading,
                        onClick = {
                            val current = selected ?: return@Button
                            loading = true
                            error = null
                            scope.launch {
                                manager.play(current)
                                loading = false
                            }
                        },
                    ) {
                        Text("إعادة الاتصال")
                    }
                    if (selected != null) {
                        IconButton(onClick = manager::stop) {
                            Icon(Icons.Filled.Stop, contentDescription = "إزالة مصدر الصوت")
                        }
                    }
                    IconButton(
                        enabled = savedUrl.isNotBlank() && !loading,
                        onClick = {
                            loading = true
                            error = null
                            scope.launch {
                                error = manager.loadPlaylist(savedUrl).exceptionOrNull()?.message
                                loading = false
                            }
                        },
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "تحديث الصوتيات")
                    }
                }

                Text("القنوات الصوتية", style = MaterialTheme.typography.titleSmall)
                if (channels.isEmpty()) {
                    Text(
                        "لا توجد قنوات صوتية محملة. افتح تبويب Audio وحمّل رابط M3U أولًا.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(channels, key = { it.url }) { channel ->
                            OutlinedButton(
                                onClick = { manager.play(channel) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (channel == selected) "▶ " + channel.name else channel.name)
                            }
                        }
                    }
                }

                Text("مزامنة الصوت: " + syncMs + " ms", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { manager.setSyncMs(syncMs - 100) }) {
                        Text("−100 ms")
                    }
                    Button(onClick = { manager.syncToVideo(videoPositionProvider()) }) {
                        Text("Sync Now")
                    }
                    OutlinedButton(onClick = { manager.setSyncMs(syncMs + 100) }) {
                        Text("+100 ms")
                    }
                }
                Slider(
                    value = syncMs.toFloat(),
                    onValueChange = {
                        manager.setSyncMs((it / 100f).toInt() * 100)
                    },
                    valueRange = -5000f..5000f,
                    steps = 99,
                )
                Text(
                    "المزامنة تُطبّق على موضع فيديو Live TV الحالي عند الضغط على Sync Now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("تم") } },
        dismissButton = {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "إغلاق")
            }
        },
    )
}
