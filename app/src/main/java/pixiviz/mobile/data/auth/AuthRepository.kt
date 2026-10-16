package pixiviz.mobile.data.auth

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import pixiviz.mobile.BuildConfig
import pixiviz.mobile.data.remote.RelayConfig
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Device-side pixiv sign-in, talking straight to oauth.secure.pixiv.net.
 *
 * The Worker cannot be used here: pixiv answers Cloudflare egress with a block page
 * (\u30d6\u30ed\u30c3\u30af\u3055\u308c\u307e\u3057\u305f). Requests therefore come from the device, which also keeps the
 * account's traffic pattern normal - relaying through a server is what got pxve-api's
 * upstream account permanently banned.
 *
 * The refresh token stays sealed by the Android Keystore; only short-lived access tokens
 * ever live in memory.
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

    /** Pulls `code` out of whatever URL the login flow landed on. */
    fun extractCode(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        Regex("[?&]code=([^&\\s]+)").find(text)?.let { return it.groupValues[1] }
        return if (text.matches(Regex("[A-Za-z0-9._~-]+")) && text.length > 8) text else null
    }

    suspend fun exchangeCode(code: String, verifier: String): Result<Unit> = withContext(Dispatchers.IO) {
        tokenRequest(
            FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("code_verifier", verifier)
                .build(),
        ).map { tokens ->
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
        val result = tokenRequest(
            FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build(),
        )
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

    private fun tokenRequest(body: FormBody): Result<Tokens> {
        val url = BuildConfig.PIXIV_OAUTH_BASE.trimEnd('/') + "/auth/token"
        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("Accept", "application/json")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                if (!response.isSuccessful) {
                    val message = root?.get("errors")?.toString()
                        ?: root?.get("error")?.jsonPrimitive?.content
                        ?: "HTTP ${response.code}"
                    error(if (response.code == 403) "pixiv 拒绝了这次请求，请稍后重试" else message)
                }
                val inner = root?.get("response")?.jsonObject ?: error("响应缺少 response 字段")
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
}
