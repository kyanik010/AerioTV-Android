package com.aeriotv.android.feature.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AudioSourceTabEntryPoint {
    fun audioSourceManager(): AudioSourceManager
}

/**
 * Main Audio source library.
 *
 * This is deliberately separate from the in-player picker: the user enters
 * the M3U URL here once, loads the complete audio-channel list, and the same
 * singleton manager is then used by PlayerScreen's Audio button.
 */
@Composable
fun AudioSourceTabContent() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val manager = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AudioSourceTabEntryPoint::class.java,
        ).audioSourceManager()
    }
    val channels by manager.channels.collectAsState()
    val selected by manager.selected.collectAsState()
    val savedUrl by manager.url.collectAsState()
    val scope = rememberCoroutineScope()
    var url by remember(savedUrl) { mutableStateOf(savedUrl) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(savedUrl) {
        manager.restoreSelected()
        if (savedUrl.isNotBlank() && channels.isEmpty()) {
            loading = true
            error = manager.loadPlaylist(savedUrl).exceptionOrNull()?.message
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(30.dp))
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Audio", style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (channels.isEmpty()) "Add an M3U audio playlist"
                    else "${channels.size} audio channels loaded",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected != null) {
                IconButton(onClick = manager::stop) {
                    Icon(Icons.Filled.Stop, contentDescription = "Remove Audio")
                }
            }
        }

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("M3U URL") },
            placeholder = { Text("https://example.com/audio.m3u") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                enabled = !loading && url.isNotBlank(),
                onClick = {
                    loading = true
                    error = null
                    scope.launch {
                        error = manager.loadPlaylist(url).exceptionOrNull()?.message
                        loading = false
                    }
                },
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Load Audio")
                }
            }
            IconButton(
                enabled = !loading && url.isNotBlank(),
                onClick = {
                    loading = true
                    error = null
                    scope.launch {
                        error = manager.loadPlaylist(url).exceptionOrNull()?.message
                        loading = false
                    }
                },
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh Audio")
            }
            if (channels.isNotEmpty()) {
                Text("${channels.size} channels", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "Audio source",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    selected?.name ?: "No audio channel selected",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(channels, key = { it.url }) { channel ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = if (channel == selected)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    onClick = { manager.play(channel) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!channel.logo.isNullOrBlank()) {
                            AsyncImage(
                                model = channel.logo,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(44.dp))
                        }
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(channel.name, style = MaterialTheme.typography.titleMedium)
                            channel.group?.takeIf { it.isNotBlank() }?.let {
                                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (channel == selected) {
                            Text("Selected", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        if (selected != null) {
            TextButton(onClick = manager::stop) {
                Text("Remove Audio")
            }
        }
    }
}
