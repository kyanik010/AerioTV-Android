package com.aeriotv.android.core.subtitles

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import java.io.File

/** Media3 sideloading helper for downloaded SRT/VTT/ASS subtitles. */
object ExternalSubtitlePlayer {
    fun configuration(file: File, language: String, label: String): MediaItem.SubtitleConfiguration {
        val mime = when (file.extension.lowercase()) {
            "vtt", "webvtt" -> MimeTypes.TEXT_VTT
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        return MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))
            .setId("external:${'$'}{file.name}")
            .setLabel(label)
            .setLanguage(language)
            .setMimeType(mime)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
    }

    /**
     * Rebuilds the current media item with one external subtitle while
     * preserving the current position and play/pause state. Media3's
     * DefaultMediaSourceFactory merges sideloaded subtitle configurations with
     * the content source.
     */
    fun attach(
        player: Player,
        file: File,
        language: String,
        label: String = language,
    ) {
        val current = player.currentMediaItem ?: return
        val position = player.currentPosition
        val playing = player.playWhenReady
        val subtitles = current.localConfiguration?.subtitleConfigurations.orEmpty()
            .filterNot { it.id?.startsWith("external:") == true }
            .toMutableList()
        subtitles += configuration(file, language, label)

        val rebuilt = current.buildUpon()
            .setSubtitleConfigurations(subtitles)
            .build()
        player.setMediaItem(rebuilt, position)
        player.prepare()
        player.playWhenReady = playing
    }

    fun clearExternal(player: Player) {
        val current = player.currentMediaItem ?: return
        val subtitles = current.localConfiguration?.subtitleConfigurations.orEmpty()
            .filterNot { it.id?.startsWith("external:") == true }
        if (subtitles.size == current.localConfiguration?.subtitleConfigurations?.size) return
        val position = player.currentPosition
        val playing = player.playWhenReady
        player.setMediaItem(current.buildUpon().setSubtitleConfigurations(subtitles).build(), position)
        player.prepare()
        player.playWhenReady = playing
    }
}
