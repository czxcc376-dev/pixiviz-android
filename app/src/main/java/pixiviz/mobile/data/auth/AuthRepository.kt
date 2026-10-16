package pixiviz.mobile.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import pixiviz.mobile.data.remote.RelayConfig
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64

/**
 * Device-side pixiv sign-in.
 *
 * The refresh token never leaves the device unsealed, and the OAuth client secret never
 * enters the APK: the authorization-code / refresh exchanges are performed by the Worker
 * through `POST /auth/token`, which is the only place that knows the client credentials.
 */
class AuthRepository(
    private val tokenStore: SecureTokenStore,
    private val config: RelayConfig,
    private val client: OkHttpClient,
    private val json: Json,
) {

    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /** Generates a PKCE verifier plus the pixiv login URL that consumes its challenge. */
    fun beginLogin(): Pair<String, String> {
        val verifier = randomUrlSafe(64)
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val url = "https://app-api.pixiv.net/web/v1/login" +
            "?code_challenge=$challenge&code_challenge_method=S256&client=pixiv-android"
        return verifier to url
    }

    /** Pulls `code` out of the callback URL the WebView lands on. */
    fun extractCode(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        Regex("[?&]code=([^&\\s]+)").find(text)?.let { return it.groupValues[1] }
        return if (text.matches(Regex("[A-Za-z0-9._~-]+")) && text.length > 8) text else null
    }

    suspend fun exchangeCode(code: String, verifier: String): Result<Unit> = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("grant_type", "authorization_code")
            put("code", code)
            put("code_verifier", verifier)
        }
        requestToken(payload).map { tokens ->
            tokens.refreshToken?.let { tokenStore.saveRefreshToken(it) }
            config.accessToken = tokens.accessToken
            _lastError.value = null
            _signedIn.value = tokens.accessToken != null
        }
    }

    /** Restores a session from the sealed refresh token, if any. */
    suspend fun restore(): Boolean = withContext(Dispatchers.IO) {
        val refreshToken = tokenStore.readRefreshToken()
        if (refreshToken.isNullOrBlank()) {
            _signedIn.value = false
            return@withContext false
        }
        if (config.baseUrl().isBlank()) {
            _signedIn.value = false
            return@withContext false
        }
        val payload = buildJsonObject {
            put("grant_type", "refresh_token")
            put("refresh_token", refreshToken)
        }
        val result = requestToken(payload)
        result.fold(
            onSuccess = { tokens ->
                config.accessToken = tokens.accessToken
                tokens.refreshToken?.let { tokenStore.saveRefreshToken(it) }
                _signedIn.value = tokens.accessToken != null
            },
            onFailure = { error ->
                _lastError.value = error.message
                _signedIn.value = false
            },
        )
        _signedIn.value
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        tokenStore.clear()
        config.accessToken = null
        _signedIn.value = false
    }

    fun clearError() {
        _lastError.value = null
    }

    private data class Tokens(val accessToken: String?, val refreshToken: String?)

    private fun requestToken(payload: JsonObject): Result<Tokens> {
        val base = config.baseUrl()
        if (base.isBlank()) return Result.failure(IllegalStateException("还没有配置 Worker 地址"))
        val body = payload.toString().toRequestBody(JSON_MEDIA)
        val builder = Request.Builder()
            .url("$base/auth/token")
            .post(body)
            .header("Accept", "application/json")
        if (config.appKey.isNotBlank()) builder.header("X-App-Key", config.appKey)

        return runCatching {
            client.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                if (!response.isSuccessful) {
                    val message = root?.get("errors")?.toString()
                        ?: root?.get("message")?.jsonPrimitive?.content
                        ?: "HTTP ${response.code}"
                    error(message)
                }
                val inner = root?.get("response")?.jsonObject
                    ?: error("响应缺少 response 字段")
                Tokens(
                    accessToken = inner["access_token"]?.jsonPrimitive?.content,
                    refreshToken = inner["refresh_token"]?.jsonPrimitive?.content,
                )
            }
        }.onFailure { _lastError.value = it.message }
    }

    private fun randomUrlSafe(length: Int): String {
        val bytes = ByteArray(length)
        SecureRandom().nextBytes(bytes)
        return base64Url(bytes).take(length)
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
