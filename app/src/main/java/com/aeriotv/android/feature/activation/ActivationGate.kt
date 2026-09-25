package com.aeriotv.android.feature.activation

import android.content.ClipData
import android.content.ClipboardManager
import android.app.Activity
import android.content.Context
import android.os.Build
import android.content.res.Configuration
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.aeriotv.android.BuildConfig
import com.aeriotv.android.core.data.SourceType
import com.aeriotv.android.core.preferences.AppLanguage
import com.aeriotv.android.core.preferences.LanguageManager
import com.aeriotv.android.core.preferences.LocalAppLanguage
import com.aeriotv.android.core.data.repository.PlaylistRepository
import com.aeriotv.android.feature.audio.AudioSourceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
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
    val currentState by rememberUpdatedState(state)

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
        while (isActive) {
            delay(30_000)
            if (currentState != ActivationState.ACTIVE) check()
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
    val language = LocalAppLanguage.current
    val isArabic = language == AppLanguage.ARABIC
    val logoRes = com.aeriotv.android.R.drawable.eagle_x_activation_logo
    val qrRes = com.aeriotv.android.R.drawable.eagle_x_support_qr
    var showLanguageMenu by remember { mutableStateOf(false) }
    val isTv = (LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
        Configuration.UI_MODE_TYPE_TELEVISION

    val statusText = when (state) {
        ActivationState.CHECKING -> if (isArabic) "جاري التحقق من الجهاز..." else "Checking device..."
        ActivationState.ACTIVATING -> if (isArabic) "جاري تجهيز الاشتراك..." else "Preparing subscription..."
        ActivationState.NOT_REGISTERED -> if (isArabic) "هذا الجهاز غير مفعّل" else "This device is not activated"
        ActivationState.SUSPENDED -> if (isArabic) "هذا الجهاز موقوف" else "This device is suspended"
        ActivationState.EXPIRED -> if (isArabic) "انتهى تفعيل هذا الجهاز" else "This device activation has expired"
        ActivationState.ERROR -> if (isArabic) (errorText ?: "تعذر التحقق من الجهاز") else "Unable to verify the device"
        ActivationState.ACTIVE -> ""
    }

    Box(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF050912), Color(0xFF0A1220), Color(0xFF03060B))))
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = if (isTv) 48.dp else 20.dp,
                vertical = if (isTv) 28.dp else 16.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.widthIn(max = if (isTv) 820.dp else 620.dp).fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xCC0D1522)),
            border = BorderStroke(1.dp, Color(0x335BE7F2)),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = if (isTv) 34.dp else 22.dp,
                    vertical = if (isTv) 24.dp else 20.dp,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isTv) 8.dp else 10.dp),
            ) {
                Image(
                    painter = painterResource(id = logoRes),
                    contentDescription = "Eagle X",
                    modifier = Modifier.size(if (isTv) 126.dp else 148.dp),
                )
                Text("Eagle X", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)

                Box {
                    OutlinedButton(
                        onClick = { showLanguageMenu = true },
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("اللغة", fontWeight = FontWeight.SemiBold) }
                    DropdownMenu(
                        expanded = showLanguageMenu,
                        onDismissRequest = { showLanguageMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("العربية") },
                            onClick = {
                                LanguageManager.set(context, AppLanguage.ARABIC)
                                showLanguageMenu = false
                                (context as? Activity)?.recreate()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("English") },
                            onClick = {
                                LanguageManager.set(context, AppLanguage.ENGLISH)
                                showLanguageMenu = false
                                (context as? Activity)?.recreate()
                            },
                        )
                    }
                }

                Text(
                    text = if (isArabic) "عنوان MAC" else "MAC ADDRESS",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFB8C0CC),
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x332C3C4D)),
                    border = BorderStroke(1.dp, Color(0x556BE7F2)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            activationId,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("MAC Address", activationId))
                                Toast.makeText(
                                    context,
                                    if (isArabic) "تم نسخ العنوان" else "MAC address copied",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                        ) { Text(if (isArabic) "نسخ" else "Copy") }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x44131D29)),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            statusText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Text(
                    text = if (isArabic) {
                        "للتفعيل أو الحصول على اشتراك IPTV\nتواصل مع الدعم عبر مسح رمز QR"
                    } else {
                        "To activate or get an IPTV subscription\ncontact support by scanning the QR code"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFD1D7E0),
                    textAlign = TextAlign.Center,
                )

                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Image(
                        painter = painterResource(id = qrRes),
                        contentDescription = "Support QR Code",
                        modifier = Modifier.size(if (isTv) 150.dp else 176.dp).padding(8.dp),
                    )
                }

                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().height(if (isTv) 58.dp else 52.dp),
                    enabled = state != ActivationState.CHECKING && state != ActivationState.ACTIVATING,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        if (isArabic) "إعادة التحقق" else "Verify Again",
                        fontWeight = FontWeight.Bold,
                    )
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
                    expiresAt = config.optString("expires_at").takeIf(String::isNotBlank),
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
