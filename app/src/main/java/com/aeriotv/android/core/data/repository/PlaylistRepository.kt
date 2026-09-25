package com.aeriotv.android.core.data.repository

import com.aeriotv.android.core.data.EPGProgramme
import com.aeriotv.android.core.data.M3UChannel
import com.aeriotv.android.core.data.SourceType
import androidx.room.withTransaction
import com.aeriotv.android.core.data.db.dao.ChannelSnapshotDao
import com.aeriotv.android.core.data.db.dao.EpgChunkCoverageDao
import com.aeriotv.android.core.data.db.dao.EpgProgrammeDao
import com.aeriotv.android.core.data.db.dao.PlaylistDao
import com.aeriotv.android.core.data.db.entity.ChannelSnapshotEntity
import com.aeriotv.android.core.data.db.entity.EpgProgrammeEntity
import com.aeriotv.android.core.data.db.entity.PlaylistEntity
import com.aeriotv.android.core.data.db.entity.dispatcharrVersionAtLeast
import com.aeriotv.android.core.data.db.entity.capabilitiesNeedProbe
import com.aeriotv.android.core.data.db.entity.isDispatcharrDirectConnect
import com.aeriotv.android.core.data.capability.CAPABILITIES_SCHEMA
import com.aeriotv.android.core.data.capability.Capability
import com.aeriotv.android.core.data.capability.CapabilityCorrections
import com.aeriotv.android.core.data.capability.CapabilityState
import com.aeriotv.android.core.data.capability.parseCustomProperties
import com.aeriotv.android.core.data.db.entity.capabilities
import com.aeriotv.android.core.data.db.entity.dispatcharrEffectiveDvrAccess
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import com.aeriotv.android.core.data.db.entity.dispatcharrAccountProfileIdList
import com.aeriotv.android.core.data.db.entity.dispatcharrCanUseCatchup
import com.aeriotv.android.core.data.db.entity.EPG_CHUNK_TTL_MS
import com.aeriotv.android.core.data.db.entity.EpgChunkCoverage
import com.aeriotv.android.core.data.db.entity.sanitizeGuideDays
import com.aeriotv.android.core.guide.guideChannelId
import com.aeriotv.android.core.data.db.entity.resolveGuideDays
import com.aeriotv.android.core.data.db.entity.GUIDE_DAYS_ALL_MAX_BACK
import com.aeriotv.android.core.data.db.entity.GUIDE_DAYS_ALL_MAX_AHEAD
import android.content.Context
import android.util.Log
import com.aeriotv.android.core.network.DispatcharrAuthBroker
import com.aeriotv.android.core.network.DispatcharrClient
import com.aeriotv.android.core.network.LanReachability
import com.aeriotv.android.core.network.DispatcharrChannel
import com.aeriotv.android.core.network.DispatcharrEpgData
import com.aeriotv.android.core.network.DispatcharrEpgEntry
import com.aeriotv.android.core.network.DispatcharrEpgSource
import com.aeriotv.android.core.network.DispatcharrTokenStore
import com.aeriotv.android.core.network.PlaylistFetcher
import com.aeriotv.android.core.parser.M3UParser
import com.aeriotv.android.core.parser.XMLTVParser
import com.aeriotv.android.core.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/**
 * Single source of truth for playlist persistence + fetch + parse.
 * Mirrors how iOS Aerio handles playlists: row stored in SwiftData, channels
 * re-parsed from source on every refresh (NOT cached individually).
 *
 * Derives the M3U URL, EPG URL, and HTTP headers from the playlist's
 * [PlaylistEntity.sourceType]:
 *  - M3uUrl: use [PlaylistEntity.urlString] directly + optional [PlaylistEntity.epgUrl].
 *  - DispatcharrApiKey: M3U = `${urlString}/output/m3u`, EPG = `${urlString}/output/epg`,
 *    headers = `{X-API-Key: ${apiKey}, Accept: application/json}` (Phase 4a).
 *  - DispatcharrUserPass: log in to /api/auth/token/, exchange the JWT access
 *    token for the user's API key via /api/accounts/users/me/, then proceed
 *    exactly like DispatcharrApiKey. Mirrors iOS DispatcharrDirectConnect's
 *    silent-rebootstrap pattern.
 *  - XtreamCodes: TODO Phase 4c (player_api.php enumeration -> get.php m3u_plus).
 */
/** GH #31: channel-snapshot insert batch size. Small enough that one chunk's
 *  entities are trivially cheap against the heap, large enough that per-chunk
 *  SQLite bind overhead stays negligible across a ~100k-row XC catalog. */
private const val CHANNEL_CACHE_CHUNK = 2_000

/** Ceiling on one EPG load (network + parse). Wide enough for a multi-hundred
 *  MB provider XMLTV on a slow link; exists so a wedged load releases the
 *  in-flight latch in [PlaylistRepository.loadEpg] instead of blocking every
 *  future EPG refresh until the process dies. */
private const val EPG_LOAD_TIMEOUT_MS = 5L * 60L * 1000L

/** Upstream Dispatcharr XMLTV feeds to layer for catch-up depth (task #210).
 *  Was 8. A Direct Connect server can list many sources, and each one is a
 *  FULL XMLTV download; the value of the 4th feed is negligible next to the
 *  cost of fetching it on every EPG load. */
private const val MAX_UPSTREAM_EPG_SOURCES = 3

/** Ceiling on ONE upstream feed. A single slow or enormous XMLTV must not be
 *  able to stall the EPG load behind it. */
// Raised 90s -> 5min (2026-09-01): the parse now runs on the background-
// priority EpgWork pool, so a long parse no longer competes with playback;
// at 90s the 224MB national feed kept 6,575 programmes and dropped the rest.
private const val UPSTREAM_EPG_PER_SOURCE_MS = 5L * 60L * 1000L

/** Ceiling on the whole upstream-layering phase. This is bonus history, not
 *  the user's guide: past this the grid ships as-is. */
private const val UPSTREAM_EPG_TOTAL_BUDGET_MS = 12L * 60L * 1000L

/** Orphaned download temp files older than this are swept at startup. The
 *  download path deletes its temp in a `finally`, but a process death mid
 *  download (force-stop while "syncing", low-memory kill) skips that entirely,
 *  and each orphan is the full size of whatever was being fetched. The Discord
 *  report of a 4GB app cache was exactly this, repeated. */
private const val ORPHAN_TEMP_MAX_AGE_MS = 60L * 60L * 1000L

/**
 * Quiet EPG sweep pacing (Logan 2026-09-12). The sweep exists to correct a
 * cache that is still being served, so every number here is chosen to be
 * unnoticeable rather than fast:
 *  - settle: how long after launch / foreground return before the first
 *    request, giving the guide time to paint from cache and any tune time to
 *    reach its first frame,
 *  - gap: the pause between two day chunks,
 *  - poll: how often a paused sweep re-asks [EpgSweepGate],
 *  - interval: the foreground re-check gate on the sources fingerprint.
 */
const val EPG_SWEEP_SETTLE_MS: Long = 20_000L
const val EPG_SWEEP_CHUNK_GAP_MS: Long = 1_500L
const val EPG_SWEEP_PAUSE_POLL_MS: Long = 2_000L
const val EPG_SOURCES_CHECK_INTERVAL_MS: Long = 15L * 60L * 1000L

/** E-6: minimum gap between EPG retention sweeps for one playlist. Upstream
 *  layering saves once per source; the cutoff is days out, so re-pruning
 *  seconds later only costs a full-table DELETE scan. */
private const val RETENTION_SWEEP_COOLDOWN_MS = 5L * 60L * 1000L

@Singleton
class PlaylistRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: PlaylistDao,
    private val fetcher: PlaylistFetcher,
    private val dispatcharrClient: DispatcharrClient,
    private val dispatcharrAuth: DispatcharrAuthBroker,
    private val dispatcharrTokenStore: DispatcharrTokenStore,
    private val appPreferences: AppPreferences,
    private val epgProgrammeDao: EpgProgrammeDao,
    private val epgChunkCoverageDao: EpgChunkCoverageDao,
    private val channelSnapshotDao: ChannelSnapshotDao,
    private val database: com.aeriotv.android.core.data.db.AerioDatabase,
    private val activeCredentials: com.aeriotv.android.core.network.ActivePlaylistCredentials,
    private val lanReachability: LanReachability,
    private val xtreamApi: com.aeriotv.android.core.network.XtreamCodesApi,
    private val timeshiftController: com.aeriotv.android.core.timeshift.TimeshiftController,
    private val catchupResolver: com.aeriotv.android.core.playback.CatchupPlaybackResolver,
    private val vodCatalogStore: com.aeriotv.android.core.data.vod.VodCatalogStore,
    private val vodSnapshotStore: com.aeriotv.android.core.preferences.VodLibrarySnapshotStore,
) {

    private fun publishActiveCredentials(playlist: PlaylistEntity?) {
        com.aeriotv.android.core.playback.DispatcharrConnectionLimit.directConnectActive =
            playlist?.sourceType == SourceType.DispatcharrApiKey.name ||
                playlist?.sourceType == SourceType.DispatcharrUserPass.name
        if (playlist == null) {
            activeCredentials.clear()
            return
        }
        val prefixes = listOfNotNull(
            playlist.urlString.takeIf { it.isNotBlank() },
            playlist.lanUrlString?.takeIf { it.isNotBlank() },
        )
        activeCredentials.set(prefixes, playlist.apiKey)
    }

    /**
     * Returns [PlaylistEntity.lanUrlString] when the server actually answers
     * at that address; otherwise [PlaylistEntity.urlString]. Replaced the old
     * home-SSID match (fine-location permission + per-device saved networks
     * that never synced, so fresh installs silently routed WAN) with a cached
     * reachability probe; see [LanReachability] for the trigger points.
     */
    suspend fun effectiveBaseUrl(playlist: PlaylistEntity): String {
        dispatcharrClient.seedAuthMode(playlist)
        val lan = playlist.lanUrlString?.takeIf { it.isNotBlank() } ?: return playlist.urlString
        return if (lanReachability.isReachable(lan)) lan else playlist.urlString
    }

    val lanVerdictFlips: kotlinx.coroutines.flow.SharedFlow<String> =
        lanReachability.verdictFlips

    suspend fun reprobeActiveBase(): String? {
        val pl = activePlaylist() ?: return null
        pl.lanUrlString?.takeIf { it.isNotBlank() }?.let { lanReachability.refresh(it) }
        return effectiveBaseUrl(pl)
    }

    suspend fun rebuildLiveStreamUrl(channelUuid: String): String? {
        val pl = activePlaylist() ?: return null
        val sourceType = pl.resolvedSourceType()
        val isDispatcharr = sourceType == SourceType.DispatcharrApiKey ||
            sourceType == SourceType.DispatcharrUserPass
        if (!isDispatcharr) return null
        val base = effectiveBaseUrl(pl)
        return dispatcharrClient.streamUrl(base, channelUuid)
    }

    data class SaveRequest(
        val sourceType: SourceType,
        val name: String?,
        val url: String,
        val lanUrl: String? = null,
        val epgUrl: String? = null,
        val apiKey: String? = null,
        val username: String? = null,
        val password: String? = null,
        val dispatcharrProfileId: Int? = null,
        val vodEnabled: Boolean = true,
        val epgRetentionDays: Int = 7,
    )

    suspend fun activePlaylist(): PlaylistEntity? {
        val pl = dao.firstActive()
        publishActiveCredentials(pl)
        return pl
    }

    suspend fun loadAndPersist(
        request: SaveRequest,
        existingId: String? = null,
    ): Result<Pair<PlaylistEntity, List<M3UChannel>>> = runCatching {
        val normalisedBase = request.url.trimEnd('/')
        val sourceType = request.sourceType
        if (!sourceType.isImplemented) throw UnsupportedOperationException("${sourceType.displayName} support lands in a later phase")
        val playlistId = existingId ?: UUID.randomUUID().toString()
        val priorRow = existingId?.let { dao.byId(it) }
        val credentialsChanged = priorRow != null && run {
            val suppliedKey = request.apiKey?.trim()?.takeIf { it.isNotBlank() }
            val keyChanged = suppliedKey != null && suppliedKey != priorRow.apiKey?.trim()?.takeIf { it.isNotBlank() }
            val userChanged = request.username?.takeIf { it.isNotBlank() } != priorRow.username?.takeIf { it.isNotBlank() }
            val passChanged = request.password?.takeIf { it.isNotBlank() } != priorRow.password?.takeIf { it.isNotBlank() }
            keyChanged || userChanged || passChanged
        }
        if (credentialsChanged) dropCachedIdentity(priorRow!!)
        val resolvedApiKey: String? = when (sourceType) {
            SourceType.DispatcharrUserPass -> {
                val suppliedKey = request.apiKey?.takeIf { it.isNotBlank() }
                val u = request.username?.takeIf { it.isNotBlank() }
                val p = request.password?.takeIf { it.isNotBlank() }
                if (suppliedKey != null && (u == null || p == null)) suppliedKey
                else {
                    val user = u ?: throw IllegalArgumentException("Username is required")
                    val pass = p ?: throw IllegalArgumentException("Password is required")
                    val jwt = dispatcharrClient.login(normalisedBase, user, pass)
                    dispatcharrTokenStore.store(playlistId, jwt.access, jwt.refresh)
                    dispatcharrClient.fetchCurrentUserApiKey(normalisedBase, jwt.access)
                }
            }
            else -> request.apiKey
        }
        val accountProfileIds: List<Int> = if (sourceType == SourceType.DispatcharrApiKey || sourceType == SourceType.DispatcharrUserPass) {
            resolvedApiKey?.takeIf { it.isNotBlank() }?.let { dispatcharrClient.fetchCurrentUserProfileIds(normalisedBase, it) } ?: emptyList()
        } else emptyList()
        val previous = existingId?.let { dao.byId(it) }
        if (previous != null) {
            dao.update(previous.copy(
                name = request.name?.takeIf { it.isNotBlank() } ?: previous.name,
                urlString = normalisedBase,
                lanUrlString = request.lanUrl?.trimEnd('/')?.takeIf { it.isNotBlank() },
                epgUrl = request.epgUrl?.takeIf { it.isNotBlank() },
                apiKey = resolvedApiKey?.takeIf { it.isNotBlank() } ?: previous.apiKey.takeUnless { credentialsChanged },
                username = request.username?.takeIf { it.isNotBlank() },
                password = request.password?.takeIf { it.isNotBlank() },
                dispatcharrProfileId = request.dispatcharrProfileId,
                vodEnabled = request.vodEnabled,
                epgRetentionDays = sanitizeGuideDays(request.epgRetentionDays),
            ))
        }
        val isDispatcharr = sourceType == SourceType.DispatcharrApiKey || sourceType == SourceType.DispatcharrUserPass
        val perms = if (isDispatcharr) resolvedApiKey?.takeIf { it.isNotBlank() }?.let { dispatcharrClient.fetchAccountPermissions(normalisedBase, it) } else null
        val serverVersion = if (isDispatcharr) resolvedApiKey?.takeIf { it.isNotBlank() }?.let { dispatcharrClient.fetchServerVersion(normalisedBase, it) } else null
        val channels = try {
            fetchChannelsFor(sourceType, normalisedBase, request.epgUrl, resolvedApiKey, request.dispatcharrProfileId, accountProfileIds, request.username, request.password, perms?.catchupEnabled ?: true)
        } catch (t: Throwable) {
            if (previous != null) runCatching { dao.update(previous) }
            throw t
        }
        val dispatcharrUserLevel: Int = if (isDispatcharr) resolvedApiKey?.takeIf { it.isNotBlank() }?.let { dispatcharrClient.fetchUserLevel(normalisedBase, it) } ?: 10 else 10
        val entity = PlaylistEntity(
            id = playlistId,
            name = request.name?.takeIf { it.isNotBlank() } ?: deriveName(normalisedBase),
            urlString = normalisedBase,
            lanUrlString = request.lanUrl?.trimEnd('/')?.takeIf { it.isNotBlank() },
            epgUrl = request.epgUrl?.takeIf { it.isNotBlank() },
            sourceType = sourceType.name,
            apiKey = resolvedApiKey?.takeIf { it.isNotBlank() },
            username = request.username?.takeIf { it.isNotBlank() },
            password = request.password?.takeIf { it.isNotBlank() },
            channelCount = channels.size,
            lastRefreshedAt = System.currentTimeMillis(),
            isActive = true,
            dispatcharrProfileId = request.dispatcharrProfileId,
            dispatcharrUserLevel = dispatcharrUserLevel,
            dispatcharrAccountProfileIds = accountProfileIds.joinToString(","),
            dispatcharrDvrAccess = perms?.dvrAccess ?: "",
            dispatcharrCatchupEnabled = perms?.catchupEnabled ?: true,
            dispatcharrVodMoviesEnabled = perms?.vodMoviesEnabled ?: true,
            dispatcharrVodSeriesEnabled = perms?.vodSeriesEnabled ?: true,
            dispatcharrServerVersion = serverVersion ?: "",
            vodEnabled = request.vodEnabled,
            epgRetentionDays = sanitizeGuideDays(request.epgRetentionDays),
        )
        if (existingId == null || dao.byId(existingId)?.isActive != true) dao.upsertAsActive(entity) else dao.upsert(entity)
        cacheScope.launch { runCatching { saveChannelsToCache(playlistId, channels) } }
        publishActiveCredentials(entity)
        runCatching { probeCapabilities(playlistId, force = true) }
        entity to channels
    }

    /**
     * Re-fetch channels for an existing playlist row, updating channelCount and
     * lastRefreshedAt without changing identity fields.
     */
    suspend fun refresh(playlist: PlaylistEntity): Result<List<M3UChannel>> = runCatching {
        val sourceType = playlist.resolvedSourceType()
        val base = effectiveBaseUrl(playlist)
        val liveAccountIds: List<Int>? = if (sourceType == SourceType.DispatcharrApiKey || sourceType == SourceType.DispatcharrUserPass) {
            playlist.apiKey?.takeIf { it.isNotBlank() }?.let { dispatcharrClient.fetchCurrentUserProfileIds(base, it) }
        } else null
        val effectiveAccountIds = liveAccountIds ?: playlist.dispatcharrAccountProfileIdList()
        val probed = if (playlist.isDispatcharrDirectConnect()) runCatching { probeCapabilities(playlist.id, force = true) }.getOrDefault(false) else false
        val fresh = if (probed) dao.byId(playlist.id) ?: playlist else playlist
        val liveVersion = if (probed) playlist.apiKey?.takeIf { it.isNotBlank() }?.let { dispatcharrClient.fetchServerVersion(base, it) } else null
        val channels = when (sourceType) {
            SourceType.DispatcharrApiKey, SourceType.DispatcharrUserPass -> dispatcharrAuth.withApiKeyRetry(playlist.id) { key -> fetchChannelsFor(sourceType, base, playlist.epgUrl, key, playlist.dispatcharrProfileId, effectiveAccountIds, catchupEnabled = fresh.dispatcharrCanUseCatchup()) }
            else -> fetchChannelsFor(sourceType, base, playlist.epgUrl, playlist.apiKey, playlist.dispatcharrProfileId, emptyList(), playlist.username, playlist.password)
        }
        val refreshed = fresh.copy(channelCount = channels.size, lastRefreshedAt = System.currentTimeMillis(), dispatcharrAccountProfileIds = if (liveAccountIds != null) liveAccountIds.joinToString(",") else fresh.dispatcharrAccountProfileIds, dispatcharrServerVersion = liveVersion ?: fresh.dispatcharrServerVersion)
        dao.update(refreshed)
        try { saveChannelsToCache(playlist.id, channels) } catch (_: Throwable) { }
        publishActiveCredentials(refreshed)
        channels
    }

    private suspend fun xtreamLiveChannels(b: String, user: String, pass: String): List<M3UChannel> {
        val categoryNames = runCatching {
            xtreamApi.getLiveCategories(b, user, pass).associate { it.id to it.name }
        }.getOrDefault(emptyMap())
        val streams = xtreamApi.getLiveStreams(b, user, pass)
        if (streams.isEmpty()) throw IllegalStateException("The server returned no live channels for these credentials. Check the username and password, and that this device is allowed to connect.")
        android.util.Log.i("PlaylistRepository", "XC live list: ${streams.size} channels from player_api, ${categoryNames.size} categories, ${streams.count { it.epgChannelId.isNotBlank() }} with EPG ids")
        val directSourceCount = streams.count { it.directSource.isNotBlank() }
        if (directSourceCount > streams.size / 2) android.util.Log.w("PlaylistRepository", "XC panel publishes direct_source on $directSourceCount/${streams.size} channels; if channels list but do not play, this panel may be relying on direct_source semantics.")
        val groups = streams.groupBy { it.categoryId }.mapValues { (categoryId, items) -> categoryNames[categoryId] ?: if (categoryId == "0") "Uncategorized" else "Category $categoryId" }
        return streams.map { stream ->
            val group = groups[stream.categoryId] ?: "Uncategorized"
            M3UChannel(id = stream.id, name = stream.name, logoUrl = stream.streamIcon, groupTitle = group, epgId = stream.epgChannelId, url = stream.streamUrl, catchupType = stream.tvArchiveType, catchupDays = stream.tvArchiveDuration, catchupSource = stream.tvArchiveSource, tvgName = stream.epgChannelId, tvgId = stream.epgChannelId, sourceType = SourceType.XtreamCodes)
        }
    }

    private fun deriveName(url: String): String = url.substringAfter("://").substringBefore('/').ifBlank { "Playlist" }
}