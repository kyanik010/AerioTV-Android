package com.aeriotv.android.feature.livetv

import com.aeriotv.android.core.data.ChannelCollection
import com.aeriotv.android.core.data.M3UChannel
import com.aeriotv.android.feature.playlist.PlaylistViewModel
import com.aeriotv.android.feature.playlist.SortMode

/**
 * E-1 stage 2 (perf campaign 2026-08-19): the ONE filter+sort pipeline behind
 * the Guide grid and the channel List. It is invoked from produceState on
 * Dispatchers.Default at both call sites.
 *
 * The hot path is allocation-light: lowercase name keys are lazy and are only
 * materialized when name ordering is actually required.
 */
internal fun computeDisplayChannels(
    channels: List<M3UChannel>,
    selectedGroup: String,
    searchQuery: String,
    sortMode: SortMode,
    allGroupNames: List<String>,
    groupSortMode: GroupSortMode,
    hiddenGroups: Set<String>,
    favoriteIds: Set<String>,
    collections: List<ChannelCollection>,
    recentIds: List<String> = emptyList(),
): List<M3UChannel> = GuideMemo.get(
    "displayChannels",
    listOf(
        GuideMemo.Ref(channels), selectedGroup, searchQuery, sortMode,
        GuideMemo.Ref(allGroupNames), groupSortMode, hiddenGroups, favoriteIds,
        GuideMemo.Ref(collections), recentIds,
    ),
) {
    computeDisplayChannelsUncached(
        channels, selectedGroup, searchQuery, sortMode,
        allGroupNames, groupSortMode, hiddenGroups, favoriteIds, collections, recentIds,
    )
}

private fun computeDisplayChannelsUncached(
    channels: List<M3UChannel>,
    selectedGroup: String,
    searchQuery: String,
    sortMode: SortMode,
    allGroupNames: List<String>,
    groupSortMode: GroupSortMode,
    hiddenGroups: Set<String>,
    favoriteIds: Set<String>,
    collections: List<ChannelCollection>,
    recentIds: List<String>,
): List<M3UChannel> {
    val query = searchQuery.trim()

    if (selectedGroup == PlaylistViewModel.RECENT_GROUP) {
        val rank = recentIds.withIndex().associate { (i, id) -> id to i }
        return channels.asSequence()
            .filter { it.id in rank }
            .filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) }
            .sortedBy { rank[it.id] ?: Int.MAX_VALUE }
            .toList()
    }

    val activeCollection = ChannelCollection.idFromToken(selectedGroup)
        ?.let { cid -> collections.firstOrNull { it.id == cid } }
    val collectionMembers = activeCollection?.memberIds?.toSet()
    val collectionSelected =
        selectedGroup.startsWith(ChannelCollection.TOKEN_PREFIX) &&
            allGroupNames.none { it == selectedGroup }
    val clusterByGroup = query.isEmpty() &&
        selectedGroup == PlaylistViewModel.ALL_GROUPS &&
        groupSortMode != GroupSortMode.Default
    val groupRankIndex = if (clusterByGroup) {
        allGroupNames.withIndex().associate { (i, g) -> g to i }
    } else {
        emptyMap()
    }

    val filtered = channels.asSequence()
        .filter { ch ->
            when {
                collectionSelected -> collectionMembers?.contains(ch.id) ?: true
                selectedGroup == PlaylistViewModel.FAVORITES_GROUP -> ch.id in favoriteIds
                selectedGroup != PlaylistViewModel.ALL_GROUPS ->
                    ch.groupTitle.equals(selectedGroup, ignoreCase = true)
                query.isNotEmpty() -> true
                else -> ch.groupTitle !in hiddenGroups
            }
        }
        .filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) }

    return when (sortMode) {
        SortMode.ByName -> filtered
            .map { ch ->
                DisplaySortKey(
                    channel = ch,
                    rank = if (clusterByGroup) groupRankIndex[ch.groupTitle] ?: Int.MAX_VALUE else 0,
                    number = ch.channelNumber?.toDoubleOrNull() ?: Double.MAX_VALUE,
                    favorite = false,
                )
            }
            .sortedWith(compareBy({ it.rank }, { it.nameLower }))
            .map { it.channel }
            .toList()

        SortMode.FavoritesFirst -> filtered
            .map { ch ->
                DisplaySortKey(
                    channel = ch,
                    rank = if (clusterByGroup) groupRankIndex[ch.groupTitle] ?: Int.MAX_VALUE else 0,
                    favorite = ch.id in favoriteIds,
                    number = ch.channelNumber?.toDoubleOrNull() ?: Double.MAX_VALUE,
                )
            }
            .sortedWith(compareBy({ it.rank }, { !it.favorite }, { it.number }, { it.nameLower }))
            .map { it.channel }
            .toList()

        SortMode.ByNumber -> filtered
            .map { ch ->
                DisplaySortKey(
                    channel = ch,
                    rank = if (clusterByGroup) groupRankIndex[ch.groupTitle] ?: Int.MAX_VALUE else 0,
                    number = ch.channelNumber?.toDoubleOrNull() ?: Double.MAX_VALUE,
                    favorite = false,
                )
            }
            .sortedWith(compareBy({ it.rank }, { it.number }, { it.nameLower }))
            .map { it.channel }
            .toList()
    }
}

/** Lowercase allocation is lazy; ByNumber still gets a deterministic name tie-break. */
private class DisplaySortKey(
    val channel: M3UChannel,
    val rank: Int,
    val number: Double,
    val favorite: Boolean,
) {
    val nameLower: String by lazy(LazyThreadSafetyMode.NONE) { channel.name.lowercase() }
}
