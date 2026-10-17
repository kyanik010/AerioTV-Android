package com.aeriotv.android.core.subtitles

data class ExternalSubtitleQuery(
    val title: String,
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val language: String = "ar",
)

data class ExternalSubtitleResult(
    val id: String,
    val provider: String,
    val language: String,
    val displayName: String,
    val releaseName: String?,
    val format: String?,
    val hearingImpaired: Boolean,
    val downloadCount: Int?,
    val fileName: String?,
    val downloadUrl: String?,
)

data class ExternalSubtitleFile(
    val bytes: ByteArray,
    val fileName: String,
    val format: String?,
    val language: String,
) {
    override fun equals(other: Any?): Boolean =
        other is ExternalSubtitleFile &&
            fileName == other.fileName &&
            format == other.format &&
            language == other.language &&
            bytes.contentEquals(other.bytes)

    override fun hashCode(): Int =
        (((bytes.contentHashCode() * 31) + fileName.hashCode()) * 31 +
            (format?.hashCode() ?: 0)) * 31 + language.hashCode()
}
