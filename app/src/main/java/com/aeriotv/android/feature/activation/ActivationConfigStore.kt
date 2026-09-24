package com.aeriotv.android.feature.activation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class ManagedVideoConfig(
    val serverUrl: String,
    val username: String,
    val password: String,
)

data class ManagedAudioConfig(
    val m3uUrl: String? = null,
    val serverUrl: String? = null,
    val username: String? = null,
    val password: String? = null,
)

data class ManagedActivationConfig(
    val expiresAt: String?,
    val video: ManagedVideoConfig?,
    val audio: ManagedAudioConfig?,
)

@Singleton
class ActivationConfigStore @Inject constructor() {
    private val _config = MutableStateFlow<ManagedActivationConfig?>(null)
    val config: StateFlow<ManagedActivationConfig?> = _config.asStateFlow()

    fun set(config: ManagedActivationConfig) {
        _config.value = config
    }

    fun clear() {
        _config.value = null
    }
}
