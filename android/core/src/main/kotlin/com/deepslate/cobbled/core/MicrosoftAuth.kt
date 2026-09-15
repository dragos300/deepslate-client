package com.deepslate.cobbled.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Microsoft → Xbox Live → Minecraft Services login.
 *
 * Two paths, both require a Microsoft account that owns Java Edition:
 * 1. Xbox Live native client (same as PojavLauncher): WebView / Custom Tabs,
 *    including the mobile `ms-xal-00000000402b5328://` redirect.
 * 2. Azure device code (Deepslate's public client): sign in in Chrome with a short code.
 */
class MicrosoftAuthClient(
    private val http: OkHttpClient = cobbledHttpClient(),
) {
    fun authorizeUrl(): String {
        val scope = enc(SCOPE)
        val redirect = enc(REDIRECT_URI)
        return "$AUTH_BASE?client_id=$CLIENT_ID&response_type=code&scope=$scope&redirect_url=$redirect"
    }

    fun isLoginRedirect(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith(MS_XAL_PREFIX) ||
            (lower.contains("login.live.com") && lower.contains("oauth20_desktop"))
    }

    fun isCancelRedirect(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("res=cancel") ||
            lower.contains("error=access_denied") ||
            lower.contains("error=access_denied".replace("_", "%5f"))
    }

    fun codeFromRedirect(url: String): String? {
        if (!isLoginRedirect(url)) return null
        if (isCancelRedirect(url)) {
            throw AuthException("Microsoft login was cancelled.")
        }
        queryParam(url, "error")?.let { err ->
            val desc = queryParam(url, "error_description") ?: err
            throw AuthException("Microsoft login failed: ${decode(desc)}")
        }
        return queryParam(url, "code")?.takeIf { it.isNotBlank() }
    }

    suspend fun loginWithXboxCode(code: String): McAccount = withContext(Dispatchers.IO) {
        val tokens = exchangeXboxCode(code)
        completeMinecraftLogin(
            msaAccess = tokens.accessToken,
            refresh = tokens.refreshToken.orEmpty(),
            kind = AuthKind.XBOX_LIVE,
            rpsTicket = tokens.accessToken,
        )
    }

    suspend fun refresh(account: McAccount): McAccount = withContext(Dispatchers.IO) {
        val refresh = account.refreshToken
        if (refresh.isBlank()) throw AuthException("Microsoft session expired. Sign in again.")
        when (account.kind) {
            AuthKind.AZURE -> {
                val tokens = refreshAzure(refresh)
                completeMinecraftLogin(
                    msaAccess = tokens.accessToken,
                    refresh = tokens.refreshToken ?: refresh,
                    kind = AuthKind.AZURE,
                    rpsTicket = "d=${tokens.accessToken}",
                )
            }
            AuthKind.XBOX_LIVE -> {
                val tokens = refreshXbox(refresh)
                completeMinecraftLogin(
                    msaAccess = tokens.accessToken,
                    refresh = tokens.refreshToken ?: refresh,
                    kind = AuthKind.XBOX_LIVE,
                    rpsTicket = tokens.accessToken,
                )
            }
        }
    }

    /** @deprecated use [refresh] with the stored account so Azure vs Xbox refresh stays correct. */
    suspend fun refresh(refreshToken: String): McAccount = withContext(Dispatchers.IO) {
        runCatching { refreshXbox(refreshToken) }.fold(
            onSuccess = { tokens ->
                completeMinecraftLogin(tokens.accessToken, tokens.refreshToken ?: refreshToken, AuthKind.XBOX_LIVE, tokens.accessToken)
            },
            onFailure = {
                val tokens = refreshAzure(refreshToken)
                completeMinecraftLogin(tokens.accessToken, tokens.refreshToken ?: refreshToken, AuthKind.AZURE, "d=${tokens.accessToken}")
            },
        )
    }

    suspend fun startDeviceCode(): DeviceCodeSession = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("client_id", AZURE_CLIENT_ID)
            .add("scope", AZURE_SCOPE)
            .build()
        val session = postForm(AZURE_DEVICE_CODE_URL, body, DeviceCodeSession.serializer())
        if (session.userCode.isBlank() || session.deviceCode.isBlank()) {
            throw AuthException("Microsoft did not return a login code. Try the on-screen sign-in.")
        }
        session
    }

    suspend fun pollDeviceCode(session: DeviceCodeSession): McAccount = withContext(Dispatchers.IO) {
        val interval = (session.interval.takeIf { it > 0 } ?: 5).coerceAtMost(15)
        val deadline = System.currentTimeMillis() + (session.expiresIn.takeIf { it > 0 } ?: 900) * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(interval * 1000L)
            val body = FormBody.Builder()
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .add("client_id", AZURE_CLIENT_ID)
                .add("device_code", session.deviceCode)
                .build()
            val req = Request.Builder().url(AZURE_TOKEN_URL).post(body).build()
            val outcome = http.newCall(req).execute().use { res ->
                val text = res.body?.string().orEmpty()
                val err = runCatching {
                    CobbledJson.decodeFromString(OAuthError.serializer(), text)
                }.getOrNull()?.error
                when {
                    err == "authorization_pending" || err == "slow_down" -> null
                    err == "expired_token" -> throw AuthException("That login code expired. Request a new one.")
                    err == "authorization_declined" -> throw AuthException("Microsoft login was declined.")
                    !res.isSuccessful -> throw AuthException("Microsoft login failed (${res.code}): ${text.take(200)}")
                    else -> CobbledJson.decodeFromString(LiveTokens.serializer(), text)
                }
            }
            if (outcome == null) continue
            return@withContext completeMinecraftLogin(
                msaAccess = outcome.accessToken,
                refresh = outcome.refreshToken.orEmpty(),
                kind = AuthKind.AZURE,
                rpsTicket = "d=${outcome.accessToken}",
            )
        }
        throw AuthException("Timed out waiting for Microsoft login.")
    }

    private fun exchangeXboxCode(code: String): LiveTokens {
        val body = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("code", code)
            .add("grant_type", "authorization_code")
            .add("redirect_url", REDIRECT_URI)
            .add("scope", SCOPE)
            .build()
        return postForm(TOKEN_URL, body, LiveTokens.serializer())
    }

    private fun refreshXbox(refreshToken: String): LiveTokens {
        val body = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .add("redirect_url", REDIRECT_URI)
            .add("scope", SCOPE)
            .build()
        return postForm(TOKEN_URL, body, LiveTokens.serializer())
    }

    private fun refreshAzure(refreshToken: String): LiveTokens {
        val body = FormBody.Builder()
            .add("client_id", AZURE_CLIENT_ID)
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .add("scope", AZURE_SCOPE)
            .build()
        return postForm(AZURE_TOKEN_URL, body, LiveTokens.serializer())
    }

    private fun completeMinecraftLogin(
        msaAccess: String,
        refresh: String,
        kind: AuthKind,
        rpsTicket: String,
    ): McAccount {
        val xbox = try {
            postJson(XBOX_AUTH_URL, XboxAuthRequest.rps(rpsTicket), XboxToken.serializer())
        } catch (err: AuthException) {
            if (rpsTicket.startsWith("d=")) throw err
            postJson(XBOX_AUTH_URL, XboxAuthRequest.rps("d=$msaAccess"), XboxToken.serializer())
        }
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
        fetchOwnedItems(mc.accessToken)
        val profile = getJson(MC_PROFILE_URL, mc.accessToken, McProfile.serializer())
        if (profile.id.isBlank() || profile.name.isBlank()) {
            throw AuthException("Could not load Minecraft profile. Open minecraft.net once, then retry.")
        }
        return McAccount(
            uuid = profile.id.replace("-", ""),
            name = profile.name,
            accessToken = mc.accessToken,
            refreshToken = refresh,
            xuid = xstsUhs,
            kind = kind,
        )
    }

    private fun fetchOwnedItems(mcAccess: String) {
        val req = Request.Builder()
            .url(MC_STORE_URL)
            .header("Authorization", "Bearer $mcAccess")
            .get()
            .build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful && res.code != 404) {
                val text = res.body?.string().orEmpty()
                throw AuthException("Could not check Java Edition ownership (${res.code}): ${text.take(160)}")
            }
        }
    }

    private fun mapXsts(err: AuthException): AuthException {
        val msg = err.message ?: return err
        return when {
            msg.contains("2148916233") ->
                AuthException("This Microsoft account does not have an Xbox profile. Create one at xbox.com, then retry.")
            msg.contains("2148916238") ->
                AuthException("This Xbox account cannot play. A child account must be in a Microsoft family.")
            else -> err
        }
    }

    private fun <T> postForm(
        url: String,
        body: FormBody,
        deserializer: kotlinx.serialization.DeserializationStrategy<T>,
    ): T {
        val req = Request.Builder().url(url).header("Accept", "application/json").post(body).build()
        return execute(req, deserializer)
    }

    private fun <T> postJson(
        url: String,
        payload: Any,
        deserializer: kotlinx.serialization.DeserializationStrategy<T>,
    ): T {
        val encoded = when (payload) {
            is XboxAuthRequest -> CobbledJson.encodeToString(XboxAuthRequest.serializer(), payload)
            is XstsRequest -> CobbledJson.encodeToString(XstsRequest.serializer(), payload)
            is McLoginRequest -> CobbledJson.encodeToString(McLoginRequest.serializer(), payload)
            else -> error("Unsupported encode type ${payload::class.simpleName}")
        }
        val req = Request.Builder().url(url).post(encoded.toRequestBody(JSON)).build()
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
                    throw AuthException(
                        "This Microsoft account does not own Minecraft Java Edition, or the profile is not created yet. Sign in once at minecraft.net, then retry.",
                    )
                }
                val xerr = Regex("\"XErr\"\\s*:\\s*(\\d+)").find(text)?.groupValues?.getOrNull(1)
                val oauth = runCatching { CobbledJson.decodeFromString(OAuthError.serializer(), text) }.getOrNull()
                val detail = xerr?.let { "XErr $it" }
                    ?: oauth?.errorDescription
                    ?: oauth?.error
                    ?: text.take(240).ifBlank { res.message }
                throw AuthException("Auth request failed (${res.code}): $detail")
            }
            if (text.isBlank()) throw AuthException("Empty auth response from ${request.url.host}")
            return CobbledJson.decodeFromString(deserializer, text)
        }
    }

    companion object {
        const val CLIENT_ID = "00000000402b5328"
        const val REDIRECT_URI = "https://login.live.com/oauth20_desktop.srf"
        const val SCOPE = "service::user.auth.xboxlive.com::MBI_SSL"
        const val AUTH_BASE = "https://login.live.com/oauth20_authorize.srf"
        const val TOKEN_URL = "https://login.live.com/oauth20_token.srf"
        const val MS_XAL_PREFIX = "ms-xal-00000000402b5328"
        const val AZURE_CLIENT_ID = "7458628c-7134-44d6-a6c6-a2d2927711ba"
        const val AZURE_SCOPE = "XboxLive.signin offline_access"
        const val AZURE_DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"
        const val AZURE_TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"
        const val XBOX_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate"
        const val XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize"
        const val MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox"
        const val MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile"
        const val MC_STORE_URL = "https://api.minecraftservices.com/entitlements/mcstore"
        private val JSON = "application/json".toMediaType()
    }
}

class AuthException(message: String) : Exception(message)

@Serializable
enum class AuthKind { XBOX_LIVE, AZURE }

@Serializable
data class LiveTokens(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
)

@Serializable
data class DeviceCodeSession(
    @SerialName("user_code") val userCode: String,
    @SerialName("device_code") val deviceCode: String,
    @SerialName("verification_uri") val verificationUri: String = "https://www.microsoft.com/link",
    @SerialName("expires_in") val expiresIn: Int = 900,
    val interval: Int = 5,
    val message: String = "",
)

@Serializable
data class OAuthError(
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)

@Serializable
data class XboxAuthRequest(
    @SerialName("Properties") val properties: XboxAuthProperties,
    @SerialName("RelyingParty") val relyingParty: String,
    @SerialName("TokenType") val tokenType: String = "JWT",
) {
    companion object {
        fun rps(rpsTicket: String) = XboxAuthRequest(
            properties = XboxAuthProperties(
                authMethod = "RPS",
                siteName = "user.auth.xboxlive.com",
                rpsTicket = rpsTicket,
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

internal fun queryParam(url: String, name: String): String? {
    val query = url.substringAfter('?', "").substringBefore('#')
    if (query.isEmpty()) return null
    return query.split('&').mapNotNull { part ->
        val eq = part.indexOf('=')
        if (eq <= 0) return@mapNotNull null
        val key = decode(part.substring(0, eq))
        if (key != name) return@mapNotNull null
        decode(part.substring(eq + 1))
    }.firstOrNull()
}

private fun enc(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

private fun decode(value: String): String =
    URLDecoder.decode(value.replace("+", "%20"), StandardCharsets.UTF_8.name())
