package com.aeriotv.android.core.subtitles

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class WyzieSubtitleService(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://sub.wyzie.io",
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(query: ExternalSubtitleQuery): Result<List<ExternalSubtitleResult>> = runCatching {
        require(apiKey.isNotBlank()) { "Wyzie API key is not configured" }
        val id = query.imdbId?.takeIf { it.isNotBlank() }
            ?: query.tmdbId?.takeIf { it > 0 }?.toString()
            ?: throw IllegalArgumentException("No TMDB/IMDb id available for subtitle search")
        val url = "${'$'}baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("id", id)
            .addQueryParameter("key", apiKey)
            .addQueryParameter("language", query.language)
            .apply {
                query.season?.let { addQueryParameter("season", it.toString()) }
                query.episode?.let { addQueryParameter("episode", it.toString()) }
            }
            .build()
        val response = withContext(Dispatchers.IO) { client.newCall(Request.Builder().url(url).build()).execute() }
        response.use {
            if (!it.isSuccessful) {
                val body = it.body?.string().orEmpty()
                if (it.code == 400 && body.contains("No subtitles found", true)) return@runCatching emptyList()
                throw IOException("Wyzie HTTP ${'$'}{it.code}")
            }
            json.decodeFromString<List<WyzieSubtitleDto>>(it.body?.string().orEmpty()).map { dto ->
                ExternalSubtitleResult(
                    id = dto.id.orEmpty(), provider = dto.source ?: "Wyzie",
                    language = dto.language.orEmpty(), displayName = dto.display ?: dto.language ?: "Unknown",
                    releaseName = dto.release, format = dto.format?.lowercase(),
                    hearingImpaired = dto.isHearingImpaired, downloadCount = dto.downloadCount,
                    fileName = dto.fileName, downloadUrl = dto.url,
                )
            }
        }
    }

    suspend fun download(result: ExternalSubtitleResult): Result<ExternalSubtitleFile> = runCatching {
        val url = result.downloadUrl ?: throw IllegalArgumentException("Subtitle has no download URL")
        val response = withContext(Dispatchers.IO) { client.newCall(Request.Builder().url(url).build()).execute() }
        response.use {
            if (!it.isSuccessful) throw IOException("Subtitle download HTTP ${'$'}{it.code}")
            val bytes = it.body?.bytes() ?: throw IOException("Empty subtitle response")
            ExternalSubtitleFile(
                bytes = bytes,
                fileName = result.fileName?.takeIf(String::isNotBlank)
                    ?: "streamvault-${'$'}{result.id}.${'$'}{result.format ?: "srt"}",
                format = result.format, language = result.language,
            )
        }
    }

    @Serializable
    private data class WyzieSubtitleDto(
        @SerialName("id") val id: String? = null,
        @SerialName("url") val url: String? = null,
        @SerialName("format") val format: String? = null,
        @SerialName("display") val display: String? = null,
        @SerialName("language") val language: String? = null,
        @SerialName("isHearingImpaired") val isHearingImpaired: Boolean = false,
        @SerialName("source") val source: String? = null,
        @SerialName("release") val release: String? = null,
        @SerialName("fileName") val fileName: String? = null,
        @SerialName("downloadCount") val downloadCount: Int? = null,
    )
}
