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

@Composable
fun AudioSourceSheet(manager: AudioSourceManager, onDismiss: () -> Unit) {
    val channels by manager.channels.collectAsState()
    val selected by manager.selected.collectAsState()
    val savedUrl by manager.url.collectAsState()
    val syncMs by manager.syncMs.collectAsState()
    var url by remember(savedUrl) { mutableStateOf(savedUrl) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        manager.restoreSelected()
        if (savedUrl.isNotBlank() && channels.isEmpty()) {
            loading = true
            error = manager.loadPlaylist(savedUrl).exceptionOrNull()?.message
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.MusicNote, null)
            Text("Audio")
        }},
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(url, { url = it }, label = { Text("M3U URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !loading, onClick = {
                        loading = true; error = null
                        LaunchedEffect(Unit) {}
                    }) { Text(if (loading) "Loading…" else "Load Audio") }
                    IconButton(onClick = {
                        loading = true
                        error = null
                    }) { Icon(Icons.Filled.Refresh, "Refresh") }
                    if (selected != null) IconButton(onClick = manager::stop) { Icon(Icons.Filled.Stop, "Remove Audio") }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it) }
                Text("Audio Sync: \${syncMs} ms")
                Slider(
                    value = syncMs.toFloat(),
                    onValueChange = { manager.setSyncMs((it / 100f).toInt() * 100) },
                    valueRange = -5000f..5000f,
                    steps = 99,
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(channels) { channel ->
                        OutlinedButton({ manager.play(channel) }, Modifier.fillMaxWidth()) {
                            Text(if (channel == selected) "▶ \${channel.name}" else channel.name)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close") } },
    )
}
