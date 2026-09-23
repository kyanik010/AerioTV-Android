package com.aeriotv.android.feature.audio

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class AudioM3uChannel(val name: String, val url: String, val logo: String? = null, val group: String? = null)

@OptIn(UnstableApi::class)
@javax.inject.Singleton
class AudioSourceManager @javax.inject.Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("audio_source", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var player: ExoPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _channels = MutableStateFlow<List<AudioM3uChannel>>(emptyList())
    val channels: StateFlow<List<AudioM3uChannel>> = _channels.asStateFlow()
    private val _selected = MutableStateFlow<AudioM3uChannel?>(null)
    val selected: StateFlow<AudioM3uChannel?> = _selected.asStateFlow()
    private val _url = MutableStateFlow(prefs.getString("m3u_url", "") ?: "")
    val url: StateFlow<String> = _url.asStateFlow()
    private val _syncMs = MutableStateFlow(prefs.getInt("sync_ms", 0))
    val syncMs: StateFlow<Int> = _syncMs.asStateFlow()

    init {
        // The audio library is restored at application startup, not only when
        // the Audio screen is opened. This makes the saved M3U immediately
        // available to the in-player "Audio Source" picker after relaunch.
        val savedUrl = _url.value
        if (savedUrl.isNotBlank()) {
            scope.launch { loadPlaylist(savedUrl) }
        }
    }

    suspend fun loadPlaylist(rawUrl: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val normalized = rawUrl.trim()
            require(normalized.isNotEmpty()) { "M3U URL is empty" }
            val connection = URL(normalized).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "AerioTV/Audio")
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val parsed = parseM3u(text)
            require(parsed.isNotEmpty()) { "No audio channels found in M3U" }
            _url.value = normalized
            prefs.edit().putString("m3u_url", normalized).apply()
            _channels.value = parsed
            restoreSelected()
            parsed.size
        }
    }

    fun restoreSelected() {
        val saved = prefs.getString("selected_url", null) ?: return
        _channels.value.firstOrNull { it.url == saved }?.let { _selected.value = it }
    }

    fun play(channel: AudioM3uChannel) {
        val selector = DefaultTrackSelector(context)
        selector.parameters = selector.buildUponParameters()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        player?.release()
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("AerioTV/Audio")
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
        player = ExoPlayer.Builder(context)
            .setTrackSelector(selector)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(http))
            .build().also { p ->
                p.setMediaItem(MediaItem.fromUri(channel.url))
                p.prepare()
                p.playWhenReady = true
            }
        _selected.value = channel
        prefs.edit().putString("selected_url", channel.url).apply()
    }

    fun stop() {
        player?.stop()
        player?.release()
        player = null
        _selected.value = null
        prefs.edit().remove("selected_url").apply()
    }

    fun setSyncMs(value: Int) {
        val clamped = value.coerceIn(-5000, 5000)
        _syncMs.value = clamped
        prefs.edit().putInt("sync_ms", clamped).apply()
    }

    /** Align the audio-only player to the current video playhead. */
    fun syncToVideo(videoPositionMs: Long) {
        val target = (videoPositionMs - _syncMs.value).coerceAtLeast(0L)
        mainHandler.post { player?.seekTo(target) }
    }

    fun seekRelative(deltaMs: Long) {
        player?.let { p -> p.seekTo((p.currentPosition + deltaMs).coerceAtLeast(0L)) }
    }

    private fun parseM3u(text: String): List<AudioM3uChannel> {
        val result = mutableListOf<AudioM3uChannel>()
        var name: String? = null
        var logo: String? = null
        var group: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF", true) -> {
                    name = line.substringAfterLast(',').trim().ifBlank { "Audio" }
                    logo = Regex("""tvg-logo=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(line)?.groupValues?.getOrNull(1)
                    group = Regex("""group-title=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(line)?.groupValues?.getOrNull(1)
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    name?.let { result += AudioM3uChannel(it, line, logo, group) }
                    name = null; logo = null; group = null
                }
            }
        }
        return result
    }
}
