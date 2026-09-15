package com.deepslate.cobbled.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class VersionRepository(
    private val http: OkHttpClient = cobbledHttpClient(),
) {
    @Volatile
    private var manifest: MojangManifest? = null

    suspend fun manifest(): MojangManifest = withContext(Dispatchers.IO) {
        manifest ?: fetchManifest().also { manifest = it }
    }

    suspend fun latestRelease(): String = manifest().latest.release

    suspend fun releaseIds(): List<String> =
        manifest().versions.filter { it.type == "release" }.map { it.id }

    suspend fun fabricSupported(stableOnly: Boolean = true): Set<String> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(FABRIC_GAME).get().build()
        http.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw IllegalStateException("Fabric meta error (${res.statusCode()})")
            CobbledJson.decodeFromString(ListSerializer(FabricGame.serializer()), text)
                .filter { !stableOnly || it.stable }
                .map { it.version }
                .toSet()
        }
    }

    suspend fun groups(fabricOnly: Boolean = false): List<VersionGroup> {
        val releases = releaseIds()
        val fabric = if (fabricOnly) {
            runCatching { fabricSupported() }.getOrNull()
        } else {
            null
        }
        return VersionCatalog.groupReleases(releases, fabric)
    }

    suspend fun resolveMinecraft(ref: VersionRef): String {
        val mc = ref.minecraft
        if (mc.isBlank() || mc == "latest" || mc == "latest-release") return latestRelease()
        if (mc == "latest-snapshot") return manifest().latest.snapshot
        return mc
    }

    suspend fun latestFabricLoader(minecraft: String): String = withContext(Dispatchers.IO) {
        val url = "$FABRIC_LOADER/${minecraft}".toHttpUrl()
        val req = Request.Builder().url(url).get().build()
        http.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw IllegalStateException("No Fabric loader for $minecraft")
            val loaders = CobbledJson.decodeFromString(ListSerializer(FabricLoaderEntry.serializer()), text)
            loaders.firstOrNull { it.loader.stable }?.loader?.version
                ?: loaders.firstOrNull()?.loader?.version
                ?: throw IllegalStateException("No Fabric loader for $minecraft")
        }
    }

    suspend fun versionMeta(id: String): Pair<MojangVersionMeta, String> = withContext(Dispatchers.IO) {
        val entry = manifest().versions.find { it.id == id }
            ?: throw IllegalStateException("Unknown version $id")
        val req = Request.Builder().url(entry.url).get().build()
        http.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw IllegalStateException("Version meta failed (${res.code})")
            CobbledJson.decodeFromString(MojangVersionMeta.serializer(), text) to text
        }
    }

    private fun fetchManifest(): MojangManifest {
        val req = Request.Builder().url(MANIFEST).get().build()
        http.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw IllegalStateException("Version manifest failed (${res.code})")
            return CobbledJson.decodeFromString(MojangManifest.serializer(), text)
        }
    }

    private fun okhttp3.Response.statusCode() = code

    companion object {
        const val MANIFEST = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"
        const val FABRIC_GAME = "https://meta.fabricmc.net/v2/versions/game"
        const val FABRIC_LOADER = "https://meta.fabricmc.net/v2/versions/loader"
    }
}

@Serializable
data class MojangManifest(
    val latest: MojangLatest,
    val versions: List<MojangVersion>,
)

@Serializable
data class MojangLatest(val release: String, val snapshot: String)

@Serializable
data class MojangVersion(
    val id: String,
    val type: String,
    val url: String,
    val releaseTime: String? = null,
)

@Serializable
data class MojangVersionMeta(
    val id: String? = null,
    val downloads: MojangDownloads? = null,
    val libraries: List<MojangLibrary> = emptyList(),
    val assetIndex: MojangAssetIndex? = null,
)

@Serializable
data class MojangDownloads(
    val client: MojangArtifact? = null,
)

@Serializable
data class MojangArtifact(
    val url: String,
    val sha1: String? = null,
    val size: Long? = null,
)

@Serializable
data class MojangLibrary(
    val name: String,
    val downloads: MojangLibraryDownloads? = null,
    val rules: List<MojangRule>? = null,
)

@Serializable
data class MojangLibraryDownloads(
    val artifact: MojangPathArtifact? = null,
)

@Serializable
data class MojangPathArtifact(
    val path: String? = null,
    val url: String? = null,
    val sha1: String? = null,
    val size: Long? = null,
)

@Serializable
data class MojangRule(
    val action: String,
    val os: MojangOsRule? = null,
)

@Serializable
data class MojangOsRule(
    val name: String? = null,
)

@Serializable
data class MojangAssetIndex(
    val id: String,
    val url: String,
)

@Serializable
data class FabricGame(val version: String, val stable: Boolean = true)

@Serializable
data class FabricLoaderEntry(val loader: FabricLoader)

@Serializable
data class FabricLoader(val version: String, val stable: Boolean = false)

@Serializable
data class ModrinthSearchResponse(
    val hits: List<ModrinthHit> = emptyList(),
)

@Serializable
data class ModrinthHit(
    @SerialName("project_id") val projectId: String,
    val slug: String,
    val title: String,
    val description: String = "",
    val downloads: Int = 0,
    @SerialName("icon_url") val iconUrl: String? = null,
    val author: String = "",
)
