package com.aeriotv.android.feature.activation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
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
    var logo by remember { mutableStateOf<Bitmap?>(null) }
    var qrCode by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.coroutineScope {
            launch {
                logo = loadActivationAsset(
                    context,
                    "https://raw.githubusercontent.com/kyanik010/mmm123/main/%D9%A2%D9%A0%D9%A2%D9%A6%D9%A0%D9%A9%D9%A2%D9%A4_%D9%A2%D9%A2%D9%A5%D9%A0%D9%A0%D9%A3.jpg",
                    "eagle_x_logo.jpg",
                )
            }
            launch {
                qrCode = loadActivationAsset(
                    context,
                    "https://raw.githubusercontent.com/kyanik010/mmm123/main/chrome_qrcode_1790272750958.png",
                    "eagle_x_support_qr.png",
                )
            }
        }
    }

    val statusText = when (state) {
        ActivationState.CHECKING -> "جاري التحقق من الجهاز..."
        ActivationState.ACTIVATING -> "جاري تجهيز الاشتراك..."
        ActivationState.NOT_REGISTERED -> "هذا الجهاز غير مفعّل"
        ActivationState.SUSPENDED -> "هذا الجهاز موقوف"
        ActivationState.EXPIRED -> "انتهى تفعيل هذا الجهاز"
        ActivationState.ERROR -> errorText ?: "تعذر التحقق من الجهاز"
        ActivationState.ACTIVE -> ""
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF07090D))
                .padding(horizontal = 24.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (logo != null) {
                    Image(
                        bitmap = logo!!.asImageBitmap(),
                        contentDescription = "Eagle X",
                        modifier = Modifier.size(96.dp),
                    )
                } else {
                    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                }

                Text(
                    text = "Eagle X",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF11151C)),
                    border = BorderStroke(1.dp, Color(0xFF252B35)),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "MAC ADDRESS",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8F9AAA),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = activationId,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                textAlign = TextAlign.Start,
                            )
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("MAC Address", activationId))
                                    Toast.makeText(context, "تم نسخ العنوان", Toast.LENGTH_SHORT).show()
                                },
                            ) {
                                Text("نسخ")
                            }
                        }
                    }
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = "للتفعيل أو الحصول على اشتراك IPTV، تواصل مع الدعم عبر مسح رمز QR",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB8C0CC),
                    textAlign = TextAlign.Center,
                )

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    if (qrCode != null) {
                        Image(
                            bitmap = qrCode!!.asImageBitmap(),
                            contentDescription = "Support QR Code",
                            modifier = Modifier.size(156.dp).padding(8.dp),
                        )
                    } else {
                        Box(Modifier.size(156.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }

                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = state != ActivationState.CHECKING && state != ActivationState.ACTIVATING,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        text = if (state == ActivationState.ERROR) "إعادة المحاولة" else "إعادة التحقق",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

private suspend fun loadActivationAsset(
    context: Context,
    url: String,
    cacheName: String,
): Bitmap? = withContext(Dispatchers.IO) {
    val cacheFile = java.io.File(context.cacheDir, cacheName)
    runCatching {
        if (cacheFile.exists() && cacheFile.length() > 0L) {
            BitmapFactory.decodeFile(cacheFile.absolutePath)
        } else null
    }.getOrNull()?.let { return@withContext it }

    runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("Accept", "image/*")
        }
        try {
            if (connection.responseCode !in 200..299) return@runCatching null
            connection.inputStream.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }
            BitmapFactory.decodeFile(cacheFile.absolutePath)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
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
