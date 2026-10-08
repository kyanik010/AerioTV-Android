package com.aeriotv.android.feature.activation

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aeriotv.android.BuildConfig
import com.aeriotv.android.core.data.SourceType
import com.aeriotv.android.core.data.repository.PlaylistRepository
import com.aeriotv.android.feature.audio.AudioSourceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private enum class ActivationState { LOGIN, CHECKING, PENDING, ACTIVE, SUSPENDED, EXPIRED, ERROR }

private data class ActivationResponse(
    val activated: Boolean,
    val status: String?,
    val config: ManagedActivationConfig?,
)

@Composable
fun ActivationGate(
    configStore: ActivationConfigStore,
    playlistRepository: PlaylistRepository,
    audioSourceManager: AudioSourceManager,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("eagle_x_subscription", Context.MODE_PRIVATE) }
    var username by remember { mutableStateOf(prefs.getString("username", "").orEmpty()) }
    var password by remember { mutableStateOf(prefs.getString("password", "").orEmpty()) }
    var state by remember { mutableStateOf(if (username.isBlank() || password.isBlank()) ActivationState.LOGIN else ActivationState.CHECKING) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun check(forceRefresh: Boolean = false) {
        if (username.isBlank() || password.isBlank()) {
            state = ActivationState.LOGIN
            return
        }
        state = ActivationState.CHECKING
        errorText = null
        val result = withContext(Dispatchers.IO) { runCatching { requestActivation(username.trim(), password) } }
        result.onSuccess { response ->
            if (!response.activated) {
                state = when (response.status) {
                    "expired" -> ActivationState.EXPIRED
                    "suspended" -> ActivationState.SUSPENDED
                    else -> ActivationState.ERROR
                }
                errorText = when (response.status) {
                    "expired" -> "انتهى الاشتراك"
                    "suspended" -> "الاشتراك موقوف"
                    "ambiguous_credentials" -> "بيانات الاشتراك غير واضحة"
                    else -> "تعذر تفعيل الاشتراك"
                }
                return@onSuccess
            }

            prefs.edit().putString("username", username.trim()).putString("password", password).apply()
            val config = response.config
            if (config == null || config.video == null) {
                state = ActivationState.PENDING
                errorText = "تم حفظ بيانات الاشتراك. بانتظار إعداد الهوست من الإدارة."
                return@onSuccess
            }

            configStore.set(config)
            val video = config.video
            val current = playlistRepository.activePlaylist()
            val currentMatches = current != null &&
                current.sourceType == SourceType.XtreamCodes.name &&
                !forceRefresh &&
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
                            vodEnabled = false,
                        ),
                    )
                }
                if (videoResult.isFailure) {
                    state = ActivationState.ERROR
                    errorText = "تعذر تحميل خدمة الفيديو"
                    return@onSuccess
                }
            }

            state = ActivationState.ACTIVE
        }.onFailure {
            state = ActivationState.ERROR
            errorText = "تعذر الاتصال بخادم التفعيل"
        }
    }

    LaunchedEffect(username, password) {
        if (username.isNotBlank() && password.isNotBlank()) check()
    }

    LaunchedEffect(Unit) {
        ActivationRefreshBus.events.collectLatest {
            if (username.isNotBlank() && password.isNotBlank()) check(forceRefresh = true)
        }
    }

    if (state == ActivationState.ACTIVE) {
        content()
        return
    }

    ActivationScreen(
        state = state,
        username = username,
        password = password,
        errorText = errorText,
        onUsernameChange = { username = it },
        onPasswordChange = { password = it },
        onLogin = {
            if (username.isBlank() || password.isBlank()) {
                errorText = "أدخل Username وPassword."
                state = ActivationState.LOGIN
            } else scope.launch { check() }
        },
        onRetry = { scope.launch { check(forceRefresh = true) } },
        onChangeCredentials = {
            prefs.edit().clear().apply()
            username = ""
            password = ""
            errorText = null
            state = ActivationState.LOGIN
        },
    )
}

@Composable
private fun ActivationScreen(
    state: ActivationState,
    username: String,
    password: String,
    errorText: String?,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onRetry: () -> Unit,
    onChangeCredentials: () -> Unit,
) {
    val isLogin = state == ActivationState.LOGIN
    val isBusy = state == ActivationState.CHECKING
    val logoRes = com.aeriotv.android.R.drawable.eagle_x_activation_logo
    val qrRes = com.aeriotv.android.R.drawable.eagle_x_support_qr
    val statusText = when (state) {
        ActivationState.LOGIN -> "أدخل بيانات اشتراك IPTV"
        ActivationState.CHECKING -> "جاري التحقق من الاشتراك..."
        ActivationState.PENDING -> "بانتظار إعداد الاشتراك من الإدارة"
        ActivationState.SUSPENDED -> "الاشتراك موقوف"
        ActivationState.EXPIRED -> "انتهى الاشتراك"
        ActivationState.ERROR -> errorText ?: "تعذر التحقق من الاشتراك"
        ActivationState.ACTIVE -> ""
    }

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF050912), Color(0xFF0A1220), Color(0xFF03060B)))
        ).padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xCC0D1522)),
            border = BorderStroke(1.dp, Color(0x335BE7F2)),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(painterResource(id = logoRes), "Eagle X", Modifier.size(132.dp))
                Text("Eagle X", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Text(statusText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White, textAlign = TextAlign.Center)

                if (isLogin) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = onUsernameChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Username") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    )
                    Button(onClick = onLogin, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = !isBusy, shape = RoundedCornerShape(16.dp)) {
                        Text("دخول", fontWeight = FontWeight.Bold)
                    }
                } else {
                    if (!errorText.isNullOrBlank()) Text(errorText, color = Color(0xFFFF8094), textAlign = TextAlign.Center)
                    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = !isBusy, shape = RoundedCornerShape(16.dp)) {
                        Text("مزامنة / إعادة التحقق", fontWeight = FontWeight.Bold)
                    }
                    if (state == ActivationState.SUSPENDED || state == ActivationState.EXPIRED || state == ActivationState.ERROR) {
                        Button(onClick = onChangeCredentials, modifier = Modifier.fillMaxWidth().height(48.dp), enabled = !isBusy, shape = RoundedCornerShape(14.dp)) {
                            Text("تغيير بيانات الاشتراك")
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Image(painterResource(id = qrRes), "Support QR Code", Modifier.size(140.dp).padding(8.dp))
                }
            }
        }
    }
}

private fun requestActivation(username: String, password: String): ActivationResponse {
    val connection = (URL(BuildConfig.DEVICE_ACTIVATION_URL).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 15_000
        readTimeout = 120_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("Accept", "application/json")
    }
    try {
        connection.outputStream.use {
            it.write(JSONObject().put("username", username).put("password", password).toString().toByteArray(Charsets.UTF_8))
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
                    ManagedVideoConfig(it.optString("server_url"), it.optString("username"), it.optString("password"))
                }
                ManagedActivationConfig(config.optString("expires_at").takeIf(String::isNotBlank), video, null)
            },
        )
    } finally {
        connection.disconnect()
    }
}

