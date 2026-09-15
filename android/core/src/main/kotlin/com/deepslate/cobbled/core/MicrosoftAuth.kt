package com.deepslate.cobbled.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Microsoft → Xbox Live → Minecraft Services login.
 *
 * Uses the public Minecraft Launcher Xbox client id (same family of flow as
 * other Java launchers). Requires a Microsoft account that owns Java Edition.
 */
class MicrosoftAuthClient(
    private val http: OkHttpClient = cobbledHttpClient(),
    private val json: JsonCodec = JsonCodec(),
) {
    class JsonCodec {
        fun <T> decode(text: String, deserializer: kotlinx.serialization.DeserializationStrategy<T>): T =
            CobbledJson.decodeFromString(deserializer, text)

        fun encode(value: Any): String = when (value) {
            is XboxAuthRequest -> CobbledJson.encodeToString(XboxAuthRequest.serializer(), value)
            is XstsRequest -> CobbledJson.encodeToString(XstsRequest.serializer(), value)
            is McLoginRequest -> CobbledJson.encodeToString(McLoginRequest.serializer(), value)
            else -> error("Unsupported encode type ${value::class.simpleName}")
        }
    }

    fun authorizeUrl(): HttpUrl =
        AUTH_BASE.toHttpUrl().newBuilder()
            .addQueryParameter("client_id", CLIENT_ID)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("scope", SCOPE)
            .addQueryParameter("redirect_uri", REDIRECT_URI)
            .addQueryParameter("prompt", "select_account")
            .build()

    fun codeFromRedirect(url: String): String? {
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        if (!isRedirect(httpUrl)) return null
        httpUrl.queryParameter("error")?.let { err ->
            val desc = httpUrl.queryParameter("error_description") ?: err
            throw AuthException("Microsoft login failed: $desc")
        }
        return httpUrl.queryParameter("code")?.takeIf { it.isNotBlank() }
    }

    fun isRedirect(url: String): Boolean = url.toHttpUrlOrNull()?.let { isRedirect(it) } ?: false

    fun isRedirect(url: HttpUrl): Boolean {
        val host = url.host.lowercase()
        val path = url.encodedPath.lowercase()
        return (host == "login.live.com" && path.contains("oauth20_desktop")) ||
            (host == "login.microsoftonline.com" && path.contains("nativeclient"))
    }

    suspend fun loginWithCode(code: String): McAccount = withContext(Dispatchers.IO) {
        val tokens = exchangeCode(code)
        completeMinecraftLogin(tokens.accessToken, tokens.refreshToken.orEmpty())
    }

    suspend fun refresh(refreshToken: String): McAccount = withContext(Dispatchers.IO) {
        val tokens = refreshTokens(refreshToken)
        completeMinecraftLogin(tokens.accessToken, tokens.refreshToken ?: refreshToken)
    }

    private fun exchangeCode(code: String): LiveTokens {
        val body = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("code", code)
            .add("grant_type", "authorization_code")
            .add("redirect_uri", REDIRECT_URI)
            .build()
        return postForm(TOKEN_URL, body, LiveTokens.serializer())
    }

    private fun refreshTokens(refreshToken: String): LiveTokens {
        val body = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .add("redirect_uri", REDIRECT_URI)
            .build()
        return postForm(TOKEN_URL, body, LiveTokens.serializer())
    }

    private fun completeMinecraftLogin(msaAccess: String, refresh: String): McAccount {
        val xbox = postJson(XBOX_AUTH_URL, XboxAuthRequest.rps(msaAccess), XboxToken.serializer())
        val uhs = xbox.displayClaims.xui.firstOrNull()?.uhs
            ?: throw AuthException("Xbox login succeeded but user hash was missing.")
        val xsts = try {
            postJson(XSTS_URL, XstsRequest.minecraft(xbox.token), XboxToken.serializer())
        } catch (err: AuthException) {
            throw mapXsts(err)
        }
        val xstsUhs = xsts.displayClaims.xui.firstOrNull()?.uhs ?: uhs
        val mc = postJson(
            MC_LOGIN_URL,
            McLoginRequest("XBL3.0 x=$xstsUhs;${xsts.token}"),
            McToken.serializer(),
        )
        val profile = getJson(MC_PROFILE_URL, mc.accessToken, McProfile.serializer())
        if (profile.id.isBlank() || profile.name.isBlank()) {
            throw AuthException("Could not load Minecraft profile. Make sure this Microsoft account owns Java Edition.")
        }
        return McAccount(
            uuid = profile.id.replace("-", ""),
            name = profile.name,
            accessToken = mc.accessToken,
            refreshToken = refresh,
            xuid = xstsUhs,
        )
    }

    private fun mapXsts(err: AuthException): AuthException {
        val msg = err.message ?: return err
        return when {
            msg.contains("2148916233") ->
                AuthException("This Microsoft account does not have an Xbox profile. Create one at xbox.com, then retry.")
            msg.contains("2148916238") ->
                AuthException("This Xbox account cannot play. It may be a child account or from an unsupported region.")
            else -> err
        }
    }

    private fun <T> postForm(
        url: String,
        body: FormBody,
        deserializer: kotlinx.serialization.DeserializationStrategy<T>,
    ): T {
        val req = Request.Builder().url(url).post(body).build()
        return execute(req, deserializer)
    }

    private fun <T> postJson(
        url: String,
        payload: Any,
        deserializer: kotlinx.serialization.DeserializationStrategy<T>,
    ): T {
        val body = json.encode(payload).toRequestBody(JSON)
        val req = Request.Builder().url(url).post(body).build()
        return execute(req, deserializer)
    }

    private fun <T> getJson(
        url: String,
        bearer: String,
        deserializer: kotlinx.serialization.DeserializationStrategy<T>,
    ): T {
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $bearer")
            .get()
            .build()
        return execute(req, deserializer)
    }

    private fun <T> execute(
        request: Request,
        deserializer: kotlinx.serialization.DeserializationStrategy<T>,
    ): T {
        http.newCall(request).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                if (res.code == 404 && request.url.toString().contains("minecraft/profile")) {
                    throw AuthException("This Microsoft account does not own Minecraft Java Edition.")
                }
                val xerr = Regex("\"XErr\"\\s*:\\s*(\\d+)").find(text)?.groupValues?.getOrNull(1)
                val detail = xerr?.let { "XErr $it" } ?: text.take(240).ifBlank { res.message }
                throw AuthException("Auth request failed (${res.code}): $detail")
            }
            if (text.isBlank()) throw AuthException("Empty auth response from ${request.url.host}")
            return json.decode(text, deserializer)
        }
    }

    private fun String.toHttpUrlOrNull(): HttpUrl? = try {
        toHttpUrl()
    } catch (_: IllegalArgumentException) {
        null
    }

    companion object {
        const val CLIENT_ID = "00000000402b5328"
        const val REDIRECT_URI = "https://login.live.com/oauth20_desktop.srf"
        const val SCOPE = "service::user.auth.xboxlive.com::MBI_SSL"
        const val AUTH_BASE = "https://login.live.com/oauth20_authorize.srf"
        const val TOKEN_URL = "https://login.live.com/oauth20_token.srf"
        const val XBOX_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate"
        const val XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize"
        const val MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox"
        const val MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile"

        private val JSON = "application/json".toMediaType()
    }
}

class AuthException(message: String) : Exception(message)

@Serializable
data class LiveTokens(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
)

@Serializable
data class XboxAuthRequest(
    @SerialName("Properties") val properties: XboxAuthProperties,
    @SerialName("RelyingParty") val relyingParty: String,
    @SerialName("TokenType") val tokenType: String = "JWT",
) {
    companion object {
        fun rps(msaAccess: String) = XboxAuthRequest(
            properties = XboxAuthProperties(
                authMethod = "RPS",
                siteName = "user.auth.xboxlive.com",
                rpsTicket = if (msaAccess.startsWith("d=")) msaAccess else "d=$msaAccess",
            ),
            relyingParty = "http://auth.xboxlive.com",
        )
    }
}

@Serializable
data class XboxAuthProperties(
    @SerialName("AuthMethod") val authMethod: String,
    @SerialName("SiteName") val siteName: String,
    @SerialName("RpsTicket") val rpsTicket: String,
)

@Serializable
data class XstsRequest(
    @SerialName("Properties") val properties: XstsProperties,
    @SerialName("RelyingParty") val relyingParty: String,
    @SerialName("TokenType") val tokenType: String = "JWT",
) {
    companion object {
        fun minecraft(userToken: String) = XstsRequest(
            properties = XstsProperties(sandboxId = "RETAIL", userTokens = listOf(userToken)),
            relyingParty = "rp://api.minecraftservices.com/",
        )
    }
}

@Serializable
data class XstsProperties(
    @SerialName("SandboxId") val sandboxId: String,
    @SerialName("UserTokens") val userTokens: List<String>,
)

@Serializable
data class XboxToken(
    @SerialName("Token") val token: String,
    @SerialName("DisplayClaims") val displayClaims: XboxDisplayClaims,
)

@Serializable
data class XboxDisplayClaims(
    val xui: List<XboxUserClaim> = emptyList(),
)

@Serializable
data class XboxUserClaim(
    val uhs: String? = null,
)

@Serializable
data class McLoginRequest(
    val identityToken: String,
)

@Serializable
data class McToken(
    @SerialName("access_token") val accessToken: String,
)

@Serializable
data class McProfile(
    val id: String,
    val name: String,
)
