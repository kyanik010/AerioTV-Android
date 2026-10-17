package com.aeriotv.android.core.subtitles

import android.content.Context
import java.io.File
import java.security.MessageDigest

class ExternalSubtitleCache(context: Context) {
    private val root = File(context.cacheDir, "external-subtitles").apply { mkdirs() }

    fun fileFor(contentKey: String, result: ExternalSubtitleResult): File {
        val safeKey = sha256("${'$'}contentKey|${'$'}{result.provider}|${'$'}{result.id}")
        val ext = result.format?.lowercase()?.takeIf { it.matches(Regex("[a-z0-9]{2,5}")) } ?: "srt"
        return File(root, "${'$'}safeKey.${'$'}ext")
    }

    fun put(contentKey: String, result: ExternalSubtitleResult, file: ExternalSubtitleFile): File {
        val target = fileFor(contentKey, result)
        if (!target.exists()) target.outputStream().use { it.write(file.bytes) }
        return target
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
