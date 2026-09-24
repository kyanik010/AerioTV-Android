package com.aeriotv.android.feature.activation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aeriotv.android.BuildConfig
import com.aeriotv.android.core.data.SourceType
import com.aeriotv.android.core.data.repository.PlaylistRepository
import com.aeriotv.android.feature.audio.AudioSourceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL
import java.util.Collections
import java.util.Locale

private enum class ActivationState { CHECKING, ACTIVATING, NOT_REGISTERED, ACTIVE, SUSPENDED, EXPIRED, ERROR }

@Composable
fun ActivationGate(
    configStore: ActivationConfigStore,
    playlistRepository: PlaylistRepository,
    audioSourceManager: AudioSourceManager,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activationId = remember { readActivationId(context) }
    var state by remember { mutableStateOf(ActivationState.CHECKING) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun check() {
        state = ActivationState.CHECKING
        errorText = null
        val result = withContext(Dispatchers.IO) {
            runCatching { requestActivation(activationId) }
        }
        result.onSuccess { response ->
            if (!response.activated) {
                state = when (response.status) {
                    "expired" -> ActivationState.EXPIRED
                    "suspended" -> ActivationState.SUSPENDED
                    else -> ActivationState.NOT_REGISTERED
                }
                return@onSuccess
            }

            val config = response.config
            if (config == null || config.video == null) {
                state = ActivationState.ERROR
                errorText = "بيانات التفعيل غير مكتملة"
                return@onSuccess
            }

            configStore.set(config)
            state = ActivationState.ACTIVATING

            val video = config.video

            // IMPORTANT: activation is a control-plane check, not a playlist
            // refresh. The old code called loadAndPersist() on every launch,
            // which made PlaylistRepository treat every app start as a source
            // save/refresh and defeated the existing Room channel cache.
            // Reuse the persisted playlist when the Supabase credentials have
            // not changed. Only rewrite it when the admin actually changed the
            // assigned Xtream subscription.
            val current = playlistRepository.activePlaylist()
            val currentMatches = current != null &&
                current.sourceType == SourceType.XtreamCodes.name &&
                current.urlString.trimEnd('/') == video.serverUrl.trimEnd('/') &&
                current.username.orEmpty() == video.username &&
                current.password.orEmpty() == video.password

            if (!currentMatches) {
                val videoResult = withContext(Dispatchers.IO) {
                    playlistRepository.loadAndPersist(
                        PlaylistRepository.SaveRequest(
                            sourceType = SourceType.XtreamCodes,
                            name = "Live TV",
                            url = video.serverUrl,
                            username = video.username,
                            password = video.password,
                            vodEnabled = true,
                        ),
                    )
                }

                if (videoResult.isFailure) {
                    state = ActivationState.ERROR
                    errorText = "تعذر تحميل خدمة الفيديو"
                    return@onSuccess
                }
            } else {
                // The playlist row and its channel snapshot remain intact.
                // PlaylistViewModel.bootstrap() will paint the cached channels
                // immediately and apply its normal 24h freshness gate.
            }

            val audioUrl = config.audio?.m3uUrl
            if (!audioUrl.isNullOrBlank()) {
                // AudioSourceManager owns its own persisted source/cache. Do not
                // force a network playlist load when the assigned M3U is the
                // same source already configured for this device.
                val audioResult = withContext(Dispatchers.IO) {
                    audioSourceManager.loadPlaylistIfChanged(audioUrl)
                }
                if (audioResult.isFailure) {
                    state = ActivationState.ERROR
                    errorText = "تعذر تحميل خدمة الصوت"
                    return@onSuccess
                }
            }

            state = ActivationState.ACTIVE
        }.onFailure {
            state = ActivationState.ERROR
            errorText = "تعذر الاتصال بخادم التفعيل"
        }
    }

    LaunchedEffect(activationId) {
        check()
        while (true) {
            delay(30_000)
            if (state != ActivationState.ACTIVE) check()
        }
    }

    if (state == ActivationState.ACTIVE) {
        content()
        return
    }

    ActivationScreen(
        activationId = activationId,
        state = state,
        errorText = errorText,
        onRetry = { scope.launch { check() } },
    )
}

@Composable
private fun ActivationScreen(
    activationId: String,
    state: ActivationState,
    errorText: String?,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current

    // These assets live in the separate mmm123 repository so the production
    // source tree stays clean. Coil loads them over HTTPS and falls back to the
    // existing built-in Aerio logo if the branding repository is temporarily
    // unavailable.
    val logoUrl = "https://raw.githubusercontent.com/kyanik010/mmm123/main/%D9%A2%D9%A0%D9%A2%D9%A6%D9%A0%D9%A9%D9%A2%D9%A4_%D9%A2%D9%A2%D9%A5%D9%A0%D9%A0%D9%A3.jpg"
    val qrUrl = "https://raw.githubusercontent.com/kyanik010/mmm123/main/chrome_qrcode_1790272750958.png"

    val statusText = when (state) {
        ActivationState.CHECKING -> "جاري التحقق من الجهاز"
        ActivationState.ACTIVATING -> "جاري تجهيز الخدمة"
        ActivationState.NOT_REGISTERED -> "هذا الجهاز غير مفعل"
        ActivationState.SUSPENDED -> "هذا الجهاز موقوف"
        ActivationState.EXPIRED -> "انتهى التفعيل"
        ActivationState.ERROR -> errorText ?: "تعذر التحقق من الجهاز"
        ActivationState.ACTIVE -> ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF071018),
                        Color(0xFF0B151F),
                        Color(0xFF05090D),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(108.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(24.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                coil.compose.AsyncImage(
                    model = logoUrl,
                    contentDescription = "شعار التطبيق",
                    placeholder = androidx.compose.ui.res.painterResource(com.aeriotv.android.R.drawable.aerio_logo),
                    error = androidx.compose.ui.res.painterResource(com.aeriotv.android.R.drawable.aerio_logo),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                        .clip(RoundedCornerShape(18.dp)),
                )
            }

            Text(
                text = "AerioTV",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )

            Text(
                text = "تفعيل الجهاز",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF63D8E8),
            )

            androidx.compose.material3.Card(
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.055f),
                ),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "معرّف الجهاز",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.62f),
                    )
                    Text(
                        text = activationId,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = Color.White,
                    )
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Device ID", activationId))
                            Toast.makeText(context, "تم نسخ معرّف الجهاز", Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Text("نسخ المعرّف")
                    }
                }
            }

            androidx.compose.material3.Card(
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = when (state) {
                        ActivationState.CHECKING, ActivationState.ACTIVATING ->
                            Color(0xFF63D8E8).copy(alpha = 0.10f)
                        ActivationState.ACTIVE -> Color(0xFF4CAF50).copy(alpha = 0.10f)
                        else -> Color(0xFFFFB74D).copy(alpha = 0.10f)
                    },
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state == ActivationState.CHECKING || state == ActivationState.ACTIVATING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        color = Color.White,
                    )
                }
            }

            if (state != ActivationState.CHECKING && state != ActivationState.ACTIVATING) {
                androidx.compose.material3.Card(
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.045f),
                    ),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "للتفعيل أو التواصل",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.70f),
                        )
                        coil.compose.AsyncImage(
                            model = qrUrl,
                            contentDescription = "باركود التواصل",
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            modifier = Modifier
                                .size(150.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(8.dp),
                        )
                    }
                }

                Button(
                    onClick = onRetry,
                    modifier = Modifier.widthIn(min = 220.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(if (state == ActivationState.ERROR) "إعادة المحاولة" else "إعادة التحقق")
                }
            }
        }
    }
}

private data class ActivationResponse(
    val activated: Boolean,
    val status: String?,
    val config: ManagedActivationConfig?,
)

private fun requestActivation(activationId: String): ActivationResponse {
    val connection = (URL(BuildConfig.DEVICE_ACTIVATION_URL).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 10_000
        readTimeout = 10_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("Accept", "application/json")
    }

    try {
        connection.outputStream.use {
            it.write(JSONObject().put("mac_address", activationId).toString().toByteArray(Charsets.UTF_8))
        }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
        val json = JSONObject(body)
        return ActivationResponse(
            activated = json.optBoolean("activated", false),
            status = json.optString("status").takeIf { it.isNotBlank() },
            config = json.optJSONObject("config")?.let { config ->
                val video = config.optJSONObject("video")?.let {
                    ManagedVideoConfig(
                        serverUrl = it.optString("server_url"),
                        username = it.optString("username"),
                        password = it.optString("password"),
                    )
                }
                val audio = config.optJSONObject("audio")?.let {
                    ManagedAudioConfig(
                        m3uUrl = it.optString("m3u_url").takeIf(String::isNotBlank),
                        serverUrl = it.optString("server_url").takeIf(String::isNotBlank),
                        username = it.optString("username").takeIf(String::isNotBlank),
                        password = it.optString("password").takeIf(String::isNotBlank),
                    )
                }
                ManagedActivationConfig(
                    expiresAt = json.optString("expires_at").takeIf(String::isNotBlank),
                    video = video,
                    audio = audio,
                )
            },
        )
    } finally {
        connection.disconnect()
    }
}

private fun readActivationId(context: Context): String {
    val candidates = listOf("wlan0", "eth0", "en0")
    for (name in candidates) {
        val mac = runCatching { NetworkInterface.getByName(name)?.hardwareAddress?.toMac() }.getOrNull()
        if (!mac.isNullOrBlank() && mac != "02:00:00:00:00:00") return mac
    }

    val interfaces = runCatching { Collections.list(NetworkInterface.getNetworkInterfaces()) }.getOrDefault(emptyList())
    for (networkInterface in interfaces) {
        val mac = runCatching { networkInterface.hardwareAddress?.toMac() }.getOrNull()
        if (!mac.isNullOrBlank() && mac != "02:00:00:00:00:00") return mac
    }

    // Android 10+ intentionally hides the factory Wi-Fi MAC from ordinary apps.
    // Keep the customer-facing activation format stable by deriving a persistent
    // MAC-shaped identifier from ANDROID_ID when the real hardware address is unavailable.
    val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        ?: Build.FINGERPRINT
    return macFromStableId(androidId)
}

private fun ByteArray.toMac(): String =
    joinToString(":") { byte -> "%02X".format(Locale.US, byte.toInt() and 0xFF) }

private fun macFromStableId(value: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
        .digest("aeriotv-android:$value".toByteArray(Charsets.UTF_8))
    val bytes = digest.copyOf(6)
    bytes[0] = (bytes[0].toInt() and 0xFC or 0x02).toByte()
    return bytes.toMac()
}
